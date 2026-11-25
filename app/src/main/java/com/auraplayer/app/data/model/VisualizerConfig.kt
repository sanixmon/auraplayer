package com.auraplayer.app.data.model

enum class VisualizerStyle(val displayName: String) {
    CIRCULAR_AURA("Ring"),
    BARS("Bars"),
    WAVEFORM("Wave"),
    MIRROR_SPECTRUM("Mirror"),
    PARTICLE_PULSE("Pulse"),
    FIRE_EMBER("Fire Ember"),
    POLYGON_RING("Polygon"),
    SPIRAL_VORTEX("Spiral")
}

enum class BackgroundVisualMode(val displayName: String) {
    ACCENT_GRADIENT("Gradient"),
    BLURRED_ALBUM_ART("Blur Cover"),
    PURE_BLACK("OLED Black")
}

enum class ParticleOverlayStyle(val displayName: String) {
    OFF("Off"),
    BOKEH_RADIAL("Bokeh"),
    EMBERS("Embers"),
    STARFIELD("Starfield")
}

enum class AveeScenePreset(
    val displayName: String,
    val style: VisualizerStyle,
    val emberDirection: EmberDirection,
    val fillMode: VisualizerFillMode,
    val colorScheme: VisualizerColorScheme,
    val backgroundMode: BackgroundVisualMode,
    val particleOverlay: ParticleOverlayStyle,
    val tuningPreset: VisualizerTuningPreset,
    val cameraShakeIntensity: Float,
    val rgbSplitIntensity: Float,
    val additiveBlendEnabled: Boolean,
    val vignetteEnabled: Boolean,
    val sharpBarsEnabled: Boolean,
    val glowEnabled: Boolean,
    val mirrorEnabled: Boolean,
    val peakCapsEnabled: Boolean
) {
    TRAP_NATION(
        displayName = "Trap Nation",
        style = VisualizerStyle.CIRCULAR_AURA,
        emberDirection = EmberDirection.UP,
        fillMode = VisualizerFillMode.SOLID,
        colorScheme = VisualizerColorScheme.DYNAMIC,
        backgroundMode = BackgroundVisualMode.BLURRED_ALBUM_ART,
        particleOverlay = ParticleOverlayStyle.EMBERS,
        tuningPreset = VisualizerTuningPreset.PUNCHY,
        cameraShakeIntensity = 0.75f,
        rgbSplitIntensity = 0.25f,
        additiveBlendEnabled = true,
        vignetteEnabled = true,
        sharpBarsEnabled = false,
        glowEnabled = true,
        mirrorEnabled = false,
        peakCapsEnabled = false
    ),
    NCS_HALO(
        displayName = "NCS Halo",
        style = VisualizerStyle.CIRCULAR_AURA,
        emberDirection = EmberDirection.RADIAL,
        fillMode = VisualizerFillMode.LINE,
        colorScheme = VisualizerColorScheme.AURORA,
        backgroundMode = BackgroundVisualMode.BLURRED_ALBUM_ART,
        particleOverlay = ParticleOverlayStyle.BOKEH_RADIAL,
        tuningPreset = VisualizerTuningPreset.BALANCED,
        cameraShakeIntensity = 0.25f,
        rgbSplitIntensity = 0.0f,
        additiveBlendEnabled = true,
        vignetteEnabled = true,
        sharpBarsEnabled = false,
        glowEnabled = true,
        mirrorEnabled = false,
        peakCapsEnabled = true
    ),
    PHONK_GLITCH(
        displayName = "Phonk RGB",
        style = VisualizerStyle.MIRROR_SPECTRUM,
        emberDirection = EmberDirection.RADIAL,
        fillMode = VisualizerFillMode.LINE,
        colorScheme = VisualizerColorScheme.SOLAR_FLARE,
        backgroundMode = BackgroundVisualMode.PURE_BLACK,
        particleOverlay = ParticleOverlayStyle.STARFIELD,
        tuningPreset = VisualizerTuningPreset.HYPER,
        cameraShakeIntensity = 1.05f,
        rgbSplitIntensity = 1.0f,
        additiveBlendEnabled = true,
        vignetteEnabled = true,
        sharpBarsEnabled = true,
        glowEnabled = true,
        mirrorEnabled = true,
        peakCapsEnabled = true
    ),
    LOFI_CHILL(
        displayName = "Lo-Fi Wave",
        style = VisualizerStyle.WAVEFORM,
        emberDirection = EmberDirection.UP,
        fillMode = VisualizerFillMode.LINE,
        colorScheme = VisualizerColorScheme.MONOCHROME_GOLD,
        backgroundMode = BackgroundVisualMode.BLURRED_ALBUM_ART,
        particleOverlay = ParticleOverlayStyle.EMBERS,
        tuningPreset = VisualizerTuningPreset.CHILL,
        cameraShakeIntensity = 0.0f,
        rgbSplitIntensity = 0.0f,
        additiveBlendEnabled = true,
        vignetteEnabled = true,
        sharpBarsEnabled = false,
        glowEnabled = true,
        mirrorEnabled = true,
        peakCapsEnabled = false
    ),
    INFERNO_STAGE(
        displayName = "Inferno",
        style = VisualizerStyle.FIRE_EMBER,
        emberDirection = EmberDirection.UP,
        fillMode = VisualizerFillMode.LINE,
        colorScheme = VisualizerColorScheme.EMBER,
        backgroundMode = BackgroundVisualMode.PURE_BLACK,
        particleOverlay = ParticleOverlayStyle.OFF,
        tuningPreset = VisualizerTuningPreset.PUNCHY,
        cameraShakeIntensity = 0.45f,
        rgbSplitIntensity = 0.20f,
        additiveBlendEnabled = true,
        vignetteEnabled = true,
        sharpBarsEnabled = false,
        glowEnabled = true,
        mirrorEnabled = false,
        peakCapsEnabled = false
    )
}

enum class EmberDirection(val displayName: String) {
    UP("Up"),
    DOWN("Down"),
    LEFT("Left"),
    RIGHT("Right"),
    RADIAL("Radial")
}

enum class VisualizerFillMode(val displayName: String) {
    LINE("Line"),
    SOLID("Solid"),
    NONE("None")
}

enum class VisualizerColorScheme(val displayName: String) {
    DYNAMIC("Dynamic"),
    CUSTOM("Custom"),
    EMBER("Ember"),
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

enum class VisualizerTuningPreset(
    val displayName: String,
    val sensitivity: Float,
    val smoothing: Float,
    val beatPunch: Float,
    val beatSensitivity: Float,
    val bassBoost: Float
) {
    BALANCED(
        displayName = "Balanced",
        sensitivity = 1.3f,
        smoothing = 0.65f,
        beatPunch = 1.0f,
        beatSensitivity = 1.0f,
        bassBoost = 1.0f
    ),
    PUNCHY(
        displayName = "Punchy",
        sensitivity = 1.55f,
        smoothing = 0.45f,
        beatPunch = 1.55f,
        beatSensitivity = 1.25f,
        bassBoost = 1.35f
    ),
    CHILL(
        displayName = "Chill",
        sensitivity = 1.05f,
        smoothing = 0.82f,
        beatPunch = 0.60f,
        beatSensitivity = 0.85f,
        bassBoost = 0.90f
    ),
    HYPER(
        displayName = "Hyper",
        sensitivity = 1.90f,
        smoothing = 0.28f,
        beatPunch = 1.85f,
        beatSensitivity = 1.60f,
        bassBoost = 1.55f
    )
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
    val emberDirection: EmberDirection = EmberDirection.UP,
    val fillMode: VisualizerFillMode = VisualizerFillMode.LINE,
    val colorScheme: VisualizerColorScheme = VisualizerColorScheme.DYNAMIC,
    val density: VisualizerDensity = VisualizerDensity.HIGH,
    val sensitivity: Float = 1.3f,
    val smoothing: Float = 0.65f,
    val beatPunch: Float = 1.0f,
    val beatSensitivity: Float = 1.0f,
    val bassBoost: Float = 1.0f,
    val freqMinHz: Int = 30,
    val freqMaxHz: Int = 16000,
    val spreadScale: Float = 1.0f,
    val strokeThickness: Float = 1.0f,
    val rotationSpeed: Float = 1.0f,
    val syncOffsetMs: Int = 0,
    val backgroundMode: BackgroundVisualMode = BackgroundVisualMode.ACCENT_GRADIENT,
    val particleOverlay: ParticleOverlayStyle = ParticleOverlayStyle.BOKEH_RADIAL,
    val cameraShakeIntensity: Float = 0f,
    val rgbSplitIntensity: Float = 0f,
    val additiveBlendEnabled: Boolean = true,
    val vignetteEnabled: Boolean = true,
    val sharpBarsEnabled: Boolean = false,
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
) {
    fun matchingTuningPreset(): VisualizerTuningPreset? = VisualizerTuningPreset.entries.firstOrNull { preset ->
        kotlin.math.abs(sensitivity - preset.sensitivity) < 0.03f &&
            kotlin.math.abs(smoothing - preset.smoothing) < 0.03f &&
            kotlin.math.abs(beatPunch - preset.beatPunch) < 0.03f &&
            kotlin.math.abs(beatSensitivity - preset.beatSensitivity) < 0.03f &&
            kotlin.math.abs(bassBoost - preset.bassBoost) < 0.03f
    }

    fun matchingScenePreset(): AveeScenePreset? = AveeScenePreset.entries.firstOrNull { preset ->
        style == preset.style &&
            fillMode == preset.fillMode &&
            colorScheme == preset.colorScheme &&
            backgroundMode == preset.backgroundMode &&
            particleOverlay == preset.particleOverlay &&
            kotlin.math.abs(cameraShakeIntensity - preset.cameraShakeIntensity) < 0.05f &&
            kotlin.math.abs(rgbSplitIntensity - preset.rgbSplitIntensity) < 0.05f &&
            sharpBarsEnabled == preset.sharpBarsEnabled
    }
}
