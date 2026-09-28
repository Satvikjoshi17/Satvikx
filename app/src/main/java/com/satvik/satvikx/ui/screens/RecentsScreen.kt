package com.satvik.satvikx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.satvik.satvikx.ui.theme.ArcCyan
import com.satvik.satvikx.ui.theme.ArcCyanBright
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.ui.components.TrackActionBottomSheet
import com.satvik.satvikx.ui.components.TrackItem
import com.satvik.satvikx.ui.theme.PrimaryNeon
import com.satvik.satvikx.ui.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val recentTracks by viewModel.recentTracks.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedTrackForOptions by remember { mutableStateOf<TrackEntity?>(null) }
    var trackForAddToPlaylist by remember { mutableStateOf<TrackEntity?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.displayMedium,
                    color = Color.White
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ArcCyanBright)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "J.A.R.V.I.S. // FLIGHT TELEMETRY LOG • ${recentTracks.size} ENTRIES",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            color = ArcCyan
                        )
                    )
                }
            }

            if (recentTracks.isNotEmpty()) {
                IconButton(onClick = { viewModel.clearRecentHistory() }) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear History",
                        tint = Color.Gray
                    )
                }
            }
        }

        if (recentTracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = Color.DarkGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No recent playback history.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    items = recentTracks,
                    key = { it.id }
                ) { track ->
                    TrackItem(
                        track = track,
                        onClick = { viewModel.playTrack(track, recentTracks) },
                        onOptionClick = { selectedTrackForOptions = track },
                        onDownloadClick = {
                            viewModel.downloadTrack(track)
                            android.widget.Toast.makeText(context, "Download started for ${track.title}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    selectedTrackForOptions?.let { track ->
        TrackActionBottomSheet(
            track = track,
            sheetState = sheetState,
            onDismiss = { selectedTrackForOptions = null },
            onPlayNow = { viewModel.playTrack(it, recentTracks) },
            onAddToQueue = { viewModel.playTrack(it) },
            onDownload = {
                viewModel.downloadTrack(it)
                android.widget.Toast.makeText(context, "Downloading ${it.title}", android.widget.Toast.LENGTH_SHORT).show()
            },
            onAddToPlaylist = {
                trackForAddToPlaylist = it
            },
            onShare = { /* Share */ }
        )
    }

    if (trackForAddToPlaylist != null) {
        com.satvik.satvikx.ui.components.AddToPlaylistDialog(
            track = trackForAddToPlaylist,
            viewModel = viewModel,
            onDismiss = { trackForAddToPlaylist = null }
        )
    }
}
