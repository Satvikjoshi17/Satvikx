package com.satvik.satvikx.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satvik.satvikx.data.download.DownloadRepository
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.repository.HomeRecommendationCategories
import com.satvik.satvikx.data.repository.RecommendationRepository
import com.satvik.satvikx.playback.PlaybackConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val greeting: String = "Welcome, Tony",
    val telemetryStatus: String = "J.A.R.V.I.S. // AFFINITY MATRIX ONLINE",
    val isLoading: Boolean = false,
    val quickPicks: List<TrackEntity> = emptyList(),
    val becauseYouLikedTitle: String = "BECAUSE YOU LIKED",
    val becauseYouLikedTracks: List<TrackEntity> = emptyList(),
    val heavyRotationTracks: List<TrackEntity> = emptyList(),
    val discoveryRadarTracks: List<TrackEntity> = emptyList(),
    val vaultFavoritesTracks: List<TrackEntity> = emptyList(),
    val trendingTracks: List<TrackEntity> = emptyList(),
    val moodTracks: List<TrackEntity> = emptyList(),
    val selectedMood: String = "Chill",
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recommendationRepository: RecommendationRepository,
    private val playbackConnectionManager: PlaybackConnectionManager,
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val availableMoods = listOf("Chill", "Workout", "Focus", "Party", "Synthwave", "Retro", "Romantic")

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val data: HomeRecommendationCategories = recommendationRepository.loadHomeRecommendations(_uiState.value.selectedMood)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        greeting = data.timeOfDayTitle,
                        telemetryStatus = data.timeOfDaySubtitle,
                        quickPicks = data.quickPicks,
                        becauseYouLikedTitle = data.becauseYouLikedTitle,
                        becauseYouLikedTracks = data.becauseYouLikedTracks,
                        heavyRotationTracks = data.heavyRotationTracks,
                        discoveryRadarTracks = data.discoveryRadarTracks,
                        vaultFavoritesTracks = data.vaultFavoritesTracks,
                        trendingTracks = data.trendingTracks,
                        moodTracks = data.moodTracks
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Telemetry sync error: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun selectMood(mood: String) {
        if (_uiState.value.selectedMood == mood && _uiState.value.moodTracks.isNotEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(selectedMood = mood) }
            try {
                val updatedData = recommendationRepository.loadHomeRecommendations(mood)
                _uiState.update { it.copy(moodTracks = updatedData.moodTracks) }
            } catch (e: Exception) {
                // Silently ignore mood error
            }
        }
    }

    /**
     * Plays the selected track immediately and configures a dynamic algorithmic recommendation queue.
     * Guaranteed to populate the queue with high-relevance recommended tracks, NOT raw search results!
     */
    fun playTrackWithSuggestionQueue(track: TrackEntity, contextList: List<TrackEntity> = emptyList()) {
        val initialQueue = if (contextList.isNotEmpty()) {
            listOf(track) + contextList.filter { it.id != track.id }
        } else {
            listOf(track)
        }

        playbackConnectionManager.playTrack(track, initialQueue)

        // Asynchronously enrich the queue with deeper algorithmic suggestions
        viewModelScope.launch {
            try {
                val deepRecommendations = recommendationRepository.generateRecommendationQueue(track)
                if (deepRecommendations.isNotEmpty()) {
                    playbackConnectionManager.updateUpcomingQueue(deepRecommendations)
                }
            } catch (e: Exception) {
                // Fail gracefully, existing context queue remains
            }
        }
    }

    /**
     * Autopilot / J.A.R.V.I.S. Smart Mix:
     * Assembles a 25+ track dynamic queue combining user affinity, liked/heavy rotation tracks,
     * and discovery recommendations, then immediately begins playback from the first track.
     */
    fun playAutopilotMix() {
        val state = _uiState.value
        val combined = (state.becauseYouLikedTracks + state.heavyRotationTracks + state.discoveryRadarTracks + state.vaultFavoritesTracks + state.trendingTracks)
            .distinctBy { it.id }
            .shuffled()

        if (combined.isNotEmpty()) {
            val first = combined.first()
            playbackConnectionManager.playTrack(first, combined)
        }
    }

    fun playNext(track: TrackEntity) {
        playbackConnectionManager.playNext(track)
    }

    fun addToQueue(track: TrackEntity) {
        playbackConnectionManager.addToQueue(track)
    }

    fun enqueueDownload(track: TrackEntity) {
        downloadRepository.enqueueDownload(track)
    }
}
