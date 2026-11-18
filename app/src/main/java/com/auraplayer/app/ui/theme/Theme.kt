package com.auraplayer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AuraCyan,
    onPrimary = AuraObsidian,
    primaryContainer = AuraSlateHigh,
    onPrimaryContainer = AuraCyan,
    secondary = AuraViolet,
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

private val LightColorScheme = lightColorScheme(
    primary = AuraPrimaryLight,
    onPrimary = AuraLightSurface,
    primaryContainer = AuraLightContainer,
    onPrimaryContainer = AuraPrimaryLight,
    secondary = AuraSecondaryLight,
    onSecondary = AuraLightSurface,
    tertiary = AuraTertiaryLight,
    background = AuraLightBackground,
    onBackground = AuraOnLight,
    surface = AuraLightSurface,
    onSurface = AuraOnLight,
    surfaceVariant = AuraLightContainer,
    onSurfaceVariant = AuraOnLightMuted
)

@Composable
fun AuraPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = AuraTypography,
        content = content
    )
}
