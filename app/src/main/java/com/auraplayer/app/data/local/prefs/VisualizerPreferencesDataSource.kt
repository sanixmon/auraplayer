package com.auraplayer.app.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerConfig
import com.auraplayer.app.data.model.VisualizerDensity
import com.auraplayer.app.data.model.VisualizerStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.visualizerDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "aura_visualizer_prefs"
)

class VisualizerPreferencesDataSource(private val context: Context) {

    private object Keys {
        val STYLE = stringPreferencesKey("visualizer_style")
        val COLOR_SCHEME = stringPreferencesKey("visualizer_color_scheme")
        val DENSITY = stringPreferencesKey("visualizer_density")
        val SENSITIVITY = floatPreferencesKey("visualizer_sensitivity")
        val SMOOTHING = floatPreferencesKey("visualizer_smoothing")
        val GLOW_ENABLED = booleanPreferencesKey("visualizer_glow_enabled")
        val MIRROR_ENABLED = booleanPreferencesKey("visualizer_mirror_enabled")
        val PEAK_CAPS_ENABLED = booleanPreferencesKey("visualizer_peak_caps_enabled")
    }

    val configFlow: Flow<VisualizerConfig> = context.visualizerDataStore.data.map { prefs ->
        val defaults = VisualizerConfig()
        VisualizerConfig(
            style = prefs[Keys.STYLE]?.let { name ->
                VisualizerStyle.entries.firstOrNull { it.name == name }
            } ?: defaults.style,
            colorScheme = prefs[Keys.COLOR_SCHEME]?.let { name ->
                VisualizerColorScheme.entries.firstOrNull { it.name == name }
            } ?: defaults.colorScheme,
            density = prefs[Keys.DENSITY]?.let { name ->
                VisualizerDensity.entries.firstOrNull { it.name == name }
            } ?: defaults.density,
            sensitivity = (prefs[Keys.SENSITIVITY] ?: defaults.sensitivity).coerceIn(0.5f, 2.5f),
            smoothing = (prefs[Keys.SMOOTHING] ?: defaults.smoothing).coerceIn(0.15f, 0.95f),
            glowEnabled = prefs[Keys.GLOW_ENABLED] ?: defaults.glowEnabled,
            mirrorEnabled = prefs[Keys.MIRROR_ENABLED] ?: defaults.mirrorEnabled,
            peakCapsEnabled = prefs[Keys.PEAK_CAPS_ENABLED] ?: defaults.peakCapsEnabled
        )
    }

    suspend fun updateStyle(style: VisualizerStyle) {
        context.visualizerDataStore.edit { it[Keys.STYLE] = style.name }
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

    suspend fun updateGlowEnabled(enabled: Boolean) {
        context.visualizerDataStore.edit { it[Keys.GLOW_ENABLED] = enabled }
    }

    suspend fun updateMirrorEnabled(enabled: Boolean) {
        context.visualizerDataStore.edit { it[Keys.MIRROR_ENABLED] = enabled }
    }

    suspend fun updatePeakCapsEnabled(enabled: Boolean) {
        context.visualizerDataStore.edit { it[Keys.PEAK_CAPS_ENABLED] = enabled }
    }

    suspend fun resetToDefaults() {
        context.visualizerDataStore.edit { it.clear() }
    }
}
