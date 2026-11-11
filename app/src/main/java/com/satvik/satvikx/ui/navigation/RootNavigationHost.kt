package com.satvik.satvikx.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
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
import com.satvik.satvikx.ui.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootNavigationHost(
    playerViewModel: PlayerViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel()
) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { Screen.bottomNavItems.size })

    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val updateState by playerViewModel.updateState.collectAsStateWithLifecycle()
    val likedTrackIds by libraryViewModel.likedTrackIds.collectAsStateWithLifecycle()
    val lyricsState by playerViewModel.lyricsState.collectAsStateWithLifecycle()

    var isFullPlayerExpanded by remember { mutableStateOf(false) }
    var isQueueOpen by remember { mutableStateOf(false) }
    var trackForPlaylistDialog by remember { mutableStateOf<TrackEntity?>(null) }
    val fullPlayerSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { sheetValue ->
            if (isQueueOpen && sheetValue != SheetValue.Expanded) {
                false
            } else {
                true
            }
        }
    )
    var lastSearchClickTime by remember { mutableLongStateOf(0L) }

    // Intercept hardware back button when on a secondary tab to return to Home (Instagram style)
    BackHandler(enabled = pagerState.currentPage != 0 && !isFullPlayerExpanded && !isQueueOpen) {
        coroutineScope.launch {
            pagerState.animateScrollToPage(0)
        }
    }

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
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing))
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
                    Screen.bottomNavItems.forEachIndexed { index, screen ->
                        val isSelected = pagerState.currentPage == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                val isSearch = screen.route == Screen.Search.route
                                val currentTime = System.currentTimeMillis()
                                val isDoubleTap = isSearch && (currentTime - lastSearchClickTime < 500L)
                                if (isSearch) {
                                    lastSearchClickTime = currentTime
                                }

                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(
                                        page = index,
                                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                                    )
                                }

                                if (isSearch) {
                                    if (isDoubleTap) {
                                        // Double-tap on Search: clear previous search query for instant fresh typing
                                        searchViewModel.clearSearch()
                                        searchViewModel.requestSearchFocus()
                                    } else if (isSelected) {
                                        searchViewModel.requestSearchFocus()
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                                )
                            }
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
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                pageNestedScrollConnection = remember {
                    object : NestedScrollConnection {
                        // Do not allow nested child horizontal scroll containers (like recommendation carousels)
                        // to drag or switch the parent tab pager
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = Offset.Zero
                        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = Offset.Zero
                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = Velocity.Zero
                    }
                }
            ) { pageIndex ->
                when (Screen.bottomNavItems[pageIndex]) {
                    Screen.Home -> HomeScreen()
                    Screen.Search -> SearchScreen(
                        viewModel = searchViewModel,
                        libraryViewModel = libraryViewModel
                    )
                    Screen.Playlists -> PlaylistsScreen()
                    Screen.Downloads -> DownloadsScreen()
                    Screen.Recents -> RecentsScreen()
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
            onDismiss = {
                isQueueOpen = false
                isFullPlayerExpanded = false
            },
            isQueueOpen = isQueueOpen,
            onQueueOpenChange = { isQueueOpen = it },
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
            onMoveQueueItem = { from, to -> playerViewModel.moveQueueItem(from, to) },
            onRefreshQueue = { playerViewModel.refreshQueue() },
            onStartSleepTimer = { playerViewModel.startSleepTimer(it) },
            onCancelSleepTimer = { playerViewModel.cancelSleepTimer() },
            isFavorite = isCurrentTrackLiked,
            onToggleFavorite = { libraryViewModel.toggleFavorite(it) },
            lyricsState = lyricsState,
            onRetryLyrics = { playerViewModel.retryFetchLyrics() }
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
