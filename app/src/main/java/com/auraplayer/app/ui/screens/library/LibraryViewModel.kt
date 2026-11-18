package com.auraplayer.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.auraplayer.app.data.model.AudioFormat
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
    val unavailableCount: Int = 0,
    val searchQuery: String = "",
    val selectedFormat: AudioFormat? = null,
    val sortOrder: LibrarySortOrder = LibrarySortOrder.TITLE,
    val showUnavailable: Boolean = false,
    val isScanning: Boolean = false,
    val statusMessage: String? = null
)

private data class LibraryQueryParams(
    val query: String,
    val format: AudioFormat?,
    val sortOrder: LibrarySortOrder,
    val includeUnavailable: Boolean
)

class LibraryViewModel(
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedFormat = MutableStateFlow<AudioFormat?>(null)
    private val sortOrder = MutableStateFlow(LibrarySortOrder.TITLE)
    private val showUnavailable = MutableStateFlow(false)
    private val isScanning = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)

    private val queryParams = combine(
        searchQuery,
        selectedFormat,
        sortOrder,
        showUnavailable
    ) { query, format, sort, includeUnavailable ->
        LibraryQueryParams(
            query = query,
            format = format,
            sortOrder = sort,
            includeUnavailable = includeUnavailable
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val pagedTracks: Flow<PagingData<Track>> = queryParams
        .flatMapLatest { params ->
            musicRepository.observePagedTracks(
                query = params.query,
                formatFilter = params.format,
                sortOrder = params.sortOrder,
                includeUnavailable = params.includeUnavailable
            )
        }
        .cachedIn(viewModelScope)

    val uiState: StateFlow<LibraryUiState> = combine(
        playlistRepository.observePlaylists(),
        musicRepository.observeWatchedFolders(),
        musicRepository.observeUnavailableCount(),
        queryParams,
        combine(isScanning, statusMessage) { scanning, msg -> scanning to msg }
    ) { playlists, folders, unavailableCount, params, (scanning, message) ->
        LibraryUiState(
            playlists = playlists,
            watchedFolders = folders,
            unavailableCount = unavailableCount,
            searchQuery = params.query,
            selectedFormat = params.format,
            sortOrder = params.sortOrder,
            showUnavailable = params.includeUnavailable,
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

    fun onFormatFilterSelected(format: AudioFormat?) {
        selectedFormat.value = if (selectedFormat.value == format) null else format
    }

    fun onSortOrderSelected(order: LibrarySortOrder) {
        sortOrder.value = order
    }

    fun toggleShowUnavailable() {
        showUnavailable.value = !showUnavailable.value
    }

    fun syncLibrary() {
        if (isScanning.value) return
        viewModelScope.launch {
            isScanning.value = true
            runCatching {
                musicRepository.syncDeviceLibrary()
            }.onSuccess { count ->
                statusMessage.value = "Library scan complete: $count available tracks"
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
                statusMessage.value = if (added) {
                    "Added watched folder and scanned audio"
                } else {
                    "Could not read selected folder"
                }
            }.onFailure { err ->
                statusMessage.value = "Folder grant failed: ${err.message ?: "Unknown error"}"
            }
            isScanning.value = false
        }
    }

    fun removeWatchedFolder(treeUriString: String) {
        viewModelScope.launch {
            musicRepository.removeWatchedFolder(treeUriString)
            statusMessage.value = "Removed watched folder"
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
                    "Imported $added track(s) from storage"
                } else {
                    "Selected track(s) already in library"
                }
            }.onFailure { err ->
                statusMessage.value = "Import failed: ${err.message ?: "Unknown error"}"
            }
            isScanning.value = false
        }
    }

    fun relocateTrack(trackId: Long, newUriString: String) {
        viewModelScope.launch {
            val ok = musicRepository.relocateTrack(trackId, newUriString)
            statusMessage.value = if (ok) "Track relinked" else "Failed to relink track"
        }
    }

    fun purgeUnavailableTracks() {
        viewModelScope.launch {
            val removed = musicRepository.purgeUnavailableTracks()
            showUnavailable.value = false
            statusMessage.value = "Cleaned up $removed unavailable track(s)"
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
                formatFilter = selectedFormat.value,
                sortOrder = sortOrder.value
            )
            if (playable.isEmpty()) {
                statusMessage.value = "No available tracks to play"
                return@launch
            }
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
