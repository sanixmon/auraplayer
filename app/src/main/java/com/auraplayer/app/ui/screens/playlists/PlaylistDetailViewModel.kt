package com.auraplayer.app.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.auraplayer.app.data.backup.AuraBackupManager
import com.auraplayer.app.data.model.Playlist
import com.auraplayer.app.data.model.PlaylistEntry
import com.auraplayer.app.data.model.Track
import com.auraplayer.app.data.repository.MusicRepository
import com.auraplayer.app.data.repository.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val entries: List<PlaylistEntry> = emptyList(),
    val libraryTracks: List<Track> = emptyList(),
    val statusMessage: String? = null
)

class PlaylistDetailViewModel(
    private val playlistId: Long,
    private val playlistRepository: PlaylistRepository,
    private val musicRepository: MusicRepository,
    private val backupManager: AuraBackupManager
) : ViewModel() {

    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PlaylistDetailUiState> = combine(
        playlistRepository.observePlaylistWithEntries(playlistId),
        musicRepository.observeAvailableTracks(),
        statusMessage
    ) { withEntries, availableTracks, msg ->
        PlaylistDetailUiState(
            playlist = withEntries?.playlist,
            entries = withEntries?.entries.orEmpty(),
            libraryTracks = availableTracks,
            statusMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = PlaylistDetailUiState()
    )

    fun addTrack(trackId: Long) {
        viewModelScope.launch {
            playlistRepository.addTrackToPlaylist(playlistId, trackId)
        }
    }

    fun removeEntry(entryId: Long) {
        viewModelScope.launch {
            playlistRepository.removeEntry(playlistId, entryId)
        }
    }

    fun saveEntryOrder(orderedEntryIds: List<Long>) {
        viewModelScope.launch {
            playlistRepository.reorderEntries(playlistId, orderedEntryIds)
        }
    }

    fun relocateTrack(trackId: Long, newUriString: String) {
        viewModelScope.launch {
            val ok = musicRepository.relocateTrack(trackId, newUriString)
            statusMessage.value = if (ok) "Track relinked" else "Unable to relink file"
        }
    }

    fun exportPlaylistToUri(uriString: String) {
        viewModelScope.launch {
            runCatching {
                val json = backupManager.exportPlaylistJson(playlistId)
                backupManager.writeBackupToUri(uriString, json)
            }.onSuccess { ok ->
                statusMessage.value = if (ok) "Playlist exported to JSON" else "Export failed"
            }.onFailure { err ->
                statusMessage.value = "Export failed: ${err.message ?: "Unknown error"}"
            }
        }
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }

    class Factory(
        private val playlistId: Long,
        private val playlistRepository: PlaylistRepository,
        private val musicRepository: MusicRepository,
        private val backupManager: AuraBackupManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlaylistDetailViewModel(
                playlistId,
                playlistRepository,
                musicRepository,
                backupManager
            ) as T
        }
    }
}
