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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.ui.components.AddToPlaylistDialog
import com.satvik.satvikx.ui.components.OpticallyCenteredPlayIcon
import com.satvik.satvikx.ui.components.ShimmerTrackList
import com.satvik.satvikx.ui.components.TrackActionBottomSheet
import com.satvik.satvikx.ui.components.TrackItem
import com.satvik.satvikx.ui.components.isolateHorizontalScroll
import com.satvik.satvikx.ui.theme.SpotifyGreen
import com.satvik.satvikx.ui.viewmodel.LibraryViewModel
import com.satvik.satvikx.ui.viewmodel.SearchViewModel
import kotlinx.coroutines.delay

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
    LaunchedEffect(uiState.query) {
        if (textFieldValue.text.trim() != uiState.query.trim() || uiState.query.isEmpty()) {
            textFieldValue = TextFieldValue(
                text = uiState.query,
                selection = TextRange(uiState.query.length)
            )
        }
    }

    // Fluid back-and-forth navigation: first close keyboard/unfocus, then clear search and return to Explore
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

    // Curated Spotify Mobile Browse Categories with signature colors and album covers
    val exploreGenres = remember {
        listOf(
            SpotifyCategory(
                title = "Bollywood",
                query = "Bollywood Hits",
                color = Color(0xFFE91E63),
                thumbnailUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300"
            ),
            SpotifyCategory(
                title = "Pop",
                query = "Pop Hits 2026",
                color = Color(0xFF8B5CF6),
                thumbnailUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300"
            ),
            SpotifyCategory(
                title = "Punjabi",
                query = "Punjabi Hits",
                color = Color(0xFFF59E0B),
                thumbnailUrl = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=300"
            ),
            SpotifyCategory(
                title = "Hip-Hop",
                query = "Hip Hop Rap",
                color = Color(0xFFEA580C),
                thumbnailUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=300"
            ),
            SpotifyCategory(
                title = "Chill & Lo-Fi",
                query = "Chill Lo-Fi Beats",
                color = Color(0xFF509BF5),
                thumbnailUrl = "https://images.unsplash.com/photo-1445985543469-433ec45754ac?w=300"
            ),
            SpotifyCategory(
                title = "Workout",
                query = "Workout Motivation Songs",
                color = Color(0xFFEF4444),
                thumbnailUrl = "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?w=300"
            ),
            SpotifyCategory(
                title = "Indie",
                query = "Indie Music",
                color = Color(0xFF10B981),
                thumbnailUrl = "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=300"
            ),
            SpotifyCategory(
                title = "Dance / EDM",
                query = "EDM Festival Hits",
                color = Color(0xFF06B6D4),
                thumbnailUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=300"
            ),
            SpotifyCategory(
                title = "Rock",
                query = "Classic Rock Hits",
                color = Color(0xFFE91429),
                thumbnailUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=300"
            ),
            SpotifyCategory(
                title = "Romance",
                query = "Romantic Love Songs",
                color = Color(0xFFEC4899),
                thumbnailUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=300"
            ),
            SpotifyCategory(
                title = "Night Drive",
                query = "Night Drive Synthwave",
                color = Color(0xFF1E3A8A),
                thumbnailUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=300"
            ),
            SpotifyCategory(
                title = "Focus & Study",
                query = "Focus Instrumental Beats",
                color = Color(0xFF475569),
                thumbnailUrl = "https://images.unsplash.com/photo-1434030216411-0b793f4b4173?w=300"
            )
        )
    }

    val quickPicks = listOf("Trending", "Chill Lofi", "Gym Workout", "Bollywood", "Punjabi", "Synthwave", "EDM")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(Color(0xFF121212))
    ) {
        // Authentic Spotify Mobile Header
        Text(
            text = "Search",
            style = MaterialTheme.typography.displayMedium.copy(
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            ),
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
        )

        // Spotify Signature Clean White Search Bar Pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasActiveQueryOrResults) {
                    IconButton(
                        onClick = {
                            isSearchFocused = false
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            textFieldValue = TextFieldValue("", TextRange.Zero)
                            viewModel.clearSearch()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Explore",
                            tint = Color(0xFF121212)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF121212),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                BasicTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        textFieldValue = newValue
                        if (newValue.text != uiState.query) {
                            viewModel.onQueryChanged(newValue.text)
                        }
                        isSearchFocused = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            isSearchFocused = focusState.isFocused
                            if (focusState.isFocused && textFieldValue.text.isNotEmpty()) {
                                if (textFieldValue.selection.start == 0 && textFieldValue.selection.end == 0) {
                                    textFieldValue = textFieldValue.copy(
                                        selection = TextRange(textFieldValue.text.length)
                                    )
                                }
                            }
                        },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF121212),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(Color(0xFF121212)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
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
                    decorationBox = { innerTextField ->
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                text = "What do you want to listen to?",
                                style = TextStyle(
                                    color = Color(0xFF535353),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                        innerTextField()
                    }
                )

                if (textFieldValue.text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            textFieldValue = TextFieldValue("", TextRange.Zero)
                            viewModel.clearSearch()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = Color(0xFF121212),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Content Area
        Column(modifier = Modifier.fillMaxSize()) {
            // Quick Pick Filter Capsules
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .isolateHorizontalScroll()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPicks.forEach { chipText ->
                    val isSelected = uiState.query.equals(chipText, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) SpotifyGreen else Color(0xFF282828),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                isSearchFocused = false
                                viewModel.executeSearch(chipText, saveToHistory = true)
                            }
                    ) {
                        Text(
                            text = chipText,
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

            Spacer(modifier = Modifier.height(10.dp))

            // Results / Browse All Grid / Loading
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    // Initial search loading
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
                    // Results exist: Spotify Top Result Spotlight + Songs List
                    uiState.results.isNotEmpty() -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (uiState.isSearching) {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp),
                                    color = SpotifyGreen,
                                    trackColor = Color.Transparent
                                )
                            }

                            val topResult = uiState.results.first()
                            val songResults = uiState.results.drop(1)

                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                // Top Result Spotlight
                                item {
                                    Text(
                                        text = "Top result",
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp
                                        ),
                                        color = Color.White,
                                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
                                    )

                                    // Spotify Elevated Top Result Card
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF181818))
                                            .clickable {
                                                keyboardController?.hide()
                                                focusManager.clearFocus()
                                                isSearchFocused = false
                                                viewModel.playTrack(topResult)
                                            }
                                            .padding(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Top Result Square Artwork
                                            Box(
                                                modifier = Modifier
                                                    .size(70.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF282828))
                                            ) {
                                                if (topResult.thumbnailUrl.isNotBlank()) {
                                                    AsyncImage(
                                                        model = topResult.thumbnailUrl,
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.MusicNote,
                                                        contentDescription = null,
                                                        tint = Color.White.copy(alpha = 0.5f),
                                                        modifier = Modifier
                                                            .align(Alignment.Center)
                                                            .size(36.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(14.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = topResult.title,
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 17.sp
                                                    ),
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Spacer(modifier = Modifier.height(6.dp))

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(
                                                        shape = RoundedCornerShape(10.dp),
                                                        color = Color(0xFF282828),
                                                        modifier = Modifier.padding(end = 8.dp)
                                                    ) {
                                                        Text(
                                                            text = "Song",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.SemiBold
                                                            ),
                                                            color = Color.White,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                        )
                                                    }

                                                    Text(
                                                        text = topResult.artist,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontSize = 13.sp
                                                        ),
                                                        color = Color(0xFFA7A7A7),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            // Spotify Signature Centered Green Play Button
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .shadow(4.dp, CircleShape)
                                                    .clip(CircleShape)
                                                    .background(SpotifyGreen)
                                                    .clickable {
                                                        keyboardController?.hide()
                                                        focusManager.clearFocus()
                                                        isSearchFocused = false
                                                        viewModel.playTrack(topResult)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                OpticallyCenteredPlayIcon(
                                                    modifier = Modifier.size(17.dp),
                                                    tint = Color.Black
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(18.dp))

                                    if (songResults.isNotEmpty()) {
                                        Text(
                                            text = "Songs",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 20.sp
                                            ),
                                            color = Color.White,
                                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                                        )
                                    }
                                }

                                items(
                                    items = songResults,
                                    key = { it.id }
                                ) { track ->
                                    TrackItem(
                                        modifier = Modifier.animateItem(),
                                        track = track,
                                        onClick = {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            isSearchFocused = false
                                            viewModel.playTrack(track)
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
                        // Spotify "Browse all" 2-Column Grid with Recent Searches
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            // Spotify Recent Searches
                            if (uiState.searchHistory.isNotEmpty()) {
                                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                    Column(modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Recent searches",
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                ),
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Clear all",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFFA7A7A7)
                                                ),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .clickable { viewModel.clearAllSearchHistory() }
                                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                                .isolateHorizontalScroll(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            uiState.searchHistory.take(8).forEach { historyQuery ->
                                                Surface(
                                                    shape = RoundedCornerShape(18.dp),
                                                    color = Color(0xFF282828),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(18.dp))
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
                                                            tint = Color(0xFFA7A7A7),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = historyQuery,
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                fontWeight = FontWeight.Medium,
                                                                fontSize = 13.sp
                                                            ),
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
                                                                tint = Color(0xFFA7A7A7),
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // "Browse all" Section Header
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = "Browse all",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    ),
                                    color = Color.White,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                )
                            }

                            // Signature Spotify 2-Column Genre Cards with Rotated Artwork
                            items(exploreGenres) { category ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(104.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(category.color)
                                        .clickable {
                                            keyboardController?.hide()
                                            focusManager.clearFocus()
                                            isSearchFocused = false
                                            viewModel.executeSearch(category.query, saveToHistory = true)
                                        }
                                ) {
                                    Text(
                                        text = category.title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            lineHeight = 20.sp
                                        ),
                                        color = Color.White,
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .fillMaxWidth(0.65f)
                                    )

                                    // Signature Rotated Artwork Tile Overflowing Bottom-Right Corner
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = 16.dp, y = 10.dp)
                                            .rotate(25f)
                                            .size(68.dp)
                                            .shadow(6.dp, RoundedCornerShape(4.dp))
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF1E1E1E))
                                    ) {
                                        AsyncImage(
                                            model = category.thumbnailUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
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
                viewModel.playTrack(it)
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

private data class SpotifyCategory(
    val title: String,
    val query: String,
    val color: Color,
    val thumbnailUrl: String
)
