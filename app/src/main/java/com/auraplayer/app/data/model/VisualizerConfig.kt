package com.auraplayer.app.data.model

enum class VisualizerStyle(val displayName: String) {
    BARS("Bars"),
    WAVEFORM("Wave"),
    CIRCULAR_AURA("Aura"),
    MIRROR_SPECTRUM("Mirror"),
    PARTICLE_PULSE("Pulse")
}

enum class VisualizerColorScheme(val displayName: String) {
    AURORA("Aurora"),
    SOLAR_FLARE("Solar"),
    NEON_CYBER("Neon"),
    OCEAN_MIST("Ocean"),
    MONOCHROME_GOLD("Gold")
}

enum class VisualizerDensity(val displayName: String, val bandCount: Int) {
    LOW("24", 24),
    MEDIUM("48", 48),
    HIGH("64", 64),
    ULTRA("96", 96)
}

/**
 * Persisted sound visualizer configuration.
 */
data class VisualizerConfig(
    val style: VisualizerStyle = VisualizerStyle.BARS,
    val colorScheme: VisualizerColorScheme = VisualizerColorScheme.AURORA,
    val density: VisualizerDensity = VisualizerDensity.MEDIUM,
    val sensitivity: Float = 1.25f,
    val smoothing: Float = 0.65f,
    val glowEnabled: Boolean = true,
    val mirrorEnabled: Boolean = false,
    val peakCapsEnabled: Boolean = true
)
