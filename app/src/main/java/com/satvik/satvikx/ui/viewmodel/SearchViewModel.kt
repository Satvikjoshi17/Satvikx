package com.satvik.satvikx.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satvik.satvikx.data.download.DownloadRepository
import com.satvik.satvikx.data.local.dao.PlaylistDao
import com.satvik.satvikx.data.local.entity.PlaylistTrackCrossRef
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.repository.StreamRepository
import com.satvik.satvikx.playback.PlaybackConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val error: String? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val streamRepository: StreamRepository,
    private val playbackConnectionManager: PlaybackConnectionManager,
    private val recommendationRepository: RecommendationRepository,
    private val downloadRepository: DownloadRepository,
    private val playlistDao: PlaylistDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

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

        // Seed with trending results without putting text in search bar
        loadInitialTrending()
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
            loadInitialTrending()
        }
    }

    fun executeSearch(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(query = query, isSearching = true, error = null) }
            streamRepository.searchTracks(query).collect { result ->
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

    fun playTrack(track: TrackEntity, customQueue: List<TrackEntity>? = null) {
        if (customQueue != null) {
            playbackConnectionManager.playTrack(track, customQueue)
        } else {
            // YouTube behavior: Selected song plays immediately as seed track,
            // while YouTube algorithmic recommendations learned from history populate the "Up Next" queue.
            playbackConnectionManager.playTrack(track, listOf(track))
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
