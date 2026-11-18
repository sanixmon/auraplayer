package com.auraplayer.app.ui.screens.playlists

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auraplayer.app.data.model.PlaylistEntry
import com.auraplayer.app.data.model.Track
import com.auraplayer.app.ui.components.TrackListItem
import kotlinx.coroutines.launch

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
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val localEntries = remember { mutableStateListOf<PlaylistEntry>() }
    var draggedEntryId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var showAddTracksSheet by remember { mutableStateOf(false) }

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

    LaunchedEffect(state.entries, draggedEntryId) {
        if (draggedEntryId == null) {
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
                            text = "${localEntries.size} songs",
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
                    IconButton(onClick = { showAddTracksSheet = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add songs"
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
                        onClick = { onPlayTracks(localEntries.map { it.track }, 0) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                    OutlinedButton(
                        onClick = { showAddTracksSheet = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Songs")
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
                        text = "Empty playlist",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showAddTracksSheet = true }) {
                        Text("Add Songs")
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
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
                        val isDragging = draggedEntryId == entry.entryId
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
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffsetY else 0f
                                    scaleX = if (isDragging) 1.03f else 1f
                                    scaleY = if (isDragging) 1.03f else 1f
                                    shadowElevation = if (isDragging) 16f else 0f
                                },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragHandle,
                                        contentDescription = "Drag to reorder",
                                        tint = if (isDragging) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier
                                            .padding(horizontal = 6.dp, vertical = 8.dp)
                                            .pointerInput(entry.entryId) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        draggedEntryId = entry.entryId
                                                        dragOffsetY = 0f
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        if (draggedEntryId != entry.entryId) return@detectDragGestures
                                                        dragOffsetY += dragAmount.y

                                                        val visibleItems = listState.layoutInfo.visibleItemsInfo
                                                        val currentItemInfo = visibleItems.firstOrNull {
                                                            it.key == entry.entryId
                                                        } ?: return@detectDragGestures

                                                        val currentIdx = currentItemInfo.index
                                                        val currentCenterY = currentItemInfo.offset +
                                                            (currentItemInfo.size / 2f) +
                                                            dragOffsetY

                                                        // Swap with next item when dragged center crosses next item's midpoint
                                                        val nextItem = visibleItems.firstOrNull {
                                                            it.index == currentIdx + 1
                                                        }
                                                        val prevItem = visibleItems.firstOrNull {
                                                            it.index == currentIdx - 1
                                                        }

                                                        if (nextItem != null &&
                                                            currentIdx < localEntries.lastIndex &&
                                                            currentCenterY > nextItem.offset + nextItem.size / 2f
                                                        ) {
                                                            val moved = localEntries.removeAt(currentIdx)
                                                            localEntries.add(currentIdx + 1, moved)
                                                            dragOffsetY -= (nextItem.offset - currentItemInfo.offset)
                                                            haptic.performHapticFeedback(
                                                                HapticFeedbackType.TextHandleMove
                                                            )
                                                        } else if (prevItem != null &&
                                                            currentIdx > 0 &&
                                                            currentCenterY < prevItem.offset + prevItem.size / 2f
                                                        ) {
                                                            val moved = localEntries.removeAt(currentIdx)
                                                            localEntries.add(currentIdx - 1, moved)
                                                            dragOffsetY += (currentItemInfo.offset - prevItem.offset)
                                                            haptic.performHapticFeedback(
                                                                HapticFeedbackType.TextHandleMove
                                                            )
                                                        }

                                                        // Auto-scroll near viewport top/bottom edges
                                                        val viewportStart = listState.layoutInfo.viewportStartOffset
                                                        val viewportEnd = listState.layoutInfo.viewportEndOffset
                                                        val topEdge = viewportStart + 90f
                                                        val bottomEdge = viewportEnd - 90f
                                                        val itemTop = currentItemInfo.offset + dragOffsetY
                                                        val itemBottom = itemTop + currentItemInfo.size
                                                        if (itemBottom > bottomEdge) {
                                                            scope.launch {
                                                                listState.scrollBy(18f)
                                                            }
                                                        } else if (itemTop < topEdge) {
                                                            scope.launch {
                                                                listState.scrollBy(-18f)
                                                            }
                                                        }
                                                    },
                                                    onDragEnd = {
                                                        viewModel.saveEntryOrder(
                                                            localEntries.map { it.entryId }
                                                        )
                                                        draggedEntryId = null
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragCancel = {
                                                        draggedEntryId = null
                                                        dragOffsetY = 0f
                                                    }
                                                )
                                            }
                                    )
                                    IconButton(onClick = { viewModel.removeEntry(entry.entryId) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove"
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
                    text = "Add Songs",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (state.libraryTracks.isEmpty()) {
                    Text(
                        text = "No songs in library yet.",
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
                                            contentDescription = "Add"
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
