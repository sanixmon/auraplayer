package com.auraplayer.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.auraplayer.app.data.artwork.toArtworkRequest
import com.auraplayer.app.data.model.Track
import com.auraplayer.app.ui.util.formatDurationMs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListItem(
    track: Track,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onRelocateUnavailable: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val bgColor = when {
        !track.isAvailable -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .combinedClickable(
                enabled = track.isAvailable,
                onClick = onClick,
                onLongClick = if (onLongClick != null) {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                } else {
                    null
                }
            )
            .alpha(if (track.isAvailable) 1f else 0.55f)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    !track.isAvailable -> Icons.Rounded.WarningAmber
                    isActive -> Icons.Rounded.GraphicEq
                    else -> Icons.Rounded.MusicNote
                },
                contentDescription = null,
                tint = when {
                    !track.isAvailable -> MaterialTheme.colorScheme.error
                    isActive -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp)
            )
            if (track.isAvailable) {
                AsyncImage(
                    model = track.toArtworkRequest(),
                    contentDescription = track.album,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isActive && track.isAvailable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (track.isAvailable) {
                    "${track.artist} • ${formatDurationMs(track.durationMs)}"
                } else {
                    "Missing • ${track.artist}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (track.isAvailable) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!track.isAvailable && onRelocateUnavailable != null) {
            IconButton(onClick = onRelocateUnavailable) {
                Icon(
                    imageVector = Icons.Rounded.FindInPage,
                    contentDescription = "Locate file",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        trailingContent?.invoke()
    }
}
