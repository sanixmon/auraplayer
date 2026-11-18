package com.auraplayer.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.auraplayer.app.data.backup.AuraBackupManager
import com.auraplayer.app.data.local.prefs.VisualizerPreferencesDataSource
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerConfig
import com.auraplayer.app.data.model.VisualizerDensity
import com.auraplayer.app.data.model.VisualizerFrame
import com.auraplayer.app.data.model.VisualizerStyle
import com.auraplayer.app.playback.PlaybackController
import com.auraplayer.app.playback.VisualizerBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val preferences: VisualizerPreferencesDataSource,
    private val backupManager: AuraBackupManager,
    visualizerBus: VisualizerBus,
    playbackController: PlaybackController
) : ViewModel() {

    val visualizerConfig: StateFlow<VisualizerConfig> = preferences.configFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = VisualizerConfig()
        )

    val visualizerFrame: StateFlow<VisualizerFrame> = visualizerBus.frames

    val isPlaying: StateFlow<Boolean> = playbackController.state
        .map { it.isPlaying }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = false
        )

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun setStyle(style: VisualizerStyle) {
        viewModelScope.launch { preferences.updateStyle(style) }
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

    fun setGlowEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.updateGlowEnabled(enabled) }
    }

    fun setMirrorEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.updateMirrorEnabled(enabled) }
    }

    fun setPeakCapsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferences.updatePeakCapsEnabled(enabled) }
    }

    fun resetDefaults() {
        viewModelScope.launch { preferences.resetToDefaults() }
    }

    fun exportFullBackup(uriString: String) {
        viewModelScope.launch {
            runCatching {
                val json = backupManager.exportFullBackupJson()
                backupManager.writeBackupToUri(uriString, json)
            }.onSuccess { ok ->
                _statusMessage.value = if (ok) {
                    "Aura Backup saved"
                } else {
                    "Failed to save Aura Backup"
                }
            }.onFailure { err ->
                _statusMessage.value = "Backup failed: ${err.message ?: "Unknown error"}"
            }
        }
    }

    fun importFullBackup(uriString: String) {
        viewModelScope.launch {
            runCatching {
                backupManager.importFromUri(uriString)
            }.onSuccess { result ->
                _statusMessage.value = "Restored ${result.playlistsImported} playlist(s), " +
                    "${result.tracksLinked} linked, ${result.tracksPlaceholderUnavailable} unavailable" +
                    if (result.visualizerRestored) " + visualizer preset" else ""
            }.onFailure { err ->
                _statusMessage.value = "Restore failed: ${err.message ?: "Invalid backup JSON"}"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    class Factory(
        private val preferences: VisualizerPreferencesDataSource,
        private val backupManager: AuraBackupManager,
        private val visualizerBus: VisualizerBus,
        private val playbackController: PlaybackController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                preferences,
                backupManager,
                visualizerBus,
                playbackController
            ) as T
        }
    }
}
