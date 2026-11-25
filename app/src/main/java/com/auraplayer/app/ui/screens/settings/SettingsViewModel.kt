package com.auraplayer.app.ui.screens.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.auraplayer.app.BuildConfig
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
import com.auraplayer.app.data.model.VisualizerTuningPreset
import com.auraplayer.app.data.updater.AppUpdateInfo
import com.auraplayer.app.data.updater.AppUpdateManager
import com.auraplayer.app.data.updater.ChangelogRelease
import com.auraplayer.app.data.updater.UpdateCheckResult
import com.auraplayer.app.playback.PlaybackController
import com.auraplayer.app.playback.VisualizerBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState
    data object Checking : AppUpdateUiState
    data class UpToDate(val currentVersion: String) : AppUpdateUiState
    data class Available(val info: AppUpdateInfo) : AppUpdateUiState
    data class Downloading(val info: AppUpdateInfo, val progress: Float) : AppUpdateUiState
    data class ReadyToInstall(val info: AppUpdateInfo, val apkFile: File) : AppUpdateUiState
    data class Error(val message: String) : AppUpdateUiState
}

class SettingsViewModel(
    private val preferences: VisualizerPreferencesDataSource,
    val visualizerBus: VisualizerBus,
    playbackController: PlaybackController,
    private val appUpdateManager: AppUpdateManager? = null
) : ViewModel() {

    private val _updateState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val updateState: StateFlow<AppUpdateUiState> = _updateState.asStateFlow()

    val bundledChangelog: List<ChangelogRelease> by lazy {
        appUpdateManager?.loadBundledChangelog().orEmpty()
    }

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

    fun setBeatPunch(value: Float) {
        viewModelScope.launch { preferences.updateBeatPunch(value) }
    }

    fun setBeatSensitivity(value: Float) {
        viewModelScope.launch { preferences.updateBeatSensitivity(value) }
    }

    fun setBassBoost(value: Float) {
        viewModelScope.launch { preferences.updateBassBoost(value) }
    }

    fun applyTuningPreset(preset: VisualizerTuningPreset) {
        viewModelScope.launch { preferences.applyTuningPreset(preset) }
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

    fun resetThemeAndCover() {
        viewModelScope.launch { preferences.resetThemeAndCover() }
    }

    fun resetVisualizerStyle() {
        viewModelScope.launch { preferences.resetVisualizerStyle() }
    }

    fun resetBeatAndAudio() {
        viewModelScope.launch { preferences.resetBeatAndAudio() }
    }

    fun resetMotionAndEffects() {
        viewModelScope.launch { preferences.resetMotionAndEffects() }
    }

    fun resetDefaults() {
        viewModelScope.launch { preferences.resetToDefaults() }
    }

    fun checkForUpdates() {
        val manager = appUpdateManager ?: return
        if (_updateState.value is AppUpdateUiState.Checking ||
            _updateState.value is AppUpdateUiState.Downloading
        ) {
            return
        }
        viewModelScope.launch {
            _updateState.value = AppUpdateUiState.Checking
            _updateState.value = when (val result = manager.checkForUpdate(BuildConfig.VERSION_NAME)) {
                is UpdateCheckResult.UpdateAvailable -> AppUpdateUiState.Available(result.info)
                is UpdateCheckResult.UpToDate -> AppUpdateUiState.UpToDate(result.currentVersion)
                is UpdateCheckResult.Error -> AppUpdateUiState.Error(result.message)
            }
        }
    }

    fun downloadUpdate(info: AppUpdateInfo) {
        val manager = appUpdateManager ?: return
        if (_updateState.value is AppUpdateUiState.Downloading) return
        viewModelScope.launch {
            _updateState.value = AppUpdateUiState.Downloading(info = info, progress = 0f)
            runCatching {
                manager.downloadUpdateApk(info) { progress ->
                    _updateState.value = AppUpdateUiState.Downloading(info = info, progress = progress)
                }
            }.onSuccess { apkFile ->
                _updateState.value = AppUpdateUiState.ReadyToInstall(info = info, apkFile = apkFile)
            }.onFailure { err ->
                _updateState.value = AppUpdateUiState.Error(
                    err.message ?: "Failed to download update APK."
                )
            }
        }
    }

    fun canInstallPackages(): Boolean = appUpdateManager?.canInstallPackages() ?: false

    fun createInstallIntent(apkFile: File): Intent? = appUpdateManager?.createInstallIntent(apkFile)

    fun createUnknownSourcesIntent(): Intent? = appUpdateManager?.createUnknownSourcesSettingsIntent()

    class Factory(
        private val preferences: VisualizerPreferencesDataSource,
        private val visualizerBus: VisualizerBus,
        private val playbackController: PlaybackController,
        private val appUpdateManager: AppUpdateManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                preferences,
                visualizerBus,
                playbackController,
                appUpdateManager
            ) as T
        }
    }
}
