package com.satvik.satvikx.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.satvik.satvikx.ui.theme.ArcCyan
import com.satvik.satvikx.ui.theme.ArcCyanBright
import com.satvik.satvikx.ui.theme.ArcCyanGlow
import com.satvik.satvikx.ui.theme.StarkBorder
import com.satvik.satvikx.ui.theme.StarkCarbon
import com.satvik.satvikx.ui.theme.StarkGold
import com.satvik.satvikx.ui.theme.StarkSurface
import com.satvik.satvikx.ui.theme.TextPrimary
import com.satvik.satvikx.ui.theme.TextSecondary
import com.satvik.satvikx.ui.viewmodel.LyricsUiState
import kotlinx.coroutines.launch

/**
 * Tony Stark / J.A.R.V.I.S. Holographic Karaoke Synced Lyrics View.
 *
 * Features:
 * - Dynamic 0ms cached vs live streamed lyrics presentation.
 * - Micro-second accurate time synchronization with active line glow highlight.
 * - Interactive tap-to-seek to jump playback directly to any lyric timestamp.
 * - Smooth intelligent auto-scroll with 4-second manual override pause.
 * - Floating "Resume Sync" HUD chip when user scrolls away from active line.
 * - Clean plain-text fallback and instrumental track status badges.
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
            .clip(RoundedCornerShape(20.dp))
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        ArcCyan.copy(alpha = 0.35f),
                        StarkBorder,
                        ArcCyan.copy(alpha = 0.15f)
                    )
                ),
                RoundedCornerShape(20.dp)
            ),
        color = StarkSurface.copy(alpha = 0.85f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top HUD Telemetry Bar with Sync Calibration Control
            LyricsTopHudBar(
                lyrics = lyrics,
                isLoading = isLoading,
                userOffsetMs = userOffsetMs,
                isCalibrateOpen = isCalibrateOpen,
                onToggleCalibrate = { isCalibrateOpen = !isCalibrateOpen }
            )

            // Animated Micro Calibration Bar
            AnimatedVisibility(visible = isCalibrateOpen && lyrics?.isSynced == true) {
                LyricsCalibrationControls(
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

            // Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoading && lyrics == null -> {
                        LyricsLoadingView()
                    }

                    lyrics != null && lyrics.lines.isEmpty() && lyrics.plainLyrics == null -> {
                        // Instrumental Track
                        InstrumentalLyricsView()
                    }

                    lyrics != null && lyrics.isSynced && lyrics.lines.isNotEmpty() -> {
                        // Time-synced Karaoke Lines with Calibrated Position
                        SyncedLinesContent(
                            lines = lyrics.lines,
                            currentPositionMs = effectivePositionMs,
                            userOffsetMs = userOffsetMs,
                            onSeekTo = onSeekTo
                        )
                    }

                    lyrics != null && !lyrics.plainLyrics.isNullOrBlank() -> {
                        // Plain Text Lyrics Fallback
                        PlainLyricsContent(plainLyrics = lyrics.plainLyrics)
                    }

                    else -> {
                        // Empty / Not Found / Error State
                        LyricsErrorView(
                            errorMessage = error ?: "NO_TELEMETRY_FOUND",
                            onRetry = onRetry
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsTopHudBar(
    lyrics: LyricsData?,
    isLoading: Boolean,
    userOffsetMs: Long = 0L,
    isCalibrateOpen: Boolean = false,
    onToggleCalibrate: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Holographic Provider Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isLoading) StarkGold else ArcCyanBright)
            )
            val badgeText = when {
                isLoading -> "SCANNING TELEMETRY..."
                lyrics == null -> "OFFLINE ARCHIVE"
                lyrics.isSynced -> "HOLO-SYNC • ${lyrics.provider}"
                lyrics.lines.isEmpty() && lyrics.plainLyrics == null -> "INSTRUMENTAL CORE"
                else -> "TEXT TELEMETRY • ${lyrics.provider}"
            }
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = if (isLoading) StarkGold else ArcCyanBright
                )
            )
        }

        // Action Controls: Sync Calibration Trigger & Audio Indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (lyrics?.isSynced == true) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCalibrateOpen) ArcCyanGlow else if (userOffsetMs != 0L) StarkGold.copy(alpha = 0.20f) else StarkCarbon,
                    border = BorderStroke(1.dp, if (userOffsetMs != 0L) StarkGold.copy(alpha = 0.65f) else ArcCyan.copy(alpha = 0.40f)),
                    modifier = Modifier.clickable { onToggleCalibrate() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Sync Offset Calibration",
                            tint = if (userOffsetMs != 0L) StarkGold else ArcCyanBright,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (userOffsetMs == 0L) "SYNC" else "${if (userOffsetMs > 0) "+" else ""}${"%.1f".format(java.util.Locale.US, userOffsetMs / 1000f)}s",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = if (userOffsetMs != 0L) StarkGold else ArcCyanBright
                            )
                        )
                    }
                }
            }

            Icon(
                imageVector = if (lyrics?.isSynced == true) Icons.Default.GraphicEq else Icons.Default.Mic,
                contentDescription = null,
                tint = ArcCyan.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun LyricsCalibrationControls(
    userOffsetMs: Long,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(StarkCarbon.copy(alpha = 0.92f))
            .border(1.dp, ArcCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "CALIBRATION",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                color = TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // -0.5s Delay
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = ArcCyan.copy(alpha = 0.15f),
                modifier = Modifier.clickable { onAdjustOffset(-500L) }
            ) {
                Text(
                    text = "-0.5s",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ArcCyanBright,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                )
            }

            // Current Offset / Tap to Reset
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (userOffsetMs != 0L) StarkGold.copy(alpha = 0.25f) else StarkSurface,
                modifier = Modifier.clickable { onResetOffset() }
            ) {
                Text(
                    text = if (userOffsetMs == 0L) "0.0s" else "${if (userOffsetMs > 0) "+" else ""}${"%.1f".format(java.util.Locale.US, userOffsetMs / 1000f)}s",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (userOffsetMs != 0L) StarkGold else TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                )
            }

            // +0.5s Advance
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = ArcCyan.copy(alpha = 0.15f),
                modifier = Modifier.clickable { onAdjustOffset(500L) }
            ) {
                Text(
                    text = "+0.5s",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ArcCyanBright,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun SyncedLinesContent(
    lines: List<LrcLine>,
    currentPositionMs: Long,
    userOffsetMs: Long = 0L,
    onSeekTo: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val activeLineIndex = remember(lines, currentPositionMs) {
        LrcParser.findActiveLineIndex(lines, currentPositionMs)
    }

    // Manual scroll detection: Pause auto-scroll for 4s if user drags
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

    // Smooth auto-scroll to center the active line
    LaunchedEffect(activeLineIndex, isUserBrowsing) {
        if (!isUserBrowsing && activeLineIndex in lines.indices) {
            listState.animateScrollToItem(
                index = activeLineIndex,
                scrollOffset = -180
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 120.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(lines, key = { index, item -> "${index}_${item.startTimeMs}" }) { index, line ->
                val isActive = index == activeLineIndex
                val isPast = activeLineIndex != -1 && index < activeLineIndex

                val targetColor = when {
                    isActive -> ArcCyanBright
                    isPast -> TextSecondary.copy(alpha = 0.40f)
                    else -> TextPrimary.copy(alpha = 0.75f)
                }

                val animatedTextColor by animateColorAsState(
                    targetValue = targetColor,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    label = "lyric_color"
                )

                val animatedBgColor by animateColorAsState(
                    targetValue = if (isActive) ArcCyanGlow else Color.Transparent,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    label = "lyric_bg"
                )

                val animatedBorderColor by animateColorAsState(
                    targetValue = if (isActive) ArcCyan.copy(alpha = 0.35f) else Color.Transparent,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                    label = "lyric_border"
                )

                val animatedScale by animateFloatAsState(
                    targetValue = if (isActive) 1.05f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "lyric_scale"
                )

                val fontSize = when {
                    isActive -> 20.sp
                    isPast -> 16.sp
                    else -> 17.sp
                }

                val fontWeight = when {
                    isActive -> FontWeight.ExtraBold
                    else -> FontWeight.Medium
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(animatedBgColor)
                        .border(1.dp, animatedBorderColor, RoundedCornerShape(12.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSeekTo(maxOf(0L, line.startTimeMs - userOffsetMs))
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = fontWeight,
                            fontSize = fontSize,
                            color = animatedTextColor,
                            textAlign = TextAlign.Center,
                            lineHeight = 28.sp
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Floating "Resume Sync" Button if user scrolled away
        val showResumeButton = isUserBrowsing && activeLineIndex in lines.indices &&
                (listState.firstVisibleItemIndex !in (activeLineIndex - 2)..(activeLineIndex + 2))

        AnimatedVisibility(
            visible = showResumeButton,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            Surface(
                color = StarkCarbon.copy(alpha = 0.92f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .border(1.dp, ArcCyanBright, RoundedCornerShape(20.dp))
                    .clickable {
                        lastUserInteractionTimeMs = 0L
                        coroutineScope.launch {
                            listState.animateScrollToItem(
                                index = activeLineIndex,
                                scrollOffset = -180
                            )
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Resume Sync",
                        tint = ArcCyanBright,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "SYNC TO AUDIO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = ArcCyanBright
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PlainLyricsContent(plainLyrics: String) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = plainLyrics,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    color = TextPrimary.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp,
                    fontFamily = FontFamily.SansSerif
                )
            )
        }
    }
}

@Composable
private fun InstrumentalLyricsView() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(90.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = ArcCyan.copy(alpha = pulseAlpha * 0.4f),
                    radius = size.minDimension / 2f,
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = StarkGold.copy(alpha = pulseAlpha),
                    radius = size.minDimension / 2.8f,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = ArcCyanBright,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "INSTRUMENTAL COMPOSITION",
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                color = ArcCyanBright,
                letterSpacing = 1.2.sp
            )
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "No vocal telemetry detected in audio stream.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            ),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LyricsLoadingView() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.sweepGradient(listOf(ArcCyan, StarkGold, Color.Transparent)),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = ArcCyanBright,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "ACQUIRING SATELLITE TELEMETRY...",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = ArcCyanBright,
                letterSpacing = 1.sp
            )
        )
    }
}

@Composable
private fun LyricsErrorView(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            tint = TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(44.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "LYRICS UNAVAILABLE",
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = TextSecondary
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "No matched lyrics in global catalog.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextSecondary.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = StarkSurface,
                contentColor = ArcCyanBright
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.border(1.dp, ArcCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "RETRY SCAN",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

