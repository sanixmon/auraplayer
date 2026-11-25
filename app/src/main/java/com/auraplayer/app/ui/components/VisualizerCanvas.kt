package com.auraplayer.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import com.auraplayer.app.data.model.EmberDirection
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerConfig
import com.auraplayer.app.data.model.VisualizerDensity
import com.auraplayer.app.data.model.VisualizerFillMode
import com.auraplayer.app.data.model.VisualizerFrame
import com.auraplayer.app.data.model.VisualizerStyle
import com.auraplayer.app.playback.VisualizerBus
import com.auraplayer.app.ui.theme.AccentColors
import com.auraplayer.app.ui.theme.LocalAlbumAccentColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

data class VisualizerPalette(
    val start: Color,
    val mid: Color,
    val end: Color,
    val glow: Color
)

private data class RadialParticle(
    val angleRad: Float,
    var radiusProgress: Float,
    val speedFactor: Float,
    val baseSizeDp: Float,
    val colorIndex: Int
)

private class ShockwaveRing(
    var active: Boolean = false,
    var progress: Float = 0f,
    var strength: Float = 0f
)

private const val MAX_EMBER_PARTICLES = 160

private class EmberParticle(
    var x: Float = 0.5f,
    var y: Float = 1f,
    var vx: Float = 0f,
    var vy: Float = -0.3f,
    var life: Float = 0f,
    var lifeSpeed: Float = 0.45f,
    var baseSizeDp: Float = 2.4f,
    var turbulencePhase: Float = 0f,
    var turbulenceFreq: Float = 3.2f,
    var turbulenceAmp: Float = 0.045f,
    var flickerPhase: Float = 0f,
    var flickerSpeed: Float = 11f,
    var speedScale: Float = 1f,
    var mirrorSide: Boolean = false,
    var radialAngle: Float = 0f,
    var radialProgress: Float = 0f
)

private fun nextRand01(rngState: IntArray): Float {
    var x = rngState[0]
    x = x xor (x shl 13)
    x = x xor (x ushr 17)
    x = x xor (x shl 5)
    rngState[0] = x
    return ((x ushr 8) and 0xFFFFFF) / 16777216f
}

fun VisualizerColorScheme.toPalette(
    config: VisualizerConfig,
    dynamicAccent: AccentColors? = null
): VisualizerPalette = when (this) {
    VisualizerColorScheme.DYNAMIC -> {
        val p = dynamicAccent?.primary ?: Color(0xFF00E5FF)
        val s = dynamicAccent?.secondary ?: Color(0xFFA855F7)
        VisualizerPalette(
            start = p,
            mid = blendColorsHsv(p, s),
            end = s,
            glow = p
        )
    }
    VisualizerColorScheme.CUSTOM -> {
        val p = Color(config.customVizPrimaryArgb)
        val s = Color(config.customVizSecondaryArgb)
        VisualizerPalette(
            start = p,
            mid = blendColorsHsv(p, s),
            end = s,
            glow = p
        )
    }
    VisualizerColorScheme.EMBER -> VisualizerPalette(
        start = Color(0xFFFDE047),
        mid = Color(0xFFF97316),
        end = Color(0xFFDC2626),
        glow = Color(0xFFF97316)
    )
    VisualizerColorScheme.AURORA -> VisualizerPalette(
        start = Color(0xFF00E5FF),
        mid = Color(0xFF6366F1),
        end = Color(0xFFA855F7),
        glow = Color(0xFF00E5FF)
    )
    VisualizerColorScheme.SOLAR_FLARE -> VisualizerPalette(
        start = Color(0xFFF59E0B),
        mid = Color(0xFFEF4444),
        end = Color(0xFFEC4899),
        glow = Color(0xFFF59E0B)
    )
    VisualizerColorScheme.NEON_CYBER -> VisualizerPalette(
        start = Color(0xFF22D3EE),
        mid = Color(0xFF10B981),
        end = Color(0xFFD946EF),
        glow = Color(0xFF10B981)
    )
    VisualizerColorScheme.OCEAN_MIST -> VisualizerPalette(
        start = Color(0xFF38BDF8),
        mid = Color(0xFF3B82F6),
        end = Color(0xFF2DD4BF),
        glow = Color(0xFF38BDF8)
    )
    VisualizerColorScheme.MONOCHROME_GOLD -> VisualizerPalette(
        start = Color(0xFFFDE68A),
        mid = Color(0xFFF59E0B),
        end = Color(0xFFF8FAFC),
        glow = Color(0xFFFDE68A)
    )
}

/**
 * Interpolates in HSV along the shortest hue arc so complementary primary/secondary pairs
 * produce vibrant intermediate hues instead of desaturated gray/brown mud.
 */
private fun blendColorsHsv(a: Color, b: Color): Color {
    val hsvA = FloatArray(3)
    val hsvB = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (a.red * 255f).toInt().coerceIn(0, 255),
        (a.green * 255f).toInt().coerceIn(0, 255),
        (a.blue * 255f).toInt().coerceIn(0, 255),
        hsvA
    )
    android.graphics.Color.RGBToHSV(
        (b.red * 255f).toInt().coerceIn(0, 255),
        (b.green * 255f).toInt().coerceIn(0, 255),
        (b.blue * 255f).toInt().coerceIn(0, 255),
        hsvB
    )
    var dHue = (hsvB[0] - hsvA[0]) % 360f
    if (dHue > 180f) dHue -= 360f
    if (dHue < -180f) dHue += 360f
    val midH = ((hsvA[0] + dHue * 0.5f) % 360f + 360f) % 360f
    val midS = ((hsvA[1] + hsvB[1]) * 0.5f).coerceIn(0f, 1f)
    val midV = max((hsvA[2] + hsvB[2]) * 0.5f, max(hsvA[2], hsvB[2]) * 0.9f).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(midH, midS, midV)))
}

@Composable
fun VisualizerCanvas(
    frame: VisualizerFrame,
    config: VisualizerConfig,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    demoWhenIdle: Boolean = false,
    centerRingRadiusRatio: Float = 0.24f,
    centerRingRadiusDp: Dp? = null,
    centerOverride: Offset? = null,
    visualizerBus: VisualizerBus? = null,
    dynamicAccent: AccentColors? = LocalAlbumAccentColors.current
) {
    val bandCount = config.density.bandCount
    val sampledBands = remember(bandCount) { FloatArray(bandCount) }
    val sampledWave = remember { FloatArray(64) }
    val sampledWaveMax = remember { FloatArray(64) }
    val sampledWaveMin = remember { FloatArray(64) }
    val sampledScalars = remember { FloatArray(VisualizerBus.SCALAR_COUNT) }

    val smoothedBands = remember(bandCount) { FloatArray(bandCount) }
    val peakCaps = remember(bandCount) { FloatArray(bandCount) }
    val peakHoldSec = remember(bandCount) { FloatArray(bandCount) }
    val peakVelocity = remember(bandCount) { FloatArray(bandCount) }
    val smoothedWave = remember { FloatArray(64) }
    val smoothedWaveMax = remember { FloatArray(64) }
    val smoothedWaveMin = remember { FloatArray(64) }
    val ringPointsX = remember(bandCount) { FloatArray(bandCount) }
    val ringPointsY = remember(bandCount) { FloatArray(bandCount) }
    val polyPointsX = remember(bandCount) { FloatArray(max(bandCount, 64) + 4) }
    val polyPointsY = remember(bandCount) { FloatArray(max(bandCount, 64) + 4) }
    val polyPointsYBottom = remember { FloatArray(68) }

    val primaryPath = remember { Path() }
    val secondaryPath = remember { Path() }
    val shockwaves = remember { List(4) { ShockwaveRing() } }

    val particles = remember {
        List(64) { idx ->
            RadialParticle(
                angleRad = ((idx * 137.508f) % 360f) * (PI.toFloat() / 180f),
                radiusProgress = (idx % 19) / 19f,
                speedFactor = 0.45f + ((idx * 7) % 10) * 0.08f,
                baseSizeDp = 1.4f + (idx % 4) * 0.65f,
                colorIndex = idx % 3
            )
        }
    }
    val emberParticles = remember { List(MAX_EMBER_PARTICLES) { EmberParticle() } }
    val emberRng = remember { IntArray(1) { 0x5A17E3B5 } }
    val lastEmberDirection = remember { arrayOf<EmberDirection?>(null) }
    val lastEmberMirror = remember { BooleanArray(1) }

    var animTimeSec by remember { mutableFloatStateOf(0f) }
    var lastDeltaSec by remember { mutableFloatStateOf(0.016f) }
    var lastShockwaveTimeSec by remember { mutableFloatStateOf(-1f) }
    var lastEmberBurstTimeSec by remember { mutableFloatStateOf(-1f) }
    var frameTick by remember { mutableLongStateOf(0L) }
    var smoothedBass by remember { mutableFloatStateOf(0f) }
    var rollingBassBaseline by remember { mutableFloatStateOf(0f) }
    var smoothedPunch by remember { mutableFloatStateOf(0f) }
    var emberActiveAlpha by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, demoWhenIdle) {
        var lastNanos = 0L
        var settleFrames = if (isPlaying || demoWhenIdle) Int.MAX_VALUE else 75
        while (settleFrames > 0) {
            withFrameNanos { nowNanos ->
                val dt = if (lastNanos != 0L) {
                    ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                } else {
                    0.016f
                }
                lastNanos = nowNanos
                lastDeltaSec = dt
                if (isPlaying || demoWhenIdle) {
                    animTimeSec += dt
                } else {
                    settleFrames--
                }
                frameTick++
            }
        }
    }

    val palette = config.colorScheme.toPalette(config = config, dynamicAccent = dynamicAccent)

    Canvas(modifier = modifier) {
        val tick = frameTick
        if (tick < 0L) return@Canvas

        val dt = lastDeltaSec
        val useDemo = !isPlaying && demoWhenIdle
        val smoothFactor = config.smoothing.coerceIn(0.15f, 0.95f)
        val sensitivity = config.sensitivity.coerceIn(0.5f, 2.5f)
        val spread = config.spreadScale.coerceIn(0.5f, 1.5f)
        val thickness = config.strokeThickness.coerceIn(0.6f, 1.8f)
        val spinSpeed = config.rotationSpeed.coerceIn(0f, 2.5f)

        // Exponential smoothing time constants: attack ~15 ms, release ~150 ms scaled by setting.
        val attackTau = 0.015f
        val releaseTau = (0.045f + smoothFactor * 0.165f).coerceIn(0.05f, 0.28f)
        val attackAlpha = 1f - exp(-dt / attackTau)
        val releaseAlpha = 1f - exp(-dt / releaseTau)

        val hasBusSnapshot = isPlaying && visualizerBus != null && visualizerBus.sampleInterpolatedInto(
            delayMs = config.syncOffsetMs,
            outBands = sampledBands,
            outWave = sampledWave,
            outScalars = sampledScalars,
            outWaveMax = sampledWaveMax,
            outWaveMin = sampledWaveMin
        )

        val targetBass = when {
            useDemo -> (0.42f + 0.34f * sin(animTimeSec * 3.1f)).coerceIn(0f, 1f)
            hasBusSnapshot -> (sampledScalars[VisualizerBus.SCALAR_BASS] * sensitivity).coerceIn(0f, 1f)
            isPlaying -> (frame.bassEnergy * sensitivity).coerceIn(0f, 1f)
            else -> 0f
        }
        val bassAlpha = 1f - exp(-dt / if (targetBass > smoothedBass) 0.018f else 0.14f)
        smoothedBass += (targetBass - smoothedBass) * bassAlpha
        val baselineAlpha = 1f - exp(-dt / 0.42f)
        rollingBassBaseline += (targetBass - rollingBassBaseline) * baselineAlpha
        val bassTransient = ((targetBass - rollingBassBaseline) * 2.5f).coerceIn(0f, 1f)

        val currentBeatPulse = when {
            useDemo -> (((sin(animTimeSec * 3.1f) + 1f) * 0.5f).pow(2.4f) * 0.8f).coerceIn(0f, 1f)
            hasBusSnapshot -> (sampledScalars[VisualizerBus.SCALAR_BEAT] * sensitivity).coerceIn(0f, 1f)
            isPlaying -> (frame.beatPulse * sensitivity).coerceIn(0f, 1f)
            else -> 0f
        }

        // Combined transient-driven punch envelope matched cleanly to CoverArtView bounce
        // (attack 10 ms, release 115 ms), scaled by user's Beat Punch tuning.
        val punchScale = config.beatPunch.coerceIn(0f, 2.0f)
        val targetPunch = if (isPlaying || useDemo) {
            (max(
                currentBeatPulse,
                max(bassTransient * 0.85f, targetBass.pow(2.4f) * 0.40f)
            ) * punchScale).coerceIn(0f, 1.35f)
        } else {
            0f
        }
        val punchTau = if (targetPunch > smoothedPunch) 0.010f else 0.115f
        smoothedPunch += (targetPunch - smoothedPunch) * (1f - exp(-dt / punchTau))

        val targetEmberAlpha = if (isPlaying || useDemo) 1f else 0f
        val emberAlphaTau = if (targetEmberAlpha > emberActiveAlpha) 0.08f else 0.32f
        emberActiveAlpha += (targetEmberAlpha - emberActiveAlpha) * (1f - exp(-dt / emberAlphaTau))

        // Trigger beat-synced shockwaves (180 ms cooldown).
        if ((isPlaying || demoWhenIdle) &&
            currentBeatPulse > 0.36f &&
            animTimeSec - lastShockwaveTimeSec > 0.18f
        ) {
            shockwaves.firstOrNull { !it.active }?.let { slot ->
                slot.active = true
                slot.progress = 0f
                slot.strength = currentBeatPulse
                lastShockwaveTimeSec = animTimeSec
            }
        }

        for (sw in shockwaves) {
            if (sw.active) {
                sw.progress += 1.45f * dt
                if (sw.progress >= 1f) {
                    sw.active = false
                }
            }
        }

        if ((isPlaying || demoWhenIdle) && config.particlesEnabled) {
            val particleVelocity = (0.20f + smoothedBass * 0.9f + smoothedPunch * 1.45f) *
                (0.4f + spinSpeed * 0.6f)
            for (p in particles) {
                p.radiusProgress = (p.radiusProgress + particleVelocity * dt * p.speedFactor) % 1f
            }
        }

        val fallbackBands = frame.spectrumBands
        for (i in 0 until bandCount) {
            val target = when {
                useDemo -> {
                    val phase = animTimeSec * 3.0f + i * 0.24f
                    ((sin(phase) * 0.35f + cos(phase * 0.55f) * 0.25f + 0.45f) * 0.65f * sensitivity)
                        .coerceIn(0.05f, 1f)
                }
                hasBusSnapshot -> (sampledBands[i] * sensitivity).coerceIn(0f, 1f)
                isPlaying && fallbackBands.isNotEmpty() -> {
                    val srcIdx = ((i.toFloat() / bandCount) * fallbackBands.size)
                        .toInt()
                        .coerceIn(0, fallbackBands.lastIndex)
                    (fallbackBands[srcIdx] * sensitivity).coerceIn(0f, 1f)
                }
                else -> 0f
            }

            val prev = smoothedBands[i]
            val alpha = if (target > prev) attackAlpha else releaseAlpha
            val next = (prev + (target - prev) * alpha).coerceIn(0f, 1f)
            smoothedBands[i] = next

            // Peak dots with 300 ms hold + gravity acceleration.
            if (next >= peakCaps[i]) {
                peakCaps[i] = next
                peakHoldSec[i] = 0.30f
                peakVelocity[i] = 0f
            } else if (peakHoldSec[i] > 0f) {
                peakHoldSec[i] = (peakHoldSec[i] - dt).coerceAtLeast(0f)
            } else {
                peakVelocity[i] += 2.6f * dt
                val dropped = peakCaps[i] - peakVelocity[i] * dt
                if (dropped <= next) {
                    peakCaps[i] = next
                    peakVelocity[i] = 0f
                } else {
                    peakCaps[i] = dropped
                }
            }
        }

        val fallbackWave = frame.waveform
        val fallbackWaveMax = frame.waveformMax
        val fallbackWaveMin = frame.waveformMin
        val waveAlpha = 1f - exp(-dt / 0.024f)

        for (i in smoothedWave.indices) {
            val targetWave = when {
                useDemo -> {
                    val phase = animTimeSec * 4.5f + i * 0.22f
                    (sin(phase) * 0.52f * sensitivity).coerceIn(-1f, 1f)
                }
                hasBusSnapshot -> (sampledWave[i] * sensitivity).coerceIn(-1f, 1f)
                isPlaying && fallbackWave.isNotEmpty() -> {
                    val srcIdx = ((i.toFloat() / smoothedWave.size) * fallbackWave.size)
                        .toInt()
                        .coerceIn(0, fallbackWave.lastIndex)
                    (fallbackWave[srcIdx] * sensitivity).coerceIn(-1f, 1f)
                }
                else -> 0f
            }
            val targetMax = when {
                useDemo -> (kotlin.math.abs(targetWave) + 0.14f * smoothedPunch).coerceIn(-1f, 1f)
                hasBusSnapshot -> (sampledWaveMax[i] * sensitivity).coerceIn(-1f, 1f)
                isPlaying && fallbackWaveMax.isNotEmpty() -> {
                    val srcIdx = ((i.toFloat() / smoothedWaveMax.size) * fallbackWaveMax.size)
                        .toInt()
                        .coerceIn(0, fallbackWaveMax.lastIndex)
                    (fallbackWaveMax[srcIdx] * sensitivity).coerceIn(-1f, 1f)
                }
                else -> targetWave
            }
            val targetMin = when {
                useDemo -> (-kotlin.math.abs(targetWave) - 0.14f * smoothedPunch).coerceIn(-1f, 1f)
                hasBusSnapshot -> (sampledWaveMin[i] * sensitivity).coerceIn(-1f, 1f)
                isPlaying && fallbackWaveMin.isNotEmpty() -> {
                    val srcIdx = ((i.toFloat() / smoothedWaveMin.size) * fallbackWaveMin.size)
                        .toInt()
                        .coerceIn(0, fallbackWaveMin.lastIndex)
                    (fallbackWaveMin[srcIdx] * sensitivity).coerceIn(-1f, 1f)
                }
                else -> targetWave
            }

            smoothedWave[i] += (targetWave - smoothedWave[i]) * waveAlpha
            smoothedWaveMax[i] += (targetMax - smoothedWaveMax[i]) * waveAlpha
            smoothedWaveMin[i] += (targetMin - smoothedWaveMin[i]) * waveAlpha
        }

        val explicitCenterRadiusPx = centerRingRadiusDp?.toPx()
        val effectiveCenter = centerOverride ?: Offset(size.width / 2f, size.height / 2f)

        when (config.style) {
            VisualizerStyle.CIRCULAR_AURA -> drawRingVisualizer(
                bands = smoothedBands,
                peaks = peakCaps,
                particles = particles,
                shockwaves = shockwaves,
                pointsX = ringPointsX,
                pointsY = ringPointsY,
                auraPath = primaryPath,
                solidRingPath = secondaryPath,
                bassEnergy = smoothedBass,
                punch = smoothedPunch,
                palette = palette,
                config = config,
                timeSec = animTimeSec,
                center = effectiveCenter,
                centerRingRatio = centerRingRadiusRatio,
                explicitCenterRadiusPx = explicitCenterRadiusPx,
                spread = spread,
                thickness = thickness,
                spinSpeed = spinSpeed
            )
            VisualizerStyle.BARS -> drawBars(
                bands = smoothedBands,
                peaks = peakCaps,
                polyX = polyPointsX,
                polyY = polyPointsY,
                solidPath = primaryPath,
                mirrorSolidPath = secondaryPath,
                bassEnergy = smoothedBass,
                punch = smoothedPunch,
                palette = palette,
                config = config,
                center = effectiveCenter,
                spread = spread,
                thickness = thickness
            )
            VisualizerStyle.WAVEFORM -> drawWaveform(
                wave = smoothedWave,
                waveMax = smoothedWaveMax,
                waveMin = smoothedWaveMin,
                polyX = polyPointsX,
                polyYTop = polyPointsY,
                polyYBottom = polyPointsYBottom,
                mainPath = primaryPath,
                mirrorPath = secondaryPath,
                bassEnergy = smoothedBass,
                punch = smoothedPunch,
                palette = palette,
                config = config,
                center = effectiveCenter,
                spread = spread,
                thickness = thickness
            )
            VisualizerStyle.MIRROR_SPECTRUM -> drawMirrorSpectrum(
                bands = smoothedBands,
                peaks = peakCaps,
                polyX = polyPointsX,
                polyY = polyPointsY,
                solidPath = primaryPath,
                bassEnergy = smoothedBass,
                punch = smoothedPunch,
                palette = palette,
                config = config,
                center = effectiveCenter,
                spread = spread,
                thickness = thickness
            )
            VisualizerStyle.PARTICLE_PULSE -> drawParticlePulse(
                bands = smoothedBands,
                wavePath = primaryPath,
                fillPath = secondaryPath,
                polyX = polyPointsX,
                polyY = polyPointsY,
                bassEnergy = smoothedBass,
                punch = smoothedPunch,
                palette = palette,
                config = config,
                timeSec = animTimeSec,
                center = effectiveCenter,
                spread = spread,
                thickness = thickness,
                spinSpeed = spinSpeed
            )
            VisualizerStyle.FIRE_EMBER -> {
                if (lastEmberDirection[0] != config.emberDirection ||
                    lastEmberMirror[0] != config.mirrorEnabled
                ) {
                    lastEmberDirection[0] = config.emberDirection
                    lastEmberMirror[0] = config.mirrorEnabled
                    for (i in emberParticles.indices) {
                        respawnEmber(
                            particle = emberParticles[i],
                            direction = config.emberDirection,
                            mirrorEnabled = config.mirrorEnabled,
                            bands = smoothedBands,
                            rng = emberRng,
                            prewarm = true
                        )
                    }
                }
                val triggerEmberBeatBurst = (isPlaying || useDemo) &&
                    config.beatPunch > 0.05f &&
                    (currentBeatPulse > 0.36f || targetPunch > 0.62f) &&
                    animTimeSec - lastEmberBurstTimeSec > 0.16f
                if (triggerEmberBeatBurst) {
                    lastEmberBurstTimeSec = animTimeSec
                }
                drawFireEmber(
                    embers = emberParticles,
                    bands = smoothedBands,
                    rng = emberRng,
                    bassEnergy = smoothedBass,
                    punch = smoothedPunch,
                    triggerBeatBurst = triggerEmberBeatBurst,
                    activeAlpha = emberActiveAlpha,
                    palette = palette,
                    config = config,
                    dt = dt,
                    timeSec = animTimeSec,
                    center = effectiveCenter,
                    centerRingRatio = centerRingRadiusRatio,
                    explicitCenterRadiusPx = explicitCenterRadiusPx,
                    spread = spread,
                    thickness = thickness,
                    spinSpeed = spinSpeed,
                    isActive = isPlaying || useDemo
                )
            }
        }
    }
}

private fun DrawScope.drawRingVisualizer(
    bands: FloatArray,
    peaks: FloatArray,
    particles: List<RadialParticle>,
    shockwaves: List<ShockwaveRing>,
    pointsX: FloatArray,
    pointsY: FloatArray,
    auraPath: Path,
    solidRingPath: Path,
    bassEnergy: Float,
    punch: Float,
    palette: VisualizerPalette,
    config: VisualizerConfig,
    timeSec: Float,
    center: Offset,
    centerRingRatio: Float,
    explicitCenterRadiusPx: Float?,
    spread: Float,
    thickness: Float,
    spinSpeed: Float
) {
    val count = bands.size
    if (count < 3) return

    val minDim = min(size.width, size.height)
    val maxDim = max(size.width, size.height)
    val pxDensity = density
    // Match CoverArtView kick bounce so the ring & disc breathe together as one unit.
    val baseRadius = if (explicitCenterRadiusPx != null) {
        explicitCenterRadiusPx * (1f + (punch * 0.145f).coerceIn(0f, 0.22f)) + 2f * pxDensity
    } else {
        minDim * centerRingRatio.coerceIn(0.16f, 0.36f) + punch * (minDim * 0.045f) + bassEnergy * (minDim * 0.02f)
    }
    val maxSpike = minDim * (0.22f + punch * 0.05f) * spread
    val rotationOffset = timeSec * 0.24f * spinSpeed

    // 1. Tight, controlled ambient halo behind ring (no screen-wide muddy whiteout)
    if (config.glowEnabled) {
        val haloRadius = baseRadius + maxSpike + 36f * pxDensity
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.glow.copy(alpha = (0.18f + punch * 0.22f).coerceIn(0f, 0.42f)),
                    palette.end.copy(alpha = (0.08f + punch * 0.10f).coerceIn(0f, 0.22f)),
                    Color.Transparent
                ),
                center = center,
                radius = haloRadius
            ),
            radius = haloRadius,
            center = center
        )
    }

    // 2. Crisp beat-triggered shockwaves
    for (sw in shockwaves) {
        if (!sw.active) continue
        val swRadius = baseRadius + sw.progress * (maxDim * 0.44f)
        val swAlpha = ((1f - sw.progress).pow(1.4f) * (0.45f + sw.strength * 0.40f)).coerceIn(0f, 0.85f)
        drawCircle(
            color = palette.start.copy(alpha = swAlpha),
            radius = swRadius,
            center = center,
            style = Stroke(width = (2.2f * (1f - sw.progress) + 0.8f) * pxDensity * thickness)
        )
    }

    // 3. Density-scaled sharp bokeh particles
    if (config.particlesEnabled) {
        val maxParticleRadius = maxDim * 0.56f
        for (p in particles) {
            val r = baseRadius * 0.92f + p.radiusProgress * (maxParticleRadius - baseRadius * 0.85f)
            val a = p.angleRad + timeSec * 0.16f * spinSpeed * p.speedFactor
            val pos = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
            val alpha = ((1f - p.radiusProgress) * (0.38f + punch * 0.62f)).coerceIn(0f, 0.95f)
            val particleColor = when (p.colorIndex) {
                0 -> palette.start
                1 -> palette.mid
                else -> palette.end
            }
            val dotRadius = p.baseSizeDp * pxDensity * thickness * (0.85f + punch * 0.45f)
            if (config.glowEnabled) {
                drawCircle(
                    color = particleColor.copy(alpha = alpha * 0.26f),
                    radius = dotRadius * 2.1f,
                    center = pos
                )
            }
            drawCircle(
                color = particleColor.copy(alpha = alpha),
                radius = dotRadius,
                center = pos
            )
            drawCircle(
                color = Color.White.copy(alpha = alpha * 0.65f),
                radius = dotRadius * 0.42f,
                center = pos
            )
        }
    }

    // 4. Outer contour aura + Solid / Line / None rendering
    val halfCount = max(1, count / 2)
    val angleStep = (2.0 * PI / count).toFloat()
    val spokeWidth = ((minDim / count) * 0.52f * thickness).coerceIn(1.6f * pxDensity, 5.5f * pxDensity)

    if (config.fillMode != VisualizerFillMode.NONE) {
        for (i in 0 until count) {
            val mirrorIdx = if (i < halfCount) i else count - 1 - i
            val mag = bands[mirrorIdx.coerceIn(0, bands.lastIndex)]
            val angle = i * angleStep + rotationOffset
            val outerR = baseRadius + 4f * pxDensity + mag * maxSpike
            pointsX[i] = center.x + cos(angle) * outerR
            pointsY[i] = center.y + sin(angle) * outerR
        }

        buildSmoothClosedPath(auraPath, pointsX, pointsY, count)

        if (config.fillMode == VisualizerFillMode.SOLID) {
            solidRingPath.reset()
            solidRingPath.fillType = PathFillType.EvenOdd
            solidRingPath.addPath(auraPath)
            solidRingPath.addOval(
                oval = Rect(
                    left = center.x - baseRadius,
                    top = center.y - baseRadius,
                    right = center.x + baseRadius,
                    bottom = center.y + baseRadius
                )
            )
            drawPath(
                path = solidRingPath,
                brush = Brush.sweepGradient(
                    listOf(
                        palette.start.copy(alpha = 0.85f),
                        palette.mid.copy(alpha = 0.82f),
                        palette.end.copy(alpha = 0.85f),
                        palette.start.copy(alpha = 0.85f)
                    ),
                    center = center
                )
            )
        } else {
            drawPath(
                path = auraPath,
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.start.copy(alpha = 0.14f + punch * 0.10f),
                        palette.mid.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius + maxSpike + 12f * pxDensity
                )
            )
        }

        if (config.glowEnabled) {
            drawPath(
                path = auraPath,
                color = palette.glow.copy(alpha = 0.28f + punch * 0.18f),
                style = Stroke(width = 5.2f * pxDensity * thickness, join = StrokeJoin.Round)
            )
        }

        drawPath(
            path = auraPath,
            brush = Brush.sweepGradient(
                listOf(palette.start, palette.mid, palette.end, palette.start),
                center = center
            ),
            style = Stroke(width = (if (config.fillMode == VisualizerFillMode.SOLID) 2.2f else 1.8f) * pxDensity * thickness, join = StrokeJoin.Round)
        )
        // Bright specular inner highlight for crisp HD outline
        drawPath(
            path = auraPath,
            color = Color.White.copy(alpha = 0.45f + punch * 0.25f),
            style = Stroke(width = 0.85f * pxDensity * thickness, join = StrokeJoin.Round)
        )
    }

    // 5. Crisp inner glowing bezel ring
    if (config.glowEnabled) {
        drawCircle(
            color = palette.glow.copy(alpha = 0.26f + punch * 0.22f),
            radius = baseRadius,
            center = center,
            style = Stroke(width = (5.0f + punch * 2.5f) * pxDensity * thickness)
        )
    }
    drawCircle(
        brush = Brush.sweepGradient(
            listOf(palette.start, palette.mid, palette.end, palette.start),
            center = center
        ),
        radius = baseRadius,
        center = center,
        style = Stroke(width = (2.0f + punch * 1.2f) * pxDensity * thickness)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.55f + punch * 0.25f),
        radius = baseRadius,
        center = center,
        style = Stroke(width = 0.9f * pxDensity)
    )

    // 6. Sharp radial frequency spokes (in LINE & NONE) + gravity peak dots
    for (i in 0 until count) {
        val mirrorIdx = if (i < halfCount) i else count - 1 - i
        val mag = bands[mirrorIdx.coerceIn(0, bands.lastIndex)]
        val angle = i * angleStep + rotationOffset
        val cosA = cos(angle)
        val sinA = sin(angle)

        if (config.fillMode != VisualizerFillMode.SOLID) {
            val innerR = if (config.mirrorEnabled) {
                (baseRadius - mag * maxSpike * 0.28f).coerceAtLeast(baseRadius * 0.52f)
            } else {
                baseRadius + 2.5f * pxDensity
            }
            val outerR = baseRadius + 4.5f * pxDensity + mag * maxSpike
            val t = mirrorIdx.toFloat() / halfCount
            val barColor = when {
                t < 0.35f -> palette.start
                t < 0.7f -> palette.mid
                else -> palette.end
            }
            if (config.glowEnabled) {
                drawLine(
                    color = barColor.copy(alpha = 0.24f),
                    start = Offset(center.x + cosA * innerR, center.y + sinA * innerR),
                    end = Offset(center.x + cosA * outerR, center.y + sinA * outerR),
                    strokeWidth = spokeWidth * 1.9f,
                    cap = StrokeCap.Round
                )
            }
            drawLine(
                color = barColor,
                start = Offset(center.x + cosA * innerR, center.y + sinA * innerR),
                end = Offset(center.x + cosA * outerR, center.y + sinA * outerR),
                strokeWidth = spokeWidth,
                cap = StrokeCap.Round
            )
            // Crisp tip accent
            drawLine(
                color = Color.White.copy(alpha = 0.50f * mag),
                start = Offset(center.x + cosA * (outerR - min(spokeWidth * 1.4f, mag * maxSpike * 0.35f)), center.y + sinA * (outerR - min(spokeWidth * 1.4f, mag * maxSpike * 0.35f))),
                end = Offset(center.x + cosA * outerR, center.y + sinA * outerR),
                strokeWidth = spokeWidth * 0.65f,
                cap = StrokeCap.Round
            )
        }

        if (config.peakCapsEnabled) {
            val peakR = baseRadius + 8f * pxDensity + peaks[mirrorIdx.coerceIn(0, peaks.lastIndex)] * maxSpike
            drawCircle(
                color = palette.start.copy(alpha = 0.95f),
                radius = 1.6f * pxDensity * thickness,
                center = Offset(center.x + cosA * peakR, center.y + sinA * peakR)
            )
        }
    }
}

private fun buildSmoothClosedPath(
    path: Path,
    px: FloatArray,
    py: FloatArray,
    count: Int
) {
    path.reset()
    if (count < 3) return
    val startMidX = (px[count - 1] + px[0]) * 0.5f
    val startMidY = (py[count - 1] + py[0]) * 0.5f
    path.moveTo(startMidX, startMidY)
    for (i in 0 until count) {
        val next = if (i == count - 1) 0 else i + 1
        val midX = (px[i] + px[next]) * 0.5f
        val midY = (py[i] + py[next]) * 0.5f
        path.quadraticTo(px[i], py[i], midX, midY)
    }
    path.close()
}

private fun addCatmullRomCurveTo(
    path: Path,
    px: FloatArray,
    py: FloatArray,
    pointCount: Int
) {
    if (pointCount < 2) return
    for (i in 0 until pointCount - 1) {
        val p0x = px[max(0, i - 1)]
        val p0y = py[max(0, i - 1)]
        val p1x = px[i]
        val p1y = py[i]
        val p2x = px[i + 1]
        val p2y = py[i + 1]
        val p3x = px[min(pointCount - 1, i + 2)]
        val p3y = py[min(pointCount - 1, i + 2)]

        val cp1x = p1x + (p2x - p0x) / 6f
        val cp1y = p1y + (p2y - p0y) / 6f
        val cp2x = p2x - (p3x - p1x) / 6f
        val cp2y = p2y - (p3y - p1y) / 6f
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2x, p2y)
    }
}

private fun DrawScope.drawBars(
    bands: FloatArray,
    peaks: FloatArray,
    polyX: FloatArray,
    polyY: FloatArray,
    solidPath: Path,
    mirrorSolidPath: Path,
    bassEnergy: Float,
    punch: Float,
    palette: VisualizerPalette,
    config: VisualizerConfig,
    center: Offset,
    spread: Float,
    thickness: Float
) {
    val count = bands.size
    if (count == 0) return

    val pxDensity = density
    val totalWidth = size.width
    val slotWidth = totalWidth / count
    val maxHeight = size.height * (0.74f + punch * 0.08f) * spread.coerceAtMost(1.25f)
    val baselineY = if (config.mirrorEnabled) center.y else size.height * 0.80f

    if (config.glowEnabled) {
        val haloRadius = size.width * 0.45f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.glow.copy(alpha = 0.14f + punch * 0.16f + bassEnergy * 0.08f),
                    Color.Transparent
                ),
                center = Offset(size.width / 2f, baselineY),
                radius = haloRadius
            ),
            radius = haloRadius,
            center = Offset(size.width / 2f, baselineY)
        )
    }

    val brush = Brush.verticalGradient(
        colors = listOf(palette.start, palette.mid, palette.end),
        startY = (baselineY - maxHeight).coerceAtLeast(0f),
        endY = if (config.mirrorEnabled) (baselineY + maxHeight).coerceAtMost(size.height) else baselineY
    )

    if (config.fillMode == VisualizerFillMode.SOLID) {
        val heightScale = if (config.mirrorEnabled) maxHeight * 0.5f else maxHeight
        for (i in 0 until count) {
            polyX[i] = i * slotWidth + slotWidth * 0.5f
            polyY[i] = baselineY - max(2f * pxDensity, bands[i] * heightScale)
        }

        solidPath.reset()
        solidPath.moveTo(0f, baselineY)
        solidPath.lineTo(polyX[0], polyY[0])
        addCatmullRomCurveTo(solidPath, polyX, polyY, count)
        solidPath.lineTo(totalWidth, baselineY)
        solidPath.close()

        drawPath(path = solidPath, brush = brush, alpha = 0.88f)

        if (config.glowEnabled) {
            drawPath(
                path = solidPath,
                color = palette.glow.copy(alpha = 0.28f),
                style = Stroke(width = 4.5f * pxDensity * thickness, join = StrokeJoin.Round)
            )
        }

        drawPath(
            path = solidPath,
            color = palette.start,
            style = Stroke(width = 1.8f * pxDensity * thickness, join = StrokeJoin.Round)
        )
        drawPath(
            path = solidPath,
            color = Color.White.copy(alpha = 0.48f),
            style = Stroke(width = 0.8f * pxDensity, join = StrokeJoin.Round)
        )

        if (config.mirrorEnabled) {
            for (i in 0 until count) {
                polyY[i] = baselineY + max(2f * pxDensity, bands[i] * heightScale)
            }
            mirrorSolidPath.reset()
            mirrorSolidPath.moveTo(0f, baselineY)
            mirrorSolidPath.lineTo(polyX[0], polyY[0])
            addCatmullRomCurveTo(mirrorSolidPath, polyX, polyY, count)
            mirrorSolidPath.lineTo(totalWidth, baselineY)
            mirrorSolidPath.close()
            drawPath(path = mirrorSolidPath, brush = brush, alpha = 0.62f)
        }

        if (config.peakCapsEnabled) {
            for (i in 0 until count) {
                val peakY = (baselineY - peaks[i] * heightScale - 4f * pxDensity).coerceAtLeast(2f * pxDensity)
                drawCircle(
                    color = palette.start.copy(alpha = 0.95f),
                    radius = 1.5f * pxDensity * thickness,
                    center = Offset(polyX[i], peakY)
                )
            }
        }
        return
    }

    val isWireframe = config.fillMode == VisualizerFillMode.NONE
    val barWidth = (slotWidth * 0.60f * thickness).coerceIn(2f * pxDensity, slotWidth * 0.88f)
    val gapOffset = (slotWidth - barWidth) / 2f

    for (i in 0 until count) {
        val value = bands[i]
        val barHeight = max(3f * pxDensity, value * (if (config.mirrorEnabled) maxHeight * 0.5f else maxHeight))
        val x = i * slotWidth + gapOffset
        val topY = baselineY - barHeight
        val rectH = if (config.mirrorEnabled) barHeight * 2f else barHeight
        val cr = CornerRadius(barWidth / 2f, barWidth / 2f)

        if (isWireframe) {
            if (config.glowEnabled) {
                drawRoundRect(
                    color = palette.glow.copy(alpha = 0.24f),
                    topLeft = Offset(x, topY),
                    size = Size(barWidth, rectH),
                    cornerRadius = cr,
                    style = Stroke(width = 2.6f * pxDensity * thickness)
                )
            }
            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, topY),
                size = Size(barWidth, rectH),
                cornerRadius = cr,
                style = Stroke(width = 1.3f * pxDensity * thickness)
            )
        } else {
            if (config.glowEnabled) {
                drawRoundRect(
                    color = palette.glow.copy(alpha = 0.20f),
                    topLeft = Offset(x - 1.5f * pxDensity, topY - 1.5f * pxDensity),
                    size = Size(barWidth + 3f * pxDensity, rectH + 3f * pxDensity),
                    cornerRadius = CornerRadius(barWidth, barWidth)
                )
            }
            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, topY),
                size = Size(barWidth, rectH),
                cornerRadius = cr
            )
        }

        if (config.peakCapsEnabled) {
            val peakHeight = peaks[i] * (if (config.mirrorEnabled) maxHeight * 0.5f else maxHeight)
            val capY = (baselineY - peakHeight - 4f * pxDensity).coerceAtLeast(2f * pxDensity)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(x, capY),
                size = Size(barWidth, 2.2f * pxDensity),
                cornerRadius = CornerRadius(1.5f * pxDensity, 1.5f * pxDensity)
            )
        }
    }
}

private fun DrawScope.drawWaveform(
    wave: FloatArray,
    waveMax: FloatArray,
    waveMin: FloatArray,
    polyX: FloatArray,
    polyYTop: FloatArray,
    polyYBottom: FloatArray,
    mainPath: Path,
    mirrorPath: Path,
    bassEnergy: Float,
    punch: Float,
    palette: VisualizerPalette,
    config: VisualizerConfig,
    center: Offset,
    spread: Float,
    thickness: Float
) {
    if (wave.isEmpty()) return
    val pxDensity = density
    val centerY = center.y
    val amplitude = size.height * (0.34f + punch * 0.06f) * spread.coerceAtMost(1.35f)
    val stepX = size.width / max(1, wave.size - 1)

    if (config.fillMode == VisualizerFillMode.NONE) {
        // Pure floating HD waveform beads without continuous line or area fill
        for (i in wave.indices) {
            val x = i * stepX
            val y = centerY - wave[i] * amplitude
            val t = i.toFloat() / max(1, wave.lastIndex)
            val dotColor = when {
                t < 0.35f -> palette.start
                t < 0.7f -> palette.mid
                else -> palette.end
            }
            val dotR = (2.1f + kotlin.math.abs(wave[i]) * 2.2f + punch * 1.0f) * pxDensity * thickness
            if (config.glowEnabled) {
                drawCircle(
                    color = dotColor.copy(alpha = 0.25f),
                    radius = dotR * 2.1f,
                    center = Offset(x, y)
                )
            }
            drawCircle(color = dotColor, radius = dotR, center = Offset(x, y))
            drawCircle(color = Color.White.copy(alpha = 0.7f), radius = dotR * 0.4f, center = Offset(x, y))

            if (config.mirrorEnabled) {
                drawCircle(
                    color = dotColor.copy(alpha = 0.45f),
                    radius = dotR * 0.8f,
                    center = Offset(x, centerY + wave[i] * amplitude * 0.8f)
                )
            }
        }
        return
    }

    mainPath.reset()
    mirrorPath.reset()

    if (config.fillMode == VisualizerFillMode.SOLID) {
        // True smooth upper max -> lower min closed Catmull-Rom envelope (zero sawtooth zig-zag).
        val n = min(waveMax.size, waveMin.size)
        for (i in 0 until n) {
            polyX[i] = i * stepX
            polyYTop[i] = centerY - max(2f * pxDensity, waveMax[i] * amplitude)
            polyYBottom[i] = centerY - min(-2f * pxDensity, waveMin[i] * amplitude)
        }
        mainPath.moveTo(polyX[0], polyYTop[0])
        addCatmullRomCurveTo(mainPath, polyX, polyYTop, n)
        // Return right-to-left along bottom min envelope
        for (i in 0 until n) {
            val rev = n - 1 - i
            polyX[i] = rev * stepX
            polyYTop[i] = polyYBottom[rev]
        }
        mainPath.lineTo(polyX[0], polyYTop[0])
        addCatmullRomCurveTo(mainPath, polyX, polyYTop, n)
        mainPath.close()

        val fillBrush = Brush.verticalGradient(
            colors = listOf(palette.start, palette.mid, palette.end),
            startY = centerY - amplitude,
            endY = centerY + amplitude
        )
        drawPath(path = mainPath, brush = fillBrush, alpha = 0.84f)
        if (config.glowEnabled) {
            drawPath(
                path = mainPath,
                color = palette.glow.copy(alpha = 0.28f + bassEnergy * 0.16f),
                style = Stroke(width = 4.8f * pxDensity * thickness, join = StrokeJoin.Round)
            )
        }
        drawPath(
            path = mainPath,
            brush = Brush.horizontalGradient(listOf(palette.start, palette.mid, palette.end)),
            style = Stroke(width = 1.8f * pxDensity * thickness, join = StrokeJoin.Round)
        )
        drawPath(
            path = mainPath,
            color = Color.White.copy(alpha = 0.45f),
            style = Stroke(width = 0.75f * pxDensity, join = StrokeJoin.Round)
        )
        return
    }

    // LINE mode: smooth Catmull-Rom curve with tight neon halo & razor-sharp bright core
    for (i in wave.indices) {
        polyX[i] = i * stepX
        polyYTop[i] = centerY - wave[i] * amplitude
        polyYBottom[i] = centerY + wave[i] * amplitude * 0.75f
    }
    mainPath.moveTo(polyX[0], polyYTop[0])
    addCatmullRomCurveTo(mainPath, polyX, polyYTop, wave.size)

    val strokeBrush = Brush.horizontalGradient(listOf(palette.start, palette.mid, palette.end))

    if (config.glowEnabled) {
        drawPath(
            path = mainPath,
            color = palette.glow.copy(alpha = 0.26f + punch * 0.20f),
            style = Stroke(width = 6.2f * pxDensity * thickness, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    drawPath(
        path = mainPath,
        brush = strokeBrush,
        style = Stroke(width = 2.6f * pxDensity * thickness, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = mainPath,
        color = Color.White.copy(alpha = 0.65f),
        style = Stroke(width = 1.0f * pxDensity * thickness, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    if (config.mirrorEnabled) {
        mirrorPath.moveTo(polyX[0], polyYBottom[0])
        addCatmullRomCurveTo(mirrorPath, polyX, polyYBottom, wave.size)
        drawPath(
            path = mirrorPath,
            brush = strokeBrush,
            alpha = 0.55f,
            style = Stroke(width = 1.8f * pxDensity * thickness, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

private fun DrawScope.drawMirrorSpectrum(
    bands: FloatArray,
    peaks: FloatArray,
    polyX: FloatArray,
    polyY: FloatArray,
    solidPath: Path,
    bassEnergy: Float,
    punch: Float,
    palette: VisualizerPalette,
    config: VisualizerConfig,
    center: Offset,
    spread: Float,
    thickness: Float
) {
    val count = bands.size
    if (count == 0) return

    val pxDensity = density
    val halfCount = max(1, count / 2)
    val centerX = center.x
    val halfWidth = size.width / 2f
    val slotWidth = halfWidth / halfCount
    val maxH = size.height * (0.34f + punch * 0.06f) * spread.coerceAtMost(1.35f)
    val centerY = center.y

    if (config.glowEnabled) {
        val haloRadius = min(size.width, size.height) * 0.44f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.glow.copy(alpha = 0.14f + punch * 0.16f + bassEnergy * 0.08f),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = haloRadius
            ),
            radius = haloRadius,
            center = Offset(centerX, centerY)
        )
    }

    val brush = Brush.horizontalGradient(
        colors = listOf(palette.end, palette.mid, palette.start, palette.mid, palette.end)
    )

    if (config.fillMode == VisualizerFillMode.SOLID) {
        val upperCount = halfCount * 2
        for (i in 0 until halfCount) {
            val leftBand = bands[halfCount - 1 - i]
            polyX[i] = centerX - (halfCount - i) * slotWidth + slotWidth * 0.5f
            polyY[i] = centerY - max(2f * pxDensity, leftBand * maxH)

            val rightBand = bands[i]
            polyX[halfCount + i] = centerX + i * slotWidth + slotWidth * 0.5f
            polyY[halfCount + i] = centerY - max(2f * pxDensity, rightBand * maxH)
        }

        solidPath.reset()
        solidPath.moveTo(0f, centerY)
        solidPath.lineTo(polyX[0], polyY[0])
        addCatmullRomCurveTo(solidPath, polyX, polyY, upperCount)
        solidPath.lineTo(size.width, centerY)

        for (i in 0 until halfCount) {
            val rightIdx = halfCount - 1 - i
            polyX[i] = centerX + rightIdx * slotWidth + slotWidth * 0.5f
            polyY[i] = centerY + max(2f * pxDensity, bands[rightIdx] * maxH)

            polyX[halfCount + i] = centerX - (i + 1) * slotWidth + slotWidth * 0.5f
            polyY[halfCount + i] = centerY + max(2f * pxDensity, bands[i] * maxH)
        }
        solidPath.lineTo(polyX[0], polyY[0])
        addCatmullRomCurveTo(solidPath, polyX, polyY, upperCount)
        solidPath.close()

        drawPath(path = solidPath, brush = brush, alpha = 0.85f)
        if (config.glowEnabled) {
            drawPath(
                path = solidPath,
                color = palette.glow.copy(alpha = 0.28f),
                style = Stroke(width = 4.8f * pxDensity * thickness, join = StrokeJoin.Round)
            )
        }
        drawPath(
            path = solidPath,
            brush = brush,
            style = Stroke(width = 1.8f * pxDensity * thickness, join = StrokeJoin.Round)
        )
        drawPath(
            path = solidPath,
            color = Color.White.copy(alpha = 0.45f),
            style = Stroke(width = 0.75f * pxDensity, join = StrokeJoin.Round)
        )
        return
    }

    val isWireframe = config.fillMode == VisualizerFillMode.NONE
    val barWidth = (slotWidth * 0.60f * thickness).coerceIn(2f * pxDensity, slotWidth * 0.88f)
    for (i in 0 until halfCount) {
        val value = bands[i]
        val h = max(3f * pxDensity, value * maxH)
        val offset = i * slotWidth + (slotWidth - barWidth) / 2f
        val rightX = centerX + offset
        val leftX = centerX - offset - barWidth
        val cr = CornerRadius(barWidth / 2f, barWidth / 2f)

        if (isWireframe) {
            val stroke = Stroke(width = 1.3f * pxDensity * thickness)
            drawRoundRect(brush = brush, topLeft = Offset(rightX, centerY - h), size = Size(barWidth, h * 2f), cornerRadius = cr, style = stroke)
            drawRoundRect(brush = brush, topLeft = Offset(leftX, centerY - h), size = Size(barWidth, h * 2f), cornerRadius = cr, style = stroke)
        } else {
            if (config.glowEnabled) {
                val glowColor = palette.glow.copy(alpha = 0.18f)
                drawRoundRect(color = glowColor, topLeft = Offset(rightX - pxDensity, centerY - h - pxDensity), size = Size(barWidth + 2f * pxDensity, h * 2f + 2f * pxDensity), cornerRadius = CornerRadius(barWidth, barWidth))
                drawRoundRect(color = glowColor, topLeft = Offset(leftX - pxDensity, centerY - h - pxDensity), size = Size(barWidth + 2f * pxDensity, h * 2f + 2f * pxDensity), cornerRadius = CornerRadius(barWidth, barWidth))
            }
            drawRoundRect(brush = brush, topLeft = Offset(rightX, centerY - h), size = Size(barWidth, h * 2f), cornerRadius = cr)
            drawRoundRect(brush = brush, topLeft = Offset(leftX, centerY - h), size = Size(barWidth, h * 2f), cornerRadius = cr)
        }

        if (config.peakCapsEnabled) {
            val peakY = (centerY - peaks[i] * maxH - 4f * pxDensity).coerceAtLeast(2f * pxDensity)
            drawCircle(color = Color.White.copy(alpha = 0.85f), radius = 1.4f * pxDensity * thickness, center = Offset(rightX + barWidth / 2f, peakY))
            drawCircle(color = Color.White.copy(alpha = 0.85f), radius = 1.4f * pxDensity * thickness, center = Offset(leftX + barWidth / 2f, peakY))
        }
    }
}

private fun DrawScope.drawParticlePulse(
    bands: FloatArray,
    wavePath: Path,
    fillPath: Path,
    polyX: FloatArray,
    polyY: FloatArray,
    bassEnergy: Float,
    punch: Float,
    palette: VisualizerPalette,
    config: VisualizerConfig,
    timeSec: Float,
    center: Offset,
    spread: Float,
    thickness: Float,
    spinSpeed: Float
) {
    val count = bands.size
    if (count == 0) return

    val pxDensity = density
    val centerY = center.y
    val stepX = size.width / count

    for (i in 0 until count) {
        val energy = bands[i]
        val x = i * stepX + stepX / 2f
        val driftY = sin(timeSec * (2.5f * (0.4f + spinSpeed * 0.6f)) + i * 0.45f) *
            size.height * (0.12f + energy * 0.28f * spread.coerceAtMost(1.3f))
        polyX[i] = x
        polyY[i] = centerY + driftY
    }

    if (config.fillMode == VisualizerFillMode.SOLID) {
        fillPath.reset()
        fillPath.moveTo(0f, centerY)
        fillPath.lineTo(polyX[0], polyY[0])
        addCatmullRomCurveTo(fillPath, polyX, polyY, count)
        fillPath.lineTo(size.width, centerY)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.horizontalGradient(
                listOf(
                    palette.start.copy(alpha = 0.72f),
                    palette.mid.copy(alpha = 0.68f),
                    palette.end.copy(alpha = 0.72f)
                )
            )
        )
    }

    if (config.fillMode != VisualizerFillMode.NONE) {
        wavePath.reset()
        wavePath.moveTo(polyX[0], polyY[0])
        addCatmullRomCurveTo(wavePath, polyX, polyY, count)
        drawPath(
            path = wavePath,
            color = palette.mid.copy(alpha = if (config.fillMode == VisualizerFillMode.SOLID) 0.8f else 0.45f),
            style = Stroke(width = (if (config.fillMode == VisualizerFillMode.SOLID) 2.2f else 1.5f) * pxDensity * thickness)
        )
    }

    for (i in 0 until count) {
        val energy = bands[i]
        val x = polyX[i]
        val y = polyY[i]
        val radius = ((2.2f + energy * 6.5f + punch * 2.5f) * pxDensity * thickness).coerceAtLeast(1.8f * pxDensity)
        val color = when {
            i % 3 == 0 -> palette.start
            i % 3 == 1 -> palette.mid
            else -> palette.end
        }

        if (config.glowEnabled) {
            drawCircle(
                color = color.copy(alpha = 0.24f + bassEnergy * 0.14f),
                radius = radius * 2.0f,
                center = Offset(x, y)
            )
        }

        drawCircle(color = color.copy(alpha = 0.94f), radius = radius, center = Offset(x, y))
        drawCircle(color = Color.White.copy(alpha = 0.68f), radius = radius * 0.4f, center = Offset(x, y))

        if (config.mirrorEnabled) {
            drawCircle(
                color = color.copy(alpha = 0.52f),
                radius = radius * 0.78f,
                center = Offset(x, centerY - (y - centerY))
            )
        }
    }
}

private fun pickSpectrumWeightedPos(bands: FloatArray, rng: IntArray): Float {
    val base = nextRand01(rng)
    if (bands.isEmpty()) return base
    var bestPos = base
    var bestEnergy = bands[((base * bands.size).toInt()).coerceIn(0, bands.lastIndex)]
    for (attempt in 0 until 2) {
        val candidate = nextRand01(rng)
        val e = bands[((candidate * bands.size).toInt()).coerceIn(0, bands.lastIndex)]
        if (e > bestEnergy + 0.08f) {
            bestEnergy = e
            bestPos = candidate
        }
    }
    return bestPos.coerceIn(0.02f, 0.98f)
}

private fun oppositeDirection(dir: EmberDirection): EmberDirection = when (dir) {
    EmberDirection.UP -> EmberDirection.DOWN
    EmberDirection.DOWN -> EmberDirection.UP
    EmberDirection.LEFT -> EmberDirection.RIGHT
    EmberDirection.RIGHT -> EmberDirection.LEFT
    EmberDirection.RADIAL -> EmberDirection.RADIAL
}

private fun respawnEmber(
    particle: EmberParticle,
    direction: EmberDirection,
    mirrorEnabled: Boolean,
    bands: FloatArray,
    rng: IntArray,
    prewarm: Boolean
) {
    val isMirror = mirrorEnabled && nextRand01(rng) < 0.5f
    particle.mirrorSide = isMirror
    particle.speedScale = 0.55f + nextRand01(rng) * 0.95f
    particle.lifeSpeed = 0.30f + nextRand01(rng) * 0.44f
    particle.baseSizeDp = 1.5f + nextRand01(rng).pow(1.4f) * 3.1f
    particle.turbulencePhase = nextRand01(rng) * (2f * PI.toFloat())
    particle.turbulenceFreq = 1.9f + nextRand01(rng) * 3.1f
    particle.turbulenceAmp = 0.035f + nextRand01(rng) * 0.075f
    particle.flickerPhase = nextRand01(rng) * (2f * PI.toFloat())
    particle.flickerSpeed = 7.5f + nextRand01(rng) * 10.5f

    val edgePos = pickSpectrumWeightedPos(bands, rng)
    val effectiveDir = if (isMirror) oppositeDirection(direction) else direction
    val primarySpeed = (0.24f + nextRand01(rng) * 0.34f) * particle.speedScale
    val crossDrift = (nextRand01(rng) - 0.5f) * 0.10f

    when (effectiveDir) {
        EmberDirection.UP -> {
            particle.x = edgePos
            particle.y = if (prewarm) 0.04f + nextRand01(rng) * 0.98f else 1.03f
            particle.vx = crossDrift
            particle.vy = -primarySpeed
            particle.life = if (prewarm) (1f - particle.y).coerceIn(0f, 0.94f) else 0f
        }
        EmberDirection.DOWN -> {
            particle.x = edgePos
            particle.y = if (prewarm) -0.02f + nextRand01(rng) * 0.98f else -0.03f
            particle.vx = crossDrift
            particle.vy = primarySpeed
            particle.life = if (prewarm) particle.y.coerceIn(0f, 0.94f) else 0f
        }
        EmberDirection.LEFT -> {
            particle.x = if (prewarm) 0.04f + nextRand01(rng) * 0.98f else 1.03f
            particle.y = edgePos
            particle.vx = -primarySpeed
            particle.vy = crossDrift
            particle.life = if (prewarm) (1f - particle.x).coerceIn(0f, 0.94f) else 0f
        }
        EmberDirection.RIGHT -> {
            particle.x = if (prewarm) -0.02f + nextRand01(rng) * 0.98f else -0.03f
            particle.y = edgePos
            particle.vx = primarySpeed
            particle.vy = crossDrift
            particle.life = if (prewarm) particle.x.coerceIn(0f, 0.94f) else 0f
        }
        EmberDirection.RADIAL -> {
            particle.radialAngle = edgePos * (2f * PI.toFloat())
            particle.radialProgress = if (prewarm) nextRand01(rng) * 0.94f else 0f
            particle.life = particle.radialProgress
            particle.vx = cos(particle.radialAngle) * primarySpeed
            particle.vy = sin(particle.radialAngle) * primarySpeed
        }
    }
}

private fun DrawScope.drawAmbientHeatEdge(
    direction: EmberDirection,
    palette: VisualizerPalette,
    heatAlpha: Float,
    center: Offset,
    radialStartRadius: Float,
    minDim: Float,
    punch: Float
) {
    if (heatAlpha <= 0.005f) return
    val midAlpha = (heatAlpha * 0.42f).coerceIn(0f, 0.5f)
    when (direction) {
        EmberDirection.UP -> {
            val topY = size.height * 0.54f
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.end.copy(alpha = midAlpha),
                        palette.glow.copy(alpha = heatAlpha)
                    ),
                    startY = topY,
                    endY = size.height
                ),
                topLeft = Offset(0f, topY),
                size = Size(size.width, size.height - topY)
            )
        }
        EmberDirection.DOWN -> {
            val bottomY = size.height * 0.46f
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.glow.copy(alpha = heatAlpha),
                        palette.end.copy(alpha = midAlpha),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = bottomY
                ),
                topLeft = Offset.Zero,
                size = Size(size.width, bottomY)
            )
        }
        EmberDirection.LEFT -> {
            val startX = size.width * 0.56f
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        palette.end.copy(alpha = midAlpha),
                        palette.glow.copy(alpha = heatAlpha)
                    ),
                    startX = startX,
                    endX = size.width
                ),
                topLeft = Offset(startX, 0f),
                size = Size(size.width - startX, size.height)
            )
        }
        EmberDirection.RIGHT -> {
            val endX = size.width * 0.44f
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        palette.glow.copy(alpha = heatAlpha),
                        palette.end.copy(alpha = midAlpha),
                        Color.Transparent
                    ),
                    startX = 0f,
                    endX = endX
                ),
                topLeft = Offset.Zero,
                size = Size(endX, size.height)
            )
        }
        EmberDirection.RADIAL -> {
            val haloRadius = radialStartRadius + minDim * (0.34f + punch * 0.12f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.glow.copy(alpha = (heatAlpha * 1.15f).coerceIn(0f, 0.55f)),
                        palette.end.copy(alpha = midAlpha),
                        Color.Transparent
                    ),
                    center = center,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = center
            )
        }
    }
}

private fun DrawScope.drawFireEmber(
    embers: List<EmberParticle>,
    bands: FloatArray,
    rng: IntArray,
    bassEnergy: Float,
    punch: Float,
    triggerBeatBurst: Boolean,
    activeAlpha: Float,
    palette: VisualizerPalette,
    config: VisualizerConfig,
    dt: Float,
    timeSec: Float,
    center: Offset,
    centerRingRatio: Float,
    explicitCenterRadiusPx: Float?,
    spread: Float,
    thickness: Float,
    spinSpeed: Float,
    isActive: Boolean
) {
    if (activeAlpha <= 0.005f) return

    val pxDensity = density
    val minDim = min(size.width, size.height)
    val direction = config.emberDirection
    val activeCount = when (config.density) {
        VisualizerDensity.LOW -> 48
        VisualizerDensity.MEDIUM -> 80
        VisualizerDensity.HIGH -> 116
        VisualizerDensity.ULTRA -> MAX_EMBER_PARTICLES
    }.coerceAtMost(embers.size)

    val radialStartRadius = if (explicitCenterRadiusPx != null) {
        explicitCenterRadiusPx * (1f + (punch * 0.145f).coerceIn(0f, 0.22f)) + 2f * pxDensity
    } else {
        minDim * centerRingRatio.coerceIn(0.14f, 0.28f) * 0.52f + punch * (minDim * 0.04f)
    }
    val radialMaxRadius = hypot(
        max(center.x, size.width - center.x),
        max(center.y, size.height - center.y)
    ) * (0.76f + spread * 0.26f)

    // 1. Soft ambient heat glow at emitter edge (or around cover art for Radial)
    if (config.glowEnabled) {
        val heatAlpha = ((0.12f + bassEnergy * 0.14f + punch * 0.34f) * activeAlpha).coerceIn(0f, 0.58f)
        drawAmbientHeatEdge(
            direction = direction,
            palette = palette,
            heatAlpha = heatAlpha,
            center = center,
            radialStartRadius = radialStartRadius,
            minDim = minDim,
            punch = punch
        )
        if (config.mirrorEnabled && direction != EmberDirection.RADIAL) {
            drawAmbientHeatEdge(
                direction = oppositeDirection(direction),
                palette = palette,
                heatAlpha = heatAlpha * 0.8f,
                center = center,
                radialStartRadius = radialStartRadius,
                minDim = minDim,
                punch = punch
            )
        }
        if (direction == EmberDirection.RADIAL && explicitCenterRadiusPx != null) {
            drawCircle(
                color = palette.glow.copy(alpha = ((0.18f + punch * 0.36f) * activeAlpha).coerceIn(0f, 0.65f)),
                radius = radialStartRadius,
                center = center,
                style = Stroke(width = (3.2f + punch * 3.0f) * pxDensity * thickness)
            )
        }
    }

    // 2. Beat-onset spark eruption: immediately launch a wave of fresh hot embers on every kick
    if (triggerBeatBurst) {
        var burstRemaining = (activeCount / 7).coerceIn(8, 22)
        for (i in 0 until activeCount) {
            if (burstRemaining <= 0) break
            val p = embers[i]
            if (p.life > 0.58f) {
                respawnEmber(p, direction, config.mirrorEnabled, bands, rng, prewarm = false)
                p.speedScale *= 1.35f
                p.vx *= 1.35f
                p.vy *= 1.35f
                p.baseSizeDp = (p.baseSizeDp * 1.18f).coerceAtMost(5.2f)
                burstRemaining--
            }
        }
    }

    // 3. Advance physics & render glowing ember particles with crisp beat surge
    val speedMult = (0.35f + spinSpeed * 0.65f) * (0.42f + bassEnergy * 0.45f + punch * 2.25f)

    for (i in 0 until activeCount) {
        val p = embers[i]
        val sway = sin(timeSec * p.turbulenceFreq + p.turbulencePhase) *
            p.turbulenceAmp * spread * (0.80f + bassEnergy * 0.45f + punch * 0.55f)

        var posX: Float
        var posY: Float
        var dirX: Float
        var dirY: Float

        if (direction == EmberDirection.RADIAL) {
            if (isActive || activeAlpha > 0.01f) {
                val swirlDir = if (p.mirrorSide) -1f else 1f
                p.radialProgress += p.lifeSpeed * dt * speedMult * 0.80f
                p.radialAngle += sway * dt * 1.6f * swirlDir
                p.life = p.radialProgress
                if (p.life >= 1f) {
                    respawnEmber(p, direction, config.mirrorEnabled, bands, rng, prewarm = false)
                }
            }
            val easedProgress = p.radialProgress.pow(0.88f)
            val r = radialStartRadius + easedProgress * (radialMaxRadius - radialStartRadius)
            val cosA = cos(p.radialAngle)
            val sinA = sin(p.radialAngle)
            posX = center.x + cosA * r
            posY = center.y + sinA * r
            dirX = cosA
            dirY = sinA
        } else {
            val effectiveDir = if (p.mirrorSide) oppositeDirection(direction) else direction
            val isVertical = effectiveDir == EmberDirection.UP || effectiveDir == EmberDirection.DOWN
            val stepVx = (p.vx + if (isVertical) sway else 0f) * speedMult
            val stepVy = (p.vy + if (!isVertical) sway else 0f) * speedMult
            if (isActive || activeAlpha > 0.01f) {
                p.x += stepVx * dt
                p.y += stepVy * dt
                p.life += p.lifeSpeed * dt * (0.62f + speedMult * 0.36f)
                val outOfBounds = p.x < -0.10f || p.x > 1.10f || p.y < -0.10f || p.y > 1.10f
                if (p.life >= 1f || outOfBounds) {
                    respawnEmber(p, direction, config.mirrorEnabled, bands, rng, prewarm = false)
                }
            }
            posX = p.x * size.width
            posY = p.y * size.height
            val velPxX = stepVx * size.width
            val velPxY = stepVy * size.height
            val velMag = hypot(velPxX, velPxY).coerceAtLeast(0.001f)
            dirX = velPxX / velMag
            dirY = velPxY / velMag
        }

        val life = p.life.coerceIn(0f, 1f)
        val birthFade = (life / 0.08f).coerceIn(0f, 1f)
        val deathFade = (1f - life).pow(1.25f)
        val flicker = 0.72f + 0.28f * sin(timeSec * p.flickerSpeed + p.flickerPhase)
        val beatBrightness = (0.76f + punch * 0.34f).coerceIn(0.70f, 1.12f)
        val alpha = (birthFade * deathFade * flicker * beatBrightness * activeAlpha).coerceIn(0f, 0.98f)
        if (alpha <= 0.01f) continue

        // Heat-to-cool color progression across the ember's lifetime
        val emberColor = if (life < 0.42f) {
            lerp(palette.start, palette.mid, life / 0.42f)
        } else {
            lerp(palette.mid, palette.end, (life - 0.42f) / 0.58f)
        }

        val radius = (p.baseSizeDp * pxDensity * thickness *
            (0.55f + (1f - life) * 0.65f) *
            (0.78f + punch * 0.65f)).coerceAtLeast(1.1f * pxDensity)
        val pos = Offset(posX, posY)
        val hotCoreAlpha = (alpha * (1f - life * 0.58f) * (0.78f + punch * 0.22f)).coerceIn(0f, 0.96f)

        when (config.fillMode) {
            VisualizerFillMode.LINE -> {
                val tailLen = (5.0f + p.speedScale * 8.0f + punch * 14.0f) *
                    pxDensity * thickness * (0.6f + (1f - life) * 0.6f)
                val tailPos = Offset(posX - dirX * tailLen, posY - dirY * tailLen)

                if (config.glowEnabled) {
                    drawLine(
                        color = emberColor.copy(alpha = alpha * 0.24f),
                        start = tailPos,
                        end = pos,
                        strokeWidth = radius * 2.9f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = emberColor.copy(alpha = alpha * 0.28f),
                        radius = radius * 2.2f,
                        center = pos
                    )
                }
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, emberColor.copy(alpha = alpha * 0.92f)),
                        start = tailPos,
                        end = pos
                    ),
                    start = tailPos,
                    end = pos,
                    strokeWidth = radius * 1.45f,
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = emberColor.copy(alpha = alpha),
                    radius = radius,
                    center = pos
                )
                drawCircle(
                    color = Color.White.copy(alpha = hotCoreAlpha),
                    radius = radius * 0.48f,
                    center = pos
                )
            }
            VisualizerFillMode.SOLID -> {
                if (config.glowEnabled) {
                    drawCircle(
                        color = emberColor.copy(alpha = alpha * 0.18f),
                        radius = radius * 3.1f,
                        center = pos
                    )
                    drawCircle(
                        color = emberColor.copy(alpha = alpha * 0.34f),
                        radius = radius * 1.9f,
                        center = pos
                    )
                }
                drawCircle(
                    color = emberColor.copy(alpha = alpha),
                    radius = radius * 1.2f,
                    center = pos
                )
                drawCircle(
                    color = Color.White.copy(alpha = hotCoreAlpha),
                    radius = radius * 0.56f,
                    center = pos
                )
            }
            VisualizerFillMode.NONE -> {
                val sparkR = (radius * 0.72f).coerceAtLeast(1.0f * pxDensity)
                if (config.glowEnabled) {
                    drawCircle(
                        color = emberColor.copy(alpha = alpha * 0.20f),
                        radius = sparkR * 1.65f,
                        center = pos
                    )
                }
                drawCircle(
                    color = emberColor.copy(alpha = alpha),
                    radius = sparkR,
                    center = pos
                )
                drawCircle(
                    color = Color.White.copy(alpha = hotCoreAlpha),
                    radius = sparkR * 0.42f,
                    center = pos
                )
            }
        }
    }
}
