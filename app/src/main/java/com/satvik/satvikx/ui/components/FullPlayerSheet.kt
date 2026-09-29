package com.satvik.satvikx.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.playback.model.PlaybackState
import com.satvik.satvikx.ui.theme.ArcCyan
import com.satvik.satvikx.ui.theme.ArcCyanBright
import com.satvik.satvikx.ui.theme.ArcCyanGlow
import com.satvik.satvikx.ui.theme.StarkBorder
import com.satvik.satvikx.ui.theme.StarkCarbon
import com.satvik.satvikx.ui.theme.StarkCrimson
import com.satvik.satvikx.ui.theme.StarkGold
import com.satvik.satvikx.ui.theme.StarkGoldGlow
import com.satvik.satvikx.ui.theme.StarkSurface
import com.satvik.satvikx.ui.theme.TextPrimary
import com.satvik.satvikx.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    playbackState: PlaybackState,
    sheetState: SheetState,
    onDismiss: () -> Unit,
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
    onStartSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: (TrackEntity) -> Unit = {}
) {
    val track = playbackState.currentTrack ?: return
    val context = LocalContext.current

    var userDraggingSlider by remember { mutableFloatStateOf(-1f) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = StarkCarbon
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ArcCyan.copy(alpha = 0.16f),
                            StarkCarbon,
                            StarkCarbon
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Bar: Collapse, J.A.R.V.I.S. Arc Badge, Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse full player",
                            tint = ArcCyanBright,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Stark Industries / J.A.R.V.I.S. Holographic Badge
                    Surface(
                        color = StarkSurface.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.border(
                            1.dp,
                            Brush.horizontalGradient(listOf(ArcCyan, StarkGold)),
                            RoundedCornerShape(20.dp)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(ArcCyanBright)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "J.A.R.V.I.S. // MK-85 CORE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp,
                                    color = ArcCyanBright
                                )
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Sleep Timer Button
                        IconButton(
                            onClick = { showSleepTimerDialog = true },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = "Sleep Timer",
                                tint = if (playbackState.sleepTimerRemainingSeconds != null) StarkGold else Color.White
                            )
                        }

                        // Up Next Queue Button
                        IconButton(
                            onClick = { showQueueSheet = true },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "View Queue",
                                tint = Color.White
                            )
                        }

                        // Add to Playlist Button
                        IconButton(
                            onClick = onAddToPlaylist,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Add to playlist",
                                tint = Color.White
                            )
                        }

                        // Download Button
                        IconButton(
                            onClick = onDownload,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = if (track.isDownloaded) "Downloaded" else "Download",
                                tint = if (track.isDownloaded) ArcCyan else Color.White
                            )
                        }
                    }
                }

                // Sleep Timer Active Pill
                if (playbackState.sleepTimerRemainingSeconds != null) {
                    val remainingMins = playbackState.sleepTimerRemainingSeconds / 60
                    val remainingSecs = playbackState.sleepTimerRemainingSeconds % 60
                    Surface(
                        color = StarkGold.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.border(1.dp, StarkGold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = "STANDBY PROTOCOL IN: ${String.format("%02d:%02d", remainingMins, remainingSecs)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = StarkGold
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                        )
                    }
                }

                // ==========================================
                // TONY STARK HOLOGRAPHIC ARC REACTOR HOUSING
                // ==========================================
                ArcReactorHousing(
                    thumbnailUrl = track.thumbnailUrl,
                    title = track.title,
                    isPlaying = playbackState.isPlaying,
                    showDiagnostics = showDiagnostics,
                    onToggleDiagnostics = { showDiagnostics = !showDiagnostics }
                )

                // Track Metadata + Favorite Heart
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
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 21.sp
                            ),
                            maxLines = 1,
                            color = Color.White,
                            modifier = Modifier.basicMarquee()
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextSecondary,
                                    fontSize = 14.sp
                                ),
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = ArcCyanGlow,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.border(0.8.dp, ArcCyan.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            ) {
                                Text(
                                    text = "320K DIRECT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = ArcCyanBright
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    // Player Actions: Download Button & Favorite Heart
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Prominent Download Option in Music Player
                        IconButton(
                            onClick = {
                                onDownload()
                                Toast.makeText(
                                    context,
                                    if (track.isDownloaded) "Track is already downloaded" else "Download started for ${track.title}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                imageVector = if (track.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                                contentDescription = if (track.isDownloaded) "Downloaded" else "Download",
                                tint = if (track.isDownloaded) ArcCyanBright else Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // Like / Favorite Heart
                        IconButton(
                            onClick = {
                                val willBeLiked = !isFavorite
                                onToggleFavorite(track)
                                Toast.makeText(
                                    context,
                                    if (willBeLiked) "Saved to Stark Favorites" else "Removed from Favorites",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) StarkCrimson else Color.Gray.copy(alpha = 0.6f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                // J.A.R.V.I.S. Audio Spectrum Matrix
                JarvisAudioSpectrum(
                    isPlaying = playbackState.isPlaying,
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // Hyper-Interactive Holographic Arc Energy Scrubber & Timestamps
                InteractiveArcTimeline(
                    playbackState = playbackState,
                    onSeekTo = onSeekTo
                )

                // Playback Control Cluster (Stark Arc Controls)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Shuffle
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playbackState.shuffleModeEnabled) ArcCyanBright else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Skip Previous
                    IconButton(
                        onClick = onSkipPrevious,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous song",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Arc Reactor Center Play / Pause Disc
                    ArcReactorPlayButton(
                        isPlaying = playbackState.isPlaying,
                        onClick = onPlayPause
                    )

                    // Skip Next
                    IconButton(
                        onClick = onSkipNext,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next song",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    // Repeat
                    IconButton(
                        onClick = onToggleRepeat,
                        modifier = Modifier.size(46.dp)
                    ) {
                        val (icon, tint) = when (playbackState.repeatMode) {
                            Player.REPEAT_MODE_ONE -> Pair(Icons.Default.RepeatOne, ArcCyanBright)
                            Player.REPEAT_MODE_ALL -> Pair(Icons.Default.Repeat, ArcCyanBright)
                            else -> Pair(Icons.Default.Repeat, Color.Gray)
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Repeat",
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    // ==========================================
    // NOW PLAYING QUEUE MODAL SHEET
    // ==========================================
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = queueSheetState,
            containerColor = StarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "J.A.R.V.I.S. UP-NEXT SEQUENCE",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = ArcCyanBright
                        )
                        Text(
                            text = "ACTIVE QUEUE CORES: ${playbackState.queue.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = StarkGold
                        )
                    }
                    IconButton(onClick = { showQueueSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                HorizontalDivider(color = StarkBorder, modifier = Modifier.padding(vertical = 4.dp))

                if (playbackState.queue.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No queued tracks in flight sequence", color = Color.Gray, fontFamily = FontFamily.Monospace)
                    }
                } else {
                    var draggedIndex by remember { mutableStateOf<Int?>(null) }
                    var dragOffsetY by remember { mutableFloatStateOf(0f) }
                    val haptic = LocalHapticFeedback.current
                    val density = LocalDensity.current
                    val itemHeightPx = with(density) { 68.dp.toPx() }

                    LazyColumn(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                        itemsIndexed(
                            items = playbackState.queue,
                            key = { index, item -> "${item.id}_$index" }
                        ) { index, queueTrack ->
                            val isCurrentlyPlaying = index == playbackState.currentQueueIndex
                            val isBeingDragged = draggedIndex == index

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .zIndex(if (isBeingDragged) 10f else 1f)
                                    .graphicsLayer {
                                        translationY = if (isBeingDragged) dragOffsetY else 0f
                                        scaleX = if (isBeingDragged) 1.02f else 1.0f
                                        scaleY = if (isBeingDragged) 1.02f else 1.0f
                                        shadowElevation = if (isBeingDragged) 12f else 0f
                                    }
                                    .background(
                                        when {
                                            isBeingDragged -> ArcCyanGlow.copy(alpha = 0.35f)
                                            isCurrentlyPlaying -> ArcCyan.copy(alpha = 0.14f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable { onPlayTrackAtIndex(index) }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Track Index / Playing Status
                                Text(
                                    text = String.format("%02d", index + 1),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (isCurrentlyPlaying) ArcCyanBright else TextSecondary
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                // Music Thumbnail
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = StarkSurface,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .border(
                                            width = if (isCurrentlyPlaying) 1.dp else 0.5.dp,
                                            color = if (isCurrentlyPlaying) ArcCyanBright else StarkBorder,
                                            shape = RoundedCornerShape(8.dp)
                                        )
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
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = ArcCyanBright,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // Title and Artist
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = queueTrack.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.SemiBold
                                        ),
                                        color = if (isCurrentlyPlaying) ArcCyanBright else TextPrimary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = queueTrack.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }

                                // Delete from Queue Button
                                IconButton(
                                    onClick = { onRemoveFromQueue(index) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove from queue",
                                        tint = Color.Gray.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Spotify-style Drag Handle for Adjusting Queue Order
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .pointerInput(index, playbackState.queue.size) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    draggedIndex = index
                                                    dragOffsetY = 0f
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    dragOffsetY += dragAmount.y
                                                    val cur = draggedIndex ?: return@detectDragGestures

                                                    if (dragOffsetY > itemHeightPx && cur < playbackState.queue.size - 1) {
                                                        onMoveQueueItem(cur, cur + 1)
                                                        draggedIndex = cur + 1
                                                        dragOffsetY -= itemHeightPx
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    } else if (dragOffsetY < -itemHeightPx && cur > 0) {
                                                        onMoveQueueItem(cur, cur - 1)
                                                        draggedIndex = cur - 1
                                                        dragOffsetY += itemHeightPx
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                },
                                                onDragEnd = {
                                                    draggedIndex = null
                                                    dragOffsetY = 0f
                                                },
                                                onDragCancel = {
                                                    draggedIndex = null
                                                    dragOffsetY = 0f
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Drag to reorder",
                                        tint = if (isBeingDragged) ArcCyanBright else Color.White.copy(alpha = 0.6f),
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

    // ==========================================
    // STARK STANDBY SLEEP TIMER DIALOG
    // ==========================================
    if (showSleepTimerDialog) {
        val timerOptions = listOf(15, 30, 45, 60)
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = {
                Text(
                    text = "J.A.R.V.I.S. STANDBY PROTOCOL",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = StarkGold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Automate audio reactor shutdown after:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    timerOptions.forEach { minutes ->
                        Button(
                            onClick = {
                                onStartSleepTimer(minutes)
                                showSleepTimerDialog = false
                                Toast.makeText(context, "Shutdown sequence set: $minutes min", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StarkCarbon),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StarkBorder)
                        ) {
                            Text(
                                text = "$minutes MINUTES",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = ArcCyanBright
                            )
                        }
                    }
                    if (playbackState.sleepTimerRemainingSeconds != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                onCancelSleepTimer()
                                showSleepTimerDialog = false
                                Toast.makeText(context, "Standby sequence aborted", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = StarkCrimson.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StarkCrimson.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "DISENGAGE TIMER",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = StarkCrimson
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text("CLOSE", fontFamily = FontFamily.Monospace, color = Color.Gray)
                }
            },
            containerColor = StarkSurface
        )
    }
}

/**
 * Tony Stark Arc Reactor Housing around Album Artwork.
 * Concentric rotating HUD rings, power nodes, and interactive telemetry toggle.
 */
@Composable
private fun ArcReactorHousing(
    thumbnailUrl: String,
    title: String,
    isPlaying: Boolean,
    showDiagnostics: Boolean,
    onToggleDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arc_reactor")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 8000 else 22000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "reactor_rotation"
    )
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 1100 else 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "reactor_pulse"
    )

    Box(
        modifier = modifier
            .fillMaxWidth(0.85f)
            .aspectRatio(1f)
            .clickable(onClick = onToggleDiagnostics),
        contentAlignment = Alignment.Center
    ) {
        // 1. Ambient Breathing Arc Energy Glow
        Box(
            modifier = Modifier
                .fillMaxSize(0.94f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ArcCyan.copy(alpha = 0.28f * corePulse),
                            StarkGold.copy(alpha = 0.12f * corePulse),
                            Color.Transparent
                        )
                    )
                )
        )

        // 2. Holographic HUD Canvas Rings (Rotating concentric Arc Reactor arcs)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val radius = size.minDimension / 2f

            // Outer Dashed Arc Ring (Rotating)
            rotate(rotation, pivot = center) {
                drawCircle(
                    color = ArcCyan.copy(alpha = 0.5f),
                    radius = radius - 4.dp.toPx(),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 16f, 8f, 16f), 0f)
                    )
                )
                // 4 Cardinal Power Nodes
                for (angle in 0 until 360 step 90) {
                    val rad = Math.toRadians(angle.toDouble())
                    val x = center.x + (radius - 4.dp.toPx()) * Math.cos(rad).toFloat()
                    val y = center.y + (radius - 4.dp.toPx()) * Math.sin(rad).toFloat()
                    drawCircle(
                        color = StarkGold,
                        radius = 4.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(x, y)
                    )
                }
            }

            // Counter-Rotating Inner Sector Ring
            rotate(-rotation * 0.6f, pivot = center) {
                drawCircle(
                    color = StarkGold.copy(alpha = 0.4f),
                    radius = radius - 14.dp.toPx(),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 20f), 0f)
                    )
                )
            }

            // Solid Cyan Containment Ring
            drawCircle(
                color = ArcCyan.copy(alpha = 0.7f * corePulse),
                radius = radius - 20.dp.toPx(),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 3. Central Artwork Core (Circular with subtle high-tech border)
        Box(
            modifier = Modifier
                .fillMaxSize(0.74f)
                .clip(CircleShape)
                .border(2.dp, Brush.sweepGradient(listOf(ArcCyan, StarkGold, ArcCyan)), CircleShape)
                .background(StarkSurface),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = "Album art for $title",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // 4. Interactive Holographic Diagnostics Overlay (When clicked)
            if (showDiagnostics) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "J.A.R.V.I.S. TELEMETRY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp,
                                color = StarkGold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "ARC CORE: OPTIMAL",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = ArcCyanBright
                            )
                        )
                        Text(
                            text = "320 KBPS • LOSSLESS MASTER",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White)
                        )
                        Text(
                            text = "SAMPLE: 44.1 kHz • STEREO",
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "[ TAP TO RETURN ]",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = StarkGold.copy(alpha = 0.8f)
                            )
                        )
                    }
                }
            }
        }
    }
}



/**
 * Central Arc Reactor Play/Pause Button.
 */
@Composable
private fun ArcReactorPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "arc_play_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = modifier.size(72.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .size(72.dp * pulseScale)
                    .clip(CircleShape)
                    .border(1.5.dp, ArcCyan.copy(alpha = 0.5f), CircleShape)
                    .background(ArcCyan.copy(alpha = 0.12f))
            )
        }

        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = ArcCyan,
            modifier = Modifier
                .size(60.dp)
                .shadow(16.dp, CircleShape, spotColor = ArcCyanBright)
                .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(Color.White, ArcCyanBright, ArcCyan)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = StarkCarbon,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/**
 * J.A.R.V.I.S. Holographic Frequency Matrix Spectrum Visualizer.
 */
@Composable
private fun JarvisAudioSpectrum(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvis_spectrum")
    val barRatios = listOf(
        0.25f, 0.45f, 0.7f, 0.35f, 0.9f, 0.6f, 0.85f, 0.4f, 1.0f,
        0.75f, 0.5f, 0.95f, 0.65f, 0.8f, 0.3f, 0.7f, 0.5f, 0.3f
    )

    val animations = barRatios.mapIndexed { index, ratio ->
        infiniteTransition.animateFloat(
            initialValue = 0.15f,
            targetValue = if (isPlaying) ratio else 0.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 260 + (index * 31) % 360, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "jarvis_bar_$index"
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        animations.forEachIndexed { i, anim ->
            val gradient = if (i % 2 == 0) {
                Brush.verticalGradient(listOf(StarkGold, ArcCyan))
            } else {
                Brush.verticalGradient(listOf(ArcCyanBright, ArcCyan))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 1.dp)
                    .fillMaxHeight(anim.value)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(gradient)
            )
        }
    }
}

/**
 * Hyper-Interactive Holographic Arc Reactor Audio Scrubber & Timeline Bar.
 * Features live floating HUD seek target tooltip, reactive waveform energy bars,
 * dynamic seek reticle, direct haptic feedback, and quick nudge controls (-10s / +10s).
 */
@Composable
private fun InteractiveArcTimeline(
    playbackState: com.satvik.satvikx.playback.model.PlaybackState,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var userDraggingSlider by remember { androidx.compose.runtime.mutableFloatStateOf(-1f) }

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

    val isScrubbing = userDraggingSlider >= 0f

    Column(modifier = modifier.fillMaxWidth()) {
        // 1. Floating Holographic HUD Seek Tooltip (Visible when actively dragging/scrubbing)
        AnimatedVisibility(
            visible = isScrubbing,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = StarkSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .border(1.dp, ArcCyanBright.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ArcCyanBright)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SEEK TARGET: ${formatDuration(currentPositionMs / 1000L)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = ArcCyanBright,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }

                    val deltaSeconds = (currentPositionMs - playbackState.playbackPositionMs) / 1000L
                    val deltaText = if (deltaSeconds >= 0) "+${deltaSeconds}s" else "${deltaSeconds}s"
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (deltaSeconds >= 0) ArcCyanGlow else StarkGoldGlow
                    ) {
                        Text(
                            text = deltaText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (deltaSeconds >= 0) ArcCyanBright else StarkGold
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // 2. Interactive Waveform Audio Visualizer Track
        val waveformHeights = remember {
            listOf(
                0.3f, 0.45f, 0.65f, 0.4f, 0.8f, 0.55f, 0.9f, 0.35f,
                0.7f, 1.0f, 0.6f, 0.85f, 0.45f, 0.75f, 0.5f, 0.95f,
                0.65f, 0.4f, 0.8f, 0.3f, 0.7f, 0.85f, 0.5f, 0.6f,
                0.75f, 0.9f, 0.4f, 0.65f, 0.8f, 0.55f, 0.35f, 0.5f
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            waveformHeights.forEachIndexed { index, heightFraction ->
                val barProgress = index.toFloat() / waveformHeights.size
                val isPassed = barProgress <= currentProgress
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 1.dp)
                        .fillMaxHeight(if (isScrubbing) (heightFraction * 1.15f).coerceAtMost(1f) else heightFraction)
                        .clip(RoundedCornerShape(topStart = 1.dp, topEnd = 1.dp))
                        .background(
                            if (isPassed) {
                                Brush.verticalGradient(listOf(ArcCyanBright, ArcCyan))
                            } else {
                                Brush.verticalGradient(listOf(StarkBorder, StarkSurface))
                            }
                        )
                )
            }
        }

        // 3. Scrubber Slider with dynamic Arc Reactor Thumb
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
                thumbColor = if (isScrubbing) StarkGold else ArcCyanBright,
                activeTrackColor = ArcCyan,
                inactiveTrackColor = StarkBorder
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // 4. Timestamps & Quick-Nudge Controls (-10s / +10s)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Elapsed Time
            Text(
                text = formatDuration(currentPositionMs / 1000L),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = ArcCyanBright
                )
            )

            // Interactive Nudge Buttons [-10s] and [+10s]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = StarkSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, StarkBorder),
                    modifier = Modifier.clickable {
                        val newPos = (playbackState.playbackPositionMs - 10000L).coerceAtLeast(0L)
                        onSeekTo(newPos)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                ) {
                    Text(
                        text = "-10s",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ArcCyan
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "ARC 320K DIRECT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = StarkGold.copy(alpha = 0.8f)
                    )
                )

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = StarkSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, StarkBorder),
                    modifier = Modifier.clickable {
                        val newPos = (playbackState.playbackPositionMs + 10000L).coerceAtMost(playbackState.durationMs)
                        onSeekTo(newPos)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                ) {
                    Text(
                        text = "+10s",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ArcCyan
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Total Duration
            Text(
                text = formatDuration(playbackState.durationMs / 1000L),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
            )
        }
    }
}

