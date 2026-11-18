package com.auraplayer.app.ui.screens.playlists

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auraplayer.app.data.model.PlaylistEntry
import com.auraplayer.app.data.model.Track
import com.auraplayer.app.ui.components.TrackListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistDetailViewModel,
    activeTrackId: Long?,
    onBack: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val localEntries = remember { mutableStateListOf<PlaylistEntry>() }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(160f) }
    var showAddTracksSheet by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportPlaylistToUri(uri.toString())
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

    LaunchedEffect(state.entries, draggedIndex) {
        if (draggedIndex == null) {
            localEntries.clear()
            localEntries.addAll(state.entries)
        }
    }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatusMessage()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.playlist?.name ?: "Playlist",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${localEntries.size} entries • Hold & drag to reorder",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val slug = (state.playlist?.name ?: "playlist")
                                .lowercase()
                                .replace(Regex("[^a-z0-9]+"), "_")
                            exportLauncher.launch("${slug}_aura_backup.json")
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.IosShare,
                            contentDescription = "Export playlist JSON"
                        )
                    }
                    IconButton(onClick = { showAddTracksSheet = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add tracks"
                        )
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
            if (localEntries.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onPlayTracks(localEntries.map { it.track }, 0)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play Playlist")
                    }
                    OutlinedButton(
                        onClick = { showAddTracksSheet = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Tracks")
                    }
                }
            }

            if (localEntries.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "This playlist is empty",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Add tracks from your offline library (repeats allowed), then hold and drag to reorder.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showAddTracksSheet = true }) {
                        Text("Add Tracks")
                    }
                }
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
                    itemsIndexed(
                        items = localEntries,
                        key = { _, entry -> entry.entryId }
                    ) { index, entry ->
                        val isDragging = draggedIndex == index
                        TrackListItem(
                            track = entry.track,
                            isActive = entry.track.id == activeTrackId,
                            onClick = {
                                onPlayTracks(localEntries.map { it.track }, index)
                            },
                            onRelocateUnavailable = {
                                trackPendingRelocate = entry.track
                                relocateLauncher.launch(arrayOf("audio/*"))
                            },
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .onSizeChanged { size ->
                                    if (size.height > 0) {
                                        itemHeightPx = size.height.toFloat()
                                    }
                                }
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffsetY else 0f
                                    scaleX = if (isDragging) 1.02f else 1f
                                    scaleY = if (isDragging) 1.02f else 1f
                                }
                                .pointerInput(entry.entryId) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            val startIdx = localEntries.indexOfFirst {
                                                it.entryId == entry.entryId
                                            }
                                            if (startIdx >= 0) {
                                                draggedIndex = startIdx
                                                dragOffsetY = 0f
                                            }
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            val currentIdx = draggedIndex
                                                ?: return@detectDragGesturesAfterLongPress
                                            dragOffsetY += dragAmount.y
                                            val threshold = itemHeightPx * 0.65f
                                            if (dragOffsetY > threshold && currentIdx < localEntries.lastIndex) {
                                                val item = localEntries.removeAt(currentIdx)
                                                localEntries.add(currentIdx + 1, item)
                                                draggedIndex = currentIdx + 1
                                                dragOffsetY -= itemHeightPx
                                            } else if (dragOffsetY < -threshold && currentIdx > 0) {
                                                val item = localEntries.removeAt(currentIdx)
                                                localEntries.add(currentIdx - 1, item)
                                                draggedIndex = currentIdx - 1
                                                dragOffsetY += itemHeightPx
                                            }
                                        },
                                        onDragEnd = {
                                            viewModel.saveEntryOrder(localEntries.map { it.entryId })
                                            draggedIndex = null
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggedIndex = null
                                            dragOffsetY = 0f
                                        }
                                    )
                                },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragHandle,
                                        contentDescription = "Drag to reorder",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    IconButton(onClick = { viewModel.removeEntry(entry.entryId) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove entry from playlist"
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddTracksSheet) {
        ModalBottomSheet(onDismissRequest = { showAddTracksSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Add Tracks to Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (state.libraryTracks.isEmpty()) {
                    Text(
                        text = "No available tracks in your Library yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        itemsIndexed(state.libraryTracks, key = { _, t -> t.id }) { _, track ->
                            TrackListItem(
                                track = track,
                                isActive = false,
                                onClick = { viewModel.addTrack(track.id) },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.addTrack(track.id) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "Add track"
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
