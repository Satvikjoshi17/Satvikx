package com.satvik.satvikx.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.repository.ArtistAffinity
import com.satvik.satvikx.data.repository.DailyMix
import com.satvik.satvikx.ui.components.AddToPlaylistDialog
import com.satvik.satvikx.ui.components.OpticallyCenteredPlayIcon
import com.satvik.satvikx.ui.components.SatvikXLogo
import com.satvik.satvikx.ui.components.ShimmerTrackList
import com.satvik.satvikx.ui.components.StarkAuthDialog
import com.satvik.satvikx.ui.components.TrackActionBottomSheet
import com.satvik.satvikx.ui.components.TrackItem
import com.satvik.satvikx.ui.components.isolateHorizontalScroll
import com.satvik.satvikx.ui.theme.ArcCyan
import com.satvik.satvikx.ui.theme.ArcCyanBright
import com.satvik.satvikx.ui.theme.BackgroundDark
import com.satvik.satvikx.ui.theme.SpotifyGreen
import com.satvik.satvikx.ui.theme.StarkBorder
import com.satvik.satvikx.ui.theme.StarkCarbon
import com.satvik.satvikx.ui.theme.StarkGold
import com.satvik.satvikx.ui.theme.StarkSurface
import com.satvik.satvikx.ui.theme.SurfaceDark
import com.satvik.satvikx.ui.theme.SurfaceElevated
import com.satvik.satvikx.ui.theme.TextPrimary
import com.satvik.satvikx.ui.theme.TextSecondary
import com.satvik.satvikx.ui.theme.TextTertiary
import com.satvik.satvikx.ui.viewmodel.AuthViewModel
import com.satvik.satvikx.ui.viewmodel.HomeViewModel
import com.satvik.satvikx.ui.viewmodel.LibraryViewModel
import com.satvik.satvikx.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val isAuthLoading by authViewModel.isLoading.collectAsStateWithLifecycle()

    var selectedTrackForOptions by remember { mutableStateOf<TrackEntity?>(null) }
    var trackForAddToPlaylist by remember { mutableStateOf<TrackEntity?>(null) }
    var selectedArtistForDetail by remember { mutableStateOf<com.satvik.satvikx.data.repository.ArtistAffinity?>(null) }
    var showAuthDialog by remember { mutableStateOf(false) }
    val isMandatoryOnboarding = currentUser == null && !isAuthLoading
    val shouldShowAuthDialog = showAuthDialog || isMandatoryOnboarding
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val syncState by authViewModel.syncState.collectAsStateWithLifecycle()

    // Automatically re-curate home recommendations when active user session changes
    LaunchedEffect(currentUser?.id) {
        if (currentUser != null) {
            homeViewModel.loadHomeData(forceRefresh = true)
        }
    }

    // Automatically re-curate home recommendations when cloud sync brings in fresh Liked Songs
    LaunchedEffect(syncState) {
        if (syncState is com.satvik.satvikx.data.repository.SyncState.Success) {
            homeViewModel.loadHomeData(forceRefresh = true)
        }
    }

    // Dynamic resume listener: automatically syncs fresh listening history and affinities
    // whenever user returns to the Home screen after playing music
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.refreshRecentPlayback()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Spotify Subtle Ambient Background Mesh
    val ambientGradient = remember(uiState.greeting) {
        when {
            uiState.greeting.contains("morning", ignoreCase = true) -> listOf(
                Color(0xFF332015).copy(alpha = 0.55f),
                Color(0xFF1E1510).copy(alpha = 0.25f),
                BackgroundDark
            )
            uiState.greeting.contains("afternoon", ignoreCase = true) -> listOf(
                Color(0xFF1E2838).copy(alpha = 0.55f),
                Color(0xFF141A22).copy(alpha = 0.25f),
                BackgroundDark
            )
            uiState.greeting.contains("evening", ignoreCase = true) -> listOf(
                Color(0xFF261838).copy(alpha = 0.55f),
                Color(0xFF181022).copy(alpha = 0.25f),
                BackgroundDark
            )
            else -> listOf(
                Color(0xFF1A2620).copy(alpha = 0.55f),
                Color(0xFF121A16).copy(alpha = 0.25f),
                BackgroundDark
            )
        }
    }

    if (selectedArtistForDetail != null) {
        ArtistDetailScreen(
            artist = selectedArtistForDetail!!,
            onBack = { selectedArtistForDetail = null }
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundDark)
        ) {
        // Atmospheric gradient mesh at top of screen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Brush.verticalGradient(ambientGradient))
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // 1. Spotify Top Header: Profile Avatar + Filter Chips + Quick Actions
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    // Top Action Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profile Avatar
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF282828))
                                .clickable { showAuthDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentUser != null && currentUser!!.avatarUrl.isNotBlank()) {
                                Text(
                                    text = currentUser!!.avatarUrl,
                                    fontSize = 16.sp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Profile",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Filter Chips (Pills)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState())
                                .isolateHorizontalScroll(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            homeViewModel.availableMoods.forEach { mood ->
                                val isSelected = uiState.selectedMood == mood
                                val labelText = if (isSelected && mood != "All") "$mood ✕" else mood
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) SpotifyGreen else Color(0xFF282828),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { homeViewModel.selectMood(mood) }
                                ) {
                                    Text(
                                        text = labelText,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        ),
                                        color = if (isSelected) Color(0xFF121212) else Color.White,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Actions: Refresh Feed & Update Check
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { homeViewModel.loadHomeData(forceRefresh = true) },
                                enabled = !uiState.isRefreshing,
                                modifier = Modifier.size(34.dp)
                            ) {
                                if (uiState.isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                            .size(16.dp)
                            .offset(x = 1.dp),
                                        color = SpotifyGreen,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh Feed",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { playerViewModel.checkForUpdates(isManual = true) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = "Check for Updates",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    // Bold Spotify Greeting
                    val greetingTitle = remember(uiState.greeting) {
                        val raw = uiState.greeting.replace(", Tony", "").trim()
                        when {
                            raw.contains("morning", ignoreCase = true) -> "Good morning"
                            raw.contains("afternoon", ignoreCase = true) -> "Good afternoon"
                            raw.contains("evening", ignoreCase = true) -> "Good evening"
                            raw.contains("night", ignoreCase = true) -> "Good evening"
                            else -> raw
                        }
                    }

                    Text(
                        text = greetingTitle,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp)
                    )
                }
            }

            // 2. Spotify Signature 2x3 Quick Access Grid
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Assemble 6 items: Liked Songs, Daily Mix 1, and up to 4 top tracks
                    val tiles = buildList {
                        // Tile 1: Liked Songs
                        add(QuickTileItem.LikedSongs(count = uiState.likedTracks.size))
                        // Tile 2: Daily Mix 1
                        uiState.dailyMixes.firstOrNull()?.let { add(QuickTileItem.Mix(it)) }
                        // Remaining tiles from user's actual recent songs, heavy rotation, or quick picks
                        val trackCandidates = (uiState.jumpBackInTracks + uiState.heavyRotationTracks + uiState.quickPicks)
                            .distinctBy { it.id }
                            .take(4)
                        trackCandidates.forEach { add(QuickTileItem.Track(it)) }
                    }.take(6)

                    tiles.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.5.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { tile ->
                                SpotifyQuickTile(
                                    tile = tile,
                                    onClick = {
                                        when (tile) {
                                            is QuickTileItem.LikedSongs -> homeViewModel.playLikedSongs()
                                            is QuickTileItem.Mix -> homeViewModel.playDailyMix(tile.mix)
                                            is QuickTileItem.Track -> homeViewModel.playTrackWithSuggestionQueue(tile.track, uiState.jumpBackInTracks.ifEmpty { uiState.quickPicks })
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // 3. Jump Back In (Recently played / resume listening - placed prominently right after quick tiles)
            if (uiState.jumpBackInTracks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(18.dp))
                    SpotifySectionHeader(
                        title = "Jump back in",
                        subtitle = "Pick up where you left off"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .isolateHorizontalScroll()
                    ) {
                        items(uiState.jumpBackInTracks, key = { "jump_${it.id}" }) { track ->
                            SpotifyRecommendationCard(
                                track = track,
                                onClick = { homeViewModel.playTrackWithSuggestionQueue(track, uiState.jumpBackInTracks) },
                                onOptionsClick = { selectedTrackForOptions = track }
                            )
                        }
                    }
                }
            }

            // 4. AI DJ / SatvikX Autopilot Mix
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF182230),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { homeViewModel.playAutopilotMix() }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF1B2433),
                                        Color(0xFF161D29),
                                        Color(0xFF12161E)
                                    )
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0D2538)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = "DJ",
                                        tint = SpotifyGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = "AI DJ",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.2.sp,
                                            fontSize = 10.5.sp
                                        ),
                                        color = SpotifyGreen
                                    )
                                    Text(
                                        text = "SatvikX Autopilot Mix",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        ),
                                        color = Color.White,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                    Text(
                                        text = "${uiState.autopilotTargetSinger} • ${uiState.autopilotTargetGenre}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp
                                        ),
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SpotifyGreen)
                                    .clickable { homeViewModel.playAutopilotMix() },
                                contentAlignment = Alignment.Center
                            ) {
                                if (uiState.isAutopilotEngaging) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.Black,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    OpticallyCenteredPlayIcon(
                                        modifier = Modifier.size(15.dp),
                                        tint = Color.Black
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Active Mood Picks (when a mood filter other than 'All' is active)
            if (uiState.selectedMood != "All") {
                if (uiState.isLoadingMood) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        SpotifySectionHeader(
                            title = "${uiState.selectedMood} Picks",
                            subtitle = "Fetching fresh curated tracks for you..."
                        )
                        ShimmerTrackList(count = 4, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                } else if (uiState.moodTracks.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        SpotifySectionHeader(
                            title = "${uiState.selectedMood} Picks",
                            subtitle = "Curated tracks matching your active vibe"
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .isolateHorizontalScroll()
                        ) {
                            items(uiState.moodTracks, key = { "mood_${it.id}" }) { track ->
                                SpotifyRecommendationCard(
                                    track = track,
                                    onClick = { homeViewModel.playTrackWithSuggestionQueue(track, uiState.moodTracks) },
                                    onOptionsClick = { selectedTrackForOptions = track }
                                )
                            }
                        }
                    }
                }
            }

            // 6. Made For You (Daily Mixes 1-5 with Spotify-style covers & subtitles)
            if (uiState.dailyMixes.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    SpotifySectionHeader(
                        title = "Made For You",
                        subtitle = "Get fresh music picked just for you"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .isolateHorizontalScroll()
                    ) {
                        items(uiState.dailyMixes, key = { it.id }) { mix ->
                            SpotifyDailyMixCard(
                                mix = mix,
                                onClick = { homeViewModel.playDailyMix(mix) }
                            )
                        }
                    }
                }
            }

            // 7. Your Favorite Artists (Circular Spotify artist cards)
            if (uiState.topArtists.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    SpotifySectionHeader(
                        title = "Your favorite artists",
                        subtitle = "Artists you listen to the most"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .isolateHorizontalScroll()
                    ) {
                        items(uiState.topArtists, key = { it.name }) { artist ->
                            SpotifyArtistCard(
                                artist = artist,
                                onClick = { selectedArtistForDetail = artist },
                                onPlayRadioClick = {
                                    Toast.makeText(context, "Playing ${artist.name} Radio", Toast.LENGTH_SHORT).show()
                                    homeViewModel.playArtistRadio(artist.name)
                                }
                            )
                        }
                    }
                }
            }

            // Loading shimmer - only when there is no data at all (first cold run)
            if (uiState.isLoading && uiState.trendingTracks.isEmpty() && uiState.quickPicks.isEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    ShimmerTrackList(count = 4)
                }
            }

            // 8. Because You Listen To (Top liked artist recommendation)
            if (uiState.becauseYouLikedTracks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    SpotifySectionHeader(
                        title = uiState.becauseYouLikedTitle,
                        subtitle = uiState.becauseYouLikedSubtitle
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .isolateHorizontalScroll()
                    ) {
                        items(uiState.becauseYouLikedTracks, key = { "liked_${it.id}" }) { track ->
                            SpotifyRecommendationCard(
                                track = track,
                                onClick = { homeViewModel.playTrackWithSuggestionQueue(track, uiState.becauseYouLikedTracks) },
                                onOptionsClick = { selectedTrackForOptions = track }
                            )
                        }
                    }
                }
            }

            // 9. Similar To (Secondary Artist Discovery Carousel)
            if (uiState.similarArtistTracks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    SpotifySectionHeader(
                        title = uiState.similarArtistTitle,
                        subtitle = uiState.similarArtistSubtitle
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .isolateHorizontalScroll()
                    ) {
                        items(uiState.similarArtistTracks, key = { "similar_${it.id}" }) { track ->
                            SpotifyRecommendationCard(
                                track = track,
                                onClick = { homeViewModel.playTrackWithSuggestionQueue(track, uiState.similarArtistTracks) },
                                onOptionsClick = { selectedTrackForOptions = track }
                            )
                        }
                    }
                }
            }

            // 10. Heavy Rotation
            if (uiState.heavyRotationTracks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    SpotifySectionHeader(
                        title = "Heavy Rotation",
                        subtitle = "Tracks you have on repeat"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .isolateHorizontalScroll()
                    ) {
                        items(uiState.heavyRotationTracks, key = { "heavy_${it.id}" }) { track ->
                            SpotifyRecommendationCard(
                                track = track,
                                onClick = { homeViewModel.playTrackWithSuggestionQueue(track, uiState.heavyRotationTracks) },
                                onOptionsClick = { selectedTrackForOptions = track }
                            )
                        }
                    }
                }
            }

            // 11. Discovery Radar
            if (uiState.discoveryRadarTracks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    SpotifySectionHeader(
                        title = "Discovery Radar",
                        subtitle = "New discoveries tailored to your music taste"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .isolateHorizontalScroll()
                    ) {
                        items(uiState.discoveryRadarTracks, key = { "disc_${it.id}" }) { track ->
                            SpotifyRecommendationCard(
                                track = track,
                                onClick = { homeViewModel.playTrackWithSuggestionQueue(track, uiState.discoveryRadarTracks) },
                                onOptionsClick = { selectedTrackForOptions = track }
                            )
                        }
                    }
                }
            }

            // 12. Category Radar
            if (uiState.categoryRadarTracks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    SpotifySectionHeader(
                        title = uiState.categoryRadarTitle,
                        subtitle = "Deep dives into your favorite genre"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .isolateHorizontalScroll()
                    ) {
                        items(uiState.categoryRadarTracks, key = { "cat_${it.id}" }) { track ->
                            SpotifyRecommendationCard(
                                track = track,
                                onClick = { homeViewModel.playTrackWithSuggestionQueue(track, uiState.categoryRadarTracks) },
                                onOptionsClick = { selectedTrackForOptions = track }
                            )
                        }
                    }
                }
            }

            // 13. Vault Favorites (Liked and downloaded tracks)
            if (uiState.vaultFavoritesTracks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    SpotifySectionHeader(
                        title = "Your Vault",
                        subtitle = "Saved songs and offline favorites"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .isolateHorizontalScroll()
                    ) {
                        items(uiState.vaultFavoritesTracks, key = { "vault_${it.id}" }) { track ->
                            SpotifyRecommendationCard(
                                track = track,
                                onClick = { homeViewModel.playTrackWithSuggestionQueue(track, uiState.vaultFavoritesTracks) },
                                onOptionsClick = { selectedTrackForOptions = track }
                            )
                        }
                    }
                }
            }

            // 14. Trending Worldwide (Track list)
            if (uiState.trendingTracks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(26.dp))
                    SpotifySectionHeader(
                        title = "Today's Biggest Hits",
                        subtitle = "The most played songs right now"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(uiState.trendingTracks, key = { "trend_${it.id}" }) { track ->
                    TrackItem(
                        modifier = Modifier.animateItem(),
                        track = track,
                        onClick = {
                            homeViewModel.playTrackWithSuggestionQueue(
                                track,
                                uiState.trendingTracks
                            )
                        },
                        onOptionClick = { selectedTrackForOptions = track },
                        onDownloadClick = {
                            homeViewModel.enqueueDownload(track)
                            Toast.makeText(context, "Downloading ${track.title}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

    // Modal Action Sheet (Options menu)
    if (selectedTrackForOptions != null) {
        TrackActionBottomSheet(
            track = selectedTrackForOptions,
            sheetState = sheetState,
            onDismiss = { selectedTrackForOptions = null },
            onPlayNow = {
                homeViewModel.playTrackWithSuggestionQueue(it, uiState.trendingTracks)
            },
            onPlayNext = {
                homeViewModel.playNext(it)
                Toast.makeText(context, "Playing next: ${it.title}", Toast.LENGTH_SHORT).show()
            },
            onAddToQueue = {
                homeViewModel.addToQueue(it)
                Toast.makeText(context, "Added to queue: ${it.title}", Toast.LENGTH_SHORT).show()
            },
            onDownload = {
                homeViewModel.enqueueDownload(it)
                Toast.makeText(context, "Queued for download: ${it.title}", Toast.LENGTH_SHORT).show()
            },
            onAddToPlaylist = {
                trackForAddToPlaylist = it
            },
            onShare = {
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, it.title)
                    putExtra(android.content.Intent.EXTRA_TEXT, "Listen to ${it.title} by ${it.artist} on Satvikx")
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Track"))
            }
        )
    }

    // Add To Playlist Dialog
    if (trackForAddToPlaylist != null) {
        AddToPlaylistDialog(
            track = trackForAddToPlaylist,
            viewModel = libraryViewModel,
            onDismiss = { trackForAddToPlaylist = null }
        )
    }

    // Stark ID & Cloud Sync Authentication Dialog (Mandatory on first launch / unauthenticated)
    if (shouldShowAuthDialog) {
        StarkAuthDialog(
            authViewModel = authViewModel,
            isMandatory = isMandatoryOnboarding,
            onDismiss = { showAuthDialog = false }
        )
    }
}

// Data holder for 2x3 Quick Tiles
private sealed interface QuickTileItem {
    data class LikedSongs(val count: Int) : QuickTileItem
    data class Mix(val mix: DailyMix) : QuickTileItem
    data class Track(val track: TrackEntity) : QuickTileItem
}

/**
 * Spotify Signature 2x3 Quick Access Grid Tile
 */
@Composable
private fun SpotifyQuickTile(
    tile: QuickTileItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isNarrow = configuration.screenWidthDp < 360
    val thumbSize = if (isNarrow) 48.dp else 56.dp

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF282828),
        modifier = modifier
            .defaultMinSize(minHeight = thumbSize)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = thumbSize)
        ) {
            // Left square image (adaptively sized)
            when (tile) {
                is QuickTileItem.LikedSongs -> {
                    Box(
                        modifier = Modifier
                            .size(thumbSize)
                            .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF450AF5), Color(0xFF8E8EE5))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(if (isNarrow) 20.dp else 24.dp)
                        )
                    }
                }
                is QuickTileItem.Mix -> {
                    Box(
                        modifier = Modifier
                            .size(thumbSize)
                            .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                            .background(
                                Brush.linearGradient(
                                    tile.mix.gradientColors.map { Color(it) }
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val thumb = tile.mix.tracks.firstOrNull { it.thumbnailUrl.isNotBlank() }?.thumbnailUrl
                        if (!thumb.isNullOrBlank()) {
                            AsyncImage(
                                model = thumb,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(if (isNarrow) 20.dp else 24.dp)
                            )
                        }
                    }
                }
                is QuickTileItem.Track -> {
                    Box(
                        modifier = Modifier
                            .size(thumbSize)
                            .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                            .background(Color(0xFF1E1E1E)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (tile.track.thumbnailUrl.isNotBlank()) {
                            AsyncImage(
                                model = tile.track.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = TextTertiary,
                                modifier = Modifier.size(if (isNarrow) 18.dp else 20.dp)
                            )
                        }
                    }
                }
            }

            // Title & Subtitle
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = if (isNarrow) 8.dp else 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                val title = when (tile) {
                    is QuickTileItem.LikedSongs -> "Liked Songs"
                    is QuickTileItem.Mix -> tile.mix.title
                    is QuickTileItem.Track -> tile.track.title
                }
                val subtitle = when (tile) {
                    is QuickTileItem.LikedSongs -> if (tile.count > 0) "${tile.count} songs" else "Playlist"
                    is QuickTileItem.Mix -> "For You"
                    is QuickTileItem.Track -> tile.track.artist
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isNarrow) 11.5.sp else 12.5.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (isNarrow) 9.5.sp else 10.5.sp
                    ),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Spotify Signature Play Button on Right Edge
            if (!isNarrow) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(SpotifyGreen)
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center
                ) {
                    OpticallyCenteredPlayIcon(
                        modifier = Modifier.size(14.dp),
                        tint = Color.Black
                    )
                }
            }
        }
    }
}

/**
 * Spotify Section Header with clean, bold typography
 */
@Composable
private fun SpotifySectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                letterSpacing = (-0.4).sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color.White
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * Spotify Premium Style Album / Track Recommendation Card
 */
@Composable
private fun SpotifyRecommendationCard(
    track: TrackEntity,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(148.dp)
            .clickable(onClick = onClick)
    ) {
        // Square artwork with 6dp rounded corners and floating play button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF282828)),
            contentAlignment = Alignment.BottomEnd
        ) {
            if (track.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Spotify Green floating play button
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(SpotifyGreen),
                contentAlignment = Alignment.Center
            ) {
                OpticallyCenteredPlayIcon(
                    modifier = Modifier.size(15.dp),
                    tint = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title & Artist
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp
                ),
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onOptionsClick,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = TextTertiary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Spotify Signature Daily Mix Card with rich 2x2 collage cover, bottom accent stripe, and artist list
 */
@Composable
private fun SpotifyDailyMixCard(
    mix: DailyMix,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbs = remember(mix.tracks) {
        mix.tracks
            .mapNotNull { it.thumbnailUrl.takeIf { t -> t.isNotBlank() && t.startsWith("http") } }
            .distinct()
            .take(4)
    }

    val accentColor = remember(mix.id) {
        when (mix.id) {
            "mix_1" -> Color(0xFFFF4632) // Spotify Coral / Red
            "mix_2" -> Color(0xFF00D2FF) // Spotify Electric Cyan
            "mix_3" -> Color(0xFFA855F7) // Spotify Vivid Violet
            "mix_4" -> Color(0xFF1ED760) // Spotify Electric Green
            "mix_5" -> Color(0xFFFFB300) // Spotify Amber Gold
            else -> Color(0xFF1ED760)
        }
    }

    Column(
        modifier = modifier
            .width(156.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        // Daily Mix Artwork Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(156.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.linearGradient(
                        colors = if (mix.gradientColors.size >= 2) {
                            mix.gradientColors.map { Color(it) }
                        } else {
                            listOf(Color(0xFF1E3A8A), Color(0xFF0F172A))
                        }
                    )
                ),
            contentAlignment = Alignment.BottomEnd
        ) {
            if (thumbs.size >= 4) {
                // Authentic Spotify 2x2 Artwork Collage
                Column(modifier = Modifier.fillMaxSize().background(Color(0xFF242424))) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = thumbs[0],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF282828))
                        )
                        AsyncImage(
                            model = thumbs[1],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF333333))
                        )
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = thumbs[2],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF333333))
                        )
                        AsyncImage(
                            model = thumbs[3],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFF282828))
                        )
                    }
                }
            } else if (thumbs.isNotEmpty()) {
                // Crisp single lead cover art with full opacity
                AsyncImage(
                    model = thumbs.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Stylized musical waveform placeholder
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Subtle bottom gradient shadow for text/button legibility
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                        )
                    )
            )

            // Top Daily Mix Pill Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "DAILY MIX",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = Color.White
                )
            }

            // Spotify Signature Bottom Solid Colored Accent Stripe
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(accentColor)
            )

            // Floating circular Spotify Green Play Button (optically centered!)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(SpotifyGreen),
                contentAlignment = Alignment.Center
            ) {
                OpticallyCenteredPlayIcon(
                    modifier = Modifier.size(15.dp),
                    tint = Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = mix.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
            ),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = mix.subtitle,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                lineHeight = 15.sp
            ),
            color = TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Spotify Signature Circular Artist Profile Card
 */
@Composable
private fun SpotifyArtistCard(
    artist: ArtistAffinity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPlayRadioClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(115.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(110.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFF242424)),
                contentAlignment = Alignment.Center
            ) {
                if (!artist.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artist.imageUrl,
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = TextTertiary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            if (onPlayRadioClick != null) {
                Surface(
                    shape = CircleShape,
                    color = SpotifyGreen,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 2.dp)
                        .size(32.dp)
                        .clickable { onPlayRadioClick() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        OpticallyCenteredPlayIcon(
                            modifier = Modifier.size(16.dp),
                            tint = Color.Black
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp
            ),
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = "Artist",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp
            ),
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

/**
 * Minimalist Animated J.A.R.V.I.S. Hologram Arc Reactor Core.
 * Pure vector canvas drawing with counter-rotating HUD brackets and breathing cyan glow.
 */
@Composable
fun JarvisHologramReactor(
    modifier: Modifier = Modifier,
    sizeDp: Int = 42
) {
    val infiniteTransition = rememberInfiniteTransition(label = "jarvisHologram")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Canvas(modifier = modifier.size(sizeDp.dp)) {
        val radius = size.minDimension / 2f
        val center = this.center

        // 1. Ambient outer glow circle
        drawCircle(
            color = ArcCyan.copy(alpha = pulseAlpha * 0.25f),
            radius = radius * 0.95f,
            center = center
        )

        // 2. Outer segmented HUD brackets (slow rotating)
        rotate(rotation) {
            val strokeWidth = 1.5.dp.toPx()
            for (i in 0 until 3) {
                val startAngle = i * 120f + 15f
                drawArc(
                    color = ArcCyanBright,
                    startAngle = startAngle,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth)
                )
            }
        }

        // 3. Counter-rotating inner dashed ring
        rotate(-rotation * 0.6f) {
            val innerStroke = 1.dp.toPx()
            for (i in 0 until 6) {
                val startAngle = i * 60f + 10f
                drawArc(
                    color = ArcCyan.copy(alpha = 0.7f),
                    startAngle = startAngle,
                    sweepAngle = 40f,
                    useCenter = false,
                    style = Stroke(width = innerStroke)
                )
            }
        }

        // 4. Center pulsing Arc Core
        drawCircle(
            color = ArcCyanBright,
            radius = radius * 0.28f,
            center = center
        )
        drawCircle(
            color = Color.White,
            radius = radius * 0.15f,
            center = center
        )
    }
}
