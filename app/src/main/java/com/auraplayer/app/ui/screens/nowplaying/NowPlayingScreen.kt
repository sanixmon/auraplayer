package com.auraplayer.app.ui.screens.nowplaying

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auraplayer.app.data.model.CoverArtStyle
import com.auraplayer.app.data.model.NowPlayingVisualMode
import com.auraplayer.app.data.model.VisualizerColorScheme
import com.auraplayer.app.data.model.VisualizerFillMode
import com.auraplayer.app.data.model.VisualizerStyle
import com.auraplayer.app.playback.RepeatMode
import com.auraplayer.app.ui.components.CoverArtView
import com.auraplayer.app.ui.components.TrackListItem
import com.auraplayer.app.ui.components.VisualizerCanvas
import com.auraplayer.app.ui.util.formatDurationMs
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NowPlayingScreen(
    viewModel: NowPlayingViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isDockedSidePane: Boolean = false,
    isPlayerExpanded: Boolean = true,
    onExpandFullScreen: (() -> Unit)? = null,
    onDragCollapseDelta: ((Float) -> Unit)? = null,
    onDragCollapseEnd: ((Float) -> Unit)? = null
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val visualizerFrame by viewModel.visualizerFrame.collectAsStateWithLifecycle()
    val visualizerConfig by viewModel.visualizerConfig.collectAsStateWithLifecycle()

    var showQueueSheet by remember { mutableStateOf(false) }
    var showVisualizerSheet by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPositionMs by remember { mutableFloatStateOf(0f) }

    var isFullscreenIdle by remember { mutableStateOf(false) }
    var interactionNonce by remember { mutableIntStateOf(0) }

    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var stageCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var rawStageCenterInRoot by remember { mutableStateOf<Offset?>(null) }
    var stageCenterInitialized by remember { mutableStateOf(false) }
    val stageCenterAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    fun updateStageCenter() {
        val r = rootCoords
        val s = stageCoords
        if (r != null && s != null && r.isAttached && s.isAttached) {
            rawStageCenterInRoot = r.localPositionOf(
                s,
                Offset(s.size.width / 2f, s.size.height / 2f)
            )
        }
    }

    LaunchedEffect(rawStageCenterInRoot) {
        val target = rawStageCenterInRoot ?: return@LaunchedEffect
        if (!stageCenterInitialized) {
            stageCenterInitialized = true
            stageCenterAnim.snapTo(target)
        } else {
            stageCenterAnim.animateTo(target, tween(380))
        }
    }

    val canAutoFullscreen = !isDockedSidePane &&
        isPlayerExpanded &&
        playbackState.isPlaying &&
        !showQueueSheet &&
        !showVisualizerSheet &&
        !isScrubbing

    LaunchedEffect(canAutoFullscreen) {
        if (!canAutoFullscreen) {
            isFullscreenIdle = false
        }
    }

    LaunchedEffect(canAutoFullscreen, interactionNonce) {
        if (canAutoFullscreen && !isFullscreenIdle) {
            delay(5_000L)
            isFullscreenIdle = true
        }
    }

    BackHandler(enabled = !isDockedSidePane && isPlayerExpanded && isFullscreenIdle) {
        isFullscreenIdle = false
        interactionNonce++
    }

    val view = LocalView.current
    DisposableEffect(view, isFullscreenIdle, isPlayerExpanded, playbackState.isPlaying, isDockedSidePane) {
        val shouldGoFullscreen = !isDockedSidePane && isPlayerExpanded && isFullscreenIdle
        view.keepScreenOn = shouldGoFullscreen && playbackState.isPlaying
        val window = view.context.findActivity()?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (shouldGoFullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            view.keepScreenOn = false
            if (window != null) {
                WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val track = playbackState.currentTrack
    val durationMs = playbackState.durationMs.coerceAtLeast(1L)
    val displayedPositionMs = if (isScrubbing) {
        scrubPositionMs.toLong()
    } else {
        playbackState.positionMs.coerceIn(0L, durationMs)
    }
    val progressFraction = (displayedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val bgColor = MaterialTheme.colorScheme.background

    val swipeDownModifier = if (!isDockedSidePane && onDragCollapseDelta != null && onDragCollapseEnd != null) {
        Modifier.pointerInput(Unit) {
            var lastVelocity = 0f
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    lastVelocity = dragAmount
                    onDragCollapseDelta(dragAmount)
                },
                onDragEnd = { onDragCollapseEnd(lastVelocity) },
                onDragCancel = { onDragCollapseEnd(0f) }
            )
        }
    } else {
        Modifier
    }

    val baseCoverSize = when {
        visualizerConfig.visualMode == NowPlayingVisualMode.COVER_ONLY && isDockedSidePane -> 200.dp
        visualizerConfig.visualMode == NowPlayingVisualMode.COVER_ONLY -> 264.dp
        isDockedSidePane -> 136.dp
        else -> 180.dp
    }
    val animatedCoverSize by animateDpAsState(
        targetValue = if (isFullscreenIdle) baseCoverSize * 1.12f else baseCoverSize,
        animationSpec = tween(420),
        label = "fullscreen_cover_scale"
    )
    val scrimMidAlpha by animateFloatAsState(
        targetValue = if (isFullscreenIdle) 0.35f else 0.80f,
        animationSpec = tween(420),
        label = "scrim_mid_alpha"
    )
    val scrimBottomAlpha by animateFloatAsState(
        targetValue = if (isFullscreenIdle) 0.72f else 1f,
        animationSpec = tween(420),
        label = "scrim_bottom_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                rootCoords = coords
                updateStageCenter()
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed }) {
                            interactionNonce++
                        }
                    }
                }
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.16f),
                        secondary.copy(alpha = 0.08f),
                        bgColor
                    )
                )
            )
    ) {
        // Full-bleed unclipped background Visualizer aligned pixel-exact with stage center
        if (visualizerConfig.visualMode == NowPlayingVisualMode.COVER_AND_VISUALIZER ||
            visualizerConfig.visualMode == NowPlayingVisualMode.VISUALIZER_ONLY
        ) {
            VisualizerCanvas(
                frame = visualizerFrame,
                config = visualizerConfig,
                isPlaying = playbackState.isPlaying,
                demoWhenIdle = false,
                centerRingRadiusRatio = if (visualizerConfig.visualMode == NowPlayingVisualMode.COVER_AND_VISUALIZER) {
                    0.26f
                } else {
                    0.22f
                },
                centerRingRadiusDp = if (visualizerConfig.visualMode == NowPlayingVisualMode.COVER_AND_VISUALIZER) {
                    animatedCoverSize / 2f + 8.dp
                } else {
                    null
                },
                centerOverride = if (stageCenterInitialized) stageCenterAnim.value else null,
                visualizerBus = viewModel.visualizerBus,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Bottom readability scrim (softens smoothly in fullscreen mode)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFullscreenIdle) 190.dp else 310.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            bgColor.copy(alpha = scrimMidAlpha),
                            bgColor.copy(alpha = scrimBottomAlpha)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (isDockedSidePane) {
                        Modifier
                    } else {
                        Modifier
                            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                    }
                )
        ) {
            // Top bar hidden smoothly during 5s idle fullscreen
            AnimatedVisibility(
                visible = !isFullscreenIdle,
                enter = fadeIn(tween(340)) + expandVertically(tween(340)),
                exit = fadeOut(tween(340)) + shrinkVertically(tween(340))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(swipeDownModifier),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (!isDockedSidePane) {
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isDockedSidePane) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Close"
                                )
                            }
                        } else {
                            Text(
                                text = "Playing",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                    contentDescription = "Look"
                                )
                            }
                            IconButton(onClick = { showQueueSheet = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = "Queue"
                                )
                            }
                        }
                    }
                }
            }

            // Center interactive stage: tap cycles Cover -> Art + Visualizer -> Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onGloballyPositioned { coords ->
                        stageCoords = coords
                        updateStageCenter()
                    }
                    .then(swipeDownModifier)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (isFullscreenIdle) {
                                isFullscreenIdle = false
                                interactionNonce++
                            } else {
                                interactionNonce++
                                viewModel.cycleVisualMode()
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = visualizerConfig.visualMode,
                    contentAlignment = Alignment.Center,
                    transitionSpec = {
                        (fadeIn(tween(240)) + scaleIn(initialScale = 0.94f, animationSpec = tween(240)))
                            .togetherWith(fadeOut(tween(180)))
                    },
                    label = "cover_visualizer_tap_mode"
                ) { mode ->
                    if (mode == NowPlayingVisualMode.COVER_ONLY ||
                        mode == NowPlayingVisualMode.COVER_AND_VISUALIZER
                    ) {
                        CoverArtView(
                            track = track,
                            style = visualizerConfig.coverArtStyle,
                            isPlaying = playbackState.isPlaying,
                            bassEnergy = visualizerFrame.bassEnergy,
                            size = animatedCoverSize
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }

            // Bottom Track Info + Fullscreen Thin Hairline Progress OR Interactive Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 24.dp,
                        vertical = if (isFullscreenIdle) 16.dp else 12.dp
                    )
            ) {
                Text(
                    text = track?.title ?: "Ready to Play",
                    style = if (isFullscreenIdle) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.headlineSmall
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (track != null) "${track.artist} • ${track.album}" else "Pick a song to start",
                    style = if (isFullscreenIdle) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Hairline progress indicator shown during fullscreen idle
                AnimatedVisibility(
                    visible = isFullscreenIdle,
                    enter = fadeIn(tween(320)) + expandVertically(tween(320)),
                    exit = fadeOut(tween(220)) + shrinkVertically(tween(220))
                ) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(progressFraction)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(accent, secondary)
                                        )
                                    )
                            )
                        }
                    }
                }

                // Full interactive scrubber + transport controls when HUD is awake
                AnimatedVisibility(
                    visible = !isFullscreenIdle,
                    enter = fadeIn(tween(340)) + expandVertically(tween(340)),
                    exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
                ) {
                    Column {
                        Spacer(Modifier.height(12.dp))

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

                        Spacer(Modifier.height(14.dp))

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
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // In fullscreen idle mode, tap anywhere wakes HUD cleanly without hitting hidden controls,
        // while vertical drag still collapses cleanly to the MiniPlayer.
        if (isFullscreenIdle && !isDockedSidePane) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(swipeDownModifier)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                isFullscreenIdle = false
                                interactionNonce++
                            }
                        )
                    }
            )
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
                Text("Look", style = MaterialTheme.typography.titleLarge)

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

                Text("Fill", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VisualizerFillMode.entries.forEach { fill ->
                        FilterChip(
                            selected = visualizerConfig.fillMode == fill,
                            onClick = { viewModel.selectFillMode(fill) },
                            label = { Text(fill.displayName) }
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
