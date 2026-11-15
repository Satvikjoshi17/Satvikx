package com.satvik.satvikx.ui.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.playback.model.PlaybackState
import com.satvik.satvikx.ui.theme.SpotifyGreen
import com.satvik.satvikx.ui.theme.TextPrimary
import com.satvik.satvikx.ui.theme.TextSecondary
import com.satvik.satvikx.ui.theme.TextTertiary
import com.satvik.satvikx.ui.viewmodel.LyricsUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * YouTube Music & Spotify grade smooth drag-and-drop queue state manager.
 * Tracks physical offsets to deliver jitter-free reordering gestures.
 */
class QueueDragDropState(
    val listState: androidx.compose.foundation.lazy.LazyListState,
    val coroutineScope: CoroutineScope,
    val onSwap: (Int, Int) -> Unit,
    val onCommit: (Int, Int) -> Unit
) {
    var draggingItemIndex by mutableStateOf<Int?>(null)
        private set
    var draggingItemOffset by mutableFloatStateOf(0f)
        private set
    var initialIndex by mutableStateOf<Int?>(null)
        private set

    fun onDragStart(index: Int) {
        initialIndex = index
        draggingItemIndex = index
        draggingItemOffset = 0f
    }

    fun onDrag(deltaY: Float) {
        val currentDragging = draggingItemIndex ?: return
        draggingItemOffset += deltaY

        val currentItemInfo = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == currentDragging } ?: return

        val draggedItemCenter = currentItemInfo.offset + currentItemInfo.size / 2f + draggingItemOffset

        val targetItem = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { other ->
                other.index != currentDragging &&
                draggedItemCenter.toInt() in other.offset..(other.offset + other.size)
            }

        if (targetItem != null) {
            val targetIndex = targetItem.index
            val deltaOffset = targetItem.offset - currentItemInfo.offset
            onSwap(currentDragging, targetIndex)
            draggingItemOffset -= deltaOffset
            draggingItemIndex = targetIndex
        }

        val viewportStart = listState.layoutInfo.viewportStartOffset
        val viewportEnd = listState.layoutInfo.viewportEndOffset
        val scrollThreshold = 64f

        if (draggedItemCenter > viewportEnd - scrollThreshold) {
            coroutineScope.launch {
                listState.scrollBy(14f)
            }
        } else if (draggedItemCenter < viewportStart + scrollThreshold) {
            coroutineScope.launch {
                listState.scrollBy(-14f)
            }
        }
    }

    fun onDragEnd() {
        val initial = initialIndex
        val current = draggingItemIndex
        if (initial != null && current != null && initial != current) {
            onCommit(initial, current)
        }
        draggingItemIndex = null
        initialIndex = null
        draggingItemOffset = 0f
    }

    fun onDragCancel() {
        draggingItemIndex = null
        initialIndex = null
        draggingItemOffset = 0f
    }
}

/**
 * Spotify Signature Mobile Now Playing Screen & Queue.
 *
 * Designed to authentically mirror Spotify Mobile UI:
 * - Top header with "PLAYING FROM PLAYLIST" and playlist name
 * - Large square album cover art with subtle rounded corners
 * - Song title & artist with Spotify Green animated Heart
 * - Thin Spotify scrubber bar with sans-serif timestamps
 * - 64dp solid white play circle with centered black play/pause icons
 * - Clean bottom utility dock (Lyrics, Sleep Timer, Queue)
 * - Seamless partial-height Queue sheet with "Now playing" and "Next in queue"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    playbackState: PlaybackState,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    isQueueOpen: Boolean = false,
    onQueueOpenChange: (Boolean) -> Unit = {},
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onPlayTrackAtIndex: (Int) -> Unit = {},
    onRemoveFromQueue: (Int) -> Unit = {},
    onMoveQueueItem: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onRefreshQueue: suspend () -> Boolean = { false },
    onStartSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    isFavorite: Boolean = false,
    lyricsState: LyricsUiState = LyricsUiState(),
    onRetryLyrics: () -> Unit = {},
    onToggleFavorite: (TrackEntity) -> Unit = {}
) {
    val track = playbackState.currentTrack ?: return
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var userDraggingSlider by remember { mutableFloatStateOf(-1f) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }

    var isRefreshingLocally by remember { mutableStateOf(false) }
    var refreshBannerMessage by remember { mutableStateOf<String?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "queueRefreshTransition")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    val queueDragOffsetY = remember { Animatable(0f) }
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 140.dp.toPx() }

    LaunchedEffect(isQueueOpen) {
        if (isQueueOpen) {
            queueDragOffsetY.snapTo(0f)
        }
    }

    val canInteractWithQueue = isQueueOpen && queueDragOffsetY.value < 15f

    val dismissQueueWithAnimation: (Float) -> Unit = { velocityY ->
        coroutineScope.launch {
            queueDragOffsetY.animateTo(
                targetValue = 2500f,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                initialVelocity = velocityY
            )
            onQueueOpenChange(false)
            // Note: Never snapTo(0f) here! Keep it offscreen at 2500f during dismissal.
            // LaunchedEffect(isQueueOpen) snaps it back to 0f when the queue is reopened.
        }
    }

    val snapQueueBackToOpen: () -> Unit = {
        coroutineScope.launch {
            queueDragOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

    // Intercept back button when queue is open
    BackHandler(enabled = isQueueOpen) {
        dismissQueueWithAnimation(0f)
    }

    val queueListState = rememberLazyListState()

    val queueNestedScrollConnection = remember(queueListState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && queueListState.firstVisibleItemIndex == 0 && queueListState.firstVisibleItemScrollOffset == 0) {
                    val newOffset = queueDragOffsetY.value + available.y
                    coroutineScope.launch { queueDragOffsetY.snapTo(newOffset) }
                    return Offset(0f, available.y)
                }
                if (available.y < 0f && queueDragOffsetY.value > 0f) {
                    val newOffset = (queueDragOffsetY.value + available.y).coerceAtLeast(0f)
                    coroutineScope.launch { queueDragOffsetY.snapTo(newOffset) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && queueListState.firstVisibleItemIndex == 0 && queueListState.firstVisibleItemScrollOffset == 0) {
                    val newOffset = queueDragOffsetY.value + available.y
                    coroutineScope.launch { queueDragOffsetY.snapTo(newOffset) }
                    return Offset(0f, available.y)
                }
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (queueDragOffsetY.value > dismissThresholdPx || available.y > 1000f) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    dismissQueueWithAnimation(available.y)
                } else if (queueDragOffsetY.value > 0f) {
                    snapQueueBackToOpen()
                }
                return available
            }
        }
    }

    val currentProgress = if (userDraggingSlider >= 0f) {
        userDraggingSlider
    } else {
        playbackState.progressFraction
    }

    val currentPositionMs = if (userDraggingSlider >= 0f && playbackState.durationMs > 0) {
        (userDraggingSlider * playbackState.durationMs).toLong()
    } else {
        playbackState.playbackPositionMs
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (isQueueOpen) {
                dismissQueueWithAnimation(0f)
            } else {
                onDismiss()
            }
        },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color(0xFF121212)
    ) {
        BackHandler(enabled = isQueueOpen) {
            dismissQueueWithAnimation(0f)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF2C2C2C),
                            Color(0xFF161616),
                            Color(0xFF121212)
                        )
                    )
                )
        ) {
            // Main Spotify Full Player Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. Top Bar: Collapse Chevron, Context Title, 3-dots Menu
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse full player",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (playbackState.isPlaylistPlayback) "PLAYING FROM PLAYLIST" else "PLAYING FROM ARTIST",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp,
                                fontSize = 10.sp
                            ),
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (playbackState.isPlaylistPlayback) "Playlist" else track.artist,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            ),
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }

                    IconButton(
                        onClick = { showMoreOptions = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // 2. Large Square Album Artwork / Synced Lyrics Centerpiece
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = showLyrics,
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                        label = "player_center_view"
                    ) { isLyricsVisible ->
                        if (isLyricsVisible) {
                            SyncedLyricsView(
                                lyricsState = lyricsState,
                                currentPositionMs = playbackState.playbackPositionMs,
                                isPlaying = playbackState.isPlaying,
                                onSeekTo = onSeekTo,
                                onRetry = onRetryLyrics
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                shadowElevation = 18.dp,
                                color = Color(0xFF282828),
                                modifier = Modifier
                                    .fillMaxWidth(0.96f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showLyrics = true }
                            ) {
                                if (track.thumbnailUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = track.thumbnailUrl,
                                        contentDescription = "Album art for ${track.title}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = TextTertiary,
                                            modifier = Modifier.size(64.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Track Metadata & Favorite Heart Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 21.sp
                            ),
                            maxLines = 1,
                            color = Color.White,
                            modifier = Modifier.basicMarquee()
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextSecondary,
                                fontSize = 14.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Like / Favorite Heart with animated bounce
                    val favoriteScale by animateFloatAsState(
                        targetValue = if (isFavorite) 1.25f else 1.0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioHighBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "fav_scale"
                    )
                    IconButton(
                        onClick = {
                            val willBeLiked = !isFavorite
                            onToggleFavorite(track)
                            Toast.makeText(
                                context,
                                if (willBeLiked) "Added to Liked Songs" else "Removed from Liked Songs",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) "Liked" else "Like",
                            tint = if (isFavorite) SpotifyGreen else Color.White.copy(alpha = 0.75f),
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer {
                                    scaleX = favoriteScale
                                    scaleY = favoriteScale
                                }
                        )
                    }
                }

                // 4. Clean Spotify Scrubber Bar & Timestamps
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Slider(
                        value = currentProgress,
                        onValueChange = {
                            if (userDraggingSlider == -1f || (it * 100).toInt() != (userDraggingSlider * 100).toInt()) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            userDraggingSlider = it
                        },
                        onValueChangeFinished = {
                            if (userDraggingSlider >= 0f && playbackState.durationMs > 0) {
                                val targetMs = (userDraggingSlider * playbackState.durationMs).toLong()
                                onSeekTo(targetMs)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            userDraggingSlider = -1f
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0xFF404040)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(currentPositionMs / 1000L),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = TextSecondary
                            )
                        )
                        Text(
                            text = formatDuration(playbackState.durationMs / 1000L),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = TextSecondary
                            )
                        )
                    }
                }

                // 5. Signature Spotify Playback Control Cluster
                val isCompactScreen = LocalConfiguration.current.screenWidthDp < 360
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = if (isCompactScreen) 2.dp else 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Shuffle
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.size(if (isCompactScreen) 38.dp else 44.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (playbackState.shuffleModeEnabled) SpotifyGreen else Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(if (isCompactScreen) 20.dp else 23.dp)
                            )
                            if (playbackState.shuffleModeEnabled) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .size(3.5.dp)
                                        .clip(CircleShape)
                                        .background(SpotifyGreen)
                                )
                            }
                        }
                    }

                    // Skip Previous
                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier.size(if (isCompactScreen) 42.dp else 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous song",
                            tint = Color.White,
                            modifier = Modifier.size(if (isCompactScreen) 30.dp else 36.dp)
                        )
                    }

                    // 64dp Solid White Spotify Play / Pause Disc
                    Box(
                        modifier = Modifier
                            .size(if (isCompactScreen) 58.dp else 64.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable(onClick = onPlayPause),
                        contentAlignment = Alignment.Center
                    ) {
                        Crossfade(
                            targetState = playbackState.isPlaying,
                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                            label = "play_btn_crossfade"
                        ) { isPlaying ->
                            if (isPlaying) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Pause",
                                    tint = Color.Black,
                                    modifier = Modifier.size(if (isCompactScreen) 28.dp else 32.dp)
                                )
                            } else {
                                OpticallyCenteredPlayIcon(
                                    modifier = Modifier.size(if (isCompactScreen) 22.dp else 24.dp),
                                    tint = Color.Black
                                )
                            }
                        }
                    }

                    // Skip Next
                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.size(if (isCompactScreen) 42.dp else 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next song",
                            tint = Color.White,
                            modifier = Modifier.size(if (isCompactScreen) 30.dp else 36.dp)
                        )
                    }

                    // Repeat
                    IconButton(
                        onClick = onToggleRepeat,
                        modifier = Modifier.size(if (isCompactScreen) 38.dp else 44.dp)
                    ) {
                        val isRepeatActive = playbackState.repeatMode != Player.REPEAT_MODE_OFF
                        val icon = if (playbackState.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = icon,
                                contentDescription = "Repeat",
                                tint = if (isRepeatActive) SpotifyGreen else Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(if (isCompactScreen) 20.dp else 23.dp)
                            )
                            if (isRepeatActive) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Box(
                                    modifier = Modifier
                                        .size(3.5.dp)
                                        .clip(CircleShape)
                                        .background(SpotifyGreen)
                                )
                            }
                        }
                    }
                }

                // 6. Bottom Utilities Bar (Lyrics, Sleep Timer, Queue)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lyrics Button
                    IconButton(
                        onClick = { showLyrics = !showLyrics },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lyrics,
                            contentDescription = if (showLyrics) "Hide lyrics" else "Show lyrics",
                            tint = if (showLyrics) SpotifyGreen else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Sleep Timer Button / Status
                    if (playbackState.sleepTimerRemainingSeconds != null) {
                        val mins = playbackState.sleepTimerRemainingSeconds / 60
                        val secs = playbackState.sleepTimerRemainingSeconds % 60
                        Surface(
                            color = SpotifyGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable { showSleepTimerDialog = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = SpotifyGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = String.format("%02d:%02d", mins, secs),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = SpotifyGreen
                                )
                            }
                        }
                    } else {
                        IconButton(
                            onClick = { showSleepTimerDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Sleep timer",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Queue Button
                    IconButton(
                        onClick = { onQueueOpenChange(true) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "View Queue",
                            tint = if (isQueueOpen) SpotifyGreen else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // ==========================================
            // SPOTIFY SIGNATURE QUEUE OVERLAY PANEL
            // ==========================================
            // Dimmed scrim above Queue
            androidx.compose.animation.AnimatedVisibility(
                visible = isQueueOpen,
                enter = fadeIn(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(200)),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(45f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Black.copy(
                                alpha = (0.65f * (1f - (queueDragOffsetY.value / 1200f).coerceIn(0f, 1f))).coerceIn(0f, 0.65f)
                            )
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { dismissQueueWithAnimation(0f) })
                        }
                        .pointerInput(Unit) {
                            val velocityTracker = VelocityTracker()
                            detectVerticalDragGestures(
                                onDragStart = { velocityTracker.resetTracking() },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val newOffset = (queueDragOffsetY.value + dragAmount).coerceAtLeast(0f)
                                    coroutineScope.launch { queueDragOffsetY.snapTo(newOffset) }
                                },
                                onDragEnd = {
                                    val velocityY = velocityTracker.calculateVelocity().y
                                    if (queueDragOffsetY.value > dismissThresholdPx || velocityY > 1000f) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        dismissQueueWithAnimation(velocityY)
                                    } else {
                                        snapQueueBackToOpen()
                                    }
                                },
                                onDragCancel = { snapQueueBackToOpen() }
                            )
                        }
                )
            }

            // Slide-up Queue Sheet (~82% of screen height)
            androidx.compose.animation.AnimatedVisibility(
                visible = isQueueOpen,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .align(Alignment.BottomCenter)
                    .zIndex(50f)
                    .offset { IntOffset(0, queueDragOffsetY.value.roundToInt().coerceAtLeast(0)) }
                    .nestedScroll(queueNestedScrollConnection)
            ) {
                Surface(
                    color = Color(0xFF121212),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    shadowElevation = 24.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { /* Absorb clicks on sheet background */ })
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                    ) {
                        // Drag dismiss handle & Header
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    val velocityTracker = VelocityTracker()
                                    detectVerticalDragGestures(
                                        onDragStart = { velocityTracker.resetTracking() },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                                            val newOffset = (queueDragOffsetY.value + dragAmount).coerceAtLeast(0f)
                                            coroutineScope.launch { queueDragOffsetY.snapTo(newOffset) }
                                        },
                                        onDragEnd = {
                                            val velocityY = velocityTracker.calculateVelocity().y
                                            if (queueDragOffsetY.value > dismissThresholdPx || velocityY > 1000f) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                dismissQueueWithAnimation(velocityY)
                                            } else {
                                                snapQueueBackToOpen()
                                            }
                                        },
                                        onDragCancel = { snapQueueBackToOpen() }
                                    )
                                }
                        ) {
                            // Subtle centered drag bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        dismissQueueWithAnimation(0f)
                                    }
                                    .padding(top = 10.dp, bottom = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 36.dp, height = 4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFF4D4D4D))
                                )
                            }

                            // Header: "Queue" and close action
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Queue",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp
                                        ),
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${playbackState.queue.size} songs in queue",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!playbackState.isPlaylistPlayback) {
                                        IconButton(
                                            onClick = {
                                                if (!isRefreshingLocally) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    isRefreshingLocally = true
                                                    coroutineScope.launch {
                                                        try {
                                                            onRefreshQueue()
                                                        } finally {
                                                            isRefreshingLocally = false
                                                        }
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = "Refresh queue",
                                                tint = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .graphicsLayer {
                                                        rotationZ = if (isRefreshingLocally) spinAngle else 0f
                                                    }
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { dismissQueueWithAnimation(0f) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Close queue",
                                            tint = Color.White,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFF242424), thickness = 1.dp)

                        var localQueue by remember { mutableStateOf(playbackState.queue) }
                        val currentLocalQueue by rememberUpdatedState(localQueue)
                        val currentOnMove by rememberUpdatedState(onMoveQueueItem)
                        val listState = queueListState

                        val dragDropState = remember(listState) {
                            QueueDragDropState(
                                listState = listState,
                                coroutineScope = coroutineScope,
                                onSwap = { from, to ->
                                    val queue = currentLocalQueue
                                    if (from in queue.indices && to in queue.indices) {
                                        val updated = queue.toMutableList()
                                        val item = updated.removeAt(from)
                                        updated.add(to, item)
                                        localQueue = updated
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                                onCommit = { from, to ->
                                    currentOnMove(from, to)
                                }
                            )
                        }

                        LaunchedEffect(playbackState.queue) {
                            if (dragDropState.draggingItemIndex == null) {
                                localQueue = playbackState.queue
                            }
                        }

                        val uniqueKeys = remember(localQueue) {
                            val seenCounts = mutableMapOf<String, Int>()
                            localQueue.mapIndexed { index, trackItem ->
                                val rawId = trackItem.id.ifBlank { "track_$index" }
                                val count = seenCounts.getOrDefault(rawId, 0)
                                seenCounts[rawId] = count + 1
                                if (count == 0) rawId else "${rawId}_dup_${count}_$index"
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
                        ) {
                            // Section 1: "Now playing"
                            item(key = "header_now_playing") {
                                Text(
                                    text = "Now playing",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    color = Color.White,
                                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF1E1E1E))
                                        .padding(horizontal = 20.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Thumbnail
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF282828),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        if (track.thumbnailUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = track.thumbnailUrl,
                                                contentDescription = track.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    tint = TextTertiary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            ),
                                            color = SpotifyGreen,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = track.artist,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 12.sp
                                            ),
                                            color = TextSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = "Playing",
                                        tint = SpotifyGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Section 2: "Next in queue"
                            item(key = "header_next_in_queue") {
                                Text(
                                    text = "Next in queue",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    ),
                                    color = Color.White,
                                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
                                )
                            }

                            if (localQueue.isEmpty()) {
                                item(key = "empty_queue_state") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Queue is empty",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            } else {
                                itemsIndexed(
                                    items = localQueue,
                                    key = { index, trackItem ->
                                        uniqueKeys.getOrElse(index) { "${trackItem.id}_$index" }
                                    }
                                ) { index, queueTrack ->
                                    val isCurrentlyPlaying = queueTrack.id == playbackState.currentTrack?.id
                                    val isBeingDragged = dragDropState.draggingItemIndex == index
                                    val currentIndex by rememberUpdatedState(index)
                                    val currentItemKey = uniqueKeys.getOrElse(index) { "${queueTrack.id}_$index" }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (!isBeingDragged) Modifier.animateItem() else Modifier)
                                            .zIndex(if (isBeingDragged) 100f else 1f)
                                            .graphicsLayer {
                                                translationY = if (isBeingDragged) dragDropState.draggingItemOffset else 0f
                                                scaleX = if (isBeingDragged) 1.02f else 1.0f
                                                scaleY = if (isBeingDragged) 1.02f else 1.0f
                                                shadowElevation = if (isBeingDragged) 12f else 0f
                                            }
                                            .background(
                                                when {
                                                    isBeingDragged -> Color(0xFF282828)
                                                    isCurrentlyPlaying -> Color(0xFF1E1E1E)
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .clickable(enabled = canInteractWithQueue) {
                                                val actualIndex = playbackState.queue.indexOfFirst { it.id == queueTrack.id }.takeIf { it >= 0 } ?: currentIndex
                                                onPlayTrackAtIndex(actualIndex)
                                            }
                                            .padding(horizontal = 20.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Track Artwork
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF242424),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            if (queueTrack.thumbnailUrl.isNotBlank()) {
                                                AsyncImage(
                                                    model = queueTrack.thumbnailUrl,
                                                    contentDescription = queueTrack.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MusicNote,
                                                        contentDescription = null,
                                                        tint = TextTertiary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Title & Artist
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = queueTrack.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.SemiBold,
                                                    fontSize = 13.5.sp
                                                ),
                                                color = if (isCurrentlyPlaying) SpotifyGreen else Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = queueTrack.artist,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 12.sp
                                                ),
                                                color = TextSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        // Remove from Queue
                                        IconButton(
                                            enabled = canInteractWithQueue,
                                            onClick = {
                                                val actualIndex = playbackState.queue.indexOfFirst { it.id == queueTrack.id }.takeIf { it >= 0 } ?: currentIndex
                                                onRemoveFromQueue(actualIndex)
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove from queue",
                                                tint = Color.White.copy(alpha = 0.5f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Reorder Drag Handle
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .pointerInput(currentItemKey) {
                                                    detectDragGestures(
                                                        onDragStart = {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            dragDropState.onDragStart(currentIndex)
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragDropState.onDrag(dragAmount.y)
                                                        },
                                                        onDragEnd = {
                                                            dragDropState.onDragEnd()
                                                        },
                                                        onDragCancel = {
                                                            localQueue = playbackState.queue
                                                            dragDropState.onDragCancel()
                                                        }
                                                    )
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DragHandle,
                                                contentDescription = "Drag to reorder",
                                                tint = if (isBeingDragged) SpotifyGreen else Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // SPOTIFY MORE OPTIONS BOTTOM SHEET
    // ==========================================
    if (showMoreOptions) {
        ModalBottomSheet(
            onDismissRequest = { showMoreOptions = false },
            containerColor = Color(0xFF242424),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .background(Color(0xFF4D4D4D), RoundedCornerShape(2.dp))
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                // Track header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF1E1E1E),
                        modifier = Modifier.size(50.dp)
                    ) {
                        if (track.thumbnailUrl.isNotBlank()) {
                            AsyncImage(
                                model = track.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MusicNote, contentDescription = null, tint = TextTertiary)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(bottom = 8.dp))

                // Options Rows
                SpotifyOptionRow(
                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    iconTint = if (isFavorite) SpotifyGreen else Color.White,
                    title = if (isFavorite) "Remove from Liked Songs" else "Save to your Liked Songs",
                    onClick = {
                        onToggleFavorite(track)
                        showMoreOptions = false
                    }
                )

                SpotifyOptionRow(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    title = "Add to playlist",
                    onClick = {
                        showMoreOptions = false
                        onAddToPlaylist()
                    }
                )

                SpotifyOptionRow(
                    icon = if (track.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                    iconTint = if (track.isDownloaded) SpotifyGreen else Color.White,
                    title = if (track.isDownloaded) "Downloaded" else "Download track",
                    onClick = {
                        onDownload()
                        showMoreOptions = false
                        Toast.makeText(context, if (track.isDownloaded) "Track is already downloaded" else "Download started", Toast.LENGTH_SHORT).show()
                    }
                )

                SpotifyOptionRow(
                    icon = Icons.Default.Timer,
                    title = if (playbackState.sleepTimerRemainingSeconds != null) "Sleep timer active (${playbackState.sleepTimerRemainingSeconds / 60}m)" else "Sleep timer",
                    onClick = {
                        showMoreOptions = false
                        showSleepTimerDialog = true
                    }
                )

                SpotifyOptionRow(
                    icon = Icons.Default.Lyrics,
                    title = if (showLyrics) "Hide lyrics" else "Show lyrics",
                    onClick = {
                        showLyrics = !showLyrics
                        showMoreOptions = false
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ==========================================
    // SPOTIFY SLEEP TIMER DIALOG
    // ==========================================
    if (showSleepTimerDialog) {
        val timerOptions = listOf(5, 15, 30, 45, 60)
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = {
                Text(
                    text = "Stop audio in",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = Color.White
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    timerOptions.forEach { minutes ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E1E1E),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onStartSleepTimer(minutes)
                                    showSleepTimerDialog = false
                                    Toast.makeText(context, "Timer set for $minutes minutes", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Text(
                                text = "$minutes minutes",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }

                    if (playbackState.sleepTimerRemainingSeconds != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2E1B1B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onCancelSleepTimer()
                                    showSleepTimerDialog = false
                                    Toast.makeText(context, "Timer turned off", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Text(
                                text = "Turn off timer",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = Color(0xFFFF5252),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("Close", color = TextSecondary)
                }
            },
            containerColor = Color(0xFF242424)
        )
    }
}

@Composable
private fun SpotifyOptionRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    iconTint: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp
            ),
            color = Color.White
        )
    }
}
