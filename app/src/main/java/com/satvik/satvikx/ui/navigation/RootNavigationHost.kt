package com.satvik.satvikx.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.ui.components.AddToPlaylistDialog
import com.satvik.satvikx.ui.components.BottomMiniPlayer
import com.satvik.satvikx.ui.components.FullPlayerSheet
import com.satvik.satvikx.ui.components.UpdateDialog
import com.satvik.satvikx.ui.screens.DownloadsScreen
import com.satvik.satvikx.ui.screens.HomeScreen
import com.satvik.satvikx.ui.screens.PlaylistsScreen
import com.satvik.satvikx.ui.screens.RecentsScreen
import com.satvik.satvikx.ui.screens.SearchScreen
import com.satvik.satvikx.ui.theme.BackgroundDark
import com.satvik.satvikx.ui.theme.SurfaceDark
import com.satvik.satvikx.ui.viewmodel.LibraryViewModel
import com.satvik.satvikx.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootNavigationHost(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val updateState by playerViewModel.updateState.collectAsStateWithLifecycle()
    val likedTrackIds by libraryViewModel.likedTrackIds.collectAsStateWithLifecycle()

    var isFullPlayerExpanded by remember { mutableStateOf(false) }
    var trackForPlaylistDialog by remember { mutableStateOf<TrackEntity?>(null) }
    val fullPlayerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // Persistent Floating MiniPlayer docked above NavigationBar
                AnimatedVisibility(
                    visible = playbackState.currentTrack != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    BottomMiniPlayer(
                        playbackState = playbackState,
                        onPlayPause = { playerViewModel.playPause() },
                        onSkipNext = { playerViewModel.skipToNext() },
                        onExpand = { isFullPlayerExpanded = true }
                    )
                }

                // Modern Navigation Bar
                NavigationBar(
                    containerColor = SurfaceDark,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Screen.bottomNavItems.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) }
                        )
                    }
                }
            }
        },
        containerColor = BackgroundDark
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Home.route) {
                    HomeScreen()
                }
                composable(Screen.Search.route) {
                    SearchScreen()
                }
                composable(Screen.Downloads.route) {
                    DownloadsScreen()
                }
                composable(Screen.Recents.route) {
                    RecentsScreen()
                }
                composable(Screen.Playlists.route) {
                    PlaylistsScreen()
                }
            }
        }
    }

    // Modal Full Player Sheet
    if (isFullPlayerExpanded && playbackState.currentTrack != null) {
        val currentTrack = playbackState.currentTrack
        val isCurrentTrackLiked = currentTrack?.let { it.id in likedTrackIds } ?: false

        FullPlayerSheet(
            playbackState = playbackState,
            sheetState = fullPlayerSheetState,
            onDismiss = { isFullPlayerExpanded = false },
            onPlayPause = { playerViewModel.playPause() },
            onSeekTo = { playerViewModel.seekTo(it) },
            onSkipNext = { playerViewModel.skipToNext() },
            onSkipPrevious = { playerViewModel.skipToPrevious() },
            onToggleShuffle = { playerViewModel.toggleShuffle() },
            onToggleRepeat = { playerViewModel.toggleRepeat() },
            onDownload = { playerViewModel.downloadCurrentTrack() },
            onAddToPlaylist = {
                trackForPlaylistDialog = playbackState.currentTrack
            },
            onPlayTrackAtIndex = { playerViewModel.playTrackAtIndex(it) },
            onRemoveFromQueue = { playerViewModel.removeFromQueue(it) },
            onStartSleepTimer = { playerViewModel.startSleepTimer(it) },
            onCancelSleepTimer = { playerViewModel.cancelSleepTimer() },
            isFavorite = isCurrentTrackLiked,
            onToggleFavorite = { libraryViewModel.toggleFavorite(it) }
        )
    }

    // Add To Playlist Dialog
    if (trackForPlaylistDialog != null) {
        AddToPlaylistDialog(
            track = trackForPlaylistDialog,
            viewModel = libraryViewModel,
            onDismiss = { trackForPlaylistDialog = null }
        )
    }

    // In-App Self Updater Dialog
    UpdateDialog(
        updateState = updateState,
        onConfirmUpdate = { playerViewModel.downloadAndInstallUpdate(it) },
        onInstallDownloaded = { playerViewModel.installDownloadedUpdate(it) },
        onDismiss = { playerViewModel.dismissUpdate() }
    )
}
