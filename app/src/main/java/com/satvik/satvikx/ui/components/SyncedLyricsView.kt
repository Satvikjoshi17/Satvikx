package com.satvik.satvikx.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satvik.satvikx.data.parser.LrcParser
import com.satvik.satvikx.data.remote.model.LrcLine
import com.satvik.satvikx.data.remote.model.LyricsData
import com.satvik.satvikx.ui.theme.SpotifyGreen
import com.satvik.satvikx.ui.viewmodel.LyricsUiState
import kotlinx.coroutines.launch

/**
 * Spotify Signature Mobile Synced Lyrics Card.
 *
 * Features:
 * - High-contrast left-aligned lyrics layout matching Spotify mobile client.
 * - Active line: Pure white (#FFFFFF) bold 22sp text with subtle scale up.
 * - Past/unplayed lines: Soft 38% muted white text (18sp) for effortless reading.
 * - Tap-to-seek: Jump playback instantly to any lyric timestamp with tactile haptic feedback.
 * - Smooth auto-scrolling centered at ~35% viewport height with automatic 4-second resume on idle.
 * - Spotify "Sync to music" capsule pill button when user manually scrolls through lyrics.
 * - Clean provider badge attribution and fine sync offset calibration controls.
 */
@Composable
fun SyncedLyricsView(
    lyricsState: LyricsUiState,
    currentPositionMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lyrics = lyricsState.lyrics
    val isLoading = lyricsState.isLoading
    val error = lyricsState.error

    val context = LocalContext.current
    val trackId = lyrics?.trackId.orEmpty()
    val prefs = remember(context) { context.getSharedPreferences("satvikx_lyrics_calibration", android.content.Context.MODE_PRIVATE) }
    var userOffsetMs by remember(trackId) {
        mutableLongStateOf(if (trackId.isNotBlank()) prefs.getLong("offset_$trackId", 0L) else 0L)
    }
    var isCalibrateOpen by remember { mutableStateOf(false) }

    val effectivePositionMs = maxOf(0L, currentPositionMs + userOffsetMs)

    Surface(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp)),
        color = Color(0xFF242424) // Spotify dark card surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Spotify Top Header: "Lyrics" title + provider credit + optional sync tuner
            SpotifyLyricsHeader(
                lyrics = lyrics,
                isLoading = isLoading,
                userOffsetMs = userOffsetMs,
                isCalibrateOpen = isCalibrateOpen,
                onToggleCalibrate = { isCalibrateOpen = !isCalibrateOpen }
            )

            // Discreet calibration bar (only when user opens tuner)
            AnimatedVisibility(visible = isCalibrateOpen && lyrics?.isSynced == true) {
                SpotifyCalibrationBar(
                    userOffsetMs = userOffsetMs,
                    onAdjustOffset = { deltaMs ->
                        val newOffset = (userOffsetMs + deltaMs).coerceIn(-15000L, 15000L)
                        userOffsetMs = newOffset
                        if (trackId.isNotBlank()) {
                            prefs.edit().putLong("offset_$trackId", newOffset).apply()
                        }
                    },
                    onResetOffset = {
                        userOffsetMs = 0L
                        if (trackId.isNotBlank()) {
                            prefs.edit().remove("offset_$trackId").apply()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Lyrics View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    isLoading && lyrics == null -> {
                        SpotifyLyricsLoadingSkeleton()
                    }

                    lyrics != null && lyrics.lines.isEmpty() && lyrics.plainLyrics == null -> {
                        SpotifyInstrumentalView()
                    }

                    lyrics != null && lyrics.isSynced && lyrics.lines.isNotEmpty() -> {
                        SpotifySyncedLinesList(
                            lines = lyrics.lines,
                            currentPositionMs = effectivePositionMs,
                            userOffsetMs = userOffsetMs,
                            isPlaying = isPlaying,
                            onSeekTo = onSeekTo
                        )
                    }

                    lyrics != null && !lyrics.plainLyrics.isNullOrBlank() -> {
                        SpotifyPlainLyricsList(plainLyrics = lyrics.plainLyrics)
                    }

                    else -> {
                        SpotifyLyricsUnavailableView(
                            errorMessage = error,
                            onRetry = onRetry
                        )
                    }
                }
            }
        }
    }
}

/**
 * Spotify Lyrics Top Header Bar with clean typography and subtle provider credit
 */
@Composable
private fun SpotifyLyricsHeader(
    lyrics: LyricsData?,
    isLoading: Boolean,
    userOffsetMs: Long,
    isCalibrateOpen: Boolean,
    onToggleCalibrate: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Lyrics",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color.White
                )
            )

            val providerText = when {
                isLoading -> "Loading lyrics..."
                lyrics == null -> "Offline"
                lyrics.isSynced -> "Synced lyrics • ${lyrics.provider}"
                lyrics.lines.isEmpty() && lyrics.plainLyrics == null -> "Instrumental"
                else -> "Lyrics • ${lyrics.provider}"
            }

            Text(
                text = providerText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.55f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (lyrics?.isSynced == true) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isCalibrateOpen || userOffsetMs != 0L) Color(0xFF383838) else Color(0xFF2C2C2C))
                    .clickable { onToggleCalibrate() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Sync offset",
                        tint = if (userOffsetMs != 0L) SpotifyGreen else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (userOffsetMs == 0L) "Sync" else "${if (userOffsetMs > 0) "+" else ""}${"%.1f".format(java.util.Locale.US, userOffsetMs / 1000f)}s",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.5.sp,
                            color = if (userOffsetMs != 0L) SpotifyGreen else Color.White.copy(alpha = 0.85f)
                        )
                    )
                }
            }
        }
    }
}

/**
 * Spotify fine sync timing calibration pill
 */
@Composable
private fun SpotifyCalibrationBar(
    userOffsetMs: Long,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2C2C2C))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Offset",
            style = MaterialTheme.typography.labelSmall.copy(
                color = Color.White.copy(alpha = 0.65f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            )
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF383838))
                    .clickable { onAdjustOffset(-300L) }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "-0.3s",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (userOffsetMs != 0L) SpotifyGreen.copy(alpha = 0.2f) else Color(0xFF383838))
                    .clickable { onResetOffset() }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (userOffsetMs == 0L) "Reset" else "${if (userOffsetMs > 0) "+" else ""}${"%.1f".format(java.util.Locale.US, userOffsetMs / 1000f)}s",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (userOffsetMs != 0L) SpotifyGreen else Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF383838))
                    .clickable { onAdjustOffset(300L) }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "+0.3s",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

/**
 * Spotify Signature Synced Lyrics Line List:
 * - Left aligned
 * - High-contrast white active singing line (22sp, ExtraBold)
 * - Muted 38% opacity for past and future lines (18sp, Bold)
 * - Tap to seek
 * - Auto-scroll with 4-second resume timer and "Sync to music" capsule button
 */
@Composable
private fun SpotifySyncedLinesList(
    lines: List<LrcLine>,
    currentPositionMs: Long,
    userOffsetMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val activeLineIndex = remember(lines, currentPositionMs) {
        LrcParser.findActiveLineIndex(lines, currentPositionMs)
    }

    // Manual scroll detection: Pause auto-scroll for 4s if user drags/browses
    var lastUserInteractionTimeMs by remember { mutableLongStateOf(0L) }
    val isListDragged by listState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(isListDragged) {
        if (isListDragged) {
            lastUserInteractionTimeMs = System.currentTimeMillis()
        }
    }

    val isUserBrowsing = remember(lastUserInteractionTimeMs, isListDragged) {
        isListDragged || (System.currentTimeMillis() - lastUserInteractionTimeMs < 4000L)
    }

    // Smooth auto-scroll to center the active line at ~35% of viewport
    LaunchedEffect(activeLineIndex, isUserBrowsing) {
        if (!isUserBrowsing && activeLineIndex in lines.indices) {
            listState.animateScrollToItem(
                index = activeLineIndex,
                scrollOffset = -140
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 40.dp, bottom = 120.dp, start = 4.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            itemsIndexed(lines, key = { index, item -> "${index}_${item.startTimeMs}" }) { index, line ->
                val isActive = index == activeLineIndex

                val targetColor = if (isActive) {
                    Color.White
                } else {
                    Color.White.copy(alpha = 0.38f)
                }

                val animatedTextColor by animateColorAsState(
                    targetValue = targetColor,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    label = "spotify_lyric_color"
                )

                val animatedScale by animateFloatAsState(
                    targetValue = if (isActive) 1.02f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = 300f
                    ),
                    label = "spotify_lyric_scale"
                )

                val fontSize = if (isActive) 22.sp else 18.sp
                val fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                        }
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSeekTo(maxOf(0L, line.startTimeMs - userOffsetMs))
                        }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = fontWeight,
                            fontSize = fontSize,
                            color = animatedTextColor,
                            textAlign = TextAlign.Start,
                            lineHeight = if (isActive) 30.sp else 26.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Floating Spotify "Sync to music" capsule button if user scrolled away
        val showSyncCapsule = isUserBrowsing && activeLineIndex in lines.indices &&
                (listState.firstVisibleItemIndex !in (activeLineIndex - 2)..(activeLineIndex + 2))

        AnimatedVisibility(
            visible = showSyncCapsule,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.88f),
                shape = CircleShape,
                modifier = Modifier.clickable {
                    lastUserInteractionTimeMs = 0L
                    coroutineScope.launch {
                        listState.animateScrollToItem(
                            index = activeLineIndex,
                            scrollOffset = -140
                        )
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync to music",
                        tint = SpotifyGreen,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Sync to music",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Spotify Plain Text Lyrics Fallback
 */
@Composable
private fun SpotifyPlainLyricsList(plainLyrics: String) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = 20.dp, bottom = 80.dp, start = 4.dp, end = 4.dp)
    ) {
        Text(
            text = plainLyrics,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.88f),
                textAlign = TextAlign.Start,
                lineHeight = 28.sp,
                fontFamily = FontFamily.SansSerif
            )
        )
    }
}

/**
 * Spotify Instrumental Track Card
 */
@Composable
private fun SpotifyInstrumentalView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFF2C2C2C)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = SpotifyGreen,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "This song is an instrumental",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Enjoy the music!",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            ),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Spotify Lyrics Loading Skeleton with pulsing gray bars
 */
@Composable
private fun SpotifyLyricsLoadingSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 40.dp, start = 4.dp, end = 4.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        val barFractions = listOf(0.85f, 0.65f, 0.92f, 0.50f, 0.78f, 0.60f)
        barFractions.forEach { fraction ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(20.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF383838).copy(alpha = alpha))
            )
        }
    }
}

/**
 * Spotify Lyrics Unavailable / Error View
 */
@Composable
private fun SpotifyLyricsUnavailableView(
    errorMessage: String?,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Couldn't load lyrics for this song",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Check your connection or try again later",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.5.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White)
                .clickable { onRetry() }
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry",
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Try again",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontSize = 13.sp
                    )
                )
            }
        }
    }
}
