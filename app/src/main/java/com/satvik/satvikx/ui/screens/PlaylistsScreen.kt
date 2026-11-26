package com.satvik.satvikx.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.satvik.satvikx.data.local.entity.BlendEntity
import com.satvik.satvikx.data.local.entity.PlaylistWithTracks
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.entity.isLikedPlaylistName
import com.satvik.satvikx.ui.components.AddToPlaylistDialog
import com.satvik.satvikx.ui.components.CreateBlendDialog
import com.satvik.satvikx.ui.components.CuratedArtistBlendSheet
import com.satvik.satvikx.ui.components.DownloadQualityDialog
import com.satvik.satvikx.ui.components.ImportPlaylistDialog
import com.satvik.satvikx.ui.components.JoinBlendDialog
import com.satvik.satvikx.ui.components.OpticallyCenteredPlayIcon
import com.satvik.satvikx.ui.components.SharePlaylistDialog
import com.satvik.satvikx.ui.components.TrackActionBottomSheet
import com.satvik.satvikx.ui.components.TrackItem
import com.satvik.satvikx.ui.components.isolateHorizontalScroll
import com.satvik.satvikx.ui.theme.SpotifyGreen
import com.satvik.satvikx.ui.viewmodel.AuthViewModel
import com.satvik.satvikx.ui.viewmodel.BlendViewModel
import com.satvik.satvikx.ui.viewmodel.LibraryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    blendViewModel: BlendViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val blends by blendViewModel.blends.collectAsStateWithLifecycle()
    val blendUiState by blendViewModel.uiState.collectAsStateWithLifecycle()
    val authViewModel: AuthViewModel = hiltViewModel()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val pendingSharedPlaylist by viewModel.shareManager.pendingPlaylist.collectAsStateWithLifecycle()
    val pendingBlendToken by blendViewModel.pendingBlendToken.collectAsStateWithLifecycle()

    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    var selectedBlendId by remember { mutableStateOf<String?>(null) }

    // Filter Chips: "All", "Playlists", "Blends", "Downloaded"
    var selectedFilter by remember { mutableStateOf("All") }

    // Blend Hub Sheet & Dialog States
    var showBlendHubSheet by remember { mutableStateOf(false) }
    var showCreateBlendDialog by remember { mutableStateOf(false) }
    var showJoinBlendDialog by remember { mutableStateOf(false) }
    var showArtistBlendSheet by remember { mutableStateOf(false) }
    val blendHubSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val artistSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Dialog & Sheet States
    var showCreateDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var playlistToShare by remember { mutableStateOf<PlaylistWithTracks?>(null) }
    var playlistToRename by remember { mutableStateOf<PlaylistWithTracks?>(null) }
    var playlistToDownload by remember { mutableStateOf<PlaylistWithTracks?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var newPlaylistName by remember { mutableStateOf("") }

    LaunchedEffect(pendingBlendToken) {
        if (!pendingBlendToken.isNullOrBlank()) {
            showJoinBlendDialog = true
        }
    }

    // Track Action Sheet State
    var selectedTrackForOptions by remember { mutableStateOf<TrackEntity?>(null) }
    var trackForAddToAnotherPlaylist by remember { mutableStateOf<TrackEntity?>(null) }
    val trackSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Separate Liked Songs playlist from custom playlists
    val likedSongsPlaylist = remember(playlists) {
        playlists.find { isLikedPlaylistName(it.playlist.name) }
    }
    val customPlaylists = remember(playlists) {
        playlists.filterNot { isLikedPlaylistName(it.playlist.name) || !it.playlist.isCustom }
    }

    val activePlaylist = playlists.find { it.playlist.playlistId == selectedPlaylistId }

    // Back button navigation: Exit detail views cleanly
    BackHandler(enabled = selectedBlendId != null || selectedPlaylistId != null) {
        if (selectedBlendId != null) {
            selectedBlendId = null
        } else {
            selectedPlaylistId = null
        }
    }

    if (selectedBlendId != null) {
        BlendDetailScreen(
            blendId = selectedBlendId!!,
            onBack = { selectedBlendId = null }
        )
    } else if (activePlaylist != null) {
        // ==========================================
        // PURE SPOTIFY PLAYLIST DETAIL VIEW
        // ==========================================
        val isLikedSongs = isLikedPlaylistName(activePlaylist.playlist.name)
        var showPlaylistHeaderMenu by remember { mutableStateOf(false) }

        val totalSeconds = remember(activePlaylist.tracks) {
            activePlaylist.tracks.sumOf { it.durationSeconds }
        }
        val totalPlaytimeText = remember(totalSeconds) {
            if (totalSeconds >= 3600) {
                val hrs = totalSeconds / 3600
                val mins = (totalSeconds % 3600) / 60
                "${hrs} hr ${mins} min"
            } else if (totalSeconds > 0) {
                "${totalSeconds / 60} min"
            } else ""
        }

        val playlistThumbs = remember(activePlaylist.tracks) {
            activePlaylist.tracks
                .mapNotNull { it.thumbnailUrl.takeIf { t -> t.isNotBlank() && t.startsWith("http") } }
                .distinct()
                .take(4)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .background(Color(0xFF121212))
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = { selectedPlaylistId = null }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isLikedSongs && activePlaylist.tracks.isNotEmpty()) {
                        IconButton(onClick = { playlistToShare = activePlaylist }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Playlist",
                                tint = Color.White
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { showPlaylistHeaderMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = Color.White
                            )
                        }

                    DropdownMenu(
                        expanded = showPlaylistHeaderMenu,
                        onDismissRequest = { showPlaylistHeaderMenu = false }
                    ) {
                        if (activePlaylist.tracks.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Download All Tracks") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = SpotifyGreen) },
                                onClick = {
                                    showPlaylistHeaderMenu = false
                                    playlistToDownload = activePlaylist
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Shuffle Play") },
                                leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null, tint = SpotifyGreen) },
                                onClick = {
                                    showPlaylistHeaderMenu = false
                                    viewModel.playPlaylistShuffled(activePlaylist)
                                    Toast.makeText(context, "Shuffle playing ${activePlaylist.playlist.name}", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add All to Queue") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = SpotifyGreen) },
                                onClick = {
                                    showPlaylistHeaderMenu = false
                                    activePlaylist.tracks.forEach { viewModel.addTrackToQueue(it) }
                                    Toast.makeText(context, "Added ${activePlaylist.tracks.size} tracks to queue", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        if (!isLikedSongs) {
                            if (activePlaylist.tracks.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Share Playlist") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = SpotifyGreen) },
                                    onClick = {
                                        showPlaylistHeaderMenu = false
                                        playlistToShare = activePlaylist
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Rename Playlist") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White) },
                                onClick = {
                                    showPlaylistHeaderMenu = false
                                    playlistToRename = activePlaylist
                                    renameInput = activePlaylist.playlist.name
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Playlist", color = Color(0xFFFF5252)) },
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
            }
        }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                item {
                    // Spotify Playlist Hero Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Artwork Cover
                        Box(
                            modifier = Modifier
                                .size(172.dp)
                                .shadow(16.dp, RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isLikedSongs) {
                                        Brush.linearGradient(listOf(Color(0xFF450AF5), Color(0xFF8E8EE5)))
                                    } else {
                                        Brush.linearGradient(listOf(Color(0xFF282828), Color(0xFF181818)))
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLikedSongs) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(68.dp)
                                )
                            } else if (playlistThumbs.size >= 4) {
                                // 2x2 Collage
                                Column(modifier = Modifier.fillMaxSize().background(Color(0xFF242424))) {
                                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                        AsyncImage(
                                            model = playlistThumbs[0],
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        )
                                        AsyncImage(
                                            model = playlistThumbs[1],
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        )
                                    }
                                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                        AsyncImage(
                                            model = playlistThumbs[2],
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        )
                                        AsyncImage(
                                            model = playlistThumbs[3],
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        )
                                    }
                                }
                            } else if (playlistThumbs.isNotEmpty()) {
                                AsyncImage(
                                    model = playlistThumbs.first(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color(0xFFA7A7A7),
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = activePlaylist.playlist.name,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            ),
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "SatvikX",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = " • ${activePlaylist.tracks.size} songs",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = Color(0xFFA7A7A7)
                            )
                            if (totalPlaytimeText.isNotBlank()) {
                                Text(
                                    text = ", $totalPlaytimeText",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    color = Color(0xFFA7A7A7)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Spotify Action Row (Download, 3-dots, Shuffle, and Big Green Play Button)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (activePlaylist.tracks.isNotEmpty()) {
                                IconButton(
                                    onClick = { playlistToDownload = activePlaylist },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download All",
                                        tint = Color(0xFFA7A7A7),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            if (activePlaylist.tracks.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        viewModel.playPlaylistShuffled(activePlaylist)
                                        Toast.makeText(context, "Shuffle playing", Toast.LENGTH_SHORT).show()
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

                                Spacer(modifier = Modifier.width(16.dp))

                                // Spotify 52dp Floating Green Play Button with Optically Centered Play Triangle
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .shadow(8.dp, CircleShape)
                                        .clip(CircleShape)
                                        .background(SpotifyGreen)
                                        .clickable {
                                            viewModel.playPlaylist(activePlaylist)
                                            Toast.makeText(context, "Playing ${activePlaylist.playlist.name}", Toast.LENGTH_SHORT).show()
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
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (activePlaylist.tracks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp, horizontal = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "This playlist is empty",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    ),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Find songs in Search and add them to this playlist.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFA7A7A7)
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = activePlaylist.tracks,
                        key = { it.id }
                    ) { track ->
                        TrackItem(
                            track = track,
                            onClick = { viewModel.playTrack(track, activePlaylist.tracks, activePlaylist.playlist.playlistId) },
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
    } else {
        // ==========================================
        // PURE SPOTIFY "YOUR LIBRARY" SCREEN
        // ==========================================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .background(Color(0xFF121212))
        ) {
            // Spotify Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Profile Avatar Pill
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF535353)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "S",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            ),
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Your Library",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Import Shared Playlist",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Playlist",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Spotify Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .isolateHorizontalScroll()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Playlists", "Blends", "Downloaded").forEach { filter ->
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
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sort Row (Spotify "Recents" Style)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort",
                        tint = Color(0xFFA7A7A7),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Recents",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp
                        ),
                        color = Color(0xFFA7A7A7)
                    )
                }

                Text(
                    text = "${playlists.size + blends.size} items",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color(0xFF757575)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Library Items List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // 1. PINNED "LIKED SONGS" ROW (Authentic Spotify #1 Item)
                if (selectedFilter in listOf("All", "Playlists")) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (likedSongsPlaylist != null) {
                                        selectedPlaylistId = likedSongsPlaylist.playlist.playlistId
                                    } else {
                                        viewModel.ensureLikedSongsPlaylist { id ->
                                            selectedPlaylistId = id
                                        }
                                        Toast.makeText(context, "Liked Songs ready", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Purple-Blue Gradient Cover with Centered Heart
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF450AF5), Color(0xFF8E8EE5))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Liked Songs",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Liked Songs",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.5.sp
                                    ),
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "Pinned",
                                        tint = SpotifyGreen,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Playlist • ${likedSongsPlaylist?.tracks?.size ?: 0} songs",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                        color = Color(0xFFA7A7A7),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. SPOTIFY BLENDS HUB SECTION
                if (selectedFilter in listOf("All", "Blends")) {
                    item {
                        // "Create a Blend" Spotify Action Tile
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showBlendHubSheet = true }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF282828)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GroupAdd,
                                    contentDescription = "Create a Blend",
                                    tint = SpotifyGreen,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Create a Blend",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.5.sp
                                    ),
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Blend music taste with a friend or top artist",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                    color = Color(0xFFA7A7A7),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Existing Blends
                    items(blends, key = { it.blendId }) { blend ->
                        SpotifyBlendRow(
                            blend = blend,
                            onClick = { selectedBlendId = blend.blendId }
                        )
                    }
                }

                // 3. CUSTOM PLAYLISTS
                if (selectedFilter in listOf("All", "Playlists", "Downloaded")) {
                    val filteredPlaylists = if (selectedFilter == "Downloaded") {
                        customPlaylists.filter { pl -> pl.tracks.any { it.isDownloaded } }
                    } else {
                        customPlaylists
                    }

                    items(filteredPlaylists, key = { it.playlist.playlistId }) { playlistWithTracks ->
                        var showRowMenu by remember { mutableStateOf(false) }

                        SpotifyPlaylistRow(
                            playlistWithTracks = playlistWithTracks,
                            onClick = { selectedPlaylistId = playlistWithTracks.playlist.playlistId },
                            onMenuClick = { showRowMenu = true },
                            isMenuExpanded = showRowMenu,
                            onDismissMenu = { showRowMenu = false },
                            onPlayAll = {
                                viewModel.playPlaylist(playlistWithTracks)
                                Toast.makeText(context, "Playing ${playlistWithTracks.playlist.name}", Toast.LENGTH_SHORT).show()
                            },
                            onShufflePlay = {
                                viewModel.playPlaylistShuffled(playlistWithTracks)
                                Toast.makeText(context, "Shuffle playing ${playlistWithTracks.playlist.name}", Toast.LENGTH_SHORT).show()
                            },
                            onDownload = { playlistToDownload = playlistWithTracks },
                            onRename = {
                                showRowMenu = false
                                playlistToRename = playlistWithTracks
                                renameInput = playlistWithTracks.playlist.name
                            },
                            onDelete = {
                                showRowMenu = false
                                viewModel.deletePlaylist(playlistWithTracks.playlist.playlistId)
                                Toast.makeText(context, "Deleted ${playlistWithTracks.playlist.name}", Toast.LENGTH_SHORT).show()
                            },
                            onShare = {
                                showRowMenu = false
                                playlistToShare = playlistWithTracks
                            }
                        )
                    }
                }
            }
        }
    }

    // ==========================================
    // SPOTIFY BLEND CREATION BOTTOM SHEET
    // ==========================================
    if (showBlendHubSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBlendHubSheet = false },
            sheetState = blendHubSheetState,
            containerColor = Color(0xFF242424)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Create a Blend",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "A shared playlist made for two, updated daily with songs you both love.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                    color = Color(0xFFA7A7A7)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Invite Friend
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2E2E2E),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showBlendHubSheet = false
                            showCreateBlendDialog = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SpotifyGreen.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.GroupAdd, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Invite a Friend", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Text("Share an invite code or join an active room", style = MaterialTheme.typography.bodySmall, color = Color(0xFFA7A7A7))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Join with Code
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2E2E2E),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showBlendHubSheet = false
                            showJoinBlendDialog = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Hub, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enter Invite Code", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Text("Join a friend's blend with their 6-digit code", style = MaterialTheme.typography.bodySmall, color = Color(0xFFA7A7A7))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Artist Blend
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2E2E2E),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showBlendHubSheet = false
                            showArtistBlendSheet = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF59E0B).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Artist Blend", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = Color.White)
                            Text("Blend your music taste with verified flagship artists", style = MaterialTheme.typography.bodySmall, color = Color(0xFFA7A7A7))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ==========================================
    // TRACK ACTION BOTTOM SHEET
    // ==========================================
    selectedTrackForOptions?.let { track ->
        TrackActionBottomSheet(
            track = track,
            sheetState = trackSheetState,
            onDismiss = { selectedTrackForOptions = null },
            onPlayNow = { viewModel.playTrack(it, activePlaylist?.tracks.orEmpty(), activePlaylist?.playlist?.playlistId) },
            onPlayNext = {
                viewModel.playNext(it)
                Toast.makeText(context, "Playing next: ${it.title}", Toast.LENGTH_SHORT).show()
            },
            onAddToQueue = {
                viewModel.addTrackToQueue(it)
                Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
            },
            onDownload = {
                viewModel.downloadTrack(it)
                Toast.makeText(context, "Downloading ${it.title}", Toast.LENGTH_SHORT).show()
            },
            onAddToPlaylist = { trackForAddToAnotherPlaylist = it },
            onRemoveFromPlaylist = if (activePlaylist != null) {
                { trackToRemove ->
                    viewModel.removeTrackFromPlaylist(activePlaylist.playlist.playlistId, trackToRemove.id)
                    Toast.makeText(context, "Removed from ${activePlaylist.playlist.name}", Toast.LENGTH_SHORT).show()
                }
            } else null,
            onDeleteDownload = { trackToDelete ->
                viewModel.deleteDownload(trackToDelete.id)
                Toast.makeText(context, "Deleted download", Toast.LENGTH_SHORT).show()
            },
            onShare = { /* Share */ }
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

    // ==========================================
    // SPOTIFY CREATE PLAYLIST DIALOG
    // ==========================================
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Color(0xFF282828),
            title = {
                Text(
                    text = "Give your playlist a name",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = Color.White
                )
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("My playlist #1", color = Color(0xFFA7A7A7)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpotifyGreen,
                        unfocusedBorderColor = Color(0xFF3E3E3E),
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName.trim())
                            Toast.makeText(context, "Playlist created", Toast.LENGTH_SHORT).show()
                            newPlaylistName = ""
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpotifyGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = Color(0xFFA7A7A7))
                }
            }
        )
    }

    // Rename Playlist Dialog
    if (playlistToRename != null) {
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            containerColor = Color(0xFF282828),
            title = {
                Text(
                    text = "Rename playlist",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = Color.White
                )
            },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpotifyGreen,
                        unfocusedBorderColor = Color(0xFF3E3E3E),
                        focusedContainerColor = Color(0xFF1E1E1E),
                        unfocusedContainerColor = Color(0xFF1E1E1E),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            viewModel.renamePlaylist(playlistToRename!!.playlist.playlistId, renameInput.trim())
                            Toast.makeText(context, "Playlist renamed", Toast.LENGTH_SHORT).show()
                            playlistToRename = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpotifyGreen,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) {
                    Text("Cancel", color = Color(0xFFA7A7A7))
                }
            }
        )
    }

    // Download Playlist Quality Dialog
    if (playlistToDownload != null) {
        val currentQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
        DownloadQualityDialog(
            initialQuality = currentQuality,
            title = "Download Playlist",
            subtitle = playlistToDownload!!.playlist.name,
            trackCount = playlistToDownload!!.tracks.size,
            confirmButtonText = "Download All",
            onConfirm = { quality, setAsDefault ->
                if (setAsDefault) {
                    viewModel.setDownloadQuality(quality)
                }
                viewModel.downloadPlaylist(playlistToDownload!!, quality)
                Toast.makeText(
                    context,
                    "Downloading ${playlistToDownload!!.tracks.size} tracks",
                    Toast.LENGTH_SHORT
                ).show()
                playlistToDownload = null
            },
            onDismiss = { playlistToDownload = null }
        )
    }

    // Playlist Share & Import Dialogs
    if (playlistToShare != null) {
        SharePlaylistDialog(
            playlistWithTracks = playlistToShare!!,
            creatorName = currentUser?.displayName ?: "SatvikX User",
            shareManager = viewModel.shareManager,
            onDismiss = { playlistToShare = null }
        )
    }

    if (showImportDialog || pendingSharedPlaylist != null) {
        ImportPlaylistDialog(
            initialPayload = pendingSharedPlaylist,
            shareManager = viewModel.shareManager,
            onImport = { playlistName, tracks ->
                viewModel.importSharedPlaylist(playlistName, tracks) { newId ->
                    Toast.makeText(context, "Playlist \"$playlistName\" saved to your library!", Toast.LENGTH_SHORT).show()
                    selectedPlaylistId = newId
                }
                viewModel.shareManager.clearPendingPlaylist()
                showImportDialog = false
            },
            onPlayPreview = { tracks ->
                if (tracks.isNotEmpty()) {
                    viewModel.playTrack(tracks.first(), tracks)
                }
            },
            onDismiss = {
                viewModel.shareManager.clearPendingPlaylist()
                showImportDialog = false
            }
        )
    }

    // Blend Dialogs
    if (showCreateBlendDialog) {
        CreateBlendDialog(
            viewModel = blendViewModel,
            userName = blendUiState.currentUserName,
            onDismiss = { showCreateBlendDialog = false }
        )
    }

    if (showJoinBlendDialog) {
        JoinBlendDialog(
            viewModel = blendViewModel,
            initialCode = pendingBlendToken ?: "",
            onBlendCreated = { newBlendId ->
                selectedBlendId = newBlendId
                blendViewModel.clearPendingBlendToken()
                showJoinBlendDialog = false
            },
            onDismiss = {
                blendViewModel.clearPendingBlendToken()
                showJoinBlendDialog = false
            }
        )
    }

    if (showArtistBlendSheet) {
        CuratedArtistBlendSheet(
            viewModel = blendViewModel,
            sheetState = artistSheetState,
            onBlendCreated = { newBlendId ->
                selectedBlendId = newBlendId
                showArtistBlendSheet = false
            },
            onDismiss = { showArtistBlendSheet = false }
        )
    }
}

/**
 * Authentic Spotify Custom Playlist Row
 */
@Composable
private fun SpotifyPlaylistRow(
    playlistWithTracks: PlaylistWithTracks,
    onClick: () -> Unit,
    onMenuClick: () -> Unit,
    isMenuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit = {}
) {
    val thumbs = remember(playlistWithTracks.tracks) {
        playlistWithTracks.tracks
            .mapNotNull { it.thumbnailUrl.takeIf { t -> t.isNotBlank() && t.startsWith("http") } }
            .distinct()
            .take(4)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Artwork Thumbnail
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF282828)),
            contentAlignment = Alignment.Center
        ) {
            if (thumbs.size >= 4) {
                // 2x2 Collage
                Column(modifier = Modifier.fillMaxSize().background(Color(0xFF242424))) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = thumbs[0],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        AsyncImage(
                            model = thumbs[1],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = thumbs[2],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        AsyncImage(
                            model = thumbs[3],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            } else if (thumbs.isNotEmpty()) {
                AsyncImage(
                    model = thumbs.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFFA7A7A7),
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
                    fontSize = 15.5.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "Playlist • ${playlistWithTracks.tracks.size} songs",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = Color(0xFFA7A7A7),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 3-Dots Action Menu
        Box {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = Color(0xFFA7A7A7)
                )
            }

            DropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = onDismissMenu
            ) {
                if (playlistWithTracks.tracks.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Play All") },
                        leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null, tint = SpotifyGreen) },
                        onClick = {
                            onDismissMenu()
                            onPlayAll()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Shuffle Play") },
                        leadingIcon = { Icon(Icons.Default.Shuffle, contentDescription = null, tint = SpotifyGreen) },
                        onClick = {
                            onDismissMenu()
                            onShufflePlay()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Download All") },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = SpotifyGreen) },
                        onClick = {
                            onDismissMenu()
                            onDownload()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = SpotifyGreen) },
                        onClick = {
                            onDismissMenu()
                            onShare()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White) },
                    onClick = onRename
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color(0xFFFF5252)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252)) },
                    onClick = onDelete
                )
            }
        }
    }
}

/**
 * Authentic Spotify Blend Row with overlapping member avatars and match score
 */
@Composable
private fun SpotifyBlendRow(
    blend: BlendEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Dual Avatar Overlapping Circles Tile
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1E3A8A), Color(0xFF6D28D9))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(width = 46.dp, height = 30.dp)) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF38BDF8),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1E1E1E)),
                    modifier = Modifier.size(28.dp).align(Alignment.CenterStart)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = blend.creatorName.take(1).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.Black)
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = SpotifyGreen,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF1E1E1E)),
                    modifier = Modifier.size(28.dp).align(Alignment.CenterEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = blend.partnerName.take(1).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.Black)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = blend.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = SpotifyGreen.copy(alpha = 0.2f),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = "${blend.matchScore}% Match",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = SpotifyGreen
                        ),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                    )
                }

                Text(
                    text = "Blend • 35 songs",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = Color(0xFFA7A7A7),
                    maxLines = 1
                )
            }
        }
    }
}
