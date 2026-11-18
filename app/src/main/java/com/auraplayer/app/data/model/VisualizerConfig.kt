package com.auraplayer.app.data.model

enum class VisualizerStyle(val displayName: String) {
    CIRCULAR_AURA("Ring"),
    BARS("Bars"),
    WAVEFORM("Wave"),
    MIRROR_SPECTRUM("Mirror"),
    PARTICLE_PULSE("Pulse")
}

enum class VisualizerFillMode(val displayName: String) {
    LINE("Line"),
    SOLID("Solid"),
    NONE("None")
}

enum class VisualizerColorScheme(val displayName: String) {
    DYNAMIC("Dynamic"),
    CUSTOM("Custom"),
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

enum class NowPlayingVisualMode(val displayName: String) {
    COVER_ONLY("Cover"),
    COVER_AND_VISUALIZER("Art + Visualizer"),
    VISUALIZER_ONLY("Visualizer")
}

enum class CoverArtStyle(val displayName: String) {
    COVER("Cover"),
    VINYL_DISC("Vinyl")
}

enum class AppAccentMode(val displayName: String) {
    DYNAMIC_ALBUM("Dynamic"),
    CUSTOM("Custom")
}

enum class AppAccentPreset(
    val displayName: String,
    val primaryHex: Long,
    val secondaryHex: Long
) {
    AURORA_CYAN("Cyan", 0xFF00E5FF, 0xFFA855F7),
    ELECTRIC_VIOLET("Violet", 0xFFA855F7, 0xFFEC4899),
    CRIMSON_PULSE("Crimson", 0xFFFB7185, 0xFFF59E0B),
    EMERALD_GLOW("Emerald", 0xFF10B981, 0xFF22D3EE),
    AMBER_GOLD("Amber", 0xFFF59E0B, 0xFFEF4444),
    OCEAN_BLUE("Ocean", 0xFF38BDF8, 0xFF6366F1)
}

/**
 * Persisted sound visualizer and appearance configuration.
 */
data class VisualizerConfig(
    val style: VisualizerStyle = VisualizerStyle.CIRCULAR_AURA,
    val fillMode: VisualizerFillMode = VisualizerFillMode.LINE,
    val colorScheme: VisualizerColorScheme = VisualizerColorScheme.DYNAMIC,
    val density: VisualizerDensity = VisualizerDensity.HIGH,
    val sensitivity: Float = 1.3f,
    val smoothing: Float = 0.65f,
    val spreadScale: Float = 1.0f,
    val strokeThickness: Float = 1.0f,
    val rotationSpeed: Float = 1.0f,
    val syncOffsetMs: Int = 0,
    val glowEnabled: Boolean = true,
    val particlesEnabled: Boolean = true,
    val mirrorEnabled: Boolean = false,
    val peakCapsEnabled: Boolean = true,
    val visualMode: NowPlayingVisualMode = NowPlayingVisualMode.COVER_ONLY,
    val coverArtStyle: CoverArtStyle = CoverArtStyle.COVER,
    val accentMode: AppAccentMode = AppAccentMode.DYNAMIC_ALBUM,
    val accentPreset: AppAccentPreset = AppAccentPreset.AURORA_CYAN,
    val customAppPrimaryArgb: Long = AppAccentPreset.AURORA_CYAN.primaryHex,
    val customAppSecondaryArgb: Long = AppAccentPreset.AURORA_CYAN.secondaryHex,
    val customVizPrimaryArgb: Long = 0xFF00E5FF,
    val customVizSecondaryArgb: Long = 0xFFA855F7
)
