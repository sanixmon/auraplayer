package com.auraplayer.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val quickSwatches = listOf(
    0xFF00E5FF,
    0xFFA855F7,
    0xFFEC4899,
    0xFFFB7185,
    0xFFF59E0B,
    0xFF10B981,
    0xFF38BDF8,
    0xFFF8FAFC
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualColorWheelDialog(
    title: String,
    initialPrimaryArgb: Long,
    initialSecondaryArgb: Long,
    onDismiss: () -> Unit,
    onConfirm: (primaryArgb: Long, secondaryArgb: Long) -> Unit
) {
    var activeSlot by remember { mutableIntStateOf(0) }
    var primaryArgb by remember { mutableLongStateOf(initialPrimaryArgb) }
    var secondaryArgb by remember { mutableLongStateOf(initialSecondaryArgb) }

    val activeArgb = if (activeSlot == 0) primaryArgb else secondaryArgb
    val hsv = remember(activeSlot) {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(activeArgb.toInt(), out)
        out
    }

    var hue by remember(activeSlot) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(activeSlot) { mutableFloatStateOf(hsv[1]) }
    var value by remember(activeSlot) { mutableFloatStateOf(hsv[2].coerceIn(0.25f, 1f)) }
    var hexText by remember(activeSlot) { mutableStateOf(formatHex(activeArgb)) }
    var wheelSize by remember { mutableStateOf(IntSize.Zero) }

    fun applyHsv(newH: Float, newS: Float, newV: Float) {
        hue = newH
        saturation = newS
        value = newV
        val colorInt = android.graphics.Color.HSVToColor(floatArrayOf(newH, newS, newV))
        val argbLong = colorInt.toLong() and 0xFFFFFFFFL
        if (activeSlot == 0) primaryArgb = argbLong else secondaryArgb = argbLong
        hexText = formatHex(argbLong)
    }

    fun updateFromWheelOffset(offset: Offset) {
        if (wheelSize.width <= 0 || wheelSize.height <= 0) return
        val cx = wheelSize.width / 2f
        val cy = wheelSize.height / 2f
        val radius = min(cx, cy).coerceAtLeast(1f)
        val dx = offset.x - cx
        val dy = offset.y - cy
        val dist = hypot(dx, dy).coerceAtMost(radius)
        val angleDeg = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
        val sat = (dist / radius).coerceIn(0f, 1f)
        applyHsv(angleDeg, sat, value)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("Primary", "Secondary").forEachIndexed { idx, label ->
                        SegmentedButton(
                            selected = activeSlot == idx,
                            onClick = { activeSlot = idx },
                            shape = SegmentedButtonDefaults.itemShape(idx, 2),
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Color(if (idx == 0) primaryArgb else secondaryArgb)
                                            )
                                    )
                                    Text(label)
                                }
                            }
                        )
                    }
                }

                // Hue-Saturation Color Wheel
                Canvas(
                    modifier = Modifier
                        .size(196.dp)
                        .onSizeChanged { wheelSize = it }
                        .pointerInput(activeSlot) {
                            detectTapGestures { offset -> updateFromWheelOffset(offset) }
                        }
                        .pointerInput(activeSlot) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                updateFromWheelOffset(change.position)
                            }
                        }
                ) {
                    val radius = min(size.width, size.height) / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    val hueColors = listOf(
                        Color.Red,
                        Color.Yellow,
                        Color.Green,
                        Color.Cyan,
                        Color.Blue,
                        Color.Magenta,
                        Color.Red
                    )
                    drawCircle(
                        brush = Brush.sweepGradient(hueColors, center = center),
                        radius = radius,
                        center = center
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White, Color.Transparent),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                    if (value < 1f) {
                        drawCircle(
                            color = Color.Black.copy(alpha = (1f - value).coerceIn(0f, 0.75f)),
                            radius = radius,
                            center = center
                        )
                    }

                    val angleRad = hue * (PI.toFloat() / 180f)
                    val thumbDist = saturation * radius
                    val thumbPos = Offset(
                        x = center.x + cos(angleRad) * thumbDist,
                        y = center.y + sin(angleRad) * thumbDist
                    )
                    drawCircle(
                        color = Color(activeArgb),
                        radius = 12f,
                        center = thumbPos
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 12f,
                        center = thumbPos,
                        style = Stroke(width = 3.5f)
                    )
                }

                // Brightness slider
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Brightness",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = value,
                        onValueChange = { newV -> applyHsv(hue, saturation, newV) },
                        valueRange = 0.25f..1f
                    )
                }

                // Preview + Hex Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(primaryArgb), Color(secondaryArgb))
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    )
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { input ->
                            val sanitized = input.uppercase(Locale.US).take(7)
                            hexText = sanitized
                            parseHexColor(sanitized)?.let { parsedArgb ->
                                val parsedHsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(parsedArgb.toInt(), parsedHsv)
                                hue = parsedHsv[0]
                                saturation = parsedHsv[1]
                                value = parsedHsv[2].coerceIn(0.25f, 1f)
                                if (activeSlot == 0) {
                                    primaryArgb = parsedArgb
                                } else {
                                    secondaryArgb = parsedArgb
                                }
                            }
                        },
                        label = { Text("Hex") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quick Swatches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    quickSwatches.forEach { swatch ->
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(Color(swatch))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                                .clickable {
                                    val swatchHsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(swatch.toInt(), swatchHsv)
                                    applyHsv(swatchHsv[0], swatchHsv[1], swatchHsv[2].coerceIn(0.25f, 1f))
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(primaryArgb, secondaryArgb) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatHex(argb: Long): String {
    val rgb = (argb and 0x00FFFFFFL).toInt()
    return String.format(Locale.US, "#%06X", rgb)
}

private fun parseHexColor(text: String): Long? {
    val clean = text.removePrefix("#").trim()
    if (clean.length != 6) return null
    val parsed = clean.toLongOrNull(16) ?: return null
    return 0xFF000000L or parsed
}
