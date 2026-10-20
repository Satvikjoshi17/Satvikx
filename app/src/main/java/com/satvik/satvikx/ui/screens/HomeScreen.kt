package com.satvik.satvikx.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.satvik.satvikx.ui.components.ShimmerTrackList
import com.satvik.satvikx.ui.components.TrackActionBottomSheet
import com.satvik.satvikx.ui.components.TrackItem
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
import com.satvik.satvikx.ui.viewmodel.HomeViewModel
import com.satvik.satvikx.ui.viewmodel.LibraryViewModel
import com.satvik.satvikx.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeViewModel: HomeViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()

    var selectedTrackForOptions by remember { mutableStateOf<TrackEntity?>(null) }
    var trackForAddToPlaylist by remember { mutableStateOf<TrackEntity?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Spotify Premium Dynamic Ambient Background Glow (adapts smoothly to time of day)
    val ambientGradient = remember(uiState.greeting) {
        when {
            uiState.greeting.contains("morning", ignoreCase = true) -> listOf(
                Color(0xFF382314).copy(alpha = 0.75f),
                Color(0xFF1E1710).copy(alpha = 0.45f),
                BackgroundDark
            )
            uiState.greeting.contains("afternoon", ignoreCase = true) -> listOf(
                Color(0xFF0F3622).copy(alpha = 0.75f),
                Color(0xFF0C2216).copy(alpha = 0.45f),
                BackgroundDark
            )
            uiState.greeting.contains("evening", ignoreCase = true) -> listOf(
                Color(0xFF2C1338).copy(alpha = 0.75f),
                Color(0xFF191122).copy(alpha = 0.45f),
                BackgroundDark
            )
            else -> listOf(
                Color(0xFF10273B).copy(alpha = 0.75f),
                Color(0xFF0F1722).copy(alpha = 0.45f),
                BackgroundDark
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Atmospheric gradient mesh at top of screen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(Brush.verticalGradient(ambientGradient))
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // 1. Spotify Premium Command Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.greeting,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.6).sp
                                ),
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
                                        .background(SpotifyGreen)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = uiState.telemetryStatus,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = SpotifyGreen
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { playerViewModel.checkForUpdates(isManual = true) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .border(1.dp, StarkBorder, CircleShape)
                                    .background(SurfaceElevated.copy(alpha = 0.7f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = "Check for Updates",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { homeViewModel.loadHomeData(forceRefresh = true) },
                                enabled = !uiState.isRefreshing,
                                modifier = Modifier
                                    .size(36.dp)
                                    .border(1.dp, StarkBorder, CircleShape)
                                    .background(SurfaceElevated.copy(alpha = 0.7f), CircleShape)
                            ) {
                                if (uiState.isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = SpotifyGreen,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh Feed",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Spotify Premium Style Pill Filter Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        homeViewModel.availableMoods.forEach { mood ->
                            val isSelected = uiState.selectedMood == mood
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) SpotifyGreen else SurfaceElevated.copy(alpha = 0.85f),
                                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, StarkBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { homeViewModel.selectMood(mood) }
                            ) {
                                Text(
                                    text = mood,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    ),
                                    color = if (isSelected) Color.Black else TextPrimary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
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
                        // Remaining tiles from quickPicks or heavyRotation
                        val trackCandidates = (uiState.quickPicks + uiState.heavyRotationTracks)
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
                                            is QuickTileItem.Track -> homeViewModel.playTrackWithSuggestionQueue(tile.track, uiState.quickPicks)
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

            // 3. AI DJ / J.A.R.V.I.S. Smart Autopilot Banner
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceDark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .border(1.dp, StarkBorder, RoundedCornerShape(16.dp))
                        .clickable { homeViewModel.playAutopilotMix() }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF0F1E28),
                                        Color(0xFF142C38),
                                        Color(0xFF0B141E)
                                    )
                                )
                            )
                            .padding(14.dp)
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
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(SpotifyGreen.copy(alpha = 0.2f))
                                        .border(1.dp, SpotifyGreen, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = SpotifyGreen,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "AI DJ • AUTOPILOT",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            ),
                                            color = SpotifyGreen
                                        )
                                    }
                                    Text(
                                        text = "Continuous mix based on your taste",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        ),
                                        color = TextPrimary,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                    Text(
                                        text = "${uiState.autopilotTargetSinger} • ${uiState.autopilotTargetGenre}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        ),
                                        color = TextSecondary,
                                        modifier = Modifier.padding(top = 1.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = { homeViewModel.playAutopilotMix() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SpotifyGreen,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                enabled = !uiState.isAutopilotEngaging
                            ) {
                                if (uiState.isAutopilotEngaging) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.Black,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "SPIN",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Active Mood Picks (when a mood filter other than 'All' is active)
            if (uiState.selectedMood != "All" && uiState.moodTracks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    SpotifySectionHeader(
                        title = "${uiState.selectedMood} Picks",
                        subtitle = "Curated tracks matching your active vibe"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(top = 10.dp)
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

            // 5. Jump Back In (Recently played / resume listening)
            if (uiState.jumpBackInTracks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(22.dp))
                    SpotifySectionHeader(
                        title = "Jump back in",
                        subtitle = "Pick up where you left off"
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(top = 10.dp)
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
                        modifier = Modifier.padding(top = 10.dp)
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
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        items(uiState.topArtists, key = { it.name }) { artist ->
                            SpotifyArtistCard(
                                artist = artist,
                                onClick = { homeViewModel.playArtistRadio(artist.name) }
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
                        modifier = Modifier.padding(top = 10.dp)
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
                        modifier = Modifier.padding(top = 10.dp)
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
                        modifier = Modifier.padding(top = 10.dp)
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
                        modifier = Modifier.padding(top = 10.dp)
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
                        modifier = Modifier.padding(top = 10.dp)
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
                        modifier = Modifier.padding(top = 10.dp)
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
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xFF282828).copy(alpha = 0.85f),
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            // Left square 56x56 image
            when (tile) {
                is QuickTileItem.LikedSongs -> {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
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
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                is QuickTileItem.Mix -> {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
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
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                is QuickTileItem.Track -> {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
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
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Title & Subtitle
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
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
                        fontSize = 12.5.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp
                    ),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Spotify Signature Play Button on Right Edge
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(SpotifyGreen)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
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
            color = Color.White
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp
                ),
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
        // Square artwork with 8dp rounded corners and floating play button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E1E1E)),
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
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(SpotifyGreen)
                    .shadow(4.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
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
 * Spotify Signature Daily Mix Card with bottom stripe and artist list
 */
@Composable
private fun SpotifyDailyMixCard(
    mix: DailyMix,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(156.dp)
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
            // First track thumbnail overlay
            val firstThumb = mix.tracks.firstOrNull { it.thumbnailUrl.isNotBlank() }?.thumbnailUrl
            if (!firstThumb.isNullOrBlank()) {
                AsyncImage(
                    model = firstThumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.65f)
                )
                // Dark scrim over image
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                )
                            )
                        )
                )
            }

            // Top Daily Mix Pill Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "DAILY MIX",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    color = Color.White
                )
            }

            // Spotify Signature Bottom Colored Accent Stripe
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(SpotifyGreen, ArcCyanBright, StarkGold)
                        )
                    )
            )

            // Floating circular Spotify Green Play Button
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(SpotifyGreen)
                    .shadow(4.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Mix",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
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
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(110.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(105.dp)
                .clip(CircleShape)
                .background(Color(0xFF242424))
                .border(1.5.dp, SurfaceElevated, CircleShape),
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
