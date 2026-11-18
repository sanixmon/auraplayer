package com.auraplayer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.auraplayer.app.data.model.AudioFormat

@Composable
fun FormatBadge(
    format: AudioFormat,
    modifier: Modifier = Modifier
) {
    val containerColor = if (format.isLossless) {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    }
    val contentColor = if (format.isLossless) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(containerColor)
            .border(
                width = 0.75.dp,
                color = contentColor.copy(alpha = 0.45f),
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = format.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}
