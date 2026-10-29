package com.satvik.satvikx.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.satvik.satvikx.ui.theme.ArcCyan
import com.satvik.satvikx.ui.theme.ArcCyanBright
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.satvik.satvikx.ui.theme.StarkBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.ui.components.AddToPlaylistDialog
import com.satvik.satvikx.ui.components.ShimmerTrackList
import com.satvik.satvikx.ui.components.TrackActionBottomSheet
import com.satvik.satvikx.ui.components.TrackItem
import com.satvik.satvikx.ui.theme.PrimaryNeon
import com.satvik.satvikx.ui.viewmodel.LibraryViewModel
import com.satvik.satvikx.ui.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTrackForOptions by remember { mutableStateOf<TrackEntity?>(null) }
    var trackForAddToPlaylist by remember { mutableStateOf<TrackEntity?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    var isSearchFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Preserve search text with the cursor explicitly at the END of the query
    var textFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = uiState.query,
                selection = TextRange(uiState.query.length)
            )
        )
    }

    // Keep textFieldValue in sync only when external query changes (e.g. cleared, quick pick pill, or history tap)
    // Never overwrite while the user is actively typing with matching base query so pauses and spaces are preserved
    LaunchedEffect(uiState.query) {
        if (textFieldValue.text.trim() != uiState.query.trim() || uiState.query.isEmpty()) {
            textFieldValue = TextFieldValue(
                text = uiState.query,
                selection = TextRange(uiState.query.length)
            )
        }
    }

    // Fluid back-and-forth navigation (Spotify & YouTube inspiration):
    // Intercept back button: first close keyboard/unfocus, then clear search and return to Explore
    val hasActiveQueryOrResults = uiState.query.isNotEmpty() || uiState.results.isNotEmpty()
    BackHandler(enabled = isSearchFocused || hasActiveQueryOrResults) {
        if (isSearchFocused) {
            keyboardController?.hide()
            focusManager.clearFocus()
            isSearchFocused = false
        } else if (hasActiveQueryOrResults) {
            textFieldValue = TextFieldValue("", TextRange.Zero)
            viewModel.clearSearch()
        }
    }

    // Respond to bottom-bar search double-tap or re-tap by activating search and focusing text field
    LaunchedEffect(Unit) {
        viewModel.focusSearchEvents.collect {
            isSearchFocused = true
            textFieldValue = TextFieldValue(
                text = uiState.query,
                selection = TextRange(uiState.query.length)
            )
            delay(150)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    // Dismiss keyboard when user starts scrolling the search results
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isSearchFocused = false
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }

    val exploreGenres = remember {
        listOf(
            ExploreCategory("Bollywood Hits", "Bollywood Hits", "HITS", Brush.linearGradient(listOf(Color(0xFFFF416C), Color(0xFFFF4B2B)))),
            ExploreCategory("Trending 50", "Trending Music 2026", "HOT", Brush.linearGradient(listOf(Color(0xFF00C6FF), Color(0xFF0072FF)))),
            ExploreCategory("Chill Lo-Fi", "Chill Lofi Beats", "VIBES", Brush.linearGradient(listOf(Color(0xFF8A2387), Color(0xFFE94057)))),
            ExploreCategory("Gym & Workout", "Workout Motivation Songs", "ENERGY", Brush.linearGradient(listOf(Color(0xFFFF0844), Color(0xFFFFB199)))),
            ExploreCategory("Synthwave 2088", "Cyberpunk Synthwave", "CYBER", Brush.linearGradient(listOf(Color(0xFF00F5D4), Color(0xFF7B2CBF)))),
            ExploreCategory("Punjabi Beats", "Punjabi Hits 2026", "DESI", Brush.linearGradient(listOf(Color(0xFFF7971E), Color(0xFFFFD200)))),
            ExploreCategory("Night Drive", "Night Drive Phonk Synth", "DRIVE", Brush.linearGradient(listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF)))),
            ExploreCategory("Acoustic Chill", "Acoustic Pop Chill", "CALM", Brush.linearGradient(listOf(Color(0xFF11998E), Color(0xFF38EF7D)))),
            ExploreCategory("Hip Hop & Rap", "Best Hip Hop Rap", "BEATS", Brush.linearGradient(listOf(Color(0xFFED213A), Color(0xFF93291E)))),
            ExploreCategory("EDM & Club", "EDM Festival Hits", "DANCE", Brush.linearGradient(listOf(Color(0xFF654EA3), Color(0xFFEAAFC8))))
        )
    }

    val quickPicks = listOf("Trending", "Chill Lofi", "Gym Workout", "Bollywood Hits", "Synthwave", "EDM")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 8.dp)
    ) {
        // Prominent Screen Title & Stark HUD Telemetry
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "Search",
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
                    text = "J.A.R.V.I.S. HUD • STARK AUDIO ARCHIVE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        color = ArcCyan
                    )
                )
            }
        }

        // Search Bar (Cyber Vision Style with fluid Spotify-style Back & Forth Navigation)
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                if (newValue.text != uiState.query) {
                    viewModel.onQueryChanged(newValue.text)
                }
                isSearchFocused = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    isSearchFocused = focusState.isFocused
                    if (focusState.isFocused && textFieldValue.text.isNotEmpty()) {
                        // Position cursor at the very end of the preserved query
                        if (textFieldValue.selection.start == 0 && textFieldValue.selection.end == 0) {
                            textFieldValue = textFieldValue.copy(
                                selection = TextRange(textFieldValue.text.length)
                            )
                        }
                    }
                },
            placeholder = {
                Text(
                    "What do you want to listen to?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = com.satvik.satvikx.ui.theme.TextSecondary
                )
            },
            leadingIcon = {
                if (hasActiveQueryOrResults) {
                    IconButton(
                        onClick = {
                            isSearchFocused = false
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            textFieldValue = TextFieldValue("", TextRange.Zero)
                            viewModel.clearSearch()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Explore",
                            tint = ArcCyanBright
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = ArcCyan
                    )
                }
            },
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            textFieldValue = TextFieldValue("", TextRange.Zero)
                            viewModel.clearSearch()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search query",
                            tint = ArcCyanBright
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    isSearchFocused = false
                    if (uiState.query.isNotBlank()) {
                        viewModel.executeSearch(uiState.query, saveToHistory = true)
                    }
                }
            ),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ArcCyan,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = com.satvik.satvikx.ui.theme.SurfaceElevated,
                unfocusedContainerColor = com.satvik.satvikx.ui.theme.SurfaceElevated,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Content Area (Quick picks + Top Results OR Spotify-style Explore Categories with Recent Searches)
        Column(modifier = Modifier.fillMaxSize()) {
            // Quick Pick Category Chips (Spotify Style Capsules)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPicks.forEach { chipText ->
                    val isSelected = uiState.query.equals(chipText, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            isSearchFocused = false
                            viewModel.executeSearch(chipText, saveToHistory = true)
                        },
                        label = {
                            Text(
                                text = chipText,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ArcCyan,
                            selectedLabelColor = Color(0xFF04121E),
                            containerColor = com.satvik.satvikx.ui.theme.SurfaceVariantDark,
                            labelColor = Color.White
                        ),
                        border = null
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Results / Explore Grid / Loading / Error
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    // Initial search loading when no results exist yet
                    uiState.isSearching && uiState.results.isEmpty() -> {
                        ShimmerTrackList(modifier = Modifier.padding(top = 8.dp))
                    }
                    // Error when no results exist
                    uiState.error != null && uiState.results.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = uiState.error!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    // Results exist (display them stably, showing a slim top progress indicator if background refining)
                    uiState.results.isNotEmpty() -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (uiState.isSearching) {
                                androidx.compose.material3.LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp),
                                    color = ArcCyanBright,
                                    trackColor = Color.Transparent
                                )
                            }
                            Text(
                                text = "Top Results",
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = uiState.results,
                                    key = { it.id }
                                ) { track ->
                                    TrackItem(
                                        track = track,
                                        onClick = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            isSearchFocused = false
                                            viewModel.playTrack(track, uiState.results)
                                        },
                                        onOptionClick = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            isSearchFocused = false
                                            selectedTrackForOptions = track
                                        },
                                        onDownloadClick = {
                                            viewModel.downloadTrack(track)
                                            Toast.makeText(context, "Download started for ${track.title}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        // Spotify & YouTube Style Explore & Browse Landing View with Recent Searches
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 145.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Stark HUD Recent Searches Section
                            if (uiState.searchHistory.isNotEmpty()) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Column(modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.History,
                                                    contentDescription = null,
                                                    tint = ArcCyanBright,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "RECENT SEARCHES",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        letterSpacing = 0.8.sp,
                                                        color = ArcCyanBright
                                                    )
                                                )
                                            }
                                            Text(
                                                text = "Clear All",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.LightGray
                                                ),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .clickable { viewModel.clearAllSearchHistory() }
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            uiState.searchHistory.take(8).forEach { historyQuery ->
                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = com.satvik.satvikx.ui.theme.SurfaceElevated,
                                                    border = BorderStroke(0.8.dp, StarkBorder),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .clickable {
                                                            keyboardController?.hide()
                                                            focusManager.clearFocus()
                                                            isSearchFocused = false
                                                            viewModel.executeSearch(historyQuery, saveToHistory = true)
                                                        }
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.History,
                                                            contentDescription = null,
                                                            tint = com.satvik.satvikx.ui.theme.TextSecondary,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = historyQuery,
                                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                            color = Color.White
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        IconButton(
                                                            onClick = { viewModel.deleteSearchQuery(historyQuery) },
                                                            modifier = Modifier.size(20.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = "Remove",
                                                                tint = com.satvik.satvikx.ui.theme.TextSecondary,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "Explore Genres & Moods",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                )
                            }

                            items(exploreGenres) { category ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 96.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(category.gradient)
                                        .clickable {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            isSearchFocused = false
                                            viewModel.executeSearch(category.query, saveToHistory = true)
                                        }
                                        .padding(14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = category.code,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.ExtraBold,
                                                letterSpacing = 1.sp
                                            ),
                                            color = Color.White.copy(alpha = 0.75f)
                                        )
                                        Text(
                                            text = category.title,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 17.sp,
                                                lineHeight = 22.sp
                                            ),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Action Bottom Sheet
    selectedTrackForOptions?.let { track ->
        TrackActionBottomSheet(
            track = track,
            sheetState = sheetState,
            onDismiss = { selectedTrackForOptions = null },
            onPlayNow = {
                keyboardController?.hide()
                focusManager.clearFocus()
                viewModel.playTrack(it, uiState.results)
            },
            onPlayNext = {
                viewModel.playNext(it)
                Toast.makeText(context, "Playing next: ${it.title}", Toast.LENGTH_SHORT).show()
            },
            onAddToQueue = {
                viewModel.addToQueue(it)
                Toast.makeText(context, "Added to playback queue", Toast.LENGTH_SHORT).show()
            },
            onDownload = {
                viewModel.downloadTrack(it)
                Toast.makeText(context, "Downloading ${it.title}", Toast.LENGTH_SHORT).show()
            },
            onAddToPlaylist = {
                trackForAddToPlaylist = it
            },
            onDeleteDownload = { trackToDelete ->
                libraryViewModel.deleteDownload(trackToDelete.id)
                Toast.makeText(context, "Deleted ${trackToDelete.title} from offline storage", Toast.LENGTH_SHORT).show()
            },
            onShare = { /* Share */ }
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

private data class ExploreCategory(
    val title: String,
    val query: String,
    val code: String,
    val gradient: Brush
)

