package com.auraplayer.app.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.auraplayer.app.data.model.AppAccentMode
import com.auraplayer.app.data.model.AppAccentPreset
import com.auraplayer.app.data.model.AveeScenePreset
import com.auraplayer.app.data.model.BackgroundVisualMode
import com.auraplayer.app.data.model.CoverArtStyle
import com.auraplayer.app.data.model.EmberDirection
import com.auraplayer.app.data.model.NowPlayingVisualMode
import com.auraplayer.app.data.model.ParticleOverlayStyle
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerConfig
import com.auraplayer.app.data.model.VisualizerDensity
import com.auraplayer.app.data.model.VisualizerFillMode
import com.auraplayer.app.data.model.VisualizerStyle
import com.auraplayer.app.data.model.VisualizerTuningPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.visualizerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aura_visualizer_prefs"
)

class VisualizerPreferencesDataSource(private val context: Context) {

    private object Keys {
        val STYLE = stringPreferencesKey("visualizer_style")
        val EMBER_DIRECTION = stringPreferencesKey("visualizer_ember_direction")
        val FILL_MODE = stringPreferencesKey("visualizer_fill_mode")
        val COLOR_SCHEME = stringPreferencesKey("visualizer_color_scheme")
        val DENSITY = stringPreferencesKey("visualizer_density")
        val SENSITIVITY = floatPreferencesKey("visualizer_sensitivity")
        val SMOOTHING = floatPreferencesKey("visualizer_smoothing")
        val BEAT_PUNCH = floatPreferencesKey("visualizer_beat_punch")
        val BEAT_SENSITIVITY = floatPreferencesKey("visualizer_beat_sensitivity")
        val BASS_BOOST = floatPreferencesKey("visualizer_bass_boost")
        val FREQ_MIN_HZ = intPreferencesKey("visualizer_freq_min_hz")
        val FREQ_MAX_HZ = intPreferencesKey("visualizer_freq_max_hz")
        val SPREAD_SCALE = floatPreferencesKey("visualizer_spread_scale")
        val STROKE_THICKNESS = floatPreferencesKey("visualizer_stroke_thickness")
        val ROTATION_SPEED = floatPreferencesKey("visualizer_rotation_speed")
        val SYNC_OFFSET_MS = intPreferencesKey("visualizer_sync_offset_ms")
        val BACKGROUND_MODE = stringPreferencesKey("visualizer_background_mode")
        val PARTICLE_OVERLAY = stringPreferencesKey("visualizer_particle_overlay")
        val CAMERA_SHAKE = floatPreferencesKey("visualizer_camera_shake")
        val RGB_SPLIT = floatPreferencesKey("visualizer_rgb_split")
        val ADDITIVE_BLEND = booleanPreferencesKey("visualizer_additive_blend")
        val VIGNETTE_ENABLED = booleanPreferencesKey("visualizer_vignette_enabled")
        val SHARP_BARS = booleanPreferencesKey("visualizer_sharp_bars")
        val GLOW_ENABLED = booleanPreferencesKey("visualizer_glow_enabled")
        val PARTICLES_ENABLED = booleanPreferencesKey("visualizer_particles_enabled")
        val MIRROR_ENABLED = booleanPreferencesKey("visualizer_mirror_enabled")
        val PEAK_CAPS_ENABLED = booleanPreferencesKey("visualizer_peak_caps_enabled")
        val VISUAL_MODE = stringPreferencesKey("now_playing_visual_mode")
        val COVER_ART_STYLE = stringPreferencesKey("cover_art_style")
        val ACCENT_MODE = stringPreferencesKey("app_accent_mode")
        val ACCENT_PRESET = stringPreferencesKey("app_accent_preset")
        val CUSTOM_APP_PRIMARY = longPreferencesKey("custom_app_primary_argb")
        val CUSTOM_APP_SECONDARY = longPreferencesKey("custom_app_secondary_argb")
        val CUSTOM_VIZ_PRIMARY = longPreferencesKey("custom_viz_primary_argb")
        val CUSTOM_VIZ_SECONDARY = longPreferencesKey("custom_viz_secondary_argb")
    }

    val configFlow: Flow<VisualizerConfig> = context.visualizerDataStore.data.map { prefs ->
        val defaults = VisualizerConfig()
        val preset = prefs[Keys.ACCENT_PRESET]?.let { name ->
            AppAccentPreset.entries.firstOrNull { it.name == name }
        } ?: defaults.accentPreset

        VisualizerConfig(
            style = prefs[Keys.STYLE]?.let { name ->
                VisualizerStyle.entries.firstOrNull { it.name == name }
            } ?: defaults.style,
            emberDirection = prefs[Keys.EMBER_DIRECTION]?.let { name ->
                EmberDirection.entries.firstOrNull { it.name == name }
            } ?: defaults.emberDirection,
            fillMode = prefs[Keys.FILL_MODE]?.let { name ->
                VisualizerFillMode.entries.firstOrNull { it.name == name }
            } ?: defaults.fillMode,
            colorScheme = prefs[Keys.COLOR_SCHEME]?.let { name ->
                VisualizerColorScheme.entries.firstOrNull { it.name == name }
            } ?: defaults.colorScheme,
            density = prefs[Keys.DENSITY]?.let { name ->
                VisualizerDensity.entries.firstOrNull { it.name == name }
            } ?: defaults.density,
            sensitivity = (prefs[Keys.SENSITIVITY] ?: defaults.sensitivity).coerceIn(0.5f, 2.5f),
            smoothing = (prefs[Keys.SMOOTHING] ?: defaults.smoothing).coerceIn(0.15f, 0.95f),
            beatPunch = (prefs[Keys.BEAT_PUNCH] ?: defaults.beatPunch).coerceIn(0f, 2.0f),
            beatSensitivity = (prefs[Keys.BEAT_SENSITIVITY] ?: defaults.beatSensitivity).coerceIn(0.5f, 2.0f),
            bassBoost = (prefs[Keys.BASS_BOOST] ?: defaults.bassBoost).coerceIn(0.5f, 2.0f),
            freqMinHz = (prefs[Keys.FREQ_MIN_HZ] ?: defaults.freqMinHz).coerceIn(20, 500),
            freqMaxHz = (prefs[Keys.FREQ_MAX_HZ] ?: defaults.freqMaxHz).coerceIn(1000, 16000),
            spreadScale = (prefs[Keys.SPREAD_SCALE] ?: defaults.spreadScale).coerceIn(0.5f, 1.5f),
            strokeThickness = (prefs[Keys.STROKE_THICKNESS] ?: defaults.strokeThickness)
                .coerceIn(0.6f, 1.8f),
            rotationSpeed = (prefs[Keys.ROTATION_SPEED] ?: defaults.rotationSpeed)
                .coerceIn(0f, 2.5f),
            syncOffsetMs = (prefs[Keys.SYNC_OFFSET_MS] ?: defaults.syncOffsetMs).coerceIn(-100, 350),
            backgroundMode = prefs[Keys.BACKGROUND_MODE]?.let { name ->
                BackgroundVisualMode.entries.firstOrNull { it.name == name }
            } ?: defaults.backgroundMode,
            particleOverlay = prefs[Keys.PARTICLE_OVERLAY]?.let { name ->
                ParticleOverlayStyle.entries.firstOrNull { it.name == name }
            } ?: defaults.particleOverlay,
            cameraShakeIntensity = (prefs[Keys.CAMERA_SHAKE] ?: defaults.cameraShakeIntensity)
                .coerceIn(0f, 1.5f),
            rgbSplitIntensity = (prefs[Keys.RGB_SPLIT] ?: defaults.rgbSplitIntensity)
                .coerceIn(0f, 1.5f),
            additiveBlendEnabled = prefs[Keys.ADDITIVE_BLEND] ?: defaults.additiveBlendEnabled,
            vignetteEnabled = prefs[Keys.VIGNETTE_ENABLED] ?: defaults.vignetteEnabled,
            sharpBarsEnabled = prefs[Keys.SHARP_BARS] ?: defaults.sharpBarsEnabled,
            glowEnabled = prefs[Keys.GLOW_ENABLED] ?: defaults.glowEnabled,
            particlesEnabled = prefs[Keys.PARTICLES_ENABLED] ?: defaults.particlesEnabled,
            mirrorEnabled = prefs[Keys.MIRROR_ENABLED] ?: defaults.mirrorEnabled,
            peakCapsEnabled = prefs[Keys.PEAK_CAPS_ENABLED] ?: defaults.peakCapsEnabled,
            visualMode = prefs[Keys.VISUAL_MODE]?.let { name ->
                NowPlayingVisualMode.entries.firstOrNull { it.name == name }
            } ?: defaults.visualMode,
            coverArtStyle = when (prefs[Keys.COVER_ART_STYLE]) {
                CoverArtStyle.VINYL_DISC.name -> CoverArtStyle.VINYL_DISC
                "CARD_3D", "SOFT_GLASS", CoverArtStyle.COVER.name -> CoverArtStyle.COVER
                else -> defaults.coverArtStyle
            },
            accentMode = prefs[Keys.ACCENT_MODE]?.let { name ->
                AppAccentMode.entries.firstOrNull { it.name == name }
            } ?: defaults.accentMode,
            accentPreset = preset,
            customAppPrimaryArgb = prefs[Keys.CUSTOM_APP_PRIMARY] ?: preset.primaryHex,
            customAppSecondaryArgb = prefs[Keys.CUSTOM_APP_SECONDARY] ?: preset.secondaryHex,
            customVizPrimaryArgb = prefs[Keys.CUSTOM_VIZ_PRIMARY] ?: defaults.customVizPrimaryArgb,
            customVizSecondaryArgb = prefs[Keys.CUSTOM_VIZ_SECONDARY] ?: defaults.customVizSecondaryArgb
        )
    }

    suspend fun updateStyle(style: VisualizerStyle) {
        context.visualizerDataStore.edit { it[Keys.STYLE] = style.name }
    }

    suspend fun updateEmberDirection(direction: EmberDirection) {
        context.visualizerDataStore.edit { it[Keys.EMBER_DIRECTION] = direction.name }
    }

    suspend fun updateFillMode(fillMode: VisualizerFillMode) {
        context.visualizerDataStore.edit { it[Keys.FILL_MODE] = fillMode.name }
    }

    suspend fun updateSyncOffsetMs(offsetMs: Int) {
        context.visualizerDataStore.edit { it[Keys.SYNC_OFFSET_MS] = offsetMs.coerceIn(-100, 350) }
    }

    suspend fun updateBeatPunch(punch: Float) {
        context.visualizerDataStore.edit { it[Keys.BEAT_PUNCH] = punch.coerceIn(0f, 2.0f) }
    }

    suspend fun updateBeatSensitivity(sensitivity: Float) {
        context.visualizerDataStore.edit { it[Keys.BEAT_SENSITIVITY] = sensitivity.coerceIn(0.5f, 2.0f) }
    }

    suspend fun updateBassBoost(boost: Float) {
        context.visualizerDataStore.edit { it[Keys.BASS_BOOST] = boost.coerceIn(0.5f, 2.0f) }
    }

    suspend fun updateFreqRange(minHz: Int, maxHz: Int) {
        val safeMin = minHz.coerceIn(20, 500)
        val safeMax = maxHz.coerceIn(maxOf(1000, safeMin + 500), 16000)
        context.visualizerDataStore.edit {
            it[Keys.FREQ_MIN_HZ] = safeMin
            it[Keys.FREQ_MAX_HZ] = safeMax
        }
    }

    suspend fun updateBackgroundMode(mode: BackgroundVisualMode) {
        context.visualizerDataStore.edit { it[Keys.BACKGROUND_MODE] = mode.name }
    }

    suspend fun updateParticleOverlay(style: ParticleOverlayStyle) {
        context.visualizerDataStore.edit {
            it[Keys.PARTICLE_OVERLAY] = style.name
            it[Keys.PARTICLES_ENABLED] = style != ParticleOverlayStyle.OFF
        }
    }

    suspend fun updateCameraShakeIntensity(intensity: Float) {
        context.visualizerDataStore.edit { it[Keys.CAMERA_SHAKE] = intensity.coerceIn(0f, 1.5f) }
    }

    suspend fun updateRgbSplitIntensity(intensity: Float) {
        context.visualizerDataStore.edit { it[Keys.RGB_SPLIT] = intensity.coerceIn(0f, 1.5f) }
    }

    suspend fun updateAdditiveBlendEnabled(enabled: Boolean) {
        context.visualizerDataStore.edit { it[Keys.ADDITIVE_BLEND] = enabled }
    }

    suspend fun updateVignetteEnabled(enabled: Boolean) {
        context.visualizerDataStore.edit { it[Keys.VIGNETTE_ENABLED] = enabled }
    }

    suspend fun updateSharpBarsEnabled(enabled: Boolean) {
        context.visualizerDataStore.edit { it[Keys.SHARP_BARS] = enabled }
    }

    suspend fun applyScenePreset(preset: AveeScenePreset) {
        context.visualizerDataStore.edit {
            it[Keys.STYLE] = preset.style.name
            it[Keys.EMBER_DIRECTION] = preset.emberDirection.name
            it[Keys.FILL_MODE] = preset.fillMode.name
            it[Keys.COLOR_SCHEME] = preset.colorScheme.name
            it[Keys.BACKGROUND_MODE] = preset.backgroundMode.name
            it[Keys.PARTICLE_OVERLAY] = preset.particleOverlay.name
            it[Keys.PARTICLES_ENABLED] = preset.particleOverlay != ParticleOverlayStyle.OFF
            it[Keys.SENSITIVITY] = preset.tuningPreset.sensitivity
            it[Keys.SMOOTHING] = preset.tuningPreset.smoothing
            it[Keys.BEAT_PUNCH] = preset.tuningPreset.beatPunch
            it[Keys.BEAT_SENSITIVITY] = preset.tuningPreset.beatSensitivity
            it[Keys.BASS_BOOST] = preset.tuningPreset.bassBoost
            it[Keys.CAMERA_SHAKE] = preset.cameraShakeIntensity
            it[Keys.RGB_SPLIT] = preset.rgbSplitIntensity
            it[Keys.ADDITIVE_BLEND] = preset.additiveBlendEnabled
            it[Keys.VIGNETTE_ENABLED] = preset.vignetteEnabled
            it[Keys.SHARP_BARS] = preset.sharpBarsEnabled
            it[Keys.GLOW_ENABLED] = preset.glowEnabled
            it[Keys.MIRROR_ENABLED] = preset.mirrorEnabled
            it[Keys.PEAK_CAPS_ENABLED] = preset.peakCapsEnabled
        }
    }

    suspend fun applyTuningPreset(preset: VisualizerTuningPreset) {
        context.visualizerDataStore.edit {
            it[Keys.SENSITIVITY] = preset.sensitivity
            it[Keys.SMOOTHING] = preset.smoothing
            it[Keys.BEAT_PUNCH] = preset.beatPunch
            it[Keys.BEAT_SENSITIVITY] = preset.beatSensitivity
            it[Keys.BASS_BOOST] = preset.bassBoost
        }
    }

    suspend fun updateColorScheme(scheme: VisualizerColorScheme) {
        context.visualizerDataStore.edit { it[Keys.COLOR_SCHEME] = scheme.name }
    }

    suspend fun updateDensity(density: VisualizerDensity) {
        context.visualizerDataStore.edit { it[Keys.DENSITY] = density.name }
    }

    suspend fun updateSensitivity(sensitivity: Float) {
        context.visualizerDataStore.edit { it[Keys.SENSITIVITY] = sensitivity.coerceIn(0.5f, 2.5f) }
    }

    suspend fun updateSmoothing(smoothing: Float) {
        context.visualizerDataStore.edit { it[Keys.SMOOTHING] = smoothing.coerceIn(0.15f, 0.95f) }
    }

    suspend fun updateSpreadScale(scale: Float) {
        context.visualizerDataStore.edit { it[Keys.SPREAD_SCALE] = scale.coerceIn(0.5f, 1.5f) }
    }

    suspend fun updateStrokeThickness(thickness: Float) {
        context.visualizerDataStore.edit {
            it[Keys.STROKE_THICKNESS] = thickness.coerceIn(0.6f, 1.8f)
        }
    }

    suspend fun updateRotationSpeed(speed: Float) {
        context.visualizerDataStore.edit { it[Keys.ROTATION_SPEED] = speed.coerceIn(0f, 2.5f) }
    }

    suspend fun updateGlowEnabled(enabled: Boolean) {
        context.visualizerDataStore.edit { it[Keys.GLOW_ENABLED] = enabled }
    }

    suspend fun updateParticlesEnabled(enabled: Boolean) {
        context.visualizerDataStore.edit { it[Keys.PARTICLES_ENABLED] = enabled }
    }

    suspend fun updateMirrorEnabled(enabled: Boolean) {
        context.visualizerDataStore.edit { it[Keys.MIRROR_ENABLED] = enabled }
    }

    suspend fun updatePeakCapsEnabled(enabled: Boolean) {
        context.visualizerDataStore.edit { it[Keys.PEAK_CAPS_ENABLED] = enabled }
    }

    suspend fun updateVisualMode(mode: NowPlayingVisualMode) {
        context.visualizerDataStore.edit { it[Keys.VISUAL_MODE] = mode.name }
    }

    suspend fun updateCoverArtStyle(style: CoverArtStyle) {
        context.visualizerDataStore.edit { it[Keys.COVER_ART_STYLE] = style.name }
    }

    suspend fun updateAccentMode(mode: AppAccentMode) {
        context.visualizerDataStore.edit { it[Keys.ACCENT_MODE] = mode.name }
    }

    suspend fun updateAccentPreset(preset: AppAccentPreset) {
        context.visualizerDataStore.edit {
            it[Keys.ACCENT_PRESET] = preset.name
            it[Keys.ACCENT_MODE] = AppAccentMode.CUSTOM.name
            it[Keys.CUSTOM_APP_PRIMARY] = preset.primaryHex
            it[Keys.CUSTOM_APP_SECONDARY] = preset.secondaryHex
        }
    }

    suspend fun updateCustomAppColors(primaryArgb: Long, secondaryArgb: Long) {
        context.visualizerDataStore.edit {
            it[Keys.ACCENT_MODE] = AppAccentMode.CUSTOM.name
            it[Keys.CUSTOM_APP_PRIMARY] = primaryArgb
            it[Keys.CUSTOM_APP_SECONDARY] = secondaryArgb
        }
    }

    suspend fun updateCustomVisualizerColors(primaryArgb: Long, secondaryArgb: Long) {
        context.visualizerDataStore.edit {
            it[Keys.COLOR_SCHEME] = VisualizerColorScheme.CUSTOM.name
            it[Keys.CUSTOM_VIZ_PRIMARY] = primaryArgb
            it[Keys.CUSTOM_VIZ_SECONDARY] = secondaryArgb
        }
    }

    suspend fun resetThemeAndCover() {
        context.visualizerDataStore.edit {
            it.remove(Keys.ACCENT_MODE)
            it.remove(Keys.ACCENT_PRESET)
            it.remove(Keys.CUSTOM_APP_PRIMARY)
            it.remove(Keys.CUSTOM_APP_SECONDARY)
            it.remove(Keys.COVER_ART_STYLE)
            it.remove(Keys.BACKGROUND_MODE)
            it.remove(Keys.VIGNETTE_ENABLED)
        }
    }

    suspend fun resetVisualizerStyle() {
        context.visualizerDataStore.edit {
            it.remove(Keys.STYLE)
            it.remove(Keys.EMBER_DIRECTION)
            it.remove(Keys.FILL_MODE)
            it.remove(Keys.COLOR_SCHEME)
            it.remove(Keys.DENSITY)
            it.remove(Keys.PARTICLE_OVERLAY)
            it.remove(Keys.CUSTOM_VIZ_PRIMARY)
            it.remove(Keys.CUSTOM_VIZ_SECONDARY)
        }
    }

    suspend fun resetBeatAndAudio() {
        context.visualizerDataStore.edit {
            it.remove(Keys.SENSITIVITY)
            it.remove(Keys.SMOOTHING)
            it.remove(Keys.BEAT_PUNCH)
            it.remove(Keys.BEAT_SENSITIVITY)
            it.remove(Keys.BASS_BOOST)
            it.remove(Keys.FREQ_MIN_HZ)
            it.remove(Keys.FREQ_MAX_HZ)
            it.remove(Keys.SYNC_OFFSET_MS)
        }
    }

    suspend fun resetMotionAndEffects() {
        context.visualizerDataStore.edit {
            it.remove(Keys.SPREAD_SCALE)
            it.remove(Keys.STROKE_THICKNESS)
            it.remove(Keys.ROTATION_SPEED)
            it.remove(Keys.CAMERA_SHAKE)
            it.remove(Keys.RGB_SPLIT)
            it.remove(Keys.ADDITIVE_BLEND)
            it.remove(Keys.SHARP_BARS)
            it.remove(Keys.GLOW_ENABLED)
            it.remove(Keys.PARTICLES_ENABLED)
            it.remove(Keys.MIRROR_ENABLED)
            it.remove(Keys.PEAK_CAPS_ENABLED)
        }
    }

    suspend fun resetToDefaults() {
        context.visualizerDataStore.edit { it.clear() }
    }
}
