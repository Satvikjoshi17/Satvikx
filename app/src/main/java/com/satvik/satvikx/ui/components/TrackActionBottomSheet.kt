package com.satvik.satvikx.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satvik.satvikx.data.local.entity.TrackEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionBottomSheet(
    track: TrackEntity?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onPlayNow: (TrackEntity) -> Unit,
    onPlayNext: ((TrackEntity) -> Unit)? = null,
    onAddToQueue: (TrackEntity) -> Unit,
    onDownload: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onShare: (TrackEntity) -> Unit,
    onRemoveFromPlaylist: ((TrackEntity) -> Unit)? = null,
    onDeleteDownload: ((TrackEntity) -> Unit)? = null
) {
    if (track == null) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Track Header with Coding / Vision metadata HUD
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "[ 320K DIRECT MASTER ]",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = com.satvik.satvikx.ui.theme.ArcCyanBright
                        )
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "STARK AUDIO CORE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = com.satvik.satvikx.ui.theme.StarkGold
                        )
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            )

            ActionItem(
                icon = Icons.Default.PlayArrow,
                title = "Play Now",
                onClick = {
                    onPlayNow(track)
                    onDismiss()
                }
            )

            if (onPlayNext != null) {
                ActionItem(
                    icon = Icons.Default.SkipNext,
                    title = "Play Next",
                    onClick = {
                        onPlayNext(track)
                        onDismiss()
                    }
                )
            }

            ActionItem(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                title = "Add to Queue",
                onClick = {
                    onAddToQueue(track)
                    onDismiss()
                }
            )

            if (!track.isDownloaded) {
                ActionItem(
                    icon = Icons.Default.Download,
                    title = "Download Offline",
                    onClick = {
                        onDownload(track)
                        onDismiss()
                    }
                )
            } else if (onDeleteDownload != null) {
                ActionItem(
                    icon = Icons.Default.Delete,
                    title = "Delete from Downloads",
                    tint = androidx.compose.ui.graphics.Color(0xFFFF5252),
                    onClick = {
                        onDeleteDownload(track)
                        onDismiss()
                    }
                )
            }

            ActionItem(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                title = "Add to Playlist",
                onClick = {
                    onAddToPlaylist(track)
                    onDismiss()
                }
            )

            if (onRemoveFromPlaylist != null) {
                ActionItem(
                    icon = Icons.Default.Delete,
                    title = "Remove from this Playlist",
                    tint = androidx.compose.ui.graphics.Color(0xFFFF5252),
                    onClick = {
                        onRemoveFromPlaylist(track)
                        onDismiss()
                    }
                )
            }

            ActionItem(
                icon = Icons.Default.Share,
                title = "Share Link",
                onClick = {
                    onShare(track)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    title: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
            color = if (tint != MaterialTheme.colorScheme.primary) tint else MaterialTheme.colorScheme.onSurface
        )
    }
}
