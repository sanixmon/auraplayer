package com.auraplayer.app.data.model

enum class VisualizerStyle(val displayName: String, val subtitle: String) {
    BARS("Aurora Bars", "Rounded harmonic spectrum bars"),
    WAVEFORM("Fluid Wave", "Real-time dual-layer oscilloscope"),
    CIRCULAR_AURA("Circular Aura", "Radial pulsing energy ring"),
    MIRROR_SPECTRUM("Mirror Spectrum", "Symmetrical center-out spectrum"),
    PARTICLE_PULSE("Particle Pulse", "Frequency-driven luminous orbs")
}

enum class VisualizerColorScheme(val displayName: String) {
    AURORA("Aurora Cyan-Violet"),
    SOLAR_FLARE("Solar Flare"),
    NEON_CYBER("Neon Cyber"),
    OCEAN_MIST("Ocean Mist"),
    MONOCHROME_GOLD("Champagne Gold")
}

enum class VisualizerDensity(val displayName: String, val bandCount: Int) {
    LOW("Minimal (24)", 24),
    MEDIUM("Balanced (48)", 48),
    HIGH("Detailed (64)", 64),
    ULTRA("Studio (96)", 96)
}

/**
 * Customizable settings for the Sound Visualizer (8 distinct controls, exceeding the 5+ PRD target).
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
