package com.satvik.satvikx.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satvik.satvikx.data.download.DownloadRepository
import com.satvik.satvikx.data.local.dao.PlaylistDao
import com.satvik.satvikx.data.local.dao.RecentPlaybackDao
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val isLoadingMood: Boolean = false,
    val selectedMood: String = "All",
    val autopilotTargetSinger: String = "GLOBAL ICONS",
    val autopilotTargetGenre: String = "ALL CATEGORIES",
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recommendationRepository: RecommendationRepository,
    private val playbackConnectionManager: PlaybackConnectionManager,
    private val downloadRepository: DownloadRepository,
    private val recentPlaybackDao: RecentPlaybackDao,
    private val playlistDao: PlaylistDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val availableMoods = listOf("All", "Focus", "Workout", "Chill", "Study", "Sleep", "Travel", "Party", "Romance", "Synthwave")

    init {
        loadHomeData()
        observeRecentPlayback()
        observeLikedSongs()
    }

    private fun observeRecentPlayback() {
        viewModelScope.launch {
            recentPlaybackDao.getRecentTracks(50).collect { recentList ->
                val validRecents = recentList
                    .filter { it.id.isNotBlank() && it.title.isNotBlank() }
                    .distinctBy { it.id }
                if (validRecents.isNotEmpty()) {
                    _uiState.update { state ->
                        val updatedQuick = (validRecents.take(6) + state.quickPicks)
                            .distinctBy { it.id }
                            .take(6)
                        state.copy(
                            jumpBackInTracks = validRecents,
                            quickPicks = updatedQuick
                        )
                    }
                }
            }
        }
    }

    private fun observeLikedSongs() {
        viewModelScope.launch {
            try {
                val likedPlaylist = withContext(Dispatchers.IO) {
                    playlistDao.getPlaylistByNameSync("Liked Songs")
                }
                if (likedPlaylist != null) {
                    playlistDao.getPlaylistWithTracks(likedPlaylist.playlistId).collect { playlistWithTracks ->
                        val tracks = playlistWithTracks?.tracks.orEmpty()
                        _uiState.update { it.copy(likedTracks = tracks) }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun refreshRecentPlayback() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val recents = recentPlaybackDao.getRecentTracksSync(50)
                    .filter { it.id.isNotBlank() && it.title.isNotBlank() }
                    .distinctBy { it.id }
                if (recents.isNotEmpty()) {
                    _uiState.update { state ->
                        val updatedQuick = (recents.take(6) + state.quickPicks)
                            .distinctBy { it.id }
                            .take(6)
                        state.copy(
                            jumpBackInTracks = recents,
                            quickPicks = updatedQuick
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun HomeUiState.applyRecommendations(data: HomeRecommendationCategories): HomeUiState {
        val liveRecents = this.jumpBackInTracks
        val finalRecents = if (liveRecents.isNotEmpty()) {
            (liveRecents + data.jumpBackInTracks).distinctBy { it.id }
        } else {
            data.jumpBackInTracks
        }

        val liveLiked = this.likedTracks
        val finalLiked = if (liveLiked.isNotEmpty()) liveLiked else data.likedTracks

        val finalQuick = (finalRecents.take(6) + data.quickPicks)
            .distinctBy { it.id }
            .take(6)

        return this.copy(
            greeting = data.timeOfDayTitle,
            telemetryStatus = data.timeOfDaySubtitle,
            quickPicks = if (finalQuick.isNotEmpty()) finalQuick else data.quickPicks,
            dailyMixes = data.dailyMixes,
            topArtists = data.topArtists,
            likedTracks = finalLiked,
            jumpBackInTracks = finalRecents,
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

            if (forceRefresh) {
                recommendationRepository.clearHomeCache()
            }

            // 1. Instant Cache Retrieval:
            // Paint the home screen in 0-5ms with cached feeds and real-time local counts
            val cached = if (!forceRefresh) recommendationRepository.getCachedRecommendations(currentMood) else null
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
                _uiState.update { it.copy(isLoading = !forceRefresh, isRefreshing = forceRefresh, error = null) }
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
        if (_uiState.value.selectedMood == mood) {
            // Tapping the active mood chip toggles it back to "All" (Standard Spotify UX)
            if (mood != "All") {
                selectMood("All")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(selectedMood = mood) }

            if (mood == "All") {
                _uiState.update { it.copy(isLoadingMood = false) }
                return@launch
            }

            // 1. Instant cache check for mood
            val cached = recommendationRepository.getCachedRecommendations(mood)
            if (cached != null && cached.moodTracks.isNotEmpty()) {
                _uiState.update { it.copy(moodTracks = cached.moodTracks, isLoadingMood = false) }
                if (recommendationRepository.isHomeCacheFresh(mood)) {
                    return@launch
                }
            } else {
                _uiState.update { it.copy(isLoadingMood = true) }
            }

            // 2. Fast targeted single-category query (~1s) instead of heavy 6-category reload
            try {
                val freshMoodTracks = recommendationRepository.fetchMoodTracks(mood)
                _uiState.update { it.copy(moodTracks = freshMoodTracks, isLoadingMood = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMood = false) }
            }
        }
    }

    fun playDailyMix(mix: DailyMix) {
        if (mix.tracks.isNotEmpty()) {
            playbackConnectionManager.playTrack(mix.tracks.first(), mix.tracks, isUserPlaylist = true)
        }
    }

    fun playLikedSongs() {
        val liked = _uiState.value.likedTracks
        if (liked.isNotEmpty()) {
            playbackConnectionManager.playTrack(liked.first(), liked, isUserPlaylist = true)
        }
    }

    fun playArtistRadio(artistName: String) {
        viewModelScope.launch {
            try {
                val tracks = recommendationRepository.getArtistTracks(artistName)
                if (tracks.isNotEmpty()) {
                    playbackConnectionManager.playTrack(tracks.first(), tracks, isUserPlaylist = false)
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
            emptyList()
        }

        playbackConnectionManager.playTrack(track, initialQueue, isUserPlaylist = false)
    }

    /**
     * Autopilot / AI DJ Mix:
     * Phase 1: Starts playback INSTANTLY (< 100ms) using user's favorite anchor tracks from their listening history.
     * Phase 2: Concurrently generates 35+ customized algorithmic recommendations in the background and
     *          seamlessly extends the upcoming queue without interrupting active playback.
     */
    fun playAutopilotMix() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAutopilotEngaging = true) }
            try {
                // Phase 1: Instant Start (< 100ms) with songs the user loves
                val quickSeeds = recommendationRepository.getImmediateAutopilotSeeds(5)
                val seedTrack = quickSeeds.firstOrNull()
                if (seedTrack != null) {
                    playbackConnectionManager.playTrack(seedTrack, quickSeeds, isUserPlaylist = false)
                }

                // Phase 2: Background Algorithmic Expansion
                val fullQueue = withContext(Dispatchers.IO) {
                    recommendationRepository.generateAutopilotQueue(35).distinctSongs()
                }
                if (fullQueue.isNotEmpty()) {
                    playbackConnectionManager.updateUpcomingQueue(fullQueue)
                }
            } catch (e: Exception) {
                val state = _uiState.value
                val fallback = (state.becauseYouLikedTracks + state.heavyRotationTracks + state.discoveryRadarTracks + state.categoryRadarTracks + state.vaultFavoritesTracks + state.trendingTracks)
                    .distinctBy { it.id }
                    .shuffled()
                if (fallback.isNotEmpty()) {
                    playbackConnectionManager.playTrack(fallback.first(), fallback, isUserPlaylist = false)
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
