package com.satvik.satvikx.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satvik.satvikx.data.download.DownloadRepository
import com.satvik.satvikx.data.local.dao.PlaylistDao
import com.satvik.satvikx.data.local.dao.SearchHistoryDao
import com.satvik.satvikx.data.local.entity.PlaylistTrackCrossRef
import com.satvik.satvikx.data.local.entity.SearchHistoryEntity
import com.satvik.satvikx.data.local.entity.TrackEntity
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
            .debounce(400)
            .distinctUntilChanged()
            .filter { it.isNotBlank() }
            .onEach { query ->
                executeSearch(query)
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
            streamRepository.searchTracks("Trending").collect { result ->
                result.fold(
                    onSuccess = { tracks ->
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                results = tracks,
                                error = if (tracks.isEmpty()) "No tracks found" else null
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

    fun executeSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            searchHistoryDao.recordSearchQuery(SearchHistoryEntity(trimmed))
        }

        viewModelScope.launch {
            _uiState.update { it.copy(query = trimmed, isSearching = true, error = null) }
            streamRepository.searchTracks(trimmed).collect { result ->
                result.fold(
                    onSuccess = { tracks ->
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                results = tracks,
                                error = if (tracks.isEmpty()) "No tracks found" else null
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
        if (customQueue != null) {
            val filtered = customQueue.filter { it.id == track.id || it.isSongOnly() }
            playbackConnectionManager.playTrack(track, filtered)
        } else {
            // YouTube behavior: Selected song plays immediately as seed track,
            // while YouTube algorithmic recommendations populate the "Up Next" queue,
            // strictly filtering out compilation videos, listicles, and long non-song videos.
            playbackConnectionManager.playTrack(track, listOf(track))
            viewModelScope.launch {
                try {
                    val recommendations = recommendationRepository.generateRecommendationQueue(track)
                    val filteredRecs = recommendations.filter { it.isSongOnly() }
                    if (filteredRecs.isNotEmpty()) {
                        playbackConnectionManager.updateUpcomingQueue(filteredRecs)
                    }
                } catch (_: Exception) {}
            }
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
