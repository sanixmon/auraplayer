package com.auraplayer.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.auraplayer.app.data.model.VisualizerConfig
import com.auraplayer.app.di.AppContainer
import com.auraplayer.app.ui.components.MiniPlayerBar
import com.auraplayer.app.ui.screens.library.LibraryScreen
import com.auraplayer.app.ui.screens.library.LibraryViewModel
import com.auraplayer.app.ui.screens.nowplaying.NowPlayingScreen
import com.auraplayer.app.ui.screens.nowplaying.NowPlayingViewModel
import com.auraplayer.app.ui.screens.playlists.PlaylistDetailScreen
import com.auraplayer.app.ui.screens.playlists.PlaylistDetailViewModel
import com.auraplayer.app.ui.screens.playlists.PlaylistsScreen
import com.auraplayer.app.ui.screens.playlists.PlaylistsViewModel
import com.auraplayer.app.ui.screens.settings.SettingsScreen
import com.auraplayer.app.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class TopLevelDestination(
    val route: AuraRoute,
    val label: String,
    val icon: ImageVector
)

private val topLevelDestinations = listOf(
    TopLevelDestination(AuraRoute.Library, "Songs", Icons.Rounded.LibraryMusic),
    TopLevelDestination(AuraRoute.Playlists, "Playlists", Icons.AutoMirrored.Rounded.QueueMusic),
    TopLevelDestination(AuraRoute.Settings, "Settings", Icons.Rounded.Tune)
)

@Composable
fun AuraAppRoot(
    appContainer: AppContainer,
    widthSizeClass: WindowWidthSizeClass,
    navController: NavHostController = rememberNavController()
) {
    val playbackState by appContainer.playbackController.state.collectAsStateWithLifecycle()
    val visualizerFrame by appContainer.visualizerBus.frames.collectAsStateWithLifecycle()
    val visualizerConfig by appContainer.visualizerPreferences.configFlow
        .collectAsStateWithLifecycle(initialValue = VisualizerConfig())
    appContainer.visualizerBus.targetBandCount = visualizerConfig.density.bandCount

    val backStackEntry by navController.currentBackStackEntryAsState()
    val useNavRail = widthSizeClass != WindowWidthSizeClass.Compact
    val isExpandedTwoPane = widthSizeClass == WindowWidthSizeClass.Expanded

    val scope = rememberCoroutineScope()
    val expandAnim = remember { Animatable(0f) }
    val expandProgress = expandAnim.value

    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun expandPlayer() {
        scope.launch { expandAnim.animateTo(1f, springSpec) }
    }

    fun collapsePlayer() {
        scope.launch { expandAnim.animateTo(0f, springSpec) }
    }

    BackHandler(enabled = expandProgress > 0.1f) {
        collapsePlayer()
    }

    fun navigateTopLevel(route: AuraRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val totalHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val navBarBaseHeightDp = 80.dp
        val navBarHeightPx = with(density) { navBarBaseHeightDp.toPx() }
        val navBarInsetBottomPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        val miniBarHeightDp = 76.dp
        val miniBarHeightPx = with(density) { miniBarHeightDp.toPx() }

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useNavRail) {
                    // NavigationBar smoothly slides down & fades out as the player expands
                    val navSlidePx = expandProgress * (navBarHeightPx + navBarInsetBottomPx)
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .offset { IntOffset(0, navSlidePx.roundToInt()) }
                            .alpha((1f - expandProgress * 1.6f).coerceIn(0f, 1f))
                    ) {
                        topLevelDestinations.forEach { dest ->
                            val selected = backStackEntry?.destination?.hasRoute(dest.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.weight(1f))
                        topLevelDestinations.forEach { dest ->
                            val selected = backStackEntry?.destination?.hasRoute(dest.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(if (isExpandedTwoPane) 0.56f else 1f)
                        .fillMaxHeight()
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = AuraRoute.Library
                    ) {
                        composable<AuraRoute.Library> {
                            val vm: LibraryViewModel = viewModel(
                                factory = LibraryViewModel.Factory(
                                    musicRepository = appContainer.musicRepository,
                                    playlistRepository = appContainer.playlistRepository
                                )
                            )
                            LibraryScreen(
                                viewModel = vm,
                                activeTrackId = playbackState.currentTrack?.id,
                                onPlayTracks = { tracks, index ->
                                    appContainer.playbackController.playTracks(tracks, index)
                                },
                                onNavigateToPlaylists = {
                                    navigateTopLevel(AuraRoute.Playlists)
                                }
                            )
                        }

                        composable<AuraRoute.Playlists> {
                            val vm: PlaylistsViewModel = viewModel(
                                factory = PlaylistsViewModel.Factory(
                                    playlistRepository = appContainer.playlistRepository
                                )
                            )
                            PlaylistsScreen(
                                viewModel = vm,
                                onOpenPlaylist = { playlistId ->
                                    navController.navigate(AuraRoute.PlaylistDetail(playlistId))
                                }
                            )
                        }

                        composable<AuraRoute.PlaylistDetail> { entry ->
                            val route = entry.toRoute<AuraRoute.PlaylistDetail>()
                            val vm: PlaylistDetailViewModel = viewModel(
                                factory = PlaylistDetailViewModel.Factory(
                                    playlistId = route.playlistId,
                                    playlistRepository = appContainer.playlistRepository,
                                    musicRepository = appContainer.musicRepository
                                )
                            )
                            PlaylistDetailScreen(
                                viewModel = vm,
                                activeTrackId = playbackState.currentTrack?.id,
                                onBack = { navController.popBackStack() },
                                onPlayTracks = { tracks, index ->
                                    appContainer.playbackController.playTracks(tracks, index)
                                }
                            )
                        }

                        composable<AuraRoute.Settings> {
                            val vm: SettingsViewModel = viewModel(
                                factory = SettingsViewModel.Factory(
                                    preferences = appContainer.visualizerPreferences,
                                    visualizerBus = appContainer.visualizerBus,
                                    playbackController = appContainer.playbackController
                                )
                            )
                            SettingsScreen(viewModel = vm)
                        }
                    }
                }

                if (isExpandedTwoPane) {
                    VerticalDivider(modifier = Modifier.width(1.dp).fillMaxHeight())
                    val sidePlayerVm: NowPlayingViewModel = viewModel(
                        key = "tablet_docked_now_playing",
                        factory = NowPlayingViewModel.Factory(
                            playbackController = appContainer.playbackController,
                            visualizerBus = appContainer.visualizerBus,
                            visualizerPreferences = appContainer.visualizerPreferences
                        )
                    )
                    NowPlayingScreen(
                        viewModel = sidePlayerVm,
                        onBack = {},
                        isDockedSidePane = true,
                        onExpandFullScreen = { expandPlayer() },
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxHeight()
                    )
                }
            }
        }

        // Single Unified Morphing Player Overlay (MiniPlayerBar <-> Full-Screen NowPlaying)
        val showOverlay = playbackState.currentTrack != null && (!isExpandedTwoPane || expandProgress > 0.01f)
        if (showOverlay) {
            val collapsedBottomOffsetPx = if (useNavRail) {
                navBarInsetBottomPx + with(density) { 8.dp.toPx() }
            } else {
                navBarHeightPx + navBarInsetBottomPx
            }
            val collapsedTopPx = (totalHeightPx - miniBarHeightPx - collapsedBottomOffsetPx)
                .coerceAtLeast(0f)

            val currentTopPx = (collapsedTopPx * (1f - expandProgress)).roundToInt()
            val currentHeightDp = with(density) {
                (totalHeightPx - currentTopPx).toDp()
            }
            val cornerRadius = lerp(18.dp, 0.dp, expandProgress)
            val horizontalPad = lerp(if (useNavRail) 92.dp else 0.dp, 0.dp, expandProgress)

            val nowPlayingVm: NowPlayingViewModel = viewModel(
                key = "overlay_now_playing",
                factory = NowPlayingViewModel.Factory(
                    playbackController = appContainer.playbackController,
                    visualizerBus = appContainer.visualizerBus,
                    visualizerPreferences = appContainer.visualizerPreferences
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(currentHeightDp)
                    .offset { IntOffset(0, currentTopPx) }
                    .padding(horizontal = horizontalPad)
                    .clip(RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius))
            ) {
                // Full-screen NowPlaying fades in smoothly as container expands
                if (expandProgress > 0.05f) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(((expandProgress - 0.08f) / 0.92f).coerceIn(0f, 1f)),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        NowPlayingScreen(
                            viewModel = nowPlayingVm,
                            onBack = { collapsePlayer() },
                            isPlayerExpanded = expandProgress > 0.95f,
                            onDragCollapseDelta = { dy ->
                                scope.launch {
                                    val deltaProgress = -dy / collapsedTopPx.coerceAtLeast(1f)
                                    expandAnim.snapTo((expandAnim.value + deltaProgress).coerceIn(0f, 1f))
                                }
                            },
                            onDragCollapseEnd = { lastDy ->
                                scope.launch {
                                    if (lastDy > 14f || expandAnim.value < 0.72f) {
                                        expandAnim.animateTo(0f, springSpec)
                                    } else {
                                        expandAnim.animateTo(1f, springSpec)
                                    }
                                }
                            }
                        )
                    }
                }

                // MiniPlayerBar stays anchored at top of the morphing sheet and crossfades out on expand
                if (expandProgress < 0.45f) {
                    val miniAlpha = (1f - expandProgress * 3.2f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .alpha(miniAlpha)
                            .pointerInput(Unit) {
                                var lastDy = 0f
                                detectVerticalDragGestures(
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        lastDy = dragAmount
                                        scope.launch {
                                            val deltaProgress = -dragAmount / collapsedTopPx.coerceAtLeast(1f)
                                            expandAnim.snapTo(
                                                (expandAnim.value + deltaProgress).coerceIn(0f, 1f)
                                            )
                                        }
                                    },
                                    onDragEnd = {
                                        scope.launch {
                                            if (lastDy < -10f || expandAnim.value > 0.25f) {
                                                expandAnim.animateTo(1f, springSpec)
                                            } else {
                                                expandAnim.animateTo(0f, springSpec)
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch { expandAnim.animateTo(0f, springSpec) }
                                    }
                                )
                            }
                    ) {
                        MiniPlayerBar(
                            playbackState = playbackState,
                            visualizerFrame = visualizerFrame,
                            visualizerConfig = visualizerConfig,
                            onTogglePlayPause = appContainer.playbackController::togglePlayPause,
                            onSkipNext = appContainer.playbackController::skipToNext,
                            onClick = { expandPlayer() }
                        )
                    }
                }
            }
        }
    }
}
