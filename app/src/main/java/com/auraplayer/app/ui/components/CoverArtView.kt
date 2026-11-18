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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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

@Composable
fun CoverArtView(
    track: Track?,
    style: CoverArtStyle,
    isPlaying: Boolean,
    bassEnergy: Float,
    size: Dp,
    modifier: Modifier = Modifier
) {
    var vinylAngle by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying, style) {
        if (isPlaying && style == CoverArtStyle.VINYL_DISC) {
            var lastNanos = 0L
            while (true) {
                withFrameNanos { nowNanos ->
                    if (lastNanos != 0L) {
                        val dt = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                        vinylAngle = (vinylAngle + dt * 28f) % 360f
                    }
                    lastNanos = nowNanos
                }
            }
        }
    }

    val pulseScale = if (isPlaying) 1f + (bassEnergy * 0.045f).coerceIn(0f, 0.06f) else 1f
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    when (style) {
        CoverArtStyle.CARD_3D -> {
            val shape = RoundedCornerShape(24.dp)
            Box(
                modifier = modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                        rotationX = if (isPlaying) 3.5f else 0f
                        rotationY = if (isPlaying) -3.5f else 0f
                        cameraDistance = 14f * density
                    }
                    .shadow(
                        elevation = 24.dp,
                        shape = shape,
                        ambientColor = accent.copy(alpha = 0.55f),
                        spotColor = secondary.copy(alpha = 0.65f)
                    )
                    .clip(shape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.surfaceVariant,
                                secondary.copy(alpha = 0.22f)
                            )
                        )
                    )
                    .border(
                        width = 1.2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
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
                        elevation = 20.dp,
                        shape = CircleShape,
                        ambientColor = accent.copy(alpha = 0.45f),
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
                            color = Color.White.copy(alpha = 0.08f),
                            radius = maxR * factor,
                            style = Stroke(width = 1.5f)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(size * 0.68f)
                        .clip(CircleShape)
                        .border(2.dp, accent.copy(alpha = 0.5f), CircleShape)
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
                        .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                )
            }
        }

        CoverArtStyle.SOFT_GLASS -> {
            val shape = RoundedCornerShape(28.dp)
            Box(
                modifier = modifier
                    .size(size)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                    .shadow(
                        elevation = 16.dp,
                        shape = shape,
                        ambientColor = accent.copy(alpha = 0.4f),
                        spotColor = accent.copy(alpha = 0.5f)
                    )
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                    .border(
                        width = 1.dp,
                        color = accent.copy(alpha = 0.38f),
                        shape = shape
                    ),
                contentAlignment = Alignment.Center
            ) {
                CoverArtImage(track = track, iconSize = size * 0.36f)
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
