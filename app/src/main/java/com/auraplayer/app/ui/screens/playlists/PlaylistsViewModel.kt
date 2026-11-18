package com.auraplayer.app.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.auraplayer.app.data.model.Playlist
import com.auraplayer.app.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaylistsUiState(
    val playlists: List<Playlist> = emptyList()
)

class PlaylistsViewModel(
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    val uiState: StateFlow<PlaylistsUiState> = playlistRepository.observePlaylists()
        .map { PlaylistsUiState(playlists = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = PlaylistsUiState()
        )

    fun createPlaylist(name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            playlistRepository.createPlaylist(name, description)
        }
    }

    fun renamePlaylist(playlistId: Long, name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            playlistRepository.updatePlaylist(playlistId, name, description)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
        }
    }

    class Factory(
        private val playlistRepository: PlaylistRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlaylistsViewModel(playlistRepository) as T
        }
    }
}
