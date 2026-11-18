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
import com.auraplayer.app.data.model.CoverArtStyle
import com.auraplayer.app.data.model.NowPlayingVisualMode
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerConfig
import com.auraplayer.app.data.model.VisualizerDensity
import com.auraplayer.app.data.model.VisualizerFillMode
import com.auraplayer.app.data.model.VisualizerStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.visualizerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aura_visualizer_prefs"
)

class VisualizerPreferencesDataSource(private val context: Context) {

    private object Keys {
        val STYLE = stringPreferencesKey("visualizer_style")
        val FILL_MODE = stringPreferencesKey("visualizer_fill_mode")
        val COLOR_SCHEME = stringPreferencesKey("visualizer_color_scheme")
        val DENSITY = stringPreferencesKey("visualizer_density")
        val SENSITIVITY = floatPreferencesKey("visualizer_sensitivity")
        val SMOOTHING = floatPreferencesKey("visualizer_smoothing")
        val SPREAD_SCALE = floatPreferencesKey("visualizer_spread_scale")
        val STROKE_THICKNESS = floatPreferencesKey("visualizer_stroke_thickness")
        val ROTATION_SPEED = floatPreferencesKey("visualizer_rotation_speed")
        val SYNC_OFFSET_MS = intPreferencesKey("visualizer_sync_offset_ms")
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
            spreadScale = (prefs[Keys.SPREAD_SCALE] ?: defaults.spreadScale).coerceIn(0.5f, 1.5f),
            strokeThickness = (prefs[Keys.STROKE_THICKNESS] ?: defaults.strokeThickness)
                .coerceIn(0.6f, 1.8f),
            rotationSpeed = (prefs[Keys.ROTATION_SPEED] ?: defaults.rotationSpeed)
                .coerceIn(0f, 2.5f),
            syncOffsetMs = (prefs[Keys.SYNC_OFFSET_MS] ?: defaults.syncOffsetMs).coerceIn(0, 350),
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

    suspend fun updateFillMode(fillMode: VisualizerFillMode) {
        context.visualizerDataStore.edit { it[Keys.FILL_MODE] = fillMode.name }
    }

    suspend fun updateSyncOffsetMs(offsetMs: Int) {
        context.visualizerDataStore.edit { it[Keys.SYNC_OFFSET_MS] = offsetMs.coerceIn(0, 350) }
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

    suspend fun resetToDefaults() {
        context.visualizerDataStore.edit { it.clear() }
    }
}
