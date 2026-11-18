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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
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
    dynamicAccent: AccentColors? = AccentColors(
        primary = MaterialTheme.colorScheme.primary,
        secondary = MaterialTheme.colorScheme.secondary
    )
) {
    val bandCount = config.density.bandCount
    val smoothedBands = remember(bandCount) { FloatArray(bandCount) }
    val peakCaps = remember(bandCount) { FloatArray(bandCount) }
    val smoothedWave = remember { FloatArray(64) }
    val ringPointsX = remember(bandCount) { FloatArray(bandCount) }
    val ringPointsY = remember(bandCount) { FloatArray(bandCount) }

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
    var lastShockwaveTimeSec by remember { mutableFloatStateOf(-1f) }
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

    Canvas(
        modifier = modifier.graphicsLayer {
            // Offscreen compositing enables true additive (BlendMode.Plus) neon bloom.
            compositingStrategy = CompositingStrategy.Offscreen
        }
    ) {
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
            useDemo -> (0.42f + 0.34f * sin(animTimeSec * 3.1f)).coerceIn(0f, 1f)
            isPlaying -> (frame.bassEnergy * sensitivity).coerceIn(0f, 1f)
            else -> 0f
        }
        smoothedBass += (targetBass - smoothedBass) * (1f - smoothFactor * 0.62f)

        val currentBeatPulse = when {
            useDemo -> if (sin(animTimeSec * 3.1f) > 0.92f) 0.7f else 0f
            isPlaying -> (frame.beatPulse * sensitivity).coerceIn(0f, 1f)
            else -> 0f
        }

        // Trigger expanding beat shockwaves on bass transients (180ms cooldown).
        if ((isPlaying || demoWhenIdle) &&
            currentBeatPulse > 0.35f &&
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
                sw.progress += 0.022f
                if (sw.progress >= 1f) {
                    sw.active = false
                }
            }
        }

        if ((isPlaying || demoWhenIdle) && config.particlesEnabled) {
            val particleStep = (0.004f + smoothedBass * 0.026f) * (0.5f + spinSpeed * 0.5f)
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
                prev + (target - prev) * (1f - smoothFactor * 0.50f)
            } else {
                prev + (target - prev) * (1f - smoothFactor * 0.84f)
            }
            smoothedBands[i] = next.coerceIn(0f, 1f)

            if (next >= peakCaps[i]) {
                peakCaps[i] = next
            } else {
                peakCaps[i] = max(next, peakCaps[i] - 0.011f)
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
            smoothedWave[i] = smoothedWave[i] + (targetWave - smoothedWave[i]) * (1f - smoothFactor * 0.62f)
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

    // 4. Compute radial spectrum coordinates & smooth quadratic Bezier closed aura
    val halfCount = max(1, count / 2)
    val angleStep = (2.0 * PI / count).toFloat()
    val barWidth = ((minDim / count) * 0.58f * thickness).coerceIn(1.8f, 10f)

    for (i in 0 until count) {
        val mirrorIdx = if (i < halfCount) i else count - 1 - i
        val mag = bands[mirrorIdx.coerceIn(0, bands.lastIndex)]
        val angle = i * angleStep + rotationOffset
        val cosA = cos(angle)
        val sinA = sin(angle)
        val outerR = baseRadius + 8f + mag * maxSpike

        pointsX[i] = center.x + cosA * outerR
        pointsY[i] = center.y + sinA * outerR
    }

    buildSmoothClosedPath(auraPath, pointsX, pointsY, count)

    // Soft translucent aura fill + bright neon bloom ribbon
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
        style = Stroke(width = 2.4f * thickness, join = StrokeJoin.Round),
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

    // 6. Crisp radial frequency bars + peak dots
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
            cap = StrokeCap.Round,
            blendMode = addBlend
        )

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

private fun DrawScope.drawBars(
    bands: FloatArray,
    peaks: FloatArray,
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
    val barWidth = (slotWidth * 0.64f * thickness).coerceIn(2f, slotWidth * 0.92f)
    val gapOffset = (slotWidth - barWidth) / 2f
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

    mainPath.reset()
    mirrorPath.reset()

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
    val barWidth = (slotWidth * 0.62f * thickness).coerceIn(2f, slotWidth * 0.92f)
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

    wavePath.reset()
    for (i in 0 until count) {
        val energy = bands[i]
        val x = i * stepX + stepX / 2f
        val driftY = sin(timeSec * (2.5f * (0.4f + spinSpeed * 0.6f)) + i * 0.45f) *
            size.height * (0.12f + energy * 0.28f * spread.coerceAtMost(1.3f))
        val y = centerY + driftY
        if (i == 0) {
            wavePath.moveTo(x, y)
        } else {
            wavePath.lineTo(x, y)
        }

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
                center = Offset(x, centerY - driftY),
                blendMode = addBlend
            )
        }
    }

    drawPath(
        path = wavePath,
        color = palette.mid.copy(alpha = 0.28f),
        style = Stroke(width = 1.8f * thickness),
        blendMode = addBlend
    )
}
