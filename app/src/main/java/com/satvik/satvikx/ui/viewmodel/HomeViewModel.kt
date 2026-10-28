package com.satvik.satvikx.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satvik.satvikx.data.download.DownloadRepository
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.entity.distinctSongs
import com.satvik.satvikx.data.local.entity.isSameSong
import com.satvik.satvikx.data.local.entity.isSongOnly
import com.satvik.satvikx.data.repository.ArtistAffinity
import com.satvik.satvikx.data.repository.DailyMix
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
    val greeting: String = "Good morning",
    val telemetryStatus: String = "AFFINITY MATRIX ONLINE",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isAutopilotEngaging: Boolean = false,
    val quickPicks: List<TrackEntity> = emptyList(),
    val dailyMixes: List<DailyMix> = emptyList(),
    val topArtists: List<ArtistAffinity> = emptyList(),
    val likedTracks: List<TrackEntity> = emptyList(),
    val jumpBackInTracks: List<TrackEntity> = emptyList(),
    val becauseYouLikedTitle: String = "BECAUSE YOU LIKED",
    val becauseYouLikedSubtitle: String = "HANDPICKED FROM YOUR RECENT TASTES",
    val becauseYouLikedTracks: List<TrackEntity> = emptyList(),
    val similarArtistTitle: String = "SIMILAR ARTISTS",
    val similarArtistSubtitle: String = "MORE LIKE WHAT YOU LOVE",
    val similarArtistTracks: List<TrackEntity> = emptyList(),
    val heavyRotationTracks: List<TrackEntity> = emptyList(),
    val discoveryRadarTracks: List<TrackEntity> = emptyList(),
    val categoryRadarTitle: String = "CATEGORY RADAR",
    val categoryRadarTracks: List<TrackEntity> = emptyList(),
    val vaultFavoritesTracks: List<TrackEntity> = emptyList(),
    val trendingTracks: List<TrackEntity> = emptyList(),
    val moodTracks: List<TrackEntity> = emptyList(),
    val selectedMood: String = "All",
    val autopilotTargetSinger: String = "GLOBAL ICONS",
    val autopilotTargetGenre: String = "ALL CATEGORIES",
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

    val availableMoods = listOf("All", "Focus", "Workout", "Chill", "Study", "Sleep", "Travel", "Party", "Romance", "Synthwave")

    init {
        loadHomeData()
    }

    private fun HomeUiState.applyRecommendations(data: HomeRecommendationCategories): HomeUiState {
        return this.copy(
            greeting = data.timeOfDayTitle,
            telemetryStatus = data.timeOfDaySubtitle,
            quickPicks = data.quickPicks,
            dailyMixes = data.dailyMixes,
            topArtists = data.topArtists,
            likedTracks = data.likedTracks,
            jumpBackInTracks = data.jumpBackInTracks,
            becauseYouLikedTitle = data.becauseYouLikedTitle,
            becauseYouLikedSubtitle = data.becauseYouLikedSubtitle,
            becauseYouLikedTracks = data.becauseYouLikedTracks,
            similarArtistTitle = data.similarArtistTitle,
            similarArtistSubtitle = data.similarArtistSubtitle,
            similarArtistTracks = data.similarArtistTracks,
            heavyRotationTracks = data.heavyRotationTracks,
            discoveryRadarTracks = data.discoveryRadarTracks,
            categoryRadarTitle = data.categoryRadarTitle,
            categoryRadarTracks = data.categoryRadarTracks,
            vaultFavoritesTracks = data.vaultFavoritesTracks,
            trendingTracks = data.trendingTracks,
            moodTracks = data.moodTracks,
            autopilotTargetSinger = data.autopilotTargetSinger,
            autopilotTargetGenre = data.autopilotTargetGenre
        )
    }

    fun loadHomeData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val currentMood = _uiState.value.selectedMood

            // 1. Instant Cache Retrieval:
            // Paint the home screen in 0-5ms with cached feeds and real-time local counts
            val cached = recommendationRepository.getCachedRecommendations(currentMood)
            val hasCache = cached != null && (cached.trendingTracks.isNotEmpty() || cached.quickPicks.isNotEmpty())

            if (hasCache) {
                _uiState.update { it.applyRecommendations(cached).copy(isLoading = false, error = null) }
            }

            // 2. Check if cache is fresh and we don't need background network fetch
            val isCacheFresh = recommendationRepository.isHomeCacheFresh(currentMood)
            if (!forceRefresh && isCacheFresh && hasCache) {
                return@launch
            }

            // 3. Revalidation required (initial launch without cache, expired TTL, or user pressed refresh)
            if (hasCache) {
                _uiState.update { it.copy(isRefreshing = true, error = null) }
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }

            try {
                val freshData: HomeRecommendationCategories = recommendationRepository.loadHomeRecommendations(currentMood)
                _uiState.update {
                    it.applyRecommendations(freshData).copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = if (!hasCache) "Telemetry sync error: ${e.localizedMessage}" else null
                    )
                }
            }
        }
    }

    fun selectMood(mood: String) {
        if (_uiState.value.selectedMood == mood && _uiState.value.moodTracks.isNotEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(selectedMood = mood) }

            // 1. Instant cache check for mood
            val cached = recommendationRepository.getCachedRecommendations(mood)
            if (cached != null && cached.moodTracks.isNotEmpty()) {
                _uiState.update { it.copy(moodTracks = cached.moodTracks) }
                if (recommendationRepository.isHomeCacheFresh(mood)) {
                    return@launch
                }
            }

            // 2. Revalidate mood feed in background
            try {
                val updatedData = recommendationRepository.loadHomeRecommendations(mood)
                _uiState.update { it.copy(moodTracks = updatedData.moodTracks) }
            } catch (e: Exception) {
                // Silently keep existing mood tracks on network failure
            }
        }
    }

    fun playDailyMix(mix: DailyMix) {
        if (mix.tracks.isNotEmpty()) {
            playbackConnectionManager.playTrack(mix.tracks.first(), mix.tracks)
        }
    }

    fun playLikedSongs() {
        val liked = _uiState.value.likedTracks
        if (liked.isNotEmpty()) {
            playbackConnectionManager.playTrack(liked.first(), liked)
        }
    }

    fun playArtistRadio(artistName: String) {
        viewModelScope.launch {
            try {
                val tracks = recommendationRepository.getArtistTracks(artistName)
                if (tracks.isNotEmpty()) {
                    playbackConnectionManager.playTrack(tracks.first(), tracks)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Plays the selected track immediately and configures a dynamic algorithmic recommendation queue.
     * Guaranteed to populate the queue with high-relevance recommended tracks, NOT raw search results!
     */
    fun playTrackWithSuggestionQueue(track: TrackEntity, contextList: List<TrackEntity> = emptyList()) {
        val initialQueue = if (contextList.isNotEmpty()) {
            val idx = contextList.indexOfFirst { it.id == track.id }
            if (idx != -1) {
                val upcoming = contextList.subList(idx + 1, contextList.size)
                    .filter { it.isSongOnly() && !it.isSameSong(track) }
                    .distinctSongs()
                val previous = contextList.subList(0, idx)
                    .filter { it.isSongOnly() && !it.isSameSong(track) }
                    .distinctSongs()
                listOf(track) + upcoming + previous
            } else {
                listOf(track) + contextList
                    .filter { it.id != track.id && !it.isSameSong(track) && it.isSongOnly() }
                    .distinctSongs()
            }
        } else {
            val fallback = (_uiState.value.quickPicks + _uiState.value.trendingTracks)
                .filter { it.id != track.id && !it.isSameSong(track) && it.isSongOnly() }
                .distinctSongs()
                .take(10)
            listOf(track) + fallback
        }

        playbackConnectionManager.playTrack(track, initialQueue)

        // Asynchronously enrich the queue with deeper algorithmic suggestions without playback interruptions
        viewModelScope.launch {
            try {
                val deepRecommendations = recommendationRepository.generateRecommendationQueue(track)
                val filtered = deepRecommendations
                    .filter { it.isSongOnly() && !it.isSameSong(track) }
                    .distinctSongs()
                if (filtered.isNotEmpty()) {
                    playbackConnectionManager.updateUpcomingQueue(filtered)
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
        viewModelScope.launch {
            _uiState.update { it.copy(isAutopilotEngaging = true) }
            try {
                val queue = recommendationRepository.generateAutopilotQueue(35).distinctSongs()
                if (queue.isNotEmpty()) {
                    val first = queue.first()
                    playbackConnectionManager.playTrack(first, queue)
                }
            } catch (e: Exception) {
                val state = _uiState.value
                val fallback = (state.becauseYouLikedTracks + state.heavyRotationTracks + state.discoveryRadarTracks + state.categoryRadarTracks + state.vaultFavoritesTracks + state.trendingTracks)
                    .distinctBy { it.id }
                    .shuffled()
                if (fallback.isNotEmpty()) {
                    playbackConnectionManager.playTrack(fallback.first(), fallback)
                }
            } finally {
                _uiState.update { it.copy(isAutopilotEngaging = false) }
            }
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
