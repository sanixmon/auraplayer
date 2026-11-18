package com.auraplayer.app.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
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
import kotlin.math.ln

data class AccentColors(
    val primary: Color,
    val secondary: Color
)

val LocalAlbumAccentColors = compositionLocalOf {
    AccentColors(primary = AuraCyan, secondary = AuraViolet)
}

@Composable
fun rememberExtractedAlbumColors(
    currentTrack: Track?
): AccentColors {
    val context = LocalContext.current
    var extracted by remember { mutableStateOf<AccentColors?>(null) }

    LaunchedEffect(currentTrack?.id, currentTrack?.uri, currentTrack?.albumArtUri) {
        extracted = if (currentTrack == null) {
            null
        } else {
            extractPaletteColors(context, currentTrack)
        }
    }

    return extracted ?: AccentColors(primary = AuraCyan, secondary = AuraViolet)
}

fun resolveAppAccentColors(
    albumAccent: AccentColors,
    config: VisualizerConfig
): AccentColors = if (config.accentMode == AppAccentMode.DYNAMIC_ALBUM) {
    albumAccent
} else {
    AccentColors(
        primary = Color(config.customAppPrimaryArgb),
        secondary = Color(config.customAppSecondaryArgb)
    )
}

@Composable
fun rememberResolvedAccentColors(
    currentTrack: Track?,
    config: VisualizerConfig
): AccentColors {
    val albumAccent = rememberExtractedAlbumColors(currentTrack)
    return resolveAppAccentColors(albumAccent = albumAccent, config = config)
}

private suspend fun extractPaletteColors(
    context: Context,
    track: Track
): AccentColors? = withContext(Dispatchers.IO) {
    val request = ImageRequest.Builder(context)
        .data(track.toArtworkRequest())
        .size(128)
        .allowHardware(false)
        .bitmapConfig(Bitmap.Config.ARGB_8888)
        .build()

    val result = runCatching { context.imageLoader.execute(request) }.getOrNull()
    val rawBitmap = (result as? SuccessResult)?.drawable?.let { drawable ->
        (drawable as? BitmapDrawable)?.bitmap
    } ?: return@withContext null

    val bitmap = if (rawBitmap.config == Bitmap.Config.HARDWARE) {
        rawBitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return@withContext null
    } else {
        rawBitmap
    }

    val palette = Palette.from(bitmap)
        .maximumColorCount(24)
        .generate()

    val hsl = FloatArray(3)
    // Rank swatches balancing saturation and visual area so tiny accents don't hijack the whole cover
    // and monochrome covers don't get promoted as saturated hue=0 (red).
    val chromaticSwatches = palette.swatches.filter { swatch ->
        ColorUtils.colorToHSL(swatch.rgb, hsl)
        hsl[1] >= 0.14f && hsl[2] in 0.10f..0.92f
    }.sortedByDescending { swatch ->
        ColorUtils.colorToHSL(swatch.rgb, hsl)
        val satWeight = hsl[1]
        val areaWeight = ln(1.0 + swatch.population.toDouble()).toFloat()
        satWeight * areaWeight
    }

    val primarySwatch = chromaticSwatches.firstOrNull()
        ?: palette.vibrantSwatch
        ?: palette.dominantSwatch
        ?: return@withContext null

    val primaryHsl = FloatArray(3)
    ColorUtils.colorToHSL(primarySwatch.rgb, primaryHsl)

    val secondarySwatch = chromaticSwatches.firstOrNull { candidate ->
        val candHsl = FloatArray(3)
        ColorUtils.colorToHSL(candidate.rgb, candHsl)
        hueDistanceDeg(primaryHsl[0], candHsl[0]) >= 28f
    } ?: palette.mutedSwatch ?: palette.darkVibrantSwatch

    val primaryColor = normalizeForDarkSurface(primarySwatch.rgb)
    val secondaryColor = if (secondarySwatch != null) {
        val secHsl = FloatArray(3)
        ColorUtils.colorToHSL(secondarySwatch.rgb, secHsl)
        if (secHsl[1] >= 0.14f) {
            normalizeForDarkSurface(secondarySwatch.rgb)
        } else {
            deriveSecondaryFromColor(primaryColor)
        }
    } else {
        deriveSecondaryFromColor(primaryColor)
    }

    AccentColors(
        primary = primaryColor,
        secondary = secondaryColor
    )
}

private fun normalizeForDarkSurface(argb: Int): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(argb, hsl)
    // Monochrome/grayscale pixels convert to hue=0 (red) in RGB->HSL; never force-saturate neutral covers.
    if (hsl[1] < 0.12f) {
        hsl[0] = 204f
        hsl[1] = 0.24f
        hsl[2] = 0.72f
    } else {
        hsl[1] = hsl[1].coerceIn(0.48f, 0.92f)
        hsl[2] = hsl[2].coerceIn(0.56f, 0.74f)
    }
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun deriveSecondaryFromColor(primary: Color): Color {
    val hsl = FloatArray(3)
    val argb = android.graphics.Color.rgb(
        (primary.red * 255f).toInt(),
        (primary.green * 255f).toInt(),
        (primary.blue * 255f).toInt()
    )
    ColorUtils.colorToHSL(argb, hsl)
    hsl[0] = (hsl[0] + 48f) % 360f
    hsl[1] = hsl[1].coerceIn(0.42f, 0.88f)
    hsl[2] = hsl[2].coerceIn(0.58f, 0.74f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun hueDistanceDeg(a: Float, b: Float): Float {
    val diff = kotlin.math.abs(a - b) % 360f
    return if (diff > 180f) 360f - diff else diff
}
