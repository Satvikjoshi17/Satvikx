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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.satvik.satvikx.data.repository.DailyMix
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.ui.components.AddToPlaylistDialog
import com.satvik.satvikx.ui.components.ShimmerTrackList
import com.satvik.satvikx.ui.components.TrackActionBottomSheet
import com.satvik.satvikx.ui.components.TrackItem
import com.satvik.satvikx.ui.theme.ArcCyan
import com.satvik.satvikx.ui.theme.ArcCyanBright
import com.satvik.satvikx.ui.theme.ArcCyanGlow
import com.satvik.satvikx.ui.theme.StarkBorder
import com.satvik.satvikx.ui.theme.StarkCarbon
import com.satvik.satvikx.ui.theme.StarkGold
import com.satvik.satvikx.ui.theme.StarkSurface
import com.satvik.satvikx.ui.theme.TextPrimary
import com.satvik.satvikx.ui.theme.TextSecondary
import com.satvik.satvikx.ui.theme.TextTertiary
import com.satvik.satvikx.ui.viewmodel.HomeViewModel
import com.satvik.satvikx.ui.viewmodel.LibraryViewModel
import com.satvik.satvikx.ui.viewmodel.PlayerViewModel
import androidx.compose.material.icons.filled.SystemUpdate

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 8.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // 1. J.A.R.V.I.S. Command Header & Telemetry Beacon
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
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
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
                                    .background(ArcCyanBright)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = uiState.telemetryStatus,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.5.sp
                                ),
                                color = ArcCyanBright
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { playerViewModel.checkForUpdates(isManual = true) },
                            modifier = Modifier
                                .size(36.dp)
                                .border(1.dp, StarkBorder, CircleShape)
                                .background(StarkSurface, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "Check for Updates",
                                tint = ArcCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { homeViewModel.loadHomeData() },
                            modifier = Modifier
                                .size(36.dp)
                                .border(1.dp, StarkBorder, CircleShape)
                                .background(StarkSurface, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Telemetry",
                                tint = ArcCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // YouTube Music & Spotify Style Mood / Activity Filter Capsules
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    homeViewModel.availableMoods.forEach { mood ->
                        val isSelected = uiState.selectedMood == mood
                        FilterChip(
                            selected = isSelected,
                            onClick = { homeViewModel.selectMood(mood) },
                            label = {
                                Text(
                                    text = mood.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ArcCyanBright,
                                selectedLabelColor = StarkCarbon,
                                containerColor = StarkSurface,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = StarkBorder,
                                selectedBorderColor = ArcCyanBright
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Autopilot Smart Mix Hero Banner (Spotify AI DJ & YT Music Speed Dial style)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = StarkSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, StarkBorder, RoundedCornerShape(16.dp))
                        .clickable { homeViewModel.playAutopilotMix() }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF0F1A2A),
                                        Color(0xFF14243B),
                                        Color(0xFF0A1220)
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
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = StarkGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "J.A.R.V.I.S. SMART AUTOPILOT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        ),
                                        color = StarkGold
                                    )
                                }
                                Text(
                                    text = "Start AI Recommendation Queue",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    ),
                                    color = TextPrimary,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                                Text(
                                    text = "TARGET: ${uiState.autopilotTargetSinger} • ${uiState.autopilotTargetGenre}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    ),
                                    color = ArcCyanBright,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Button(
                                onClick = { homeViewModel.playAutopilotMix() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uiState.isAutopilotEngaging) StarkGold else ArcCyanBright,
                                    contentColor = StarkCarbon
                                ),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                enabled = !uiState.isAutopilotEngaging
                            ) {
                                if (uiState.isAutopilotEngaging) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = StarkCarbon,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ENGAGING...",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "ENGAGE",
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
        }

        // Active Mood Picks (when a mood filter other than 'All' is active)
        if (uiState.selectedMood != "All" && uiState.moodTracks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(18.dp))
                SectionHeader(
                    title = "${uiState.selectedMood.uppercase()} PICKS",
                    subtitle = "Handpicked tracks matching your active vibe",
                    tag = "ACTIVE VIBE",
                    tagColor = ArcCyanBright
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(uiState.moodTracks, key = { "active_mood_${it.id}" }) { track ->
                        RecommendationCard(
                            track = track,
                            onClick = {
                                homeViewModel.playTrackWithSuggestionQueue(
                                    track,
                                    uiState.moodTracks
                                )
                            },
                            onOptionsClick = { selectedTrackForOptions = track }
                        )
                    }
                }
            }
        }

        // 2. Quick Picks (Spotify-style 2x3 Grid with Mini Play AFFORDANCE)
        if (uiState.quickPicks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(18.dp))
                SectionHeader(
                    title = "JUMP BACK IN",
                    subtitle = "Your top rotation and recent cores",
                    tag = "QUICK ACCESS",
                    tagColor = ArcCyanBright
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    val chunked = uiState.quickPicks.take(6).chunked(2)
                    chunked.forEach { rowTracks ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowTracks.forEach { track ->
                                QuickPickCard(
                                    track = track,
                                    onClick = {
                                        homeViewModel.playTrackWithSuggestionQueue(
                                            track,
                                            uiState.quickPicks
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowTracks.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 3. Made For You // Daily Mixes (Spotify & YT Music Style)
        if (uiState.dailyMixes.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(22.dp))
                SectionHeader(
                    title = "MADE FOR YOU",
                    subtitle = "Algorithmic mixes personalized for you",
                    tag = "DAILY MIXES",
                    tagColor = ArcCyanBright
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(uiState.dailyMixes, key = { it.id }) { mix ->
                        DailyMixCard(
                            mix = mix,
                            onClick = { homeViewModel.playDailyMix(mix) }
                        )
                    }
                }
            }
        }

        // Loading Indicator Shimmer
        if (uiState.isLoading && uiState.trendingTracks.isEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                ShimmerTrackList(count = 4)
            }
        }

        // 4. Because You Listen To (Top liked artist recommendation)
        if (uiState.becauseYouLikedTracks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(22.dp))
                SectionHeader(
                    title = uiState.becauseYouLikedTitle,
                    subtitle = uiState.becauseYouLikedSubtitle,
                    tag = "AFFINITY ENGINE",
                    tagColor = StarkGold
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(uiState.becauseYouLikedTracks, key = { "liked_${it.id}" }) { track ->
                        RecommendationCard(
                            track = track,
                            onClick = {
                                homeViewModel.playTrackWithSuggestionQueue(
                                    track,
                                    uiState.becauseYouLikedTracks
                                )
                            },
                            onOptionsClick = { selectedTrackForOptions = track }
                        )
                    }
                }
            }
        }

        // 5. Similar To (Secondary Artist Discovery Carousel)
        if (uiState.similarArtistTracks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(22.dp))
                SectionHeader(
                    title = uiState.similarArtistTitle,
                    subtitle = uiState.similarArtistSubtitle,
                    tag = "DISCOVERY",
                    tagColor = ArcCyanBright
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(uiState.similarArtistTracks, key = { "similar_${it.id}" }) { track ->
                        RecommendationCard(
                            track = track,
                            onClick = {
                                homeViewModel.playTrackWithSuggestionQueue(
                                    track,
                                    uiState.similarArtistTracks
                                )
                            },
                            onOptionsClick = { selectedTrackForOptions = track }
                        )
                    }
                }
            }
        }

        // 6. Heavy Rotation (Repeatedly played tracks & highest affinity)
        if (uiState.heavyRotationTracks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(22.dp))
                SectionHeader(
                    title = "HEAVY ROTATION",
                    subtitle = "Tracks you play the most on repeat",
                    tag = "FREQUENCY MATRIX",
                    tagColor = ArcCyanBright
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(uiState.heavyRotationTracks, key = { "heavy_${it.id}" }) { track ->
                        RecommendationCard(
                            track = track,
                            onClick = {
                                homeViewModel.playTrackWithSuggestionQueue(
                                    track,
                                    uiState.heavyRotationTracks
                                )
                            },
                            onOptionsClick = { selectedTrackForOptions = track }
                        )
                    }
                }
            }
        }

        // 7. Discovery Radar (New sonic horizons based on user's favorite genres)
        if (uiState.discoveryRadarTracks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(22.dp))
                SectionHeader(
                    title = "DISCOVERY RADAR",
                    subtitle = "New sonic horizons based on your favorite genres",
                    tag = "ADJACENT CORES",
                    tagColor = ArcCyan
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(uiState.discoveryRadarTracks, key = { "disc_${it.id}" }) { track ->
                        RecommendationCard(
                            track = track,
                            onClick = {
                                homeViewModel.playTrackWithSuggestionQueue(
                                    track,
                                    uiState.discoveryRadarTracks
                                )
                            },
                            onOptionsClick = { selectedTrackForOptions = track }
                        )
                    }
                }
            }
        }

        // 8. Category Radar (Deep Genre / Category Classification)
        if (uiState.categoryRadarTracks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(22.dp))
                SectionHeader(
                    title = uiState.categoryRadarTitle,
                    subtitle = "Genre classification matrix",
                    tag = "CATEGORY RADAR",
                    tagColor = ArcCyanBright
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(uiState.categoryRadarTracks, key = { "cat_${it.id}" }) { track ->
                        RecommendationCard(
                            track = track,
                            onClick = {
                                homeViewModel.playTrackWithSuggestionQueue(
                                    track,
                                    uiState.categoryRadarTracks
                                )
                            },
                            onOptionsClick = { selectedTrackForOptions = track }
                        )
                    }
                }
            }
        }

        // 9. Vault Favorites (Liked songs & downloads)
        if (uiState.vaultFavoritesTracks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(22.dp))
                SectionHeader(
                    title = "VAULT FAVORITES",
                    subtitle = "Saved tracks from your library and downloads",
                    tag = "LIKED & OFFLINE",
                    tagColor = StarkGold
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(uiState.vaultFavoritesTracks, key = { "vault_${it.id}" }) { track ->
                        RecommendationCard(
                            track = track,
                            onClick = {
                                homeViewModel.playTrackWithSuggestionQueue(
                                    track,
                                    uiState.vaultFavoritesTracks
                                )
                            },
                            onOptionsClick = { selectedTrackForOptions = track }
                        )
                    }
                }
            }
        }

        // 10. Arc Mood Matrix (Ambient Mood Stations when 'All' is selected)
        if (uiState.selectedMood == "All" && uiState.moodTracks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(22.dp))
                SectionHeader(
                    title = "MOOD MATRIX",
                    subtitle = "Acoustic and ambient soundscapes",
                    tag = "CHILL & AMBIENT",
                    tagColor = ArcCyanBright
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(uiState.moodTracks, key = { "station_${it.id}" }) { track ->
                        RecommendationCard(
                            track = track,
                            onClick = {
                                homeViewModel.playTrackWithSuggestionQueue(
                                    track,
                                    uiState.moodTracks
                                )
                            },
                            onOptionsClick = { selectedTrackForOptions = track }
                        )
                    }
                }
            }
        }

        // 11. Trending Masters (Vertical Track List with Quick Suggestion Queue)
        if (uiState.trendingTracks.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(26.dp))
                SectionHeader(
                    title = "TRENDING MASTERS",
                    subtitle = "Global viral tracks updated live",
                    tag = "GLOBAL AUDIO LOG",
                    tagColor = ArcCyan
                )
                Spacer(modifier = Modifier.height(6.dp))
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

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    tag: String? = null,
    tagColor: Color = ArcCyanBright,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = TextPrimary
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }

            if (!tag.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = tagColor.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, tagColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = tagColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPickCard(
    track: TrackEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = StarkSurface,
        modifier = modifier
            .height(58.dp)
            .border(1.dp, StarkBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            // Album art thumbnail flush to the left
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(Color(0xFF161F30)),
                contentAlignment = Alignment.Center
            ) {
                if (track.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = track.thumbnailUrl,
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

            // Track info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    ),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp
                    ),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Spotify-style mini circular play button on the right edge
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ArcCyanBright)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = StarkCarbon,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    track: TrackEntity,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StarkSurface,
        modifier = modifier
            .width(148.dp)
            .border(1.dp, StarkBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Artwork Box with Play overlay trigger
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF161F30)),
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

                // Mini Arc play disc badge on corner of thumbnail
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(ArcCyanBright)
                        .border(1.dp, ArcCyanBright, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play with Suggestions",
                        tint = StarkCarbon,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title & Artist
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = TextPrimary,
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
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp
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
}

@Composable
private fun DailyMixCard(
    mix: DailyMix,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = StarkSurface,
        modifier = modifier
            .width(160.dp)
            .border(1.dp, StarkBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Artwork Box with gradient cover and stacked thumbnails
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
                    .clip(RoundedCornerShape(12.dp))
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
                // First track thumbnail if available
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
                    // Gradient scrim over image
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        StarkCarbon.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )
                }

                // Daily Mix top tag
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(StarkCarbon.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                        .border(0.5.dp, ArcCyanBright.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "DAILY MIX",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        ),
                        color = ArcCyanBright
                    )
                }

                // Floating circular Play Button
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ArcCyanBright)
                        .border(1.dp, ArcCyanBright, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Mix",
                        tint = StarkCarbon,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = mix.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = mix.subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                ),
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
