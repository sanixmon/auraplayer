package com.auraplayer.app.data.model

enum class VisualizerStyle(val displayName: String) {
    CIRCULAR_AURA("Avee Ring"),
    BARS("Bars"),
    WAVEFORM("Wave"),
    MIRROR_SPECTRUM("Mirror"),
    PARTICLE_PULSE("Pulse")
}

enum class VisualizerColorScheme(val displayName: String) {
    DYNAMIC("Dynamic"),
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
    COVER_AND_VISUALIZER("Art + Vibe"),
    COVER_ONLY("Cover"),
    VISUALIZER_ONLY("Visualizer")
}

enum class CoverArtStyle(val displayName: String) {
    CARD_3D("3D Card"),
    VINYL_DISC("Vinyl"),
    SOFT_GLASS("Glass")
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
    val colorScheme: VisualizerColorScheme = VisualizerColorScheme.DYNAMIC,
    val density: VisualizerDensity = VisualizerDensity.HIGH,
    val sensitivity: Float = 1.3f,
    val smoothing: Float = 0.65f,
    val glowEnabled: Boolean = true,
    val mirrorEnabled: Boolean = false,
    val peakCapsEnabled: Boolean = true,
    val visualMode: NowPlayingVisualMode = NowPlayingVisualMode.COVER_AND_VISUALIZER,
    val coverArtStyle: CoverArtStyle = CoverArtStyle.CARD_3D,
    val accentMode: AppAccentMode = AppAccentMode.DYNAMIC_ALBUM,
    val accentPreset: AppAccentPreset = AppAccentPreset.AURORA_CYAN
)
