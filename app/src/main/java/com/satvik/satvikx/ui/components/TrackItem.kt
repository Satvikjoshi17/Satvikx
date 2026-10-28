package com.satvik.satvikx.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.ui.theme.PrimaryNeon
import java.util.Locale

@Composable
fun TrackItem(
    track: TrackEntity,
    onClick: () -> Unit,
    onOptionClick: () -> Unit,
    onDownloadClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val durationText = formatDuration(track.durationSeconds)
    val accessibilityLabel = "Play ${track.title} by ${track.artist}, duration $durationText${if (track.isDownloaded) ", downloaded offline" else ""}"
    val isNarrowScreen = LocalConfiguration.current.screenWidthDp < 360
    val thumbSize = if (isNarrowScreen) 44.dp else 52.dp
    val actionBtnSize = if (isNarrowScreen) 38.dp else 46.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            .clickable(
                onClick = onClick,
                onClickLabel = "Play track"
            )
            .padding(horizontal = if (isNarrowScreen) 10.dp else 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with fallback
        Box(
            modifier = Modifier
                .size(thumbSize)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.matchParentSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (track.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        // Title & Artist
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics {
                    contentDescription = accessibilityLabel
                    role = Role.Button
                }
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                if (track.isDownloaded) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Downloaded for offline playback",
                        tint = PrimaryNeon,
                        modifier = Modifier
                            .size(14.dp)
                            .padding(end = 4.dp)
                    )
                }
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = " • $durationText",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Quick Download Action
        if (!track.isDownloaded && onDownloadClick != null) {
            IconButton(
                onClick = onDownloadClick,
                modifier = Modifier.size(actionBtnSize)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download ${track.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (isNarrowScreen) 18.dp else 22.dp)
                )
            }
        }

        // Quick Delete Action (for Downloads screen)
        if (onDeleteClick != null) {
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(actionBtnSize)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete ${track.title} from offline storage",
                    tint = Color(0xFFFF5252).copy(alpha = 0.85f),
                    modifier = Modifier.size(if (isNarrowScreen) 18.dp else 22.dp)
                )
            }
        }

        IconButton(
            onClick = onOptionClick,
            modifier = Modifier.size(actionBtnSize)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Options for ${track.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(if (isNarrowScreen) 20.dp else 24.dp)
            )
        }
    }
}

fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0:00"
    val minutes = seconds / 60
    val remSeconds = seconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, remSeconds)
}
