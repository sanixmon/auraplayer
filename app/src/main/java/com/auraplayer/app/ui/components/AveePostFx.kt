package com.auraplayer.app.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.auraplayer.app.data.artwork.toArtworkRequest
import com.auraplayer.app.data.model.BackgroundVisualMode
import com.auraplayer.app.data.model.Track
import com.auraplayer.app.data.model.VisualizerConfig
import com.auraplayer.app.data.model.VisualizerFrame
import com.auraplayer.app.playback.VisualizerBus
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

private const val RGB_SPLIT_AGSL = """
uniform shader content;
uniform float2 iResolution;
uniform float splitPx;

half4 main(float2 fragCoord) {
    if (splitPx < 0.35) {
        return content.eval(fragCoord);
    }
    float2 center = iResolution * 0.5;
    float2 delta = fragCoord - center;
    float len = max(length(delta), 0.001);
    float2 dir = delta / len;
    float radialFactor = clamp(len / max(iResolution.x, iResolution.y) * 1.65, 0.25, 1.0);
    float2 offset = dir * (splitPx * radialFactor);

    half4 gSample = content.eval(fragCoord);
    half r = content.eval(fragCoord + offset).r;
    half b = content.eval(fragCoord - offset).b;
    return half4(r, gSample.g, b, gSample.a);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun buildRgbSplitRenderEffect(
    shader: RuntimeShader,
    widthPx: Float,
    heightPx: Float,
    splitPx: Float
): androidx.compose.ui.graphics.RenderEffect {
    shader.setFloatUniform("iResolution", widthPx, heightPx)
    shader.setFloatUniform("splitPx", splitPx)
    return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
}

@Composable
fun rememberBeatPunchState(
    frame: VisualizerFrame,
    config: VisualizerConfig,
    isPlaying: Boolean,
    demoWhenIdle: Boolean,
    visualizerBus: VisualizerBus?,
    enabled: Boolean
): Pair<Float, Float> {
    var smoothedPunch by remember { mutableFloatStateOf(0f) }
    var animTimeSec by remember { mutableFloatStateOf(0f) }

    val latestFrame by rememberUpdatedState(frame)
    val latestConfig by rememberUpdatedState(config)
    val emptyScratch = remember { FloatArray(0) }
    val scalarScratch = remember { FloatArray(VisualizerBus.SCALAR_COUNT) }

    LaunchedEffect(isPlaying, demoWhenIdle, enabled, visualizerBus) {
        if (!enabled) {
            smoothedPunch = 0f
            return@LaunchedEffect
        }
        var lastNanos = 0L
        var rollingBass = 0f
        var settleFrames = if (isPlaying || demoWhenIdle) Int.MAX_VALUE else 45
        while (settleFrames > 0) {
            withFrameNanos { nowNanos ->
                val dt = if (lastNanos != 0L) {
                    ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                } else {
                    0.016f
                }
                lastNanos = nowNanos
                if (isPlaying || demoWhenIdle) {
                    animTimeSec += dt
                } else {
                    settleFrames--
                }

                val cfg = latestConfig
                val punchScale = cfg.beatPunch.coerceIn(0f, 2.0f)
                val targetPunch = when {
                    isPlaying -> {
                        val hasBus = visualizerBus?.sampleInterpolatedInto(
                            delayMs = cfg.syncOffsetMs,
                            outBands = emptyScratch,
                            outWave = emptyScratch,
                            outScalars = scalarScratch
                        ) == true
                        val sens = cfg.sensitivity.coerceIn(0.5f, 2.5f)
                        val liveBass = ((if (hasBus) scalarScratch[VisualizerBus.SCALAR_BASS] else latestFrame.bassEnergy) * sens)
                            .coerceIn(0f, 1f)
                        val liveBeat = ((if (hasBus) scalarScratch[VisualizerBus.SCALAR_BEAT] else latestFrame.beatPulse) * sens)
                            .coerceIn(0f, 1f)
                        rollingBass += (liveBass - rollingBass) * (1f - exp(-dt / 0.42f))
                        val bassTransient = ((liveBass - rollingBass) * 2.5f).coerceIn(0f, 1f)
                        (max(
                            liveBeat,
                            max(bassTransient * 0.85f, liveBass.pow(2.4f) * 0.40f)
                        ) * punchScale).coerceIn(0f, 1.35f)
                    }
                    demoWhenIdle -> {
                        val pulseWave = ((sin(animTimeSec * 3.1f) + 1f) * 0.5f).pow(2.4f)
                        (pulseWave * 0.8f * punchScale).coerceIn(0f, 1.35f)
                    }
                    else -> {
                        rollingBass = 0f
                        0f
                    }
                }
                val tau = if (targetPunch > smoothedPunch) 0.012f else 0.115f
                smoothedPunch += (targetPunch - smoothedPunch) * (1f - exp(-dt / tau))
            }
        }
    }

    return smoothedPunch to animTimeSec
}

/**
 * Avee-inspired reactive background supporting Accent Gradient, Blurred Album Art
 * (with subtle bass-driven breathing zoom), OLED Black, and cinematic edge Vignette.
 */
@Composable
fun AveeBackgroundLayer(
    track: Track?,
    config: VisualizerConfig,
    punch: Float,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val bgColor = MaterialTheme.colorScheme.background

    Box(modifier = modifier.fillMaxSize()) {
        when (config.backgroundMode) {
            BackgroundVisualMode.ACCENT_GRADIENT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.16f + punch * 0.06f),
                                    secondary.copy(alpha = 0.08f + punch * 0.03f),
                                    bgColor
                                )
                            )
                        )
                )
            }

            BackgroundVisualMode.PURE_BLACK -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF040509))
                )
            }

            BackgroundVisualMode.BLURRED_ALBUM_ART -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.22f),
                                    secondary.copy(alpha = 0.12f),
                                    Color(0xFF06080F)
                                )
                            )
                        )
                )
                if (track != null && track.isAvailable) {
                    val bgScale = 1.14f + (punch * 0.06f).coerceIn(0f, 0.09f)
                    val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.blur(26.dp)
                    } else {
                        Modifier
                    }
                    AsyncImage(
                        model = track.toArtworkRequest(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = bgScale
                                scaleY = bgScale
                                alpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.56f else 0.30f
                            }
                            .then(blurModifier)
                    )
                    // Deep cinematic readability scrim over blurred cover art
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.42f),
                                        Color.Black.copy(alpha = 0.54f),
                                        Color.Black.copy(alpha = 0.78f)
                                    )
                                )
                            )
                    )
                }
            }
        }

        if (config.vignetteEnabled) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = max(size.width, size.height) * 0.78f
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1.0f to Color.Black.copy(alpha = 0.56f)
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = radius
                    )
                )
            }
        }
    }
}

/**
 * Applies Avee-inspired Beat Camera Shake (`BeatCamShake`) and GPU Chromatic Aberration
 * (`RGBSplitEffect` via AGSL RuntimeShader on Android 13+) without recomposing child content.
 */
@Composable
fun AveeStagePostFxBox(
    config: VisualizerConfig,
    punch: Float,
    timeSec: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(RGB_SPLIT_AGSL)
        } else {
            null
        }
    }

    val shakeIntensity = config.cameraShakeIntensity.coerceIn(0f, 1.5f)
    val rgbIntensity = config.rgbSplitIntensity.coerceIn(0f, 1.5f)

    Box(
        modifier = modifier.graphicsLayer {
            if (shakeIntensity > 0.01f && punch > 0.02f) {
                val shakeMag = (punch * shakeIntensity).coerceIn(0f, 1.5f)
                val maxOffsetPx = 11f * density * shakeMag
                translationX = sin(timeSec * 47f) * cos(timeSec * 29f) * maxOffsetPx
                translationY = cos(timeSec * 53f) * sin(timeSec * 31f) * maxOffsetPx
                rotationZ = sin(timeSec * 37f) * 1.45f * shakeMag
                val overscan = 1f + shakeMag * 0.032f
                scaleX = overscan
                scaleY = overscan
            } else {
                translationX = 0f
                translationY = 0f
                rotationZ = 0f
                scaleX = 1f
                scaleY = 1f
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                shader != null &&
                rgbIntensity > 0.01f &&
                size.width > 1f &&
                size.height > 1f
            ) {
                val splitPx = (punch * rgbIntensity * 11f * density).coerceIn(0f, 28f * density)
                renderEffect = if (splitPx >= 0.4f) {
                    buildRgbSplitRenderEffect(
                        shader = shader,
                        widthPx = size.width,
                        heightPx = size.height,
                        splitPx = splitPx
                    )
                } else {
                    null
                }
            } else {
                renderEffect = null
            }
        },
        content = content
    )
}
