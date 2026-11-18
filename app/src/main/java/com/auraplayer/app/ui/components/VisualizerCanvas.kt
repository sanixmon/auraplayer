package com.auraplayer.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerConfig
import com.auraplayer.app.data.model.VisualizerFillMode
import com.auraplayer.app.data.model.VisualizerFrame
import com.auraplayer.app.data.model.VisualizerStyle
import com.auraplayer.app.playback.VisualizerBus
import com.auraplayer.app.ui.theme.AccentColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
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
    val baseSize: Float,
    val colorIndex: Int
)

private class ShockwaveRing(
    var active: Boolean = false,
    var progress: Float = 0f,
    var strength: Float = 0f
)

fun VisualizerColorScheme.toPalette(
    config: VisualizerConfig,
    dynamicAccent: AccentColors? = null
): VisualizerPalette = when (this) {
    VisualizerColorScheme.DYNAMIC -> {
        val p = dynamicAccent?.primary ?: Color(0xFF00E5FF)
        val s = dynamicAccent?.secondary ?: Color(0xFFA855F7)
        VisualizerPalette(
            start = p,
            mid = blendColors(p, s),
            end = s,
            glow = p
        )
    }
    VisualizerColorScheme.CUSTOM -> {
        val p = Color(config.customVizPrimaryArgb)
        val s = Color(config.customVizSecondaryArgb)
        VisualizerPalette(
            start = p,
            mid = blendColors(p, s),
            end = s,
            glow = p
        )
    }
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

private fun blendColors(a: Color, b: Color): Color = Color(
    red = (a.red + b.red) * 0.5f,
    green = (a.green + b.green) * 0.5f,
    blue = (a.blue + b.blue) * 0.5f,
    alpha = 1f
)

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
    dynamicAccent: AccentColors? = AccentColors(
        primary = MaterialTheme.colorScheme.primary,
        secondary = MaterialTheme.colorScheme.secondary
    )
) {
    val bandCount = config.density.bandCount
    val sampledBands = remember(bandCount) { FloatArray(bandCount) }
    val sampledWave = remember { FloatArray(64) }
    val sampledScalars = remember { FloatArray(VisualizerBus.SCALAR_COUNT) }

    val smoothedBands = remember(bandCount) { FloatArray(bandCount) }
    val peakCaps = remember(bandCount) { FloatArray(bandCount) }
    val peakHoldSec = remember(bandCount) { FloatArray(bandCount) }
    val peakVelocity = remember(bandCount) { FloatArray(bandCount) }
    val smoothedWave = remember { FloatArray(64) }
    val ringPointsX = remember(bandCount) { FloatArray(bandCount) }
    val ringPointsY = remember(bandCount) { FloatArray(bandCount) }
    val polyPointsX = remember(bandCount) { FloatArray(bandCount + 4) }
    val polyPointsY = remember(bandCount) { FloatArray(bandCount + 4) }

    val primaryPath = remember { Path() }
    val secondaryPath = remember { Path() }
    val shockwaves = remember { List(4) { ShockwaveRing() } }

    val particles = remember {
        List(76) { idx ->
            RadialParticle(
                angleRad = ((idx * 137.508f) % 360f) * (PI.toFloat() / 180f),
                radiusProgress = (idx % 19) / 19f,
                speedFactor = 0.45f + ((idx * 7) % 10) * 0.08f,
                baseSize = 1.8f + (idx % 4) * 0.95f,
                colorIndex = idx % 3
            )
        }
    }

    var animTimeSec by remember { mutableFloatStateOf(0f) }
    var lastDeltaSec by remember { mutableFloatStateOf(0.016f) }
    var lastShockwaveTimeSec by remember { mutableFloatStateOf(-1f) }
    var frameTick by remember { mutableLongStateOf(0L) }
    var smoothedBass by remember { mutableFloatStateOf(0f) }

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

    Canvas(
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    ) {
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
            outScalars = sampledScalars
        )

        val targetBass = when {
            useDemo -> (0.42f + 0.34f * sin(animTimeSec * 3.1f)).coerceIn(0f, 1f)
            hasBusSnapshot -> (sampledScalars[VisualizerBus.SCALAR_BASS] * sensitivity).coerceIn(0f, 1f)
            isPlaying -> (frame.bassEnergy * sensitivity).coerceIn(0f, 1f)
            else -> 0f
        }
        val bassAlpha = 1f - exp(-dt / if (targetBass > smoothedBass) 0.018f else 0.14f)
        smoothedBass += (targetBass - smoothedBass) * bassAlpha

        val currentBeatPulse = when {
            useDemo -> if (sin(animTimeSec * 3.1f) > 0.92f) 0.7f else 0f
            hasBusSnapshot -> (sampledScalars[VisualizerBus.SCALAR_BEAT] * sensitivity).coerceIn(0f, 1f)
            isPlaying -> (frame.beatPulse * sensitivity).coerceIn(0f, 1f)
            else -> 0f
        }

        // Trigger beat-synced shockwaves (180 ms cooldown).
        if ((isPlaying || demoWhenIdle) &&
            currentBeatPulse > 0.38f &&
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
                sw.progress += 1.35f * dt
                if (sw.progress >= 1f) {
                    sw.active = false
                }
            }
        }

        if ((isPlaying || demoWhenIdle) && config.particlesEnabled) {
            val particleVelocity = (0.24f + smoothedBass * 1.55f) * (0.4f + spinSpeed * 0.6f)
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
        val waveAlpha = 1f - exp(-dt / 0.028f)
        for (i in smoothedWave.indices) {
            val targetWave = when {
                useDemo -> {
                    val phase = animTimeSec * 4.5f + i * 0.3f
                    (sin(phase) * 0.55f * sensitivity).coerceIn(-1f, 1f)
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
            smoothedWave[i] = smoothedWave[i] + (targetWave - smoothedWave[i]) * waveAlpha
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
                palette = palette,
                config = config,
                center = effectiveCenter,
                spread = spread,
                thickness = thickness
            )
            VisualizerStyle.WAVEFORM -> drawWaveform(
                wave = smoothedWave,
                mainPath = primaryPath,
                mirrorPath = secondaryPath,
                bassEnergy = smoothedBass,
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
                palette = palette,
                config = config,
                timeSec = animTimeSec,
                center = effectiveCenter,
                spread = spread,
                thickness = thickness,
                spinSpeed = spinSpeed
            )
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
    val baseRadius = if (explicitCenterRadiusPx != null) {
        explicitCenterRadiusPx + bassEnergy * (minDim * 0.018f)
    } else {
        minDim * centerRingRatio.coerceIn(0.16f, 0.36f) + bassEnergy * (minDim * 0.028f)
    }
    val maxSpike = minDim * 0.23f * spread
    val rotationOffset = timeSec * 0.24f * spinSpeed
    val addBlend = if (config.glowEnabled) BlendMode.Plus else BlendMode.SrcOver
    val isSolid = config.fillMode == VisualizerFillMode.SOLID

    // 1. Additive nebula glow behind the album art
    if (config.glowEnabled) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.glow.copy(alpha = (0.28f + bassEnergy * 0.36f).coerceIn(0f, 0.65f)),
                    palette.end.copy(alpha = (0.14f + bassEnergy * 0.18f).coerceIn(0f, 0.38f)),
                    Color.Transparent
                ),
                center = center,
                radius = maxDim * 0.65f
            ),
            radius = maxDim * 0.65f,
            center = center,
            blendMode = BlendMode.Plus
        )
    }

    // 2. Beat-triggered expanding shockwave rings
    for (sw in shockwaves) {
        if (!sw.active) continue
        val swRadius = baseRadius + sw.progress * (maxDim * 0.52f)
        val swAlpha = ((1f - sw.progress) * (0.35f + sw.strength * 0.45f)).coerceIn(0f, 0.75f)
        drawCircle(
            color = palette.start.copy(alpha = swAlpha),
            radius = swRadius,
            center = center,
            style = Stroke(width = (3.5f * (1f - sw.progress) + 1f) * thickness),
            blendMode = addBlend
        )
    }

    // 3. Additive beat-reactive bokeh particles
    if (config.particlesEnabled) {
        val maxParticleRadius = maxDim * 0.62f
        for (p in particles) {
            val r = baseRadius * 0.8f + p.radiusProgress * (maxParticleRadius - baseRadius * 0.65f)
            val a = p.angleRad + timeSec * 0.16f * spinSpeed * p.speedFactor
            val pos = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
            val alpha = ((1f - p.radiusProgress) * (0.32f + bassEnergy * 0.68f)).coerceIn(0f, 0.95f)
            val color = when (p.colorIndex) {
                0 -> palette.start
                1 -> palette.mid
                else -> palette.end
            }
            if (config.glowEnabled) {
                drawCircle(
                    color = color.copy(alpha = alpha * 0.35f),
                    radius = p.baseSize * thickness * 2.1f * (1f + bassEnergy * 0.6f),
                    center = pos,
                    blendMode = BlendMode.Plus
                )
            }
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = p.baseSize * thickness * (1f + bassEnergy * 0.7f),
                center = pos,
                blendMode = addBlend
            )
        }
    }

    // 4. Radial spectrum coordinates & smooth quadratic Bezier closed aura
    val halfCount = max(1, count / 2)
    val angleStep = (2.0 * PI / count).toFloat()
    val barWidth = ((minDim / count) * 0.58f * thickness).coerceIn(1.8f, 10f)

    for (i in 0 until count) {
        val mirrorIdx = if (i < halfCount) i else count - 1 - i
        val mag = bands[mirrorIdx.coerceIn(0, bands.lastIndex)]
        val angle = i * angleStep + rotationOffset
        val outerR = baseRadius + 8f + mag * maxSpike
        pointsX[i] = center.x + cos(angle) * outerR
        pointsY[i] = center.y + sin(angle) * outerR
    }

    buildSmoothClosedPath(auraPath, pointsX, pointsY, count)

    if (isSolid) {
        // Zero-alloc donut fill (EvenOdd removes inner cover circle cleanly).
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
                    palette.start.copy(alpha = 0.88f),
                    palette.mid.copy(alpha = 0.86f),
                    palette.end.copy(alpha = 0.88f),
                    palette.start.copy(alpha = 0.88f)
                ),
                center = center
            ),
            blendMode = if (config.glowEnabled) BlendMode.Plus else BlendMode.SrcOver
        )
    } else {
        drawPath(
            path = auraPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.start.copy(alpha = 0.24f),
                    palette.mid.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = center,
                radius = baseRadius + maxSpike + 20f
            ),
            blendMode = addBlend
        )
    }

    if (config.glowEnabled) {
        drawPath(
            path = auraPath,
            color = palette.glow.copy(alpha = 0.35f),
            style = Stroke(width = 7f * thickness, join = StrokeJoin.Round),
            blendMode = BlendMode.Plus
        )
    }

    drawPath(
        path = auraPath,
        brush = Brush.sweepGradient(
            listOf(palette.start, palette.mid, palette.end, palette.start),
            center = center
        ),
        style = Stroke(width = (if (isSolid) 3.0f else 2.4f) * thickness, join = StrokeJoin.Round),
        blendMode = addBlend
    )

    // 5. Inner glowing sweep ring
    drawCircle(
        brush = Brush.sweepGradient(
            listOf(palette.start, palette.mid, palette.end, palette.start),
            center = center
        ),
        radius = baseRadius,
        center = center,
        style = Stroke(width = (3.5f + bassEnergy * 2.5f) * thickness),
        blendMode = addBlend
    )

    // 6. Radial frequency spokes + peak dots (skip heavy spokes in SOLID to showcase solid silhouette cleanly)
    for (i in 0 until count) {
        val mirrorIdx = if (i < halfCount) i else count - 1 - i
        val mag = bands[mirrorIdx.coerceIn(0, bands.lastIndex)]
        val angle = i * angleStep + rotationOffset
        val cosA = cos(angle)
        val sinA = sin(angle)

        if (!isSolid) {
            val innerR = if (config.mirrorEnabled) {
                (baseRadius - mag * maxSpike * 0.32f).coerceAtLeast(baseRadius * 0.45f)
            } else {
                baseRadius + 3f
            }
            val outerR = baseRadius + 6f + mag * maxSpike
            val t = mirrorIdx.toFloat() / halfCount
            val barColor = when {
                t < 0.35f -> palette.start
                t < 0.7f -> palette.mid
                else -> palette.end
            }
            drawLine(
                color = barColor,
                start = Offset(center.x + cosA * innerR, center.y + sinA * innerR),
                end = Offset(center.x + cosA * outerR, center.y + sinA * outerR),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
                blendMode = addBlend
            )
        }

        if (config.peakCapsEnabled) {
            val peakR = baseRadius + 12f + peaks[mirrorIdx.coerceIn(0, peaks.lastIndex)] * maxSpike
            drawCircle(
                color = palette.start.copy(alpha = 0.9f),
                radius = 2.2f * thickness,
                center = Offset(center.x + cosA * peakR, center.y + sinA * peakR),
                blendMode = addBlend
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
    palette: VisualizerPalette,
    config: VisualizerConfig,
    center: Offset,
    spread: Float,
    thickness: Float
) {
    val count = bands.size
    if (count == 0) return

    val addBlend = if (config.glowEnabled) BlendMode.Plus else BlendMode.SrcOver
    val totalWidth = size.width
    val slotWidth = totalWidth / count
    val maxHeight = size.height * 0.78f * spread.coerceAtMost(1.25f)
    val baselineY = if (config.mirrorEnabled) center.y else size.height * 0.80f

    if (config.glowEnabled) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.glow.copy(alpha = 0.16f + bassEnergy * 0.2f),
                    Color.Transparent
                ),
                center = Offset(size.width / 2f, baselineY),
                radius = size.width * 0.55f
            ),
            radius = size.width * 0.55f,
            center = Offset(size.width / 2f, baselineY),
            blendMode = BlendMode.Plus
        )
    }

    val brush = Brush.verticalGradient(
        colors = listOf(palette.start, palette.mid, palette.end),
        startY = 0f,
        endY = size.height
    )

    if (config.fillMode == VisualizerFillMode.SOLID) {
        val heightScale = if (config.mirrorEnabled) maxHeight * 0.5f else maxHeight
        for (i in 0 until count) {
            polyX[i] = i * slotWidth + slotWidth * 0.5f
            polyY[i] = baselineY - max(3f, bands[i] * heightScale)
        }

        // Trace continuous Catmull-Rom solid polygon down to baseline.
        solidPath.reset()
        solidPath.moveTo(0f, baselineY)
        solidPath.lineTo(polyX[0], polyY[0])
        addCatmullRomCurveTo(solidPath, polyX, polyY, count)
        solidPath.lineTo(totalWidth, baselineY)
        solidPath.close()

        drawPath(
            path = solidPath,
            brush = brush,
            alpha = 0.88f,
            blendMode = addBlend
        )

        if (config.glowEnabled) {
            drawPath(
                path = solidPath,
                color = palette.glow.copy(alpha = 0.42f),
                style = Stroke(width = 7f * thickness, join = StrokeJoin.Round),
                blendMode = BlendMode.Plus
            )
        }

        drawPath(
            path = solidPath,
            color = palette.start,
            style = Stroke(width = 2.4f * thickness, join = StrokeJoin.Round),
            blendMode = addBlend
        )

        if (config.mirrorEnabled) {
            for (i in 0 until count) {
                polyY[i] = baselineY + max(3f, bands[i] * heightScale)
            }
            mirrorSolidPath.reset()
            mirrorSolidPath.moveTo(0f, baselineY)
            mirrorSolidPath.lineTo(polyX[0], polyY[0])
            addCatmullRomCurveTo(mirrorSolidPath, polyX, polyY, count)
            mirrorSolidPath.lineTo(totalWidth, baselineY)
            mirrorSolidPath.close()

            drawPath(
                path = mirrorSolidPath,
                brush = brush,
                alpha = 0.65f,
                blendMode = addBlend
            )
        }

        if (config.peakCapsEnabled) {
            for (i in 0 until count) {
                val peakY = (baselineY - peaks[i] * heightScale - 4f).coerceAtLeast(2f)
                drawCircle(
                    color = palette.start.copy(alpha = 0.95f),
                    radius = 2.1f * thickness,
                    center = Offset(polyX[i], peakY),
                    blendMode = addBlend
                )
            }
        }
        return
    }

    // LINE fill mode: crisp separated neon bars
    val barWidth = (slotWidth * 0.60f * thickness).coerceIn(2f, slotWidth * 0.90f)
    val gapOffset = (slotWidth - barWidth) / 2f

    for (i in 0 until count) {
        val value = bands[i]
        val x = i * slotWidth + gapOffset
        val barHeight = max(4f, value * (if (config.mirrorEnabled) maxHeight * 0.5f else maxHeight))

        if (config.glowEnabled) {
            drawRoundRect(
                color = palette.glow.copy(alpha = 0.22f),
                topLeft = Offset(x = x - 2f, y = baselineY - barHeight - 2f),
                size = Size(
                    width = barWidth + 4f,
                    height = if (config.mirrorEnabled) barHeight * 2f + 4f else barHeight + 4f
                ),
                cornerRadius = CornerRadius(barWidth, barWidth),
                blendMode = BlendMode.Plus
            )
        }

        drawRoundRect(
            brush = brush,
            topLeft = Offset(x, baselineY - barHeight),
            size = Size(barWidth, if (config.mirrorEnabled) barHeight * 2f else barHeight),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            blendMode = addBlend
        )

        if (config.peakCapsEnabled) {
            val peakHeight = peaks[i] * (if (config.mirrorEnabled) maxHeight * 0.5f else maxHeight)
            val capY = (baselineY - peakHeight - 4f).coerceAtLeast(2f)
            drawRoundRect(
                color = palette.start.copy(alpha = 0.95f),
                topLeft = Offset(x, capY),
                size = Size(barWidth, 3f),
                cornerRadius = CornerRadius(2f, 2f),
                blendMode = addBlend
            )
        }
    }
}

private fun DrawScope.drawWaveform(
    wave: FloatArray,
    mainPath: Path,
    mirrorPath: Path,
    bassEnergy: Float,
    palette: VisualizerPalette,
    config: VisualizerConfig,
    center: Offset,
    spread: Float,
    thickness: Float
) {
    if (wave.isEmpty()) return
    val addBlend = if (config.glowEnabled) BlendMode.Plus else BlendMode.SrcOver
    val centerY = center.y
    val amplitude = size.height * 0.36f * spread.coerceAtMost(1.35f)
    val stepX = size.width / max(1, wave.size - 1)
    val isSolid = config.fillMode == VisualizerFillMode.SOLID

    mainPath.reset()
    mirrorPath.reset()

    if (isSolid) {
        // Fill closed wave body anchored to baseline (and symmetric lower lobe when mirrorEnabled).
        mainPath.moveTo(0f, centerY)
        for (i in wave.indices) {
            val x = i * stepX
            val y = centerY - wave[i] * amplitude
            if (i == 0) {
                mainPath.lineTo(x, y)
            } else {
                val prevX = (i - 1) * stepX
                val midX = (prevX + x) / 2f
                val prevY = centerY - wave[i - 1] * amplitude
                mainPath.cubicTo(midX, prevY, midX, y, x, y)
            }
        }
        mainPath.lineTo(size.width, centerY)
        mainPath.close()

        val fillBrush = Brush.verticalGradient(
            colors = listOf(palette.start, palette.mid, palette.end),
            startY = centerY - amplitude,
            endY = centerY + amplitude
        )
        drawPath(
            path = mainPath,
            brush = fillBrush,
            alpha = 0.78f,
            blendMode = addBlend
        )

        if (config.mirrorEnabled) {
            mirrorPath.moveTo(0f, centerY)
            for (i in wave.indices) {
                val x = i * stepX
                val y = centerY + wave[i] * amplitude * 0.85f
                if (i == 0) {
                    mirrorPath.lineTo(x, y)
                } else {
                    val prevX = (i - 1) * stepX
                    val midX = (prevX + x) / 2f
                    val prevY = centerY + wave[i - 1] * amplitude * 0.85f
                    mirrorPath.cubicTo(midX, prevY, midX, y, x, y)
                }
            }
            mirrorPath.lineTo(size.width, centerY)
            mirrorPath.close()
            drawPath(
                path = mirrorPath,
                brush = fillBrush,
                alpha = 0.56f,
                blendMode = addBlend
            )
        }

        if (config.glowEnabled) {
            drawPath(
                path = mainPath,
                color = palette.glow.copy(alpha = 0.35f + bassEnergy * 0.2f),
                style = Stroke(width = 8f * thickness, join = StrokeJoin.Round),
                blendMode = BlendMode.Plus
            )
        }
        drawPath(
            path = mainPath,
            brush = Brush.horizontalGradient(listOf(palette.start, palette.mid, palette.end)),
            style = Stroke(width = 2.6f * thickness, join = StrokeJoin.Round),
            blendMode = addBlend
        )
        return
    }

    // LINE mode: glowing smooth Bezier stroke
    for (i in wave.indices) {
        val x = i * stepX
        val y = centerY - wave[i] * amplitude
        val mirrorY = centerY + wave[i] * amplitude * 0.75f
        if (i == 0) {
            mainPath.moveTo(x, y)
            mirrorPath.moveTo(x, mirrorY)
        } else {
            val prevX = (i - 1) * stepX
            val midX = (prevX + x) / 2f
            val prevY = centerY - wave[i - 1] * amplitude
            val prevMirrorY = centerY + wave[i - 1] * amplitude * 0.75f
            mainPath.cubicTo(midX, prevY, midX, y, x, y)
            mirrorPath.cubicTo(midX, prevMirrorY, midX, mirrorY, x, mirrorY)
        }
    }

    val strokeBrush = Brush.horizontalGradient(
        colors = listOf(palette.start, palette.mid, palette.end)
    )

    if (config.glowEnabled) {
        drawPath(
            path = mainPath,
            color = palette.glow.copy(alpha = 0.28f + bassEnergy * 0.22f),
            style = Stroke(width = 16f * thickness, cap = StrokeCap.Round, join = StrokeJoin.Round),
            blendMode = BlendMode.Plus
        )
    }

    drawPath(
        path = mainPath,
        brush = strokeBrush,
        style = Stroke(width = 5f * thickness, cap = StrokeCap.Round, join = StrokeJoin.Round),
        blendMode = addBlend
    )

    if (config.mirrorEnabled) {
        drawPath(
            path = mirrorPath,
            brush = strokeBrush,
            alpha = 0.55f,
            style = Stroke(width = 3.5f * thickness, cap = StrokeCap.Round, join = StrokeJoin.Round),
            blendMode = addBlend
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
    palette: VisualizerPalette,
    config: VisualizerConfig,
    center: Offset,
    spread: Float,
    thickness: Float
) {
    val count = bands.size
    if (count == 0) return

    val addBlend = if (config.glowEnabled) BlendMode.Plus else BlendMode.SrcOver
    val halfCount = max(1, count / 2)
    val centerX = center.x
    val halfWidth = size.width / 2f
    val slotWidth = halfWidth / halfCount
    val maxH = size.height * 0.36f * spread.coerceAtMost(1.35f)
    val centerY = center.y

    if (config.glowEnabled) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.glow.copy(alpha = 0.18f + bassEnergy * 0.24f),
                    Color.Transparent
                ),
                center = Offset(centerX, centerY),
                radius = max(size.width, size.height) * 0.48f
            ),
            radius = max(size.width, size.height) * 0.48f,
            center = Offset(centerX, centerY),
            blendMode = BlendMode.Plus
        )
    }

    val brush = Brush.horizontalGradient(
        colors = listOf(palette.end, palette.mid, palette.start, palette.mid, palette.end)
    )

    if (config.fillMode == VisualizerFillMode.SOLID) {
        // Unified symmetric mirrored solid polygon (Catmull-Rom smooth top & bottom wings).
        val upperCount = halfCount * 2
        for (i in 0 until halfCount) {
            val leftBand = bands[halfCount - 1 - i]
            polyX[i] = centerX - (halfCount - i) * slotWidth + slotWidth * 0.5f
            polyY[i] = centerY - max(3f, leftBand * maxH)

            val rightBand = bands[i]
            polyX[halfCount + i] = centerX + i * slotWidth + slotWidth * 0.5f
            polyY[halfCount + i] = centerY - max(3f, rightBand * maxH)
        }

        solidPath.reset()
        solidPath.moveTo(0f, centerY)
        solidPath.lineTo(polyX[0], polyY[0])
        addCatmullRomCurveTo(solidPath, polyX, polyY, upperCount)
        solidPath.lineTo(size.width, centerY)

        // Bottom symmetric reflection returning right-to-left
        for (i in 0 until halfCount) {
            val rightIdx = halfCount - 1 - i
            polyX[i] = centerX + rightIdx * slotWidth + slotWidth * 0.5f
            polyY[i] = centerY + max(3f, bands[rightIdx] * maxH)

            polyX[halfCount + i] = centerX - (i + 1) * slotWidth + slotWidth * 0.5f
            polyY[halfCount + i] = centerY + max(3f, bands[i] * maxH)
        }
        solidPath.lineTo(polyX[0], polyY[0])
        addCatmullRomCurveTo(solidPath, polyX, polyY, upperCount)
        solidPath.close()

        drawPath(
            path = solidPath,
            brush = brush,
            alpha = 0.84f,
            blendMode = addBlend
        )
        if (config.glowEnabled) {
            drawPath(
                path = solidPath,
                color = palette.glow.copy(alpha = 0.36f),
                style = Stroke(width = 7f * thickness, join = StrokeJoin.Round),
                blendMode = BlendMode.Plus
            )
        }
        drawPath(
            path = solidPath,
            brush = brush,
            style = Stroke(width = 2.4f * thickness, join = StrokeJoin.Round),
            blendMode = addBlend
        )
        return
    }

    // LINE fill mode: discrete symmetric glowing bars
    val barWidth = (slotWidth * 0.60f * thickness).coerceIn(2f, slotWidth * 0.90f)
    for (i in 0 until halfCount) {
        val value = bands[i]
        val h = max(4f, value * maxH)
        val offset = i * slotWidth + (slotWidth - barWidth) / 2f

        val rightX = centerX + offset
        val leftX = centerX - offset - barWidth

        if (config.glowEnabled) {
            drawRoundRect(
                color = palette.glow.copy(alpha = 0.22f),
                topLeft = Offset(rightX - 1.5f, centerY - h - 1.5f),
                size = Size(barWidth + 3f, h * 2f + 3f),
                cornerRadius = CornerRadius(barWidth, barWidth),
                blendMode = BlendMode.Plus
            )
            drawRoundRect(
                color = palette.glow.copy(alpha = 0.22f),
                topLeft = Offset(leftX - 1.5f, centerY - h - 1.5f),
                size = Size(barWidth + 3f, h * 2f + 3f),
                cornerRadius = CornerRadius(barWidth, barWidth),
                blendMode = BlendMode.Plus
            )
        }

        drawRoundRect(
            brush = brush,
            topLeft = Offset(rightX, centerY - h),
            size = Size(barWidth, h * 2f),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            blendMode = addBlend
        )
        drawRoundRect(
            brush = brush,
            topLeft = Offset(leftX, centerY - h),
            size = Size(barWidth, h * 2f),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            blendMode = addBlend
        )

        if (config.peakCapsEnabled) {
            val peakY = (centerY - peaks[i] * maxH - 4f).coerceAtLeast(2f)
            drawCircle(
                color = palette.start,
                radius = 2.2f * thickness,
                center = Offset(rightX + barWidth / 2f, peakY),
                blendMode = addBlend
            )
            drawCircle(
                color = palette.start,
                radius = 2.2f * thickness,
                center = Offset(leftX + barWidth / 2f, peakY),
                blendMode = addBlend
            )
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

    val addBlend = if (config.glowEnabled) BlendMode.Plus else BlendMode.SrcOver
    val centerY = center.y
    val stepX = size.width / count
    val isSolid = config.fillMode == VisualizerFillMode.SOLID

    wavePath.reset()
    for (i in 0 until count) {
        val energy = bands[i]
        val x = i * stepX + stepX / 2f
        val driftY = sin(timeSec * (2.5f * (0.4f + spinSpeed * 0.6f)) + i * 0.45f) *
            size.height * (0.12f + energy * 0.28f * spread.coerceAtMost(1.3f))
        val y = centerY + driftY
        polyX[i] = x
        polyY[i] = y
    }

    wavePath.moveTo(polyX[0], polyY[0])
    addCatmullRomCurveTo(wavePath, polyX, polyY, count)

    if (isSolid) {
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
            ),
            blendMode = addBlend
        )
    }

    for (i in 0 until count) {
        val energy = bands[i]
        val x = polyX[i]
        val y = polyY[i]
        val radius = ((3.2f + energy * (size.height * 0.08f)) * thickness).coerceAtLeast(2.5f)
        val color = when {
            i % 3 == 0 -> palette.start
            i % 3 == 1 -> palette.mid
            else -> palette.end
        }

        if (config.glowEnabled) {
            drawCircle(
                color = color.copy(alpha = 0.28f + bassEnergy * 0.2f),
                radius = radius * 2.4f,
                center = Offset(x, y),
                blendMode = BlendMode.Plus
            )
        }

        drawCircle(
            color = color.copy(alpha = 0.92f),
            radius = radius,
            center = Offset(x, y),
            blendMode = addBlend
        )

        if (config.mirrorEnabled) {
            drawCircle(
                color = color.copy(alpha = 0.5f),
                radius = radius * 0.8f,
                center = Offset(x, centerY - (y - centerY)),
                blendMode = addBlend
            )
        }
    }

    drawPath(
        path = wavePath,
        color = palette.mid.copy(alpha = if (isSolid) 0.75f else 0.28f),
        style = Stroke(width = (if (isSolid) 3.0f else 1.8f) * thickness),
        blendMode = addBlend
    )
}
