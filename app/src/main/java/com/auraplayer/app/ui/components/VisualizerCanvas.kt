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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerConfig
import com.auraplayer.app.data.model.VisualizerFrame
import com.auraplayer.app.data.model.VisualizerStyle
import com.auraplayer.app.ui.theme.AccentColors
import kotlin.math.PI
import kotlin.math.cos
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
    dynamicAccent: AccentColors? = AccentColors(
        primary = MaterialTheme.colorScheme.primary,
        secondary = MaterialTheme.colorScheme.secondary
    )
) {
    val bandCount = config.density.bandCount
    val smoothedBands = remember(bandCount) { FloatArray(bandCount) }
    val peakCaps = remember(bandCount) { FloatArray(bandCount) }
    val smoothedWave = remember { FloatArray(64) }
    val particles = remember {
        List(72) { idx ->
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
    var frameTick by remember { mutableLongStateOf(0L) }
    var smoothedBass by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, demoWhenIdle) {
        var lastNanos = 0L
        var settleFrames = if (isPlaying || demoWhenIdle) Int.MAX_VALUE else 45
        while (settleFrames > 0) {
            withFrameNanos { nowNanos ->
                val dt = if (lastNanos != 0L) {
                    ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                } else {
                    0.016f
                }
                lastNanos = nowNanos
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

        val rawBands = frame.spectrumBands
        val useDemo = !isPlaying && demoWhenIdle
        val smoothFactor = config.smoothing.coerceIn(0.15f, 0.95f)
        val sensitivity = config.sensitivity.coerceIn(0.5f, 2.5f)
        val spread = config.spreadScale.coerceIn(0.5f, 1.5f)
        val thickness = config.strokeThickness.coerceIn(0.6f, 1.8f)
        val spinSpeed = config.rotationSpeed.coerceIn(0f, 2.5f)

        val targetBass = when {
            useDemo -> (0.42f + 0.28f * sin(animTimeSec * 3.1f)).coerceIn(0f, 1f)
            isPlaying -> (frame.bassEnergy * sensitivity).coerceIn(0f, 1f)
            else -> 0f
        }
        smoothedBass += (targetBass - smoothedBass) * (1f - smoothFactor * 0.65f)

        if ((isPlaying || demoWhenIdle) && config.particlesEnabled) {
            val particleStep = (0.004f + smoothedBass * 0.022f) * (0.5f + spinSpeed * 0.5f)
            for (p in particles) {
                p.radiusProgress = (p.radiusProgress + particleStep * p.speedFactor) % 1f
            }
        }

        for (i in 0 until bandCount) {
            val target = when {
                useDemo -> {
                    val phase = animTimeSec * 3.0f + i * 0.24f
                    ((sin(phase) * 0.35f + cos(phase * 0.55f) * 0.25f + 0.45f) * 0.65f * sensitivity)
                        .coerceIn(0.05f, 1f)
                }
                isPlaying && rawBands.isNotEmpty() -> {
                    val srcIdx = ((i.toFloat() / bandCount) * rawBands.size)
                        .toInt()
                        .coerceIn(0, rawBands.lastIndex)
                    (rawBands[srcIdx] * sensitivity).coerceIn(0f, 1f)
                }
                else -> 0f
            }

            val prev = smoothedBands[i]
            val next = if (target > prev) {
                prev + (target - prev) * (1f - smoothFactor * 0.55f)
            } else {
                prev + (target - prev) * (1f - smoothFactor * 0.85f)
            }
            smoothedBands[i] = next.coerceIn(0f, 1f)

            if (next >= peakCaps[i]) {
                peakCaps[i] = next
            } else {
                peakCaps[i] = max(next, peakCaps[i] - 0.01f)
            }
        }

        val rawWave = frame.waveform
        for (i in smoothedWave.indices) {
            val targetWave = when {
                useDemo -> {
                    val phase = animTimeSec * 4.5f + i * 0.3f
                    (sin(phase) * 0.55f * sensitivity).coerceIn(-1f, 1f)
                }
                isPlaying && rawWave.isNotEmpty() -> {
                    val srcIdx = ((i.toFloat() / smoothedWave.size) * rawWave.size)
                        .toInt()
                        .coerceIn(0, rawWave.lastIndex)
                    (rawWave[srcIdx] * sensitivity).coerceIn(-1f, 1f)
                }
                else -> 0f
            }
            smoothedWave[i] = smoothedWave[i] + (targetWave - smoothedWave[i]) * (1f - smoothFactor * 0.65f)
        }

        when (config.style) {
            VisualizerStyle.CIRCULAR_AURA -> drawRingVisualizer(
                bands = smoothedBands,
                peaks = peakCaps,
                particles = particles,
                bassEnergy = smoothedBass,
                palette = palette,
                config = config,
                timeSec = animTimeSec,
                centerRingRatio = centerRingRadiusRatio,
                spread = spread,
                thickness = thickness,
                spinSpeed = spinSpeed
            )
            VisualizerStyle.BARS -> drawBars(
                bands = smoothedBands,
                peaks = peakCaps,
                palette = palette,
                config = config,
                spread = spread,
                thickness = thickness
            )
            VisualizerStyle.WAVEFORM -> drawWaveform(
                wave = smoothedWave,
                palette = palette,
                config = config,
                spread = spread,
                thickness = thickness
            )
            VisualizerStyle.MIRROR_SPECTRUM -> drawMirrorSpectrum(
                bands = smoothedBands,
                peaks = peakCaps,
                palette = palette,
                config = config,
                spread = spread,
                thickness = thickness
            )
            VisualizerStyle.PARTICLE_PULSE -> drawParticlePulse(
                bands = smoothedBands,
                palette = palette,
                config = config,
                timeSec = animTimeSec,
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
    bassEnergy: Float,
    palette: VisualizerPalette,
    config: VisualizerConfig,
    timeSec: Float,
    centerRingRatio: Float,
    spread: Float,
    thickness: Float,
    spinSpeed: Float
) {
    val count = bands.size
    if (count == 0) return

    val center = Offset(size.width / 2f, size.height / 2f)
    val minDim = min(size.width, size.height)
    val maxDim = max(size.width, size.height)
    val baseRadius = minDim * centerRingRatio.coerceIn(0.16f, 0.36f) + bassEnergy * (minDim * 0.025f)
    val maxSpike = minDim * 0.22f * spread
    val rotationOffset = timeSec * 0.24f * spinSpeed

    // Unclipped full-bleed ambient glow
    if (config.glowEnabled) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.glow.copy(alpha = (0.20f + bassEnergy * 0.28f).coerceIn(0f, 0.55f)),
                    palette.end.copy(alpha = (0.09f + bassEnergy * 0.14f).coerceIn(0f, 0.3f)),
                    Color.Transparent
                ),
                center = center,
                radius = maxDim * 0.65f
            ),
            radius = maxDim * 0.65f,
            center = center
        )
    }

    // Beat-reactive particles expanding across the entire full-bleed stage
    if (config.particlesEnabled) {
        val maxParticleRadius = maxDim * 0.62f
        for (p in particles) {
            val r = baseRadius * 0.8f + p.radiusProgress * (maxParticleRadius - baseRadius * 0.65f)
            val a = p.angleRad + timeSec * 0.16f * spinSpeed * p.speedFactor
            val pos = Offset(center.x + cos(a) * r, center.y + sin(a) * r)
            val alpha = ((1f - p.radiusProgress) * (0.28f + bassEnergy * 0.65f)).coerceIn(0f, 0.9f)
            val color = when (p.colorIndex) {
                0 -> palette.start
                1 -> palette.mid
                else -> palette.end
            }
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = p.baseSize * thickness * (1f + bassEnergy * 0.7f),
                center = pos
            )
        }
    }

    // Inner sweep halo ring
    drawCircle(
        brush = Brush.sweepGradient(listOf(palette.start, palette.mid, palette.end, palette.start)),
        radius = baseRadius,
        center = center,
        style = Stroke(width = (3.5f + bassEnergy * 2.5f) * thickness)
    )

    val halfCount = max(1, count / 2)
    val outerPath = Path()
    val angleStep = (2.0 * PI / count).toFloat()
    val barWidth = ((minDim / count) * 0.58f * thickness).coerceIn(1.8f, 10f)

    for (i in 0 until count) {
        val mirrorIdx = if (i < halfCount) i else count - 1 - i
        val mag = bands[mirrorIdx.coerceIn(0, bands.lastIndex)]
        val angle = i * angleStep + rotationOffset
        val cosA = cos(angle)
        val sinA = sin(angle)

        val innerR = if (config.mirrorEnabled) baseRadius - mag * maxSpike * 0.25f else baseRadius + 4f
        val outerR = baseRadius + 8f + mag * maxSpike

        val start = Offset(center.x + cosA * innerR, center.y + sinA * innerR)
        val end = Offset(center.x + cosA * outerR, center.y + sinA * outerR)

        val t = mirrorIdx.toFloat() / halfCount
        val barColor = when {
            t < 0.35f -> palette.start
            t < 0.7f -> palette.mid
            else -> palette.end
        }

        drawLine(
            color = barColor,
            start = start,
            end = end,
            strokeWidth = barWidth,
            cap = StrokeCap.Round
        )

        val contourPoint = Offset(center.x + cosA * (outerR + 3f), center.y + sinA * (outerR + 3f))
        if (i == 0) {
            outerPath.moveTo(contourPoint.x, contourPoint.y)
        } else {
            outerPath.lineTo(contourPoint.x, contourPoint.y)
        }

        if (config.peakCapsEnabled) {
            val peakR = baseRadius + 10f + peaks[mirrorIdx.coerceIn(0, peaks.lastIndex)] * maxSpike
            drawCircle(
                color = palette.start.copy(alpha = 0.85f),
                radius = 2.1f * thickness,
                center = Offset(center.x + cosA * peakR, center.y + sinA * peakR)
            )
        }
    }
    outerPath.close()

    drawPath(
        path = outerPath,
        color = palette.glow.copy(alpha = if (config.glowEnabled) 0.38f else 0.2f),
        style = Stroke(width = 2.2f * thickness, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawBars(
    bands: FloatArray,
    peaks: FloatArray,
    palette: VisualizerPalette,
    config: VisualizerConfig,
    spread: Float,
    thickness: Float
) {
    val count = bands.size
    if (count == 0) return

    val totalWidth = size.width
    val slotWidth = totalWidth / count
    val barWidth = (slotWidth * 0.64f * thickness).coerceIn(2f, slotWidth * 0.92f)
    val gapOffset = (slotWidth - barWidth) / 2f
    val maxHeight = size.height * 0.78f * spread.coerceAtMost(1.25f)
    val baselineY = if (config.mirrorEnabled) size.height / 2f else size.height * 0.92f

    val brush = Brush.verticalGradient(
        colors = listOf(palette.start, palette.mid, palette.end),
        startY = 0f,
        endY = size.height
    )

    for (i in 0 until count) {
        val value = bands[i]
        val x = i * slotWidth + gapOffset
        val barHeight = max(4f, value * (if (config.mirrorEnabled) maxHeight * 0.5f else maxHeight))

        if (config.glowEnabled) {
            drawRoundRect(
                color = palette.glow.copy(alpha = 0.20f),
                topLeft = Offset(x = x - 2f, y = baselineY - barHeight - 2f),
                size = Size(
                    width = barWidth + 4f,
                    height = if (config.mirrorEnabled) barHeight * 2f + 4f else barHeight + 4f
                ),
                cornerRadius = CornerRadius(barWidth, barWidth)
            )
        }

        drawRoundRect(
            brush = brush,
            topLeft = Offset(x, baselineY - barHeight),
            size = Size(barWidth, if (config.mirrorEnabled) barHeight * 2f else barHeight),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )

        if (config.peakCapsEnabled) {
            val peakHeight = peaks[i] * (if (config.mirrorEnabled) maxHeight * 0.5f else maxHeight)
            val capY = (baselineY - peakHeight - 4f).coerceAtLeast(2f)
            drawRoundRect(
                color = palette.start.copy(alpha = 0.92f),
                topLeft = Offset(x, capY),
                size = Size(barWidth, 3f),
                cornerRadius = CornerRadius(2f, 2f)
            )
        }
    }
}

private fun DrawScope.drawWaveform(
    wave: FloatArray,
    palette: VisualizerPalette,
    config: VisualizerConfig,
    spread: Float,
    thickness: Float
) {
    if (wave.isEmpty()) return
    val centerY = size.height / 2f
    val amplitude = size.height * 0.36f * spread.coerceAtMost(1.35f)
    val stepX = size.width / max(1, wave.size - 1)

    val mainPath = Path()
    val mirrorPath = Path()

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
            color = palette.glow.copy(alpha = 0.25f),
            style = Stroke(width = 12f * thickness, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }

    drawPath(
        path = mainPath,
        brush = strokeBrush,
        style = Stroke(width = 5f * thickness, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    if (config.mirrorEnabled) {
        drawPath(
            path = mirrorPath,
            brush = strokeBrush,
            alpha = 0.45f,
            style = Stroke(width = 3.5f * thickness, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

private fun DrawScope.drawMirrorSpectrum(
    bands: FloatArray,
    peaks: FloatArray,
    palette: VisualizerPalette,
    config: VisualizerConfig,
    spread: Float,
    thickness: Float
) {
    val count = bands.size
    if (count == 0) return

    val halfCount = max(1, count / 2)
    val centerX = size.width / 2f
    val halfWidth = size.width / 2f
    val slotWidth = halfWidth / halfCount
    val barWidth = (slotWidth * 0.62f * thickness).coerceIn(2f, slotWidth * 0.92f)
    val maxH = size.height * 0.36f * spread.coerceAtMost(1.35f)
    val centerY = size.height / 2f

    val brush = Brush.horizontalGradient(
        colors = listOf(palette.end, palette.mid, palette.start, palette.mid, palette.end)
    )

    for (i in 0 until halfCount) {
        val value = bands[i]
        val h = max(4f, value * maxH)
        val offset = i * slotWidth + (slotWidth - barWidth) / 2f

        val rightX = centerX + offset
        val leftX = centerX - offset - barWidth

        if (config.glowEnabled) {
            drawRoundRect(
                color = palette.glow.copy(alpha = 0.18f),
                topLeft = Offset(rightX - 1.5f, centerY - h - 1.5f),
                size = Size(barWidth + 3f, h * 2f + 3f),
                cornerRadius = CornerRadius(barWidth, barWidth)
            )
            drawRoundRect(
                color = palette.glow.copy(alpha = 0.18f),
                topLeft = Offset(leftX - 1.5f, centerY - h - 1.5f),
                size = Size(barWidth + 3f, h * 2f + 3f),
                cornerRadius = CornerRadius(barWidth, barWidth)
            )
        }

        drawRoundRect(
            brush = brush,
            topLeft = Offset(rightX, centerY - h),
            size = Size(barWidth, h * 2f),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )
        drawRoundRect(
            brush = brush,
            topLeft = Offset(leftX, centerY - h),
            size = Size(barWidth, h * 2f),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )

        if (config.peakCapsEnabled) {
            val peakY = (centerY - peaks[i] * maxH - 4f).coerceAtLeast(2f)
            drawCircle(
                color = palette.start,
                radius = 2.2f * thickness,
                center = Offset(rightX + barWidth / 2f, peakY)
            )
            drawCircle(
                color = palette.start,
                radius = 2.2f * thickness,
                center = Offset(leftX + barWidth / 2f, peakY)
            )
        }
    }
}

private fun DrawScope.drawParticlePulse(
    bands: FloatArray,
    palette: VisualizerPalette,
    config: VisualizerConfig,
    timeSec: Float,
    spread: Float,
    thickness: Float,
    spinSpeed: Float
) {
    val count = bands.size
    if (count == 0) return

    val centerY = size.height / 2f
    val stepX = size.width / count

    for (i in 0 until count) {
        val energy = bands[i]
        val x = i * stepX + stepX / 2f
        val driftY = sin(timeSec * (2.5f * (0.4f + spinSpeed * 0.6f)) + i * 0.45f) *
            size.height * (0.12f + energy * 0.28f * spread.coerceAtMost(1.3f))
        val y = centerY + driftY
        val radius = ((3f + energy * (size.height * 0.08f)) * thickness).coerceAtLeast(2.5f)
        val color = when {
            i % 3 == 0 -> palette.start
            i % 3 == 1 -> palette.mid
            else -> palette.end
        }

        if (config.glowEnabled) {
            drawCircle(
                color = color.copy(alpha = 0.22f),
                radius = radius * 2.1f,
                center = Offset(x, y)
            )
        }

        drawCircle(
            color = color.copy(alpha = 0.88f),
            radius = radius,
            center = Offset(x, y)
        )

        if (config.mirrorEnabled) {
            drawCircle(
                color = color.copy(alpha = 0.45f),
                radius = radius * 0.8f,
                center = Offset(x, centerY - driftY)
            )
        }
    }
}
