package com.satvik.satvikx.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.ui.components.AddToPlaylistDialog
import com.satvik.satvikx.ui.components.OpticallyCenteredPlayIcon
import com.satvik.satvikx.ui.components.TrackActionBottomSheet
import com.satvik.satvikx.ui.components.TrackItem
import com.satvik.satvikx.ui.components.isolateHorizontalScroll
import com.satvik.satvikx.ui.theme.SpotifyGreen
import com.satvik.satvikx.ui.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val recentTracks by viewModel.recentTracks.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "A-Z", "By Artist"
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var selectedTrackForOptions by remember { mutableStateOf<TrackEntity?>(null) }
    var trackForAddToPlaylist by remember { mutableStateOf<TrackEntity?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Filter and search history
    val filteredTracks = remember(recentTracks, searchQuery, selectedFilter) {
        var list = recentTracks
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
            }
        }
        when (selectedFilter) {
            "A-Z" -> list.sortedBy { it.title.lowercase() }
            "By Artist" -> list.sortedBy { it.artist.lowercase() }
            else -> list
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(Color(0xFF121212))
    ) {
        // 1. Spotify Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Recently played",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )

            if (recentTracks.isNotEmpty()) {
                IconButton(onClick = { showClearConfirmDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear History",
                        tint = Color(0xFFA7A7A7),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 2. Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .height(42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF282828))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color(0xFFA7A7A7),
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(SpotifyGreen),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Find in recently played",
                                style = TextStyle(
                                    color = Color(0xFFA7A7A7),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        innerTextField()
                    }
                )

                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = Color(0xFFA7A7A7),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 3. Sort Filter Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .isolateHorizontalScroll()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "A-Z", "By Artist").forEach { filter ->
                val isSelected = selectedFilter == filter
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) SpotifyGreen else Color(0xFF282828),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { selectedFilter = filter }
                ) {
                    Text(
                        text = filter,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp
                        ),
                        color = if (isSelected) Color.Black else Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // 4. Action Row (Track Count, Shuffle, and 52dp Play Button)
        if (recentTracks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${filteredTracks.size} songs",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    ),
                    color = Color(0xFFA7A7A7)
                )

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = {
                        if (filteredTracks.isNotEmpty()) {
                            viewModel.playTrack(filteredTracks.random(), filteredTracks)
                            Toast.makeText(context, "Shuffle playing recent tracks", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = Color(0xFFA7A7A7),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(SpotifyGreen)
                        .clickable {
                            if (filteredTracks.isNotEmpty()) {
                                viewModel.playTrack(filteredTracks.first(), filteredTracks)
                                Toast.makeText(context, "Playing recent tracks", Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    OpticallyCenteredPlayIcon(
                        modifier = Modifier.size(20.dp),
                        tint = Color.Black
                    )
                }
            }
        }

        // 5. Track List or Empty State
        if (recentTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF282828)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = Color(0xFFA7A7A7),
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "No listening history yet",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Tracks, mixes, and playlists you play will show up here.",
                        color = Color(0xFFA7A7A7),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else if (filteredTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No songs matching \"$searchQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA7A7A7)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(
                    items = filteredTracks,
                    key = { it.id }
                ) { track ->
                    TrackItem(
                        track = track,
                        onClick = { viewModel.playTrack(track, filteredTracks) },
                        onOptionClick = { selectedTrackForOptions = track },
                        onDownloadClick = {
                            viewModel.downloadTrack(track)
                            Toast.makeText(context, "Download started for ${track.title}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // Clear History Confirmation Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            containerColor = Color(0xFF282828),
            title = {
                Text(
                    text = "Clear listening history?",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = "This will remove all songs from your listening history. Your saved library and playlists will not be affected.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA7A7A7)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearRecentHistory()
                        showClearConfirmDialog = false
                        Toast.makeText(context, "Listening history cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE91429),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Clear", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = Color(0xFFA7A7A7))
                }
            }
        )
    }

    // Action Bottom Sheet
    selectedTrackForOptions?.let { track ->
        TrackActionBottomSheet(
            track = track,
            sheetState = sheetState,
            onDismiss = { selectedTrackForOptions = null },
            onPlayNow = { viewModel.playTrack(it, recentTracks) },
            onPlayNext = {
                viewModel.playNext(it)
                Toast.makeText(context, "Playing next: ${it.title}", Toast.LENGTH_SHORT).show()
            },
            onAddToQueue = {
                viewModel.addTrackToQueue(it)
                Toast.makeText(context, "Added to queue: ${it.title}", Toast.LENGTH_SHORT).show()
            },
            onDownload = {
                viewModel.downloadTrack(it)
                Toast.makeText(context, "Downloading ${it.title}", Toast.LENGTH_SHORT).show()
            },
            onAddToPlaylist = { trackForAddToPlaylist = it },
            onRemoveFromHistory = { trackToRemove ->
                viewModel.removeFromHistory(trackToRemove.id)
                Toast.makeText(context, "Removed from history", Toast.LENGTH_SHORT).show()
            },
            onDeleteDownload = { trackToDelete ->
                viewModel.deleteDownload(trackToDelete.id)
                Toast.makeText(context, "Deleted ${trackToDelete.title} from offline storage", Toast.LENGTH_SHORT).show()
            },
            onShare = { /* Share */ }
        )
    }

    if (trackForAddToPlaylist != null) {
        AddToPlaylistDialog(
            track = trackForAddToPlaylist,
            viewModel = viewModel,
            onDismiss = { trackForAddToPlaylist = null }
        )
    }
}
