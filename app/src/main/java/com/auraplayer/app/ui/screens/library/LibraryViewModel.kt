package com.auraplayer.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.auraplayer.app.data.model.Playlist
import com.auraplayer.app.data.model.Track
import com.auraplayer.app.data.model.WatchedFolder
import com.auraplayer.app.data.repository.LibrarySortOrder
import com.auraplayer.app.data.repository.MusicRepository
import com.auraplayer.app.data.repository.PlaylistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val playlists: List<Playlist> = emptyList(),
    val watchedFolders: List<WatchedFolder> = emptyList(),
    val searchQuery: String = "",
    val sortOrder: LibrarySortOrder = LibrarySortOrder.TITLE,
    val isScanning: Boolean = false,
    val statusMessage: String? = null
)

class LibraryViewModel(
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(LibrarySortOrder.TITLE)
    private val isScanning = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedTracks: Flow<PagingData<Track>> = combine(searchQuery, sortOrder) { q, sort ->
        q to sort
    }.flatMapLatest { (query, sort) ->
        musicRepository.observePagedTracks(
            query = query,
            formatFilter = null,
            sortOrder = sort,
            includeUnavailable = false
        )
    }.cachedIn(viewModelScope)

    val uiState: StateFlow<LibraryUiState> = combine(
        playlistRepository.observePlaylists(),
        musicRepository.observeWatchedFolders(),
        searchQuery,
        sortOrder,
        combine(isScanning, statusMessage) { scanning, msg -> scanning to msg }
    ) { playlists, folders, query, sort, (scanning, message) ->
        LibraryUiState(
            playlists = playlists,
            watchedFolders = folders,
            searchQuery = query,
            sortOrder = sort,
            isScanning = scanning,
            statusMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = LibraryUiState()
    )

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onSortOrderSelected(order: LibrarySortOrder) {
        sortOrder.value = order
    }

    fun syncLibrary(silent: Boolean = true) {
        if (isScanning.value) return
        viewModelScope.launch {
            isScanning.value = true
            runCatching {
                musicRepository.syncDeviceLibrary()
            }.onSuccess { count ->
                if (!silent) {
                    statusMessage.value = "$count songs found"
                }
            }.onFailure { err ->
                statusMessage.value = "Scan failed: ${err.message ?: "Unknown error"}"
            }
            isScanning.value = false
        }
    }

    fun addWatchedFolder(treeUriString: String) {
        viewModelScope.launch {
            isScanning.value = true
            runCatching {
                musicRepository.addWatchedFolder(treeUriString)
            }.onSuccess { added ->
                if (!added) {
                    statusMessage.value = "Could not open folder"
                }
            }.onFailure { err ->
                statusMessage.value = "Folder error: ${err.message ?: "Unknown error"}"
            }
            isScanning.value = false
        }
    }

    fun removeWatchedFolder(treeUriString: String) {
        viewModelScope.launch {
            musicRepository.removeWatchedFolder(treeUriString)
        }
    }

    fun importExternalUris(uriStrings: List<String>) {
        if (uriStrings.isEmpty()) return
        viewModelScope.launch {
            isScanning.value = true
            runCatching {
                musicRepository.importExternalFiles(uriStrings)
            }.onSuccess { added ->
                statusMessage.value = if (added > 0) {
                    "Added $added song(s)"
                } else {
                    "Already in library"
                }
            }.onFailure { err ->
                statusMessage.value = "Import failed: ${err.message ?: "Unknown error"}"
            }
            isScanning.value = false
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            playlistRepository.addTrackToPlaylist(playlistId, trackId)
            statusMessage.value = "Added to playlist"
        }
    }

    fun playLibrarySelection(
        startTrackId: Long? = null,
        shuffle: Boolean = false,
        onReady: (List<Track>, Int) -> Unit
    ) {
        viewModelScope.launch {
            val playable = musicRepository.getPlayableTracks(
                query = searchQuery.value,
                formatFilter = null,
                sortOrder = sortOrder.value
            )
            if (playable.isEmpty()) return@launch
            if (shuffle) {
                onReady(playable.shuffled(), 0)
            } else {
                val startIdx = startTrackId?.let { id ->
                    playable.indexOfFirst { it.id == id }.coerceAtLeast(0)
                } ?: 0
                onReady(playable, startIdx)
            }
        }
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }

    class Factory(
        private val musicRepository: MusicRepository,
        private val playlistRepository: PlaylistRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(musicRepository, playlistRepository) as T
        }
    }
}
