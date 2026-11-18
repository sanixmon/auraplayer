package com.auraplayer.app.ui.screens.nowplaying

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auraplayer.app.data.model.CoverArtStyle
import com.auraplayer.app.data.model.NowPlayingVisualMode
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerStyle
import com.auraplayer.app.playback.RepeatMode
import com.auraplayer.app.ui.components.CoverArtView
import com.auraplayer.app.ui.components.TrackListItem
import com.auraplayer.app.ui.components.VisualizerCanvas
import com.auraplayer.app.ui.util.formatDurationMs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isDockedSidePane: Boolean = false,
    onExpandFullScreen: (() -> Unit)? = null
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val visualizerFrame by viewModel.visualizerFrame.collectAsStateWithLifecycle()
    val visualizerConfig by viewModel.visualizerConfig.collectAsStateWithLifecycle()

    var showQueueSheet by remember { mutableStateOf(false) }
    var showVisualizerSheet by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }

    val track = playbackState.currentTrack
    val durationMs = playbackState.durationMs.coerceAtLeast(1L)
    val displayedPositionMs = if (isScrubbing) {
        scrubPositionMs.toLong()
    } else {
        playbackState.positionMs.coerceIn(0L, durationMs)
    }

    val stageHeight = if (isDockedSidePane) 260.dp else 340.dp
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                title = {
                    Text(
                        text = if (isDockedSidePane) "Playing" else "",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    if (!isDockedSidePane) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Close"
                            )
                        }
                    }
                },
                actions = {
                    if (isDockedSidePane && onExpandFullScreen != null) {
                        IconButton(onClick = onExpandFullScreen) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInFull,
                                contentDescription = "Expand"
                            )
                        }
                    }
                    IconButton(onClick = { showVisualizerSheet = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = "Look & visualizer"
                        )
                    }
                    IconButton(onClick = { showQueueSheet = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = "Queue"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.14f),
                            secondary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Stage: Avee/vizz.fm Visualizer in background + Large Cover Art in foreground
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(stageHeight),
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                tonalElevation = 2.dp
            ) {
                AnimatedContent(
                    targetState = visualizerConfig.visualMode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "now_playing_stage_mode"
                ) { mode ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (mode == NowPlayingVisualMode.COVER_AND_VISUALIZER ||
                            mode == NowPlayingVisualMode.VISUALIZER_ONLY
                        ) {
                            VisualizerCanvas(
                                frame = visualizerFrame,
                                config = visualizerConfig,
                                isPlaying = playbackState.isPlaying,
                                demoWhenIdle = false,
                                centerRingRadiusRatio = if (mode == NowPlayingVisualMode.COVER_AND_VISUALIZER) {
                                    0.24f
                                } else {
                                    0.20f
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp)
                            )
                        }

                        if (mode == NowPlayingVisualMode.COVER_AND_VISUALIZER ||
                            mode == NowPlayingVisualMode.COVER_ONLY
                        ) {
                            val coverSize = when {
                                mode == NowPlayingVisualMode.COVER_ONLY && isDockedSidePane -> 185.dp
                                mode == NowPlayingVisualMode.COVER_ONLY -> 235.dp
                                isDockedSidePane -> 125.dp
                                else -> 162.dp
                            }
                            CoverArtView(
                                track = track,
                                style = visualizerConfig.coverArtStyle,
                                isPlaying = playbackState.isPlaying,
                                bassEnergy = visualizerFrame.bassEnergy,
                                size = coverSize
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Mode Switcher: Art + Vibe | Cover | Visualizer
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                NowPlayingVisualMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = visualizerConfig.visualMode == mode,
                        onClick = { viewModel.selectVisualMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = NowPlayingVisualMode.entries.size
                        ),
                        label = { Text(mode.displayName, maxLines = 1) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = track?.title ?: "Ready to Play",
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (track != null) "${track.artist} • ${track.album}" else "Pick a song to start",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(14.dp))

            Slider(
                value = displayedPositionMs.toFloat(),
                onValueChange = { value ->
                    isScrubbing = true
                    scrubPositionMs = value
                },
                onValueChangeFinished = {
                    viewModel.seekTo(scrubPositionMs.toLong())
                    isScrubbing = false
                },
                valueRange = 0f..durationMs.toFloat(),
                enabled = track != null,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDurationMs(displayedPositionMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDurationMs(playbackState.durationMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = viewModel::toggleShuffle) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.shuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                IconButton(
                    onClick = viewModel::skipPrevious,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(34.dp)
                    )
                }

                FilledIconButton(
                    onClick = viewModel::togglePlayPause,
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) {
                            Icons.Rounded.Pause
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = viewModel::skipNext,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(onClick = viewModel::cycleRepeatMode) {
                    Icon(
                        imageVector = if (playbackState.repeatMode == RepeatMode.ONE) {
                            Icons.Rounded.RepeatOne
                        } else {
                            Icons.Rounded.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (playbackState.repeatMode != RepeatMode.OFF) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showVisualizerSheet) {
        ModalBottomSheet(onDismissRequest = { showVisualizerSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Look & Visualizer", style = MaterialTheme.typography.titleLarge)

                Text("Cover Art", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CoverArtStyle.entries.forEach { style ->
                        FilterChip(
                            selected = visualizerConfig.coverArtStyle == style,
                            onClick = { viewModel.selectCoverArtStyle(style) },
                            label = { Text(style.displayName) }
                        )
                    }
                }

                Text("Visualizer", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VisualizerStyle.entries.forEach { style ->
                        FilterChip(
                            selected = visualizerConfig.style == style,
                            onClick = { viewModel.selectVisualizerStyle(style) },
                            label = { Text(style.displayName) }
                        )
                    }
                }

                Text("Colors", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VisualizerColorScheme.entries.forEach { scheme ->
                        FilterChip(
                            selected = visualizerConfig.colorScheme == scheme,
                            onClick = { viewModel.selectVisualizerColorScheme(scheme) },
                            label = { Text(scheme.displayName) }
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    if (showQueueSheet) {
        ModalBottomSheet(onDismissRequest = { showQueueSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (playbackState.queue.isEmpty()) {
                    Text(
                        text = "Queue is empty",
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
                        itemsIndexed(playbackState.queue, key = { idx, t -> "${t.id}_$idx" }) { index, item ->
                            TrackListItem(
                                track = item,
                                isActive = index == playbackState.currentIndex,
                                onClick = {
                                    viewModel.skipToQueueIndex(index)
                                    showQueueSheet = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
