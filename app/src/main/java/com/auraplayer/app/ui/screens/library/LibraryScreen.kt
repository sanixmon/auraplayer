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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
            viewModel.syncLibrary(silent = true)
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

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission && pagedTracks.itemCount == 0) {
            viewModel.syncLibrary(silent = true)
        }
    }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusMessage()
    }

    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var trackPendingPlaylist by remember { mutableStateOf<Track?>(null) }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Songs", style = MaterialTheme.typography.titleLarge)
                        if (pagedTracks.itemCount > 0) {
                            Text(
                                text = "${pagedTracks.itemCount} songs",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add music"
                        )
                    }
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = "Sort"
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
                                viewModel.syncLibrary(silent = false)
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
                                contentDescription = "Rescan"
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
                placeholder = { Text("Search songs, artists, albums") },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

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
                    hasFilter = state.searchQuery.isNotBlank(),
                    onRequestPermission = {
                        val perms = buildList {
                            add(audioPermission)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        permissionLauncher.launch(perms.toTypedArray())
                    },
                    onAddMusic = { showAddSheet = true },
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
                            onAddToPlaylist = { trackPendingPlaylist = track }
                        )
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Add Music", style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            showAddSheet = false
                            watchedFolderLauncher.launch(null)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Folder")
                    }
                    OutlinedButton(
                        onClick = {
                            showAddSheet = false
                            externalAudioLauncher.launch(arrayOf("audio/*"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.AudioFile, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Files")
                    }
                }

                if (state.watchedFolders.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Folders", style = MaterialTheme.typography.titleMedium)
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
                                        contentDescription = "Remove folder"
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
                    text = "Add to playlist",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(12.dp))
                if (state.playlists.isEmpty()) {
                    Button(
                        onClick = {
                            trackPendingPlaylist = null
                            onNavigateToPlaylists()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("New Playlist")
                    }
                } else {
                    state.playlists.forEach { playlist ->
                        ListItem(
                            headlineContent = { Text(playlist.name) },
                            supportingContent = { Text("${playlist.trackCount} songs") },
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
    onAddMusic: () -> Unit,
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
                hasFilter -> "No matching songs"
                !hasPermission -> "Find your music"
                else -> "No songs yet"
            },
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        if (!hasPermission && !hasFilter) {
            Button(onClick = onRequestPermission) {
                Text("Allow Music Access")
            }
            Spacer(Modifier.height(8.dp))
        }
        if (!hasFilter) {
            OutlinedButton(onClick = onAddMusic) {
                Text("Add Folder or Files")
            }
        }
    }
}
