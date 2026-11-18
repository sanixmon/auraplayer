package com.auraplayer.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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

    val backStackEntry by navController.currentBackStackEntryAsState()
    val isNowPlayingRoute = backStackEntry?.destination?.hasRoute(AuraRoute.NowPlaying::class) == true
    val useNavRail = widthSizeClass != WindowWidthSizeClass.Compact
    val isExpandedTwoPane = widthSizeClass == WindowWidthSizeClass.Expanded && !isNowPlayingRoute

    fun navigateTopLevel(route: AuraRoute) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!useNavRail && !isNowPlayingRoute) {
                Column(
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    if (playbackState.currentTrack != null) {
                        MiniPlayerBar(
                            playbackState = playbackState,
                            visualizerFrame = visualizerFrame,
                            visualizerConfig = visualizerConfig,
                            onTogglePlayPause = appContainer.playbackController::togglePlayPause,
                            onSkipNext = appContainer.playbackController::skipToNext,
                            onClick = { navController.navigate(AuraRoute.NowPlaying) }
                        )
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
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
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
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

                            composable<AuraRoute.NowPlaying> {
                                val vm: NowPlayingViewModel = viewModel(
                                    factory = NowPlayingViewModel.Factory(
                                        playbackController = appContainer.playbackController,
                                        visualizerBus = appContainer.visualizerBus,
                                        visualizerPreferences = appContainer.visualizerPreferences
                                    )
                                )
                                NowPlayingScreen(
                                    viewModel = vm,
                                    onBack = {
                                        if (!navController.popBackStack()) {
                                            navigateTopLevel(AuraRoute.Library)
                                        }
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

                    if (useNavRail && !isExpandedTwoPane && !isNowPlayingRoute && playbackState.currentTrack != null) {
                        MiniPlayerBar(
                            playbackState = playbackState,
                            visualizerFrame = visualizerFrame,
                            visualizerConfig = visualizerConfig,
                            onTogglePlayPause = appContainer.playbackController::togglePlayPause,
                            onSkipNext = appContainer.playbackController::skipToNext,
                            onClick = { navController.navigate(AuraRoute.NowPlaying) },
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(bottom = 8.dp)
                        )
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
                    onExpandFullScreen = {
                        navController.navigate(AuraRoute.NowPlaying)
                    },
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxHeight()
                )
            }
        }
    }
}
