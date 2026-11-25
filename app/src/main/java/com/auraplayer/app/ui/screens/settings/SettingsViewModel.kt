package com.auraplayer.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.auraplayer.app.data.local.prefs.VisualizerPreferencesDataSource
import com.auraplayer.app.data.model.AppAccentMode
import com.auraplayer.app.data.model.AppAccentPreset
import com.auraplayer.app.data.model.CoverArtStyle
import com.auraplayer.app.data.model.EmberDirection
import com.auraplayer.app.data.model.Track
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerConfig
import com.auraplayer.app.data.model.VisualizerDensity
import com.auraplayer.app.data.model.VisualizerFillMode
import com.auraplayer.app.data.model.VisualizerFrame
import com.auraplayer.app.data.model.VisualizerStyle
import com.auraplayer.app.playback.PlaybackController
import com.auraplayer.app.playback.VisualizerBus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferences: VisualizerPreferencesDataSource,
    val visualizerBus: VisualizerBus,
    playbackController: PlaybackController
) : ViewModel() {

    val visualizerConfig: StateFlow<VisualizerConfig> = preferences.configFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = VisualizerConfig()
        )

    val visualizerFrame: StateFlow<VisualizerFrame> = visualizerBus.frames

    val currentTrack: StateFlow<Track?> = playbackController.state
        .map { it.currentTrack }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null
        )

    val isPlaying: StateFlow<Boolean> = playbackController.state
        .map { it.isPlaying }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false
        )

    fun setStyle(style: VisualizerStyle) {
        viewModelScope.launch { preferences.updateStyle(style) }
    }

    fun setEmberDirection(direction: EmberDirection) {
        viewModelScope.launch { preferences.updateEmberDirection(direction) }
    }

    fun setFillMode(fillMode: VisualizerFillMode) {
        viewModelScope.launch { preferences.updateFillMode(fillMode) }
    }

    fun setSyncOffsetMs(offsetMs: Int) {
        viewModelScope.launch { preferences.updateSyncOffsetMs(offsetMs) }
    }

    fun setColorScheme(scheme: VisualizerColorScheme) {
        viewModelScope.launch { preferences.updateColorScheme(scheme) }
    }

    fun setDensity(density: VisualizerDensity) {
        viewModelScope.launch { preferences.updateDensity(density) }
    }

    fun setSensitivity(value: Float) {
        viewModelScope.launch { preferences.updateSensitivity(value) }
    }

    fun setSmoothing(value: Float) {
        viewModelScope.launch { preferences.updateSmoothing(value) }
    }

    fun setSpreadScale(value: Float) {
        viewModelScope.launch { preferences.updateSpreadScale(value) }
    }

    fun setStrokeThickness(value: Float) {
        viewModelScope.launch { preferences.updateStrokeThickness(value) }
    }

    fun setRotationSpeed(value: Float) {
        viewModelScope.launch { preferences.updateRotationSpeed(value) }
    }

    fun setGlowEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.updateGlowEnabled(enabled) }
    }

    fun setParticlesEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.updateParticlesEnabled(enabled) }
    }

    fun setMirrorEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.updateMirrorEnabled(enabled) }
    }

    fun setPeakCapsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.updatePeakCapsEnabled(enabled) }
    }

    fun setCoverArtStyle(style: CoverArtStyle) {
        viewModelScope.launch { preferences.updateCoverArtStyle(style) }
    }

    fun setAccentMode(mode: AppAccentMode) {
        viewModelScope.launch { preferences.updateAccentMode(mode) }
    }

    fun setAccentPreset(preset: AppAccentPreset) {
        viewModelScope.launch { preferences.updateAccentPreset(preset) }
    }

    fun setCustomAppColors(primaryArgb: Long, secondaryArgb: Long) {
        viewModelScope.launch { preferences.updateCustomAppColors(primaryArgb, secondaryArgb) }
    }

    fun setCustomVisualizerColors(primaryArgb: Long, secondaryArgb: Long) {
        viewModelScope.launch {
            preferences.updateCustomVisualizerColors(primaryArgb, secondaryArgb)
        }
    }

    fun resetDefaults() {
        viewModelScope.launch { preferences.resetToDefaults() }
    }

    class Factory(
        private val preferences: VisualizerPreferencesDataSource,
        private val visualizerBus: VisualizerBus,
        private val playbackController: PlaybackController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                preferences,
                visualizerBus,
                playbackController
            ) as T
        }
    }
}
