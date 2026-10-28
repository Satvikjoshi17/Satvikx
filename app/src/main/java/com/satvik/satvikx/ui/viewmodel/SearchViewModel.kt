package com.satvik.satvikx.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satvik.satvikx.data.download.DownloadRepository
import com.satvik.satvikx.data.local.dao.PlaylistDao
import com.satvik.satvikx.data.local.dao.SearchHistoryDao
import com.satvik.satvikx.data.local.entity.PlaylistTrackCrossRef
import com.satvik.satvikx.data.local.entity.SearchHistoryEntity
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.entity.distinctSongs
import com.satvik.satvikx.data.local.entity.isSameSong
import com.satvik.satvikx.data.local.entity.isSongOnly
import com.satvik.satvikx.data.repository.StreamRepository
import com.satvik.satvikx.playback.PlaybackConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.satvik.satvikx.data.repository.RecommendationRepository

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<TrackEntity> = emptyList(),
    val error: String? = null,
    val searchHistory: List<String> = emptyList()
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    private val playbackConnectionManager: PlaybackConnectionManager,
    private val recommendationRepository: RecommendationRepository,
    private val downloadRepository: DownloadRepository,
    private val playlistDao: PlaylistDao,
    private val searchHistoryDao: SearchHistoryDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _focusSearchEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val focusSearchEvents: SharedFlow<Unit> = _focusSearchEvents.asSharedFlow()

    fun requestSearchFocus() {
        _focusSearchEvents.tryEmit(Unit)
    }

    private val queryFlow = MutableStateFlow("")

    init {
        // Debounce query input to eliminate unnecessary API requests
        queryFlow
            .debounce(450)
            .distinctUntilChanged()
            .filter { it.isNotBlank() }
            .onEach { query ->
                executeSearch(query, saveToHistory = false)
            }
            .launchIn(viewModelScope)

        // Observe recent search history queries
        searchHistoryDao.getRecentSearchQueries()
            .onEach { historyEntities ->
                _uiState.update { it.copy(searchHistory = historyEntities.map { item -> item.query }) }
            }
            .launchIn(viewModelScope)
    }

    private fun loadInitialTrending() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, error = null) }
            streamRepository.searchTracks("Trending Global Hits").collect { result ->
                result.fold(
                    onSuccess = { tracks ->
                        val songsOnly = tracks.filter { it.isSongOnly() }
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                results = songsOnly,
                                error = if (songsOnly.isEmpty()) "No tracks found" else null
                            )
                        }
                    },
                    onFailure = { throwable ->
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = throwable.message ?: "Search failed. Check connection."
                            )
                        }
                    }
                )
            }
        }
    }

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        queryFlow.value = newQuery
        if (newQuery.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), isSearching = false, error = null) }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(query = "", results = emptyList(), isSearching = false, error = null) }
        queryFlow.value = ""
    }

    /**
     * Resolves generic query strings or quick pick category names into high-relevance music queries,
     * ensuring YouTube and JioSaavn APIs return actual music tracks and song streams rather than
     * non-music video tutorials, exercise guides, or vlogs.
     */
    private fun resolveMusicIntentQuery(rawQuery: String): String {
        val q = rawQuery.trim()
        val lower = q.lowercase()

        return when {
            // Gym / Workout queries
            lower == "gym" || lower == "workout" || lower == "gym workout" || lower == "gym & workout" || lower == "fitness" ->
                "Gym Workout Motivation Songs"
            lower.startsWith("gym workout") && !lower.contains("song") && !lower.contains("music") && !lower.contains("track") ->
                "$q motivation songs"
            (lower.startsWith("workout") || lower.endsWith("workout")) && !lower.contains("song") && !lower.contains("music") && !lower.contains("track") ->
                "$q music songs"
            lower.startsWith("gym") && !lower.contains("song") && !lower.contains("music") ->
                "$q motivation songs"

            // Trending queries
            lower == "trending" || lower == "trending 50" || lower == "trending global" ->
                "Trending Global Music Hits 2026"

            // Lo-Fi queries
            lower == "chill lofi" || lower == "lofi" || lower == "lo-fi" ->
                "Chill Lofi Beats Songs"

            // Synthwave
            lower == "synthwave" || lower == "synthwave 2088" ->
                "Cyberpunk Synthwave Music"

            // EDM
            lower == "edm" || lower == "edm & club" ->
                "EDM Festival Hits Music"

            // Bollywood & Regional
            lower == "bollywood hits" ->
                "Bollywood Hits Songs 2026"
            lower == "punjabi beats" ->
                "Punjabi Hits Songs 2026"
            lower == "night drive" ->
                "Night Drive Phonk Synthwave Songs"
            lower == "acoustic chill" ->
                "Acoustic Pop Chill Songs"
            lower == "hip hop & rap" ->
                "Best Hip Hop Rap Songs"

            else -> q
        }
    }

    /**
     * Executes track search with strict song-only filtering and intelligent music intent resolution.
     * @param saveToHistory When true (e.g. keyboard Search action or clicking a suggestion),
     *                      saves the query into search history. Live debounced typing passes false
     *                      so pausing while typing never records incomplete queries.
     */
    fun executeSearch(query: String, saveToHistory: Boolean = false) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        if (saveToHistory) {
            viewModelScope.launch {
                searchHistoryDao.recordSearchQuery(SearchHistoryEntity(trimmed))
            }
        }
        _uiState.update { it.copy(query = trimmed) }

        val backendQuery = resolveMusicIntentQuery(trimmed)

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, error = null) }
            streamRepository.searchTracks(backendQuery).collect { result ->
                result.fold(
                    onSuccess = { tracks ->
                        val allowMashup = trimmed.contains("mashup", ignoreCase = true)
                        val songsOnly = tracks.filter { it.isSongOnly(allowMashup = allowMashup) }
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                results = songsOnly,
                                error = if (songsOnly.isEmpty()) "No matching songs found" else null
                            )
                        }
                    },
                    onFailure = { throwable ->
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                error = throwable.message ?: "Search failed. Check connection."
                            )
                        }
                    }
                )
            }
        }
    }

    fun deleteSearchQuery(query: String) {
        viewModelScope.launch {
            searchHistoryDao.deleteSearchQuery(query)
        }
    }

    fun clearAllSearchHistory() {
        viewModelScope.launch {
            searchHistoryDao.clearAllSearchHistory()
        }
    }

    fun playTrack(track: TrackEntity, customQueue: List<TrackEntity>? = null) {
        val initialQueue = if (!customQueue.isNullOrEmpty()) {
            val idx = customQueue.indexOfFirst { it.id == track.id }
            if (idx != -1) {
                val upcoming = customQueue.subList(idx + 1, customQueue.size)
                    .filter { it.isSongOnly() && !it.isSameSong(track) }
                    .distinctSongs()
                val previous = customQueue.subList(0, idx)
                    .filter { it.isSongOnly() && !it.isSameSong(track) }
                    .distinctSongs()
                listOf(track) + upcoming + previous
            } else {
                listOf(track) + customQueue
                    .filter { it.id != track.id && !it.isSameSong(track) && it.isSongOnly() }
                    .distinctSongs()
            }
        } else {
            listOf(track)
        }

        playbackConnectionManager.playTrack(track, initialQueue)

        // Enrich the upcoming queue with algorithmic recommendations without interrupting active playback
        viewModelScope.launch {
            try {
                val recommendations = recommendationRepository.generateRecommendationQueue(track)
                val filteredRecs = recommendations
                    .filter { it.isSongOnly() && !it.isSameSong(track) }
                    .distinctSongs()
                if (filteredRecs.isNotEmpty()) {
                    playbackConnectionManager.updateUpcomingQueue(filteredRecs)
                }
            } catch (_: Exception) {}
        }
    }

    fun playNext(track: TrackEntity) {
        playbackConnectionManager.playNext(track)
    }

    fun addToQueue(track: TrackEntity) {
        playbackConnectionManager.addToQueue(track)
    }

    fun downloadTrack(track: TrackEntity) {
        downloadRepository.enqueueDownload(track)
    }

    fun addTrackToPlaylist(playlistId: Long, track: TrackEntity) {
        viewModelScope.launch {
            playlistDao.insertPlaylistTrackCrossRef(
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = track.id
                )
            )
        }
    }
}
