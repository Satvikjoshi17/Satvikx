package com.satvik.satvikx.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.satvik.satvikx.playback.model.PlaybackState
import com.satvik.satvikx.ui.theme.ArcCyan
import com.satvik.satvikx.ui.theme.ArcCyanBright
import com.satvik.satvikx.ui.theme.StarkBorder
import com.satvik.satvikx.ui.theme.StarkCarbon
import com.satvik.satvikx.ui.theme.StarkGold
import com.satvik.satvikx.ui.theme.StarkSurface
import com.satvik.satvikx.ui.theme.TextSecondary
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape

@Composable
fun BottomMiniPlayer(
    playbackState: PlaybackState,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = playbackState.currentTrack ?: return

    val isNarrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360
    val thumbSize = if (isNarrow) 40.dp else 46.dp
    val actionBtnSize = if (isNarrow) 40.dp else 48.dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (isNarrow) 6.dp else 10.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, StarkBorder, RoundedCornerShape(12.dp))
            .clickable(
                onClick = onExpand,
                onClickLabel = "Open Stark Arc player"
            ),
        color = StarkSurface,
        shadowElevation = 10.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .padding(horizontal = if (isNarrow) 8.dp else 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Arc Reactor Album Thumbnail
                Box(
                    modifier = Modifier
                        .size(thumbSize)
                        .clip(CircleShape)
                        .border(1.5.dp, if (playbackState.isPlaying) ArcCyan else StarkBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(if (isNarrow) 8.dp else 12.dp))

                // Track Title & Artist
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            contentDescription = "Currently playing ${track.title} by ${track.artist}"
                            role = Role.Button
                        }
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isNarrow) 13.sp else 14.sp
                        ),
                        maxLines = 1,
                        color = Color.White,
                        modifier = Modifier.basicMarquee()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = if (isNarrow) 11.sp else 12.sp,
                                color = TextSecondary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "• 320K",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ArcCyanBright
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Play / Pause / Buffering Action
                Box(
                    modifier = Modifier.size(actionBtnSize),
                    contentAlignment = Alignment.Center
                ) {
                    if (playbackState.isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = ArcCyanBright,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier.size(actionBtnSize)
                        ) {
                            Icon(
                                imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                tint = ArcCyanBright,
                                modifier = Modifier.size(if (isNarrow) 24.dp else 30.dp)
                            )
                        }
                    }
                }

                // Skip Next
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.size(actionBtnSize)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Skip next",
                        tint = Color.White,
                        modifier = Modifier.size(if (isNarrow) 22.dp else 28.dp)
                    )
                }
            }

            // Real-time Stark Progress Indicator Bar
            LinearProgressIndicator(
                progress = { playbackState.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp),
                color = ArcCyan,
                trackColor = StarkCarbon
            )
        }
    }
}
