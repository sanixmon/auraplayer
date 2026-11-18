package com.auraplayer.app.ui.screens.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.auraplayer.app.data.local.prefs.VisualizerPreferencesDataSource
import com.auraplayer.app.data.model.CoverArtStyle
import com.auraplayer.app.data.model.NowPlayingVisualMode
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerConfig
import com.auraplayer.app.data.model.VisualizerFrame
import com.auraplayer.app.data.model.VisualizerStyle
import com.auraplayer.app.playback.PlaybackController
import com.auraplayer.app.playback.PlaybackUiState
import com.auraplayer.app.playback.VisualizerBus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NowPlayingViewModel(
    private val playbackController: PlaybackController,
    visualizerBus: VisualizerBus,
    private val visualizerPreferences: VisualizerPreferencesDataSource
) : ViewModel() {

    val playbackState: StateFlow<PlaybackUiState> = playbackController.state

    val visualizerFrame: StateFlow<VisualizerFrame> = visualizerBus.frames

    val visualizerConfig: StateFlow<VisualizerConfig> = visualizerPreferences.configFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = VisualizerConfig()
        )

    fun togglePlayPause() = playbackController.togglePlayPause()

    fun skipNext() = playbackController.skipToNext()

    fun skipPrevious() = playbackController.skipToPrevious()

    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    fun skipToQueueIndex(index: Int) = playbackController.skipToQueueIndex(index)

    fun toggleShuffle() = playbackController.toggleShuffle()

    fun cycleRepeatMode() = playbackController.cycleRepeatMode()

    fun selectVisualizerStyle(style: VisualizerStyle) {
        viewModelScope.launch { visualizerPreferences.updateStyle(style) }
    }

    fun selectVisualizerColorScheme(scheme: VisualizerColorScheme) {
        viewModelScope.launch { visualizerPreferences.updateColorScheme(scheme) }
    }

    fun selectVisualMode(mode: NowPlayingVisualMode) {
        viewModelScope.launch { visualizerPreferences.updateVisualMode(mode) }
    }

    fun selectCoverArtStyle(style: CoverArtStyle) {
        viewModelScope.launch { visualizerPreferences.updateCoverArtStyle(style) }
    }

    class Factory(
        private val playbackController: PlaybackController,
        private val visualizerBus: VisualizerBus,
        private val visualizerPreferences: VisualizerPreferencesDataSource
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NowPlayingViewModel(
                playbackController,
                visualizerBus,
                visualizerPreferences
            ) as T
        }
    }
}
