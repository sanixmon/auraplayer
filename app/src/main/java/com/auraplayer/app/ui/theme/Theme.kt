package com.auraplayer.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
fun AuraPlayerTheme(
    accentColors: AccentColors = AccentColors(AuraCyan, AuraViolet),
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val animatedPrimary by animateColorAsState(
        targetValue = accentColors.primary,
        animationSpec = tween(durationMillis = 500),
        label = "accent_primary"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = accentColors.secondary,
        animationSpec = tween(durationMillis = 500),
        label = "accent_secondary"
    )

    val onPrimaryColor = if (animatedPrimary.luminance() > 0.45f) AuraObsidian else Color.White

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = animatedPrimary,
            onPrimary = onPrimaryColor,
            primaryContainer = animatedPrimary.copy(alpha = 0.20f),
            onPrimaryContainer = animatedPrimary,
            secondary = animatedSecondary,
            onSecondary = AuraOnDark,
            secondaryContainer = AuraSlateContainer,
            onSecondaryContainer = AuraOnDark,
            tertiary = AuraAmber,
            background = AuraObsidian,
            onBackground = AuraOnDark,
            surface = AuraSlateDark,
            onSurface = AuraOnDark,
            surfaceVariant = AuraSlateContainer,
            onSurfaceVariant = AuraOnDarkMuted,
            outline = AuraOutlineDark
        )
    } else {
        lightColorScheme(
            primary = animatedPrimary,
            onPrimary = onPrimaryColor,
            primaryContainer = animatedPrimary.copy(alpha = 0.14f),
            onPrimaryContainer = animatedPrimary,
            secondary = animatedSecondary,
            onSecondary = AuraLightSurface,
            tertiary = AuraTertiaryLight,
            background = AuraLightBackground,
            onBackground = AuraOnLight,
            surface = AuraLightSurface,
            onSurface = AuraOnLight,
            surfaceVariant = AuraLightContainer,
            onSurfaceVariant = AuraOnLightMuted
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuraTypography,
        content = content
    )
}
