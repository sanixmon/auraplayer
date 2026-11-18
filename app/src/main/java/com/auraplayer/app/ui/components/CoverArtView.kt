package com.auraplayer.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.auraplayer.app.data.artwork.toArtworkRequest
import com.auraplayer.app.data.model.CoverArtStyle
import com.auraplayer.app.data.model.Track
import com.auraplayer.app.playback.VisualizerBus
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

@Composable
fun CoverArtView(
    track: Track?,
    style: CoverArtStyle,
    isPlaying: Boolean,
    bassEnergy: Float,
    size: Dp,
    modifier: Modifier = Modifier,
    beatPulse: Float = 0f,
    sensitivity: Float = 1.3f,
    syncOffsetMs: Int = 0,
    demoWhenIdle: Boolean = false,
    visualizerBus: VisualizerBus? = null
) {
    var vinylAngle by remember { mutableFloatStateOf(0f) }
    var smoothedPunch by remember { mutableFloatStateOf(0f) }

    val latestBass by rememberUpdatedState(bassEnergy)
    val latestBeat by rememberUpdatedState(beatPulse)
    val latestSensitivity by rememberUpdatedState(sensitivity)
    val latestSyncDelay by rememberUpdatedState(syncOffsetMs)

    val emptyScratch = remember { FloatArray(0) }
    val scalarScratch = remember { FloatArray(VisualizerBus.SCALAR_COUNT) }

    LaunchedEffect(isPlaying, demoWhenIdle, style, visualizerBus) {
        var lastNanos = 0L
        var animSec = 0f
        var settleFrames = if (isPlaying || demoWhenIdle) Int.MAX_VALUE else 45
        while (settleFrames > 0) {
            withFrameNanos { nowNanos ->
                val dt = if (lastNanos != 0L) {
                    ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                } else {
                    0.016f
                }
                lastNanos = nowNanos

                if (isPlaying && style == CoverArtStyle.VINYL_DISC) {
                    vinylAngle = (vinylAngle + dt * (28f + smoothedPunch * 26f)) % 360f
                }

                val targetPunch = when {
                    isPlaying -> {
                        val hasBus = visualizerBus?.sampleInterpolatedInto(
                            delayMs = latestSyncDelay,
                            outBands = emptyScratch,
                            outWave = emptyScratch,
                            outScalars = scalarScratch
                        ) == true
                        val sens = latestSensitivity.coerceIn(0.5f, 2.5f)
                        val liveBass = (if (hasBus) scalarScratch[VisualizerBus.SCALAR_BASS] else latestBass) * sens
                        val liveBeat = (if (hasBus) scalarScratch[VisualizerBus.SCALAR_BEAT] else latestBeat) * sens
                        max(
                            liveBeat.coerceIn(0f, 1f) * 0.92f,
                            liveBass.coerceIn(0f, 1f).pow(1.65f) * 0.72f
                        ).coerceIn(0f, 1f)
                    }
                    demoWhenIdle -> {
                        animSec += dt
                        val pulseWave = ((sin(animSec * 3.1f) + 1f) * 0.5f).pow(2.4f)
                        (pulseWave * 0.8f).coerceIn(0f, 1f)
                    }
                    else -> {
                        settleFrames--
                        0f
                    }
                }

                val tau = if (targetPunch > smoothedPunch) 0.010f else 0.115f
                smoothedPunch += (targetPunch - smoothedPunch) * (1f - exp(-dt / tau))
            }
        }
    }

    val pulseScale = if (isPlaying || demoWhenIdle || smoothedPunch > 0.005f) {
        1f + (smoothedPunch * 0.145f).coerceIn(0f, 0.16f)
    } else {
        1f
    }
    val glowAlpha = (0.35f + smoothedPunch * 0.55f).coerceIn(0f, 0.95f)
    val dynamicElevation = 18.dp + (smoothedPunch * 18f).dp
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    when (style) {
        CoverArtStyle.COVER -> {
            val shape = RoundedCornerShape(22.dp)
            Box(
                modifier = modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                    .shadow(
                        elevation = dynamicElevation,
                        shape = shape,
                        ambientColor = accent.copy(alpha = glowAlpha),
                        spotColor = secondary.copy(alpha = glowAlpha)
                    )
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = (1.2f + smoothedPunch * 1.1f).dp,
                        color = Color.White.copy(alpha = 0.18f + smoothedPunch * 0.35f),
                        shape = shape
                    ),
                contentAlignment = Alignment.Center
            ) {
                CoverArtImage(track = track, iconSize = size * 0.36f)
            }
        }

        CoverArtStyle.VINYL_DISC -> {
            Box(
                modifier = modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        rotationZ = vinylAngle
                    }
                    .shadow(
                        elevation = dynamicElevation,
                        shape = CircleShape,
                        ambientColor = accent.copy(alpha = glowAlpha),
                        spotColor = Color.Black
                    )
                    .clip(CircleShape)
                    .background(Color(0xFF101218)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxR = this.size.minDimension / 2f
                    listOf(0.88f, 0.78f, 0.68f).forEach { factor ->
                        drawCircle(
                            color = Color.White.copy(alpha = 0.08f + smoothedPunch * 0.05f),
                            radius = maxR * factor,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(size * 0.68f)
                        .clip(CircleShape)
                        .border((2f + smoothedPunch * 1.2f).dp, accent.copy(alpha = 0.5f + smoothedPunch * 0.45f), CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CoverArtImage(
                        track = track,
                        iconSize = size * 0.26f,
                        isVinyl = true
                    )
                }
                // Center spindle hole
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0A0C12))
                        .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun CoverArtImage(
    track: Track?,
    iconSize: Dp,
    isVinyl: Boolean = false
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = if (isVinyl) Icons.Rounded.Album else Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(iconSize)
        )
        if (track != null && track.isAvailable) {
            AsyncImage(
                model = track.toArtworkRequest(),
                contentDescription = track.album,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
