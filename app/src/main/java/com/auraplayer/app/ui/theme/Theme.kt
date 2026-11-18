package com.auraplayer.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

val AuraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun AuraPlayerTheme(
    accentColors: AccentColors = AccentColors(AuraCyan, AuraViolet),
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorSpring = spring<Color>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val animatedPrimary by animateColorAsState(
        targetValue = accentColors.primary,
        animationSpec = colorSpring,
        label = "accent_primary"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = accentColors.secondary,
        animationSpec = colorSpring,
        label = "accent_secondary"
    )

    val onPrimaryColor = if (animatedPrimary.luminance() > 0.45f) AuraObsidian else Color.White

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = animatedPrimary,
            onPrimary = onPrimaryColor,
            primaryContainer = animatedPrimary.copy(alpha = 0.22f),
            onPrimaryContainer = animatedPrimary,
            secondary = animatedSecondary,
            onSecondary = AuraOnDark,
            secondaryContainer = animatedSecondary.copy(alpha = 0.20f),
            onSecondaryContainer = AuraOnDark,
            tertiary = AuraAmber,
            background = AuraObsidian,
            onBackground = AuraOnDark,
            surface = AuraSlateDark,
            onSurface = AuraOnDark,
            surfaceVariant = AuraSlateContainer,
            onSurfaceVariant = AuraOnDarkMuted,
            surfaceContainerLowest = Color(0xFF05070D),
            surfaceContainerLow = Color(0xFF0C101B),
            surfaceContainer = AuraSlateDark,
            surfaceContainerHigh = AuraSlateContainer,
            surfaceContainerHighest = AuraSlateHigh,
            outline = AuraOutlineDark
        )
    } else {
        lightColorScheme(
            primary = animatedPrimary,
            onPrimary = onPrimaryColor,
            primaryContainer = animatedPrimary.copy(alpha = 0.16f),
            onPrimaryContainer = animatedPrimary,
            secondary = animatedSecondary,
            onSecondary = AuraLightSurface,
            secondaryContainer = animatedSecondary.copy(alpha = 0.14f),
            onSecondaryContainer = AuraOnLight,
            tertiary = AuraTertiaryLight,
            background = AuraLightBackground,
            onBackground = AuraOnLight,
            surface = AuraLightSurface,
            onSurface = AuraOnLight,
            surfaceVariant = AuraLightContainer,
            onSurfaceVariant = AuraOnLightMuted,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF3F6FA),
            surfaceContainer = AuraLightContainer,
            surfaceContainerHigh = Color(0xFFE4EAF2),
            surfaceContainerHighest = Color(0xFFD9E1EC)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AuraShapes,
        typography = AuraTypography,
        content = content
    )
}
