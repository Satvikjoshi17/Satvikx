package com.satvik.satvikx.ui.screens

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import com.satvik.satvikx.ui.components.DownloadQualityDialog
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.satvik.satvikx.data.local.entity.PlaylistWithTracks
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.ui.components.AddToPlaylistDialog
import com.satvik.satvikx.ui.components.TrackActionBottomSheet
import com.satvik.satvikx.ui.components.TrackItem
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.satvik.satvikx.ui.theme.ArcCyan
import com.satvik.satvikx.ui.theme.ArcCyanBright
import com.satvik.satvikx.ui.theme.StarkCarbon
import com.satvik.satvikx.ui.theme.StarkGold
import com.satvik.satvikx.ui.theme.PrimaryNeon
import com.satvik.satvikx.ui.theme.SurfaceDark
import com.satvik.satvikx.ui.theme.SurfaceElevated
import com.satvik.satvikx.ui.theme.SurfaceVariantDark
import com.satvik.satvikx.ui.theme.TextPrimary
import com.satvik.satvikx.ui.theme.TextSecondary
import com.satvik.satvikx.ui.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }

    // Dialog & Sheet States
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<PlaylistWithTracks?>(null) }
    var playlistToDownload by remember { mutableStateOf<PlaylistWithTracks?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var newPlaylistName by remember { mutableStateOf("") }

    // 3-Dots Track Action Sheet State
    var selectedTrackForOptions by remember { mutableStateOf<TrackEntity?>(null) }
    var trackForAddToAnotherPlaylist by remember { mutableStateOf<TrackEntity?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val activePlaylist = playlists.find { it.playlist.playlistId == selectedPlaylistId }

    if (activePlaylist != null) {
        // ==========================================
        // PLAYLIST DETAIL VIEW (CODING & VISION THEME)
        // ==========================================
        var showPlaylistHeaderMenu by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedPlaylistId = null }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "// PLAYLIST: ${activePlaylist.playlist.name.uppercase()}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        ),
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "[ TRACKS: ${String.format("%02d", activePlaylist.tracks.size)} ]",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = PrimaryNeon
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• LOCAL_SQLITE_STORE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = TextSecondary
                        )
                    }
                }

                // Play All Button (Neon Cyber Play)
                if (activePlaylist.tracks.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            viewModel.playPlaylist(activePlaylist)
                            Toast.makeText(context, "Playing ${activePlaylist.playlist.name}", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Play All",
                            tint = PrimaryNeon,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Shuffle Play Button
                    IconButton(
                        onClick = {
                            viewModel.playPlaylistShuffled(activePlaylist)
                            Toast.makeText(context, "Shuffle playing ${activePlaylist.playlist.name}", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle Play",
                            tint = ArcCyanBright,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Download Full Playlist Button
                    IconButton(
                        onClick = {
                            playlistToDownload = activePlaylist
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download Playlist",
                            tint = PrimaryNeon,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // 3-Dots Playlist Options Menu
                Box {
                    IconButton(onClick = { showPlaylistHeaderMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Playlist Options",
                            tint = TextSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = showPlaylistHeaderMenu,
                        onDismissRequest = { showPlaylistHeaderMenu = false }
                    ) {
                        if (activePlaylist.tracks.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Download All Tracks", fontFamily = FontFamily.Monospace) },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = PrimaryNeon) },
                                onClick = {
                                    showPlaylistHeaderMenu = false
                                    playlistToDownload = activePlaylist
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Shuffle Play", fontFamily = FontFamily.Monospace) },
                                leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null, tint = ArcCyanBright) },
                                onClick = {
                                    showPlaylistHeaderMenu = false
                                    viewModel.playPlaylistShuffled(activePlaylist)
                                    Toast.makeText(context, "Shuffle playing ${activePlaylist.playlist.name}", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add All to Queue", fontFamily = FontFamily.Monospace) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = PrimaryNeon) },
                                onClick = {
                                    showPlaylistHeaderMenu = false
                                    activePlaylist.tracks.forEach { viewModel.addTrackToQueue(it) }
                                    Toast.makeText(context, "Added ${activePlaylist.tracks.size} tracks to queue", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Rename Playlist", fontFamily = FontFamily.Monospace) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = PrimaryNeon) },
                            onClick = {
                                showPlaylistHeaderMenu = false
                                playlistToRename = activePlaylist
                                renameInput = activePlaylist.playlist.name
                            }
                        )
                        if (activePlaylist.tracks.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Clear All Tracks", fontFamily = FontFamily.Monospace) },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFFFB74D)) },
                                onClick = {
                                    showPlaylistHeaderMenu = false
                                    viewModel.clearPlaylist(activePlaylist.playlist.playlistId)
                                    Toast.makeText(context, "Cleared tracks", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete Playlist", fontFamily = FontFamily.Monospace, color = Color(0xFFFF5252)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252)) },
                            onClick = {
                                showPlaylistHeaderMenu = false
                                selectedPlaylistId = null
                                viewModel.deletePlaylist(activePlaylist.playlist.playlistId)
                                Toast.makeText(context, "Deleted playlist", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            HorizontalDivider(
                color = SurfaceVariantDark,
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Playlist Track List
            if (activePlaylist.tracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SurfaceElevated,
                            modifier = Modifier
                                .size(72.dp)
                                .border(1.dp, SurfaceVariantDark, RoundedCornerShape(12.dp))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = PrimaryNeon,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "[ PLAYLIST_BUFFER_EMPTY ]",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Add tracks from Search, Downloads, or Recents using the 3-dots action menu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = activePlaylist.tracks,
                        key = { _, item -> item.id }
                    ) { index, track ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Monospace Track Index Counter (Coding Style: 01, 02, 03...)
                            Text(
                                text = String.format("%02d", index + 1),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary.copy(alpha = 0.6f)
                                ),
                                modifier = Modifier.padding(start = 16.dp, end = 2.dp)
                            )

                            Box(modifier = Modifier.weight(1f)) {
                                TrackItem(
                                    track = track,
                                    onClick = { viewModel.playTrack(track, activePlaylist.tracks) },
                                    onOptionClick = {
                                        selectedTrackForOptions = track
                                    },
                                    onDownloadClick = {
                                        viewModel.downloadTrack(track)
                                        Toast.makeText(context, "Download enqueued", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // ==========================================
        // PLAYLISTS COLLECTION LIST (CODING & VISION)
        // ==========================================
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = ArcCyan,
                    contentColor = StarkCarbon,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(1.dp, ArcCyanBright, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "New Playlist")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "NEW PLAYLIST",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
            ) {
                // Header (Stark HUD Aesthetic)
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Playlists",
                        style = MaterialTheme.typography.displayMedium,
                        color = TextPrimary
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
                            text = "J.A.R.V.I.S. // STARK AUDIO DIRECTORY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp,
                                color = ArcCyan
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ${playlists.size} VAULTS",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = StarkGold
                        )
                    }
                }

                HorizontalDivider(
                    color = SurfaceVariantDark,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = SurfaceElevated,
                                modifier = Modifier
                                    .size(72.dp)
                                    .border(1.dp, SurfaceVariantDark, RoundedCornerShape(14.dp))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = PrimaryNeon,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "// NO_PLAYLISTS_ALLOCATED",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create custom playlists with the + NEW_PLAYLIST button below.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = playlists,
                            key = { it.playlist.playlistId }
                        ) { playlistWithTracks ->
                            var showRowMenu by remember { mutableStateOf(false) }

                            PlaylistCyberRow(
                                playlistWithTracks = playlistWithTracks,
                                onClick = { selectedPlaylistId = playlistWithTracks.playlist.playlistId },
                                onPlayAll = {
                                    if (playlistWithTracks.tracks.isNotEmpty()) {
                                        viewModel.playPlaylist(playlistWithTracks)
                                        Toast.makeText(context, "Playing ${playlistWithTracks.playlist.name}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onShufflePlay = {
                                    if (playlistWithTracks.tracks.isNotEmpty()) {
                                        viewModel.playPlaylistShuffled(playlistWithTracks)
                                        Toast.makeText(context, "Shuffle playing ${playlistWithTracks.playlist.name}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onDownload = {
                                    playlistToDownload = playlistWithTracks
                                },
                                onMenuClick = { showRowMenu = true },
                                isMenuExpanded = showRowMenu,
                                onDismissMenu = { showRowMenu = false },
                                onRename = {
                                    showRowMenu = false
                                    playlistToRename = playlistWithTracks
                                    renameInput = playlistWithTracks.playlist.name
                                },
                                onClear = {
                                    showRowMenu = false
                                    viewModel.clearPlaylist(playlistWithTracks.playlist.playlistId)
                                    Toast.makeText(context, "Cleared tracks", Toast.LENGTH_SHORT).show()
                                },
                                onDelete = {
                                    showRowMenu = false
                                    viewModel.deletePlaylist(playlistWithTracks.playlist.playlistId)
                                    Toast.makeText(context, "Deleted ${playlistWithTracks.playlist.name}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 3-DOTS TRACK OPTIONS BOTTOM SHEET
    // ==========================================
    if (selectedTrackForOptions != null) {
        TrackActionBottomSheet(
            track = selectedTrackForOptions,
            sheetState = sheetState,
            onDismiss = { selectedTrackForOptions = null },
            onPlayNow = {
                viewModel.playTrack(it, activePlaylist?.tracks.orEmpty())
            },
            onPlayNext = {
                viewModel.playNext(it)
                Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
            },
            onAddToQueue = {
                viewModel.addTrackToQueue(it)
                Toast.makeText(context, "Added to playback queue", Toast.LENGTH_SHORT).show()
            },
            onDownload = {
                viewModel.downloadTrack(it)
                Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show()
            },
            onAddToPlaylist = {
                trackForAddToAnotherPlaylist = it
            },
            onShare = {
                Toast.makeText(context, "Track ID: ${it.id}", Toast.LENGTH_SHORT).show()
            },
            onRemoveFromPlaylist = if (activePlaylist != null) {
                { trackToRemove ->
                    viewModel.removeTrackFromPlaylist(activePlaylist.playlist.playlistId, trackToRemove.id)
                    Toast.makeText(context, "Removed from ${activePlaylist.playlist.name}", Toast.LENGTH_SHORT).show()
                }
            } else null
        )
    }

    // Add To Another Playlist Dialog
    if (trackForAddToAnotherPlaylist != null) {
        AddToPlaylistDialog(
            track = trackForAddToAnotherPlaylist,
            viewModel = viewModel,
            onDismiss = { trackForAddToAnotherPlaylist = null }
        )
    }

    // Create New Playlist Dialog (Coding Vision UI)
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    text = "// INIT_PLAYLIST",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = PrimaryNeon
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter an identifier for this playlist buffer:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("e.g. Cyber Synth 2026", color = Color.Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryNeon,
                            unfocusedBorderColor = SurfaceVariantDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName.trim())
                            Toast.makeText(context, "Playlist initialized", Toast.LENGTH_SHORT).show()
                            newPlaylistName = ""
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text(
                        "[ EXEC_CREATE ]",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNeon
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("[ CANCEL ]", fontFamily = FontFamily.Monospace, color = Color.Gray)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // Rename Playlist Dialog (Coding Vision UI)
    if (playlistToRename != null) {
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = {
                Text(
                    text = "// RENAME_PLAYLIST",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = PrimaryNeon
                )
            },
            text = {
                Column {
                    Text(
                        text = "Modify playlist identifier:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryNeon,
                            unfocusedBorderColor = SurfaceVariantDark,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            viewModel.renamePlaylist(playlistToRename!!.playlist.playlistId, renameInput.trim())
                            Toast.makeText(context, "Playlist renamed", Toast.LENGTH_SHORT).show()
                            playlistToRename = null
                        }
                    }
                ) {
                    Text(
                        "[ UPDATE ]",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNeon
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) {
                    Text("[ CANCEL ]", fontFamily = FontFamily.Monospace, color = Color.Gray)
                }
            },
            containerColor = SurfaceDark
        )
    }

    // Download Playlist Quality Dialog
    if (playlistToDownload != null) {
        val currentQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
        DownloadQualityDialog(
            initialQuality = currentQuality,
            title = "Download Playlist",
            subtitle = "// BUFFER: ${playlistToDownload!!.playlist.name.uppercase()}",
            trackCount = playlistToDownload!!.tracks.size,
            confirmButtonText = "Download All",
            onConfirm = { quality, setAsDefault ->
                if (setAsDefault) {
                    viewModel.setDownloadQuality(quality)
                }
                viewModel.downloadPlaylist(playlistToDownload!!, quality)
                Toast.makeText(
                    context,
                    "Downloading ${playlistToDownload!!.tracks.size} tracks (${quality.label})",
                    Toast.LENGTH_LONG
                ).show()
                playlistToDownload = null
            },
            onDismiss = { playlistToDownload = null }
        )
    }
}

@Composable
private fun PlaylistCyberRow(
    playlistWithTracks: PlaylistWithTracks,
    onClick: () -> Unit,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onDownload: () -> Unit,
    onMenuClick: () -> Unit,
    isMenuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = SurfaceElevated,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .size(54.dp)
                .border(1.dp, SurfaceVariantDark, RoundedCornerShape(10.dp))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = PrimaryNeon,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlistWithTracks.playlist.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = TextPrimary,
                maxLines = 1
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = "[ ${String.format("%02d", playlistWithTracks.tracks.size)} TRACKS ]",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = PrimaryNeon
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ID: #PL_${playlistWithTracks.playlist.playlistId}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextSecondary
                )
            }
        }

        // Quick Shuffle button right on the row if tracks are present
        if (playlistWithTracks.tracks.isNotEmpty()) {
            IconButton(onClick = onShufflePlay) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle Playlist",
                    tint = ArcCyanBright,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 3-Dots Action Menu for each playlist card
        Box {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = TextSecondary
                )
            }

            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = onDismissMenu
            ) {
                if (playlistWithTracks.tracks.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Download All", fontFamily = FontFamily.Monospace) },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = PrimaryNeon) },
                        onClick = {
                            onDismissMenu()
                            onDownload()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Play All", fontFamily = FontFamily.Monospace) },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PrimaryNeon) },
                        onClick = {
                            onDismissMenu()
                            onPlayAll()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Shuffle Play", fontFamily = FontFamily.Monospace) },
                        leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null, tint = ArcCyanBright) },
                        onClick = {
                            onDismissMenu()
                            onShufflePlay()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Rename", fontFamily = FontFamily.Monospace) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = PrimaryNeon) },
                    onClick = onRename
                )
                if (playlistWithTracks.tracks.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Clear Tracks", fontFamily = FontFamily.Monospace) },
                        leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFFFB74D)) },
                        onClick = onClear
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete", fontFamily = FontFamily.Monospace, color = Color(0xFFFF5252)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252)) },
                    onClick = onDelete
                )
            }
        }
    }
}
