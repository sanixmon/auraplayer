package com.auraplayer.app.ui.theme

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.auraplayer.app.data.artwork.toArtworkRequest
import com.auraplayer.app.data.model.AppAccentMode
import com.auraplayer.app.data.model.Track
import com.auraplayer.app.data.model.VisualizerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AccentColors(
    val primary: Color,
    val secondary: Color
)

@Composable
fun rememberResolvedAccentColors(
    currentTrack: Track?,
    config: VisualizerConfig
): AccentColors {
    val context = LocalContext.current
    val customOrFallback = AccentColors(
        primary = Color(config.customAppPrimaryArgb),
        secondary = Color(config.customAppSecondaryArgb)
    )
    var extracted by remember { mutableStateOf<AccentColors?>(null) }

    LaunchedEffect(currentTrack?.uri, currentTrack?.albumArtUri, config.accentMode) {
        if (config.accentMode != AppAccentMode.DYNAMIC_ALBUM || currentTrack == null) {
            extracted = null
            return@LaunchedEffect
        }
        extracted = extractPaletteColors(context, currentTrack)
    }

    return if (config.accentMode == AppAccentMode.DYNAMIC_ALBUM) {
        extracted ?: AccentColors(
            primary = Color(config.accentPreset.primaryHex),
            secondary = Color(config.accentPreset.secondaryHex)
        )
    } else {
        customOrFallback
    }
}

private suspend fun extractPaletteColors(
    context: Context,
    track: Track
): AccentColors? = withContext(Dispatchers.IO) {
    val request = ImageRequest.Builder(context)
        .data(track.toArtworkRequest())
        .size(128)
        .allowHardware(false)
        .build()

    val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
    val bitmap = (result as? SuccessResult)?.drawable?.let { drawable ->
        (drawable as? BitmapDrawable)?.bitmap
    } ?: return@withContext null

    val palette = Palette.from(bitmap).generate()
    val primaryArgb = palette.vibrantSwatch?.rgb
        ?: palette.lightVibrantSwatch?.rgb
        ?: palette.dominantSwatch?.rgb
        ?: return@withContext null

    val secondaryArgb = palette.mutedSwatch?.rgb
        ?: palette.darkVibrantSwatch?.rgb
        ?: shiftHue(primaryArgb, 45f)

    AccentColors(
        primary = normalizeForDarkSurface(primaryArgb),
        secondary = normalizeForDarkSurface(secondaryArgb)
    )
}

private fun normalizeForDarkSurface(argb: Int): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    hsl[1] = hsl[1].coerceIn(0.45f, 0.92f)
    hsl[2] = hsl[2].coerceIn(0.55f, 0.74f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun shiftHue(argb: Int, degrees: Float): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    hsl[0] = (hsl[0] + degrees) % 360f
    return ColorUtils.HSLToColor(hsl)
}
