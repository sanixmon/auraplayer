package com.auraplayer.app.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.auraplayer.app.data.backup.AuraBackupManager
import com.auraplayer.app.data.model.Playlist
import com.auraplayer.app.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaylistsUiState(
    val playlists: List<Playlist> = emptyList(),
    val statusMessage: String? = null
)

class PlaylistsViewModel(
    private val playlistRepository: PlaylistRepository,
    private val backupManager: AuraBackupManager
) : ViewModel() {

    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PlaylistsUiState> = combine(
        playlistRepository.observePlaylists(),
        statusMessage
    ) { playlists, msg ->
        PlaylistsUiState(playlists = playlists, statusMessage = msg)
    }.stateIn(
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

    fun importBackup(uriString: String) {
        viewModelScope.launch {
            runCatching {
                backupManager.importFromUri(uriString)
            }.onSuccess { result ->
                statusMessage.value = "Imported ${result.playlistsImported} playlist(s): " +
                    "${result.tracksLinked} linked, ${result.tracksPlaceholderUnavailable} unavailable"
            }.onFailure { err ->
                statusMessage.value = "Import failed: ${err.message ?: "Invalid backup JSON"}"
            }
        }
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }

    class Factory(
        private val playlistRepository: PlaylistRepository,
        private val backupManager: AuraBackupManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlaylistsViewModel(playlistRepository, backupManager) as T
        }
    }
}
