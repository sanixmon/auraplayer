package com.auraplayer.app.ui.screens.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.auraplayer.app.data.model.AudioFormat
import com.auraplayer.app.data.model.Track
import com.auraplayer.app.data.repository.LibrarySortOrder
import com.auraplayer.app.ui.components.TrackListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    activeTrackId: Long?,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onNavigateToPlaylists: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val pagedTracks = viewModel.pagedTracks.collectAsLazyPagingItems()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasAudioPermission = grants[audioPermission] == true
        if (hasAudioPermission) {
            viewModel.syncLibrary()
        }
    }

    val externalAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importExternalUris(uris.map { it.toString() })
        }
    }

    val watchedFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.addWatchedFolder(uri.toString())
        }
    }

    var trackPendingRelocate by remember { mutableStateOf<Track?>(null) }
    val relocateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val target = trackPendingRelocate
        if (uri != null && target != null) {
            viewModel.relocateTrack(target.id, uri.toString())
        }
        trackPendingRelocate = null
    }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission && pagedTracks.itemCount == 0) {
            viewModel.syncLibrary()
        }
    }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusMessage()
    }

    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showExternalSourcesSheet by remember { mutableStateOf(false) }
    var trackPendingPlaylist by remember { mutableStateOf<Track?>(null) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Aura Library", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "${pagedTracks.itemCount} tracks • ${state.watchedFolders.size} watched folder(s)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showExternalSourcesSheet = true }) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = "External storage & watched folders"
                        )
                    }
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = "Sort library"
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            LibrarySortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = if (order == state.sortOrder) {
                                                "• ${order.displayName}"
                                            } else {
                                                order.displayName
                                            }
                                        )
                                    },
                                    onClick = {
                                        viewModel.onSortOrderSelected(order)
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            if (hasAudioPermission) {
                                viewModel.syncLibrary()
                            } else {
                                val perms = buildList {
                                    add(audioPermission)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                permissionLauncher.launch(perms.toTypedArray())
                            }
                        }
                    ) {
                        if (state.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Run Library Scan"
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text("Search tracks, artists, or albums") },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.selectedFormat == null,
                        onClick = { viewModel.onFormatFilterSelected(null) },
                        label = { Text("All (${AudioFormat.supportedFormats.size})") }
                    )
                }
                if (state.unavailableCount > 0) {
                    item {
                        FilterChip(
                            selected = state.showUnavailable,
                            onClick = viewModel::toggleShowUnavailable,
                            label = { Text("Unavailable (${state.unavailableCount})") }
                        )
                    }
                }
                items(AudioFormat.supportedFormats, key = { it.name }) { format ->
                    FilterChip(
                        selected = state.selectedFormat == format,
                        onClick = { viewModel.onFormatFilterSelected(format) },
                        label = { Text(format.displayName) }
                    )
                }
            }

            if (state.showUnavailable && state.unavailableCount > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${state.unavailableCount} missing track(s)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::purgeUnavailableTracks) {
                            Text("Purge All")
                        }
                    }
                }
            }

            if (pagedTracks.itemCount > 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.playLibrarySelection(
                                startTrackId = null,
                                shuffle = false,
                                onReady = onPlayTracks
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play All")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.playLibrarySelection(
                                startTrackId = null,
                                shuffle = true,
                                onReady = onPlayTracks
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
            }

            if (pagedTracks.itemCount == 0) {
                EmptyLibraryState(
                    hasPermission = hasAudioPermission,
                    hasFilter = state.searchQuery.isNotBlank() ||
                        state.selectedFormat != null ||
                        state.showUnavailable,
                    onRequestPermission = {
                        val perms = buildList {
                            add(audioPermission)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        permissionLauncher.launch(perms.toTypedArray())
                    },
                    onImportSources = { showExternalSourcesSheet = true },
                    onRescan = viewModel::syncLibrary,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        count = pagedTracks.itemCount,
                        key = pagedTracks.itemKey { it.id }
                    ) { index ->
                        val track = pagedTracks[index] ?: return@items
                        TrackListItem(
                            track = track,
                            isActive = track.id == activeTrackId,
                            onClick = {
                                viewModel.playLibrarySelection(
                                    startTrackId = track.id,
                                    shuffle = false,
                                    onReady = onPlayTracks
                                )
                            },
                            onAddToPlaylist = { trackPendingPlaylist = track },
                            onRelocateUnavailable = {
                                trackPendingRelocate = track
                                relocateLauncher.launch(arrayOf("audio/*"))
                            }
                        )
                    }
                }
            }
        }
    }

    if (showExternalSourcesSheet) {
        ModalBottomSheet(onDismissRequest = { showExternalSourcesSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("External Storage & Watched Folders", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Grant folders for automatic Library Scans or import individual audio files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            showExternalSourcesSheet = false
                            watchedFolderLauncher.launch(null)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Watch Folder")
                    }
                    OutlinedButton(
                        onClick = {
                            showExternalSourcesSheet = false
                            externalAudioLauncher.launch(arrayOf("audio/*"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.AudioFile, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Pick Files")
                    }
                }

                if (state.watchedFolders.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Watched Folders", style = MaterialTheme.typography.titleMedium)
                    state.watchedFolders.forEach { folder ->
                        ListItem(
                            headlineContent = { Text(folder.displayName) },
                            leadingContent = {
                                Icon(Icons.Rounded.Folder, contentDescription = null)
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = { viewModel.removeWatchedFolder(folder.uri) }
                                ) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        contentDescription = "Remove watched folder"
                                    )
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    trackPendingPlaylist?.let { selectedTrack ->
        ModalBottomSheet(onDismissRequest = { trackPendingPlaylist = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Add \"${selectedTrack.title}\" to playlist",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(12.dp))
                if (state.playlists.isEmpty()) {
                    Text(
                        text = "You don't have any playlists yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            trackPendingPlaylist = null
                            onNavigateToPlaylists()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create a Playlist")
                    }
                } else {
                    state.playlists.forEach { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            supportingContent = { Text("${playlist.trackCount} tracks") },
                            leadingContent = {
                                Icon(Icons.Rounded.AddCircleOutline, contentDescription = null)
                            },
                            trailingContent = {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.addTrackToPlaylist(playlist.id, selectedTrack.id)
                                        trackPendingPlaylist = null
                                    }
                                ) {
                                    Text("Add")
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    hasPermission: Boolean,
    hasFilter: Boolean,
    onRequestPermission: () -> Unit,
    onImportSources: () -> Unit,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.LibraryMusic,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = when {
                hasFilter -> "No matching tracks"
                !hasPermission -> "Access your offline music"
                else -> "Your offline library is empty"
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                hasFilter -> "Try clearing your search or format filter."
                !hasPermission -> "Grant audio access to discover 20+ formats (MP3, FLAC, WAV, ALAC, WMA, APE, OPUS, OGG, and more) stored locally."
                else -> "Run a Library Scan or watch an external folder / SD card."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        if (!hasPermission && !hasFilter) {
            Button(onClick = onRequestPermission) {
                Text("Grant Audio Permission")
            }
            Spacer(Modifier.height(10.dp))
        } else if (!hasFilter) {
            Button(onClick = onRescan) {
                Text("Run Library Scan")
            }
            Spacer(Modifier.height(10.dp))
        }
        OutlinedButton(onClick = onImportSources) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Watch Folder / Import Files")
        }
    }
}
