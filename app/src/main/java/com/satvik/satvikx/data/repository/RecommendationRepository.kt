package com.satvik.satvikx.data.repository

import com.satvik.satvikx.data.local.dao.PlaybackTelemetryDao
import com.satvik.satvikx.data.local.dao.PlaylistDao
import com.satvik.satvikx.data.local.dao.RecentPlaybackDao
import com.satvik.satvikx.data.local.dao.TrackDao
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.entity.cleanArtist
import com.satvik.satvikx.data.local.entity.cleanArtistName
import com.satvik.satvikx.data.local.entity.cleanSongTitle
import com.satvik.satvikx.data.local.entity.distinctSongs
import com.satvik.satvikx.data.local.entity.getEffectiveArtists
import com.satvik.satvikx.data.local.entity.isSameSong
import com.satvik.satvikx.data.local.entity.isSongOnly
import com.satvik.satvikx.data.remote.saavn.SaavnMediaEngine
import com.satvik.satvikx.recommendation.AdaptiveSessionManager
import com.satvik.satvikx.recommendation.CandidateRetrievalEngine
import com.satvik.satvikx.recommendation.HumanCuratedQueueSequencer
import com.satvik.satvikx.recommendation.LongTermProfile
import com.satvik.satvikx.recommendation.RankingScoringEngine
import com.satvik.satvikx.recommendation.SessionAdaptationTrigger
import com.satvik.satvikx.recommendation.SessionVibeClassifier
import com.satvik.satvikx.recommendation.SessionVibeMode
import com.satvik.satvikx.recommendation.ShortTermSessionProfile
import com.satvik.satvikx.recommendation.UserTasteProfiler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ArtistAffinity(
    val name: String,
    val imageUrl: String? = null
)

data class UserAffinities(
    val topLikedArtists: List<String> = emptyList(),
    val topRecentArtists: List<String> = emptyList(),
    val topDownloadedArtists: List<String> = emptyList(),
    val overallTopArtists: List<String> = emptyList(),
    val topArtists: List<ArtistAffinity> = emptyList(),
    val topGenres: List<String> = emptyList(),
    val heavyRotationTracks: List<TrackEntity> = emptyList(),
    val vaultTracks: List<TrackEntity> = emptyList(),
    val primaryArtist: String? = null,
    val secondaryArtist: String? = null,
    val primaryGenre: String? = null
)

data class DailyMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val tracks: List<TrackEntity>,
    val gradientColors: List<Long>
)

data class AcousticVibeInfo(
    val vibeName: String,
    val searchQuery: String
)

data class HomeRecommendationCategories(
    val becauseYouLikedTitle: String = "BECAUSE YOU LIKED",
    val becauseYouLikedSubtitle: String = "BASED ON YOUR LISTENING PROFILE",
    val becauseYouLikedTracks: List<TrackEntity> = emptyList(),
    val similarArtistTitle: String = "MORE LIKE THIS",
    val similarArtistSubtitle: String = "DISCOVERIES FROM SIMILAR ARTISTS",
    val similarArtistTracks: List<TrackEntity> = emptyList(),
    val heavyRotationTracks: List<TrackEntity> = emptyList(),
    val discoveryRadarTracks: List<TrackEntity> = emptyList(),
    val vaultFavoritesTracks: List<TrackEntity> = emptyList(),
    val moodTracks: List<TrackEntity> = emptyList(),
    val trendingTracks: List<TrackEntity> = emptyList(),
    val quickPicks: List<TrackEntity> = emptyList(),
    val dailyMixes: List<DailyMix> = emptyList(),
    val topArtists: List<ArtistAffinity> = emptyList(),
    val likedTracks: List<TrackEntity> = emptyList(),
    val jumpBackInTracks: List<TrackEntity> = emptyList(),
    val categoryRadarTitle: String = "CATEGORY RADAR",
    val categoryRadarTracks: List<TrackEntity> = emptyList(),
    val timeOfDayTitle: String = "TIME-SHIFTED PROTOCOL",
    val timeOfDaySubtitle: String = "ACOUSTIC FOCUS MATRIX",
    val autopilotTargetSinger: String = "GLOBAL ICONS",
    val autopilotTargetGenre: String = "ALL CATEGORIES"
)

/**
 * Chief Recommendation Systems Architecture for SatvikX Music.
 *
 * Combines the strongest qualities of Spotify (Daily Mixes & Artist Spacing),
 * YouTube Music (Continuous Watch-Next Continuation Graph), Apple Music (Human Curation Pacing),
 * and TikTok (Real-Time Skip/Replay Immediate Feedback Adaptation).
 */
@Singleton
class RecommendationRepository @Inject constructor(
    private val recentPlaybackDao: RecentPlaybackDao,
    private val playbackTelemetryDao: PlaybackTelemetryDao,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val streamRepository: StreamRepository,
    private val saavnMediaEngine: SaavnMediaEngine,
    private val homeFeedCacheManager: HomeFeedCacheManager,
    private val adaptiveSessionManager: AdaptiveSessionManager,
    private val userTasteProfiler: UserTasteProfiler,
    private val candidateRetrievalEngine: CandidateRetrievalEngine,
    private val rankingScoringEngine: RankingScoringEngine,
    private val queueSequencer: HumanCuratedQueueSequencer,
    private val vibeClassifier: SessionVibeClassifier
) {

    // Anti-looping ring buffers: tracks recently recommended in active session
    private val sessionExclusionIds = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val sessionExclusionKeys = java.util.Collections.synchronizedList(mutableListOf<String>())

    fun recordRecommendedTrack(track: TrackEntity) {
        synchronized(sessionExclusionIds) {
            sessionExclusionIds.add(track.id)
            if (sessionExclusionIds.size > 140) sessionExclusionIds.removeAt(0)
        }
        val key = track.cleanSongTitle().replace(" ", "")
        if (key.isNotBlank()) {
            synchronized(sessionExclusionKeys) {
                sessionExclusionKeys.add(key)
                if (sessionExclusionKeys.size > 140) sessionExclusionKeys.removeAt(0)
            }
        }
    }

    fun recordRecommendedTrack(trackId: String) {
        synchronized(sessionExclusionIds) {
            sessionExclusionIds.add(trackId)
            if (sessionExclusionIds.size > 140) sessionExclusionIds.removeAt(0)
        }
    }

    private fun isExcludedFromSession(track: TrackEntity): Boolean {
        synchronized(sessionExclusionIds) {
            if (sessionExclusionIds.contains(track.id)) return true
        }
        val key = track.cleanSongTitle().replace(" ", "")
        if (key.isNotBlank()) {
            synchronized(sessionExclusionKeys) {
                if (sessionExclusionKeys.contains(key)) return true
            }
        }
        return false
    }

    // ==========================================
    // REAL-TIME SESSION LEARNING & ADAPTATION HOOKS
    // ==========================================

    fun onTrackStarted(track: TrackEntity): SessionAdaptationTrigger? {
        recordRecommendedTrack(track)
        return adaptiveSessionManager.onTrackStarted(track)
    }

    fun onTrackSkipped(track: TrackEntity, listenedDurationMs: Long): SessionAdaptationTrigger? {
        return adaptiveSessionManager.onTrackSkipped(track, listenedDurationMs)
    }

    fun onTrackLiked(track: TrackEntity): SessionAdaptationTrigger {
        return adaptiveSessionManager.onTrackLiked(track)
    }

    fun onTrackReplayed(track: TrackEntity): SessionAdaptationTrigger? {
        return adaptiveSessionManager.onTrackReplayed(track)
    }

    fun onTrackCompleted(track: TrackEntity) {
        adaptiveSessionManager.onTrackCompleted(track)
    }

    fun getSessionProfile(): ShortTermSessionProfile {
        return adaptiveSessionManager.getSessionProfile()
    }

    // ==========================================
    // USER AFFINITIES WITH EXPONENTIAL TIME DECAY
    // ==========================================

    suspend fun analyzeUserAffinities(): UserAffinities = withContext(Dispatchers.IO) {
        val profile = userTasteProfiler.computeLongTermProfile()

        val recentTracks = recentPlaybackDao.getRecentTracksSync(60)
        val likedPlaylist = playlistDao.getPlaylistByNameSync("Liked Songs")
        val likedTracks = likedPlaylist?.let {
            playlistDao.getPlaylistWithTracksSync(it.playlistId)?.tracks
        } ?: emptyList()
        val downloadedTracks = trackDao.getDownloadedTracksSync()

        val artistThumbnails = mutableMapOf<String, String>()
        (recentTracks + likedTracks + downloadedTracks).forEach { track ->
            track.getEffectiveArtists().forEach { artist ->
                if (!artistThumbnails.containsKey(artist) && track.thumbnailUrl.isNotBlank()) {
                    artistThumbnails[artist] = track.thumbnailUrl
                }
            }
        }

        val topArtistAffinities = profile.topArtists.take(8).map { artist ->
            ArtistAffinity(name = artist, imageUrl = artistThumbnails[artist])
        }

        val likedArtists = likedTracks.flatMap { it.getEffectiveArtists() }.distinct()
        val recentArtists = recentTracks.flatMap { it.getEffectiveArtists() }.distinct()
        val downloadedArtists = downloadedTracks.flatMap { it.getEffectiveArtists() }.distinct()

        UserAffinities(
            topLikedArtists = likedArtists,
            topRecentArtists = recentArtists,
            topDownloadedArtists = downloadedArtists,
            overallTopArtists = profile.topArtists,
            topArtists = topArtistAffinities,
            topGenres = profile.topGenres,
            heavyRotationTracks = profile.heavyRotationTracks,
            vaultTracks = profile.vaultTracks,
            primaryArtist = profile.primaryArtist,
            secondaryArtist = profile.secondaryArtist,
            primaryGenre = profile.primaryGenre
        )
    }

    // ==========================================
    // NEXT-GEN ALGORITHMIC QUEUE GENERATION
    // ==========================================

    /**
     * Generates a tailored, human-curated algorithmic queue for a base track.
     * Enforces all constraints:
     * - Generates >= 300 candidates before ranking across 8 parallel streams.
     * - Ranks candidates using the multi-objective probability formula.
     * - Enforces 80% familiar / 20% discovery ratio with 1-in-5 discovery insertion.
     * - Max 2 consecutive tracks by same artist, max 3 consecutive tracks by same genre.
     * - Complete fuzzy deduplication and anti-looping protection.
     */
    suspend fun generateRecommendationQueue(
        baseTrack: TrackEntity,
        limit: Int = 15
    ): List<TrackEntity> = withContext(Dispatchers.IO) {
        adaptiveSessionManager.onTrackStarted(baseTrack)
        recordRecommendedTrack(baseTrack)

        val sessionProfile = adaptiveSessionManager.getSessionProfile()
        val longTermProfile = userTasteProfiler.computeLongTermProfile()
        val recentHistory = try {
            recentPlaybackDao.getRecentTracksSync(50)
        } catch (_: Exception) {
            emptyList()
        }

        // 1. Parallel Candidate Harvesting (>= 300 Candidates Target)
        val rawCandidates = candidateRetrievalEngine.retrieveCandidates(
            seedTrack = baseTrack,
            sessionProfile = sessionProfile,
            longTermProfile = longTermProfile
        )

        // 2. Candidate Hygiene Filtering
        val skippedTrackIds = try {
            playbackTelemetryDao.getFrequentlySkippedTrackIds().toSet()
        } catch (_: Exception) {
            emptySet()
        }

        var hygieneCandidates = rawCandidates.filter { candidate ->
            candidate.id != baseTrack.id &&
            !candidate.isSameSong(baseTrack) &&
            candidate.isSongOnly() &&
            candidate.id !in skippedTrackIds &&
            !isExcludedFromSession(candidate) &&
            recentHistory.none { it.isSameSong(candidate) }
        }.distinctSongs()

        // Resilient fallback if hygiene filter was overly aggressive
        if (hygieneCandidates.size < 12) {
            val relaxedRecent = recentHistory.take(5)
            hygieneCandidates = rawCandidates.filter { candidate ->
                candidate.id != baseTrack.id &&
                !candidate.isSameSong(baseTrack) &&
                candidate.isSongOnly() &&
                candidate.id !in skippedTrackIds &&
                relaxedRecent.none { it.isSameSong(candidate) }
            }.distinctSongs()
        }

        // 3. Multi-Objective Probability Ranking
        val scoredList = rankingScoringEngine.scoreAndRankCandidates(
            candidates = hygieneCandidates,
            sessionProfile = sessionProfile,
            longTermProfile = longTermProfile,
            recentHistory = recentHistory
        )

        // 4. Human-Curated Queue Sequencing (80/20 Pacing + Diversity Constraints)
        val sequencedQueue = queueSequencer.sequenceQueue(
            scoredCandidates = scoredList,
            seedTrack = baseTrack,
            limit = limit
        )

        sequencedQueue.forEach { recordRecommendedTrack(it) }
        sequencedQueue
    }

    /**
     * Continuous Smart Autopilot Generator.
     */
    suspend fun generateAutopilotQueue(limit: Int = 35): List<TrackEntity> = withContext(Dispatchers.IO) {
        val longTermProfile = userTasteProfiler.computeLongTermProfile()
        val sessionProfile = adaptiveSessionManager.getSessionProfile()
        val primaryArtist = sessionProfile.sessionTracks.lastOrNull()?.cleanArtistName()
            ?: longTermProfile.primaryArtist

        val anchorTrack = sessionProfile.sessionTracks.lastOrNull()
            ?: longTermProfile.heavyRotationTracks.firstOrNull()
            ?: longTermProfile.vaultTracks.firstOrNull()
            ?: TrackEntity(
                id = "anchor_seed",
                title = "$primaryArtist Signature Anthem",
                artist = primaryArtist,
                durationSeconds = 240,
                thumbnailUrl = ""
            )

        generateRecommendationQueue(anchorTrack, limit)
    }

    fun detectAcousticVibe(title: String, artist: String): AcousticVibeInfo {
        val dummy = TrackEntity(id = "", title = title, artist = artist, durationSeconds = 200, thumbnailUrl = "")
        val vibe = vibeClassifier.classifyTrackVibe(dummy)
        return AcousticVibeInfo(vibe.displayName, vibeClassifier.getVibeSearchQuery(vibe))
    }

    fun clearHomeCache() {
        homeFeedCacheManager.clearCache()
    }

    fun notifyPlaybackRecorded() {
        homeFeedCacheManager.notifyPlaybackRecorded()
    }

    fun isHomeCacheFresh(selectedMood: String): Boolean = homeFeedCacheManager.isCacheFresh(selectedMood)

    suspend fun getCachedRecommendations(selectedMood: String): HomeRecommendationCategories? = withContext(Dispatchers.IO) {
        val cached = homeFeedCacheManager.getCachedFeed(selectedMood) ?: return@withContext null
        try {
            val (timeTitle, timeSubtitle, _) = computeTimeOfDayInfo(cached.autopilotTargetGenre.lowercase())
            val likedPlaylist = playlistDao.getPlaylistByNameSync("Liked Songs")
            val likedTracks = likedPlaylist?.let {
                playlistDao.getPlaylistWithTracksSync(it.playlistId)?.tracks
            } ?: cached.likedTracks

            val jumpBackIn = recentPlaybackDao.getRecentTracksSync(15)
                .distinctBy { it.id }
                .filter { it.isSongOnly() }
                .ifEmpty { cached.jumpBackInTracks }

            cached.copy(
                timeOfDayTitle = timeTitle,
                timeOfDaySubtitle = timeSubtitle,
                likedTracks = likedTracks,
                jumpBackInTracks = jumpBackIn
            )
        } catch (_: Exception) {
            cached
        }
    }

    private fun getCuratedFallbackTracks(query: String, count: Int = 10): List<TrackEntity> {
        val q = query.lowercase(Locale.ROOT).trim()
        val matches = MASTER_CURATED_TRACKS.filter { seed ->
            val a = seed.artist.lowercase(Locale.ROOT)
            val g = seed.genre.lowercase(Locale.ROOT)
            val t = seed.title.lowercase(Locale.ROOT)
            q.contains(a) || q.contains(g) || a.contains(q) || t.contains(q)
        }.map { it.toTrackEntity() }

        return (matches.shuffled() + MASTER_CURATED_TRACKS.shuffled().map { it.toTrackEntity() })
            .distinctSongs()
            .take(count)
    }

    private suspend fun fetchRecommendationFeedTracks(rawQuery: String, fallbackKey: String): List<TrackEntity> {
        val fromSearch = try {
            withTimeoutOrNull(5500L) {
                streamRepository.searchTracks(rawQuery).firstOrNull()?.getOrNull()
            }
        } catch (_: Exception) { null }

        if (!fromSearch.isNullOrEmpty()) {
            return fromSearch
        }

        val cleanQuery = rawQuery
            .replace("songs official audio", "")
            .replace("official audio", "")
            .replace("official video", "")
            .trim()
            .ifBlank { rawQuery }

        val fromSaavn = try {
            withTimeoutOrNull(3500L) {
                saavnMediaEngine.searchTracks(cleanQuery)
            }
        } catch (_: Exception) { null }

        if (!fromSaavn.isNullOrEmpty()) {
            trackDao.insertTracks(fromSaavn)
            return fromSaavn
        }

        return getCuratedFallbackTracks(fallbackKey, 10)
    }

    suspend fun loadHomeRecommendations(selectedMood: String): HomeRecommendationCategories = withContext(Dispatchers.IO) {
        val affinities = analyzeUserAffinities()
        val (timeTitle, timeSubtitle, _) = computeTimeOfDayInfo(affinities.topGenres.firstOrNull())

        val allUserArtists = affinities.overallTopArtists.ifEmpty { affinities.topLikedArtists }
        val fallbackFlagshipArtists = listOf(
            "Arijit Singh", "The Weeknd", "Taylor Swift", "Coldplay",
            "Diljit Dosanjh", "Drake", "Ed Sheeran", "Sidhu Moose Wala", "Anuv Jain", "Linkin Park",
            "Karan Aujla", "Post Malone", "Pritam", "Dua Lipa", "AP Dhillon", "Bruno Mars"
        )
        val candidateArtists = (allUserArtists.take(6) + fallbackFlagshipArtists).distinct()

        val primaryArtist = candidateArtists.shuffled().firstOrNull() ?: "Arijit Singh"
        val secondaryArtist = candidateArtists.filter { !it.equals(primaryArtist, ignoreCase = true) }.shuffled().firstOrNull() ?: "The Weeknd"

        val candidateGenres = (affinities.topGenres.take(4) + listOf("bollywood", "pop", "acoustic", "rock", "punjabi", "indie", "synthwave")).distinct()
        val categoryGenre = candidateGenres.shuffled().firstOrNull() ?: "pop"
        val discoveryGenre = candidateGenres.filter { !it.equals(categoryGenre, ignoreCase = true) }.shuffled().firstOrNull() ?: "chill acoustic"

        val becauseLikedQuery = "$primaryArtist songs official audio"
        val similarArtistQuery = "$secondaryArtist songs official audio"
        val discoveryQuery = "$discoveryGenre songs official audio"
        val categoryQuery = "$categoryGenre songs official audio"
        val moodQuery = getQueryForMood(selectedMood)

        val becauseLikedDeferred = async(Dispatchers.IO) { fetchRecommendationFeedTracks(becauseLikedQuery, primaryArtist) }
        val similarArtistDeferred = async(Dispatchers.IO) { fetchRecommendationFeedTracks(similarArtistQuery, secondaryArtist) }
        val discoveryDeferred = async(Dispatchers.IO) { fetchRecommendationFeedTracks(discoveryQuery, discoveryGenre) }
        val categoryDeferred = async(Dispatchers.IO) { fetchRecommendationFeedTracks(categoryQuery, categoryGenre) }
        val moodDeferred = async(Dispatchers.IO) { fetchRecommendationFeedTracks(moodQuery, selectedMood) }
        val trendingDeferred = async(Dispatchers.IO) {
            val trendingQueries = listOf("Top Global Hits Music", "Billboard Hot 100 Official", "Viral Hits Songs", "Top Trending Songs official audio")
            fetchRecommendationFeedTracks(trendingQueries.random(), "bollywood")
        }

        val becauseLikedTracks = becauseLikedDeferred.await().filter { it.isSongOnly() }.distinctSongs()
        val similarArtistTracks = similarArtistDeferred.await().filter { it.isSongOnly() }.distinctSongs()
        val discoveryTracks = discoveryDeferred.await().filter { it.isSongOnly() }.distinctSongs()
        val categoryTracks = categoryDeferred.await().filter { it.isSongOnly() }.distinctSongs()
        val moodTracks = moodDeferred.await().filter { it.isSongOnly() }.distinctSongs()
        val trendingTracks = trendingDeferred.await().filter { it.isSongOnly() }.distinctSongs()

        val quickPicks = (affinities.heavyRotationTracks.shuffled() + affinities.vaultTracks.shuffled() + trendingTracks.shuffled() + MASTER_CURATED_TRACKS.shuffled().map { it.toTrackEntity() })
            .filter { it.isSongOnly() }
            .distinctSongs()
            .take(6)

        val dailyMix1Tracks = (becauseLikedTracks.shuffled().take(8) + affinities.heavyRotationTracks.shuffled().filter { it.isSongOnly() }.take(4))
            .distinctSongs()
            .ifEmpty { trendingTracks.shuffled().take(10) }

        val dailyMix2Tracks = (similarArtistTracks.shuffled().take(8) + discoveryTracks.shuffled().take(4))
            .distinctSongs()
            .ifEmpty { trendingTracks.drop(4).shuffled().take(10) }

        val dailyMix3Tracks = (discoveryTracks.shuffled().take(8) + moodTracks.shuffled().take(4))
            .distinctSongs()
            .ifEmpty { trendingTracks.shuffled().take(10) }

        val dailyMix4Tracks = (categoryTracks.shuffled().take(8) + trendingTracks.shuffled().take(4))
            .distinctSongs()
            .ifEmpty { trendingTracks.shuffled().take(10) }

        val dailyMix5Tracks = (moodTracks.shuffled().take(8) + categoryTracks.shuffled().take(4))
            .distinctSongs()
            .ifEmpty { trendingTracks.shuffled().take(10) }

        val likedPlaylist = playlistDao.getPlaylistByNameSync("Liked Songs")
        val likedTracks = likedPlaylist?.let {
            playlistDao.getPlaylistWithTracksSync(it.playlistId)?.tracks
        } ?: emptyList()

        val jumpBackIn = recentPlaybackDao.getRecentTracksSync(15)
            .filter { it.isSongOnly() }
            .distinctSongs()

        val dailyMix1Artists = dailyMix1Tracks.map { it.artist }.distinct().take(3).joinToString(", ")
        val dailyMix2Artists = dailyMix2Tracks.map { it.artist }.distinct().take(3).joinToString(", ")
        val dailyMix3Artists = dailyMix3Tracks.map { it.artist }.distinct().take(3).joinToString(", ")
        val dailyMix4Artists = dailyMix4Tracks.map { it.artist }.distinct().take(3).joinToString(", ")
        val dailyMix5Artists = dailyMix5Tracks.map { it.artist }.distinct().take(3).joinToString(", ")

        val dailyMixes = listOf(
            DailyMix(
                id = "mix_1",
                title = "Daily Mix 1",
                subtitle = if (dailyMix1Artists.isNotBlank()) "$dailyMix1Artists and more" else "$primaryArtist, Pritam & favorites",
                tracks = dailyMix1Tracks,
                gradientColors = listOf(0xFFFF416CL, 0xFFFF4B2BL)
            ),
            DailyMix(
                id = "mix_2",
                title = "Daily Mix 2",
                subtitle = if (dailyMix2Artists.isNotBlank()) "$dailyMix2Artists and more" else "$secondaryArtist, Discoveries & more",
                tracks = dailyMix2Tracks,
                gradientColors = listOf(0xFF00C6FFL, 0xFF0072FFL)
            ),
            DailyMix(
                id = "mix_3",
                title = "Chill Mix",
                subtitle = if (dailyMix3Artists.isNotBlank()) "$dailyMix3Artists and more" else "Lo-Fi, Acoustic & Downtempo",
                tracks = dailyMix3Tracks,
                gradientColors = listOf(0xFF8A2387L, 0xFFE94057L)
            ),
            DailyMix(
                id = "mix_4",
                title = "Energy Mix",
                subtitle = if (dailyMix4Artists.isNotBlank()) "$dailyMix4Artists and more" else "Gym, Phonk & High Tempo",
                tracks = dailyMix4Tracks,
                gradientColors = listOf(0xFF11998EL, 0xFF38EF7DL)
            ),
            DailyMix(
                id = "mix_5",
                title = "Focus Flow",
                subtitle = if (dailyMix5Artists.isNotBlank()) "$dailyMix5Artists and more" else "Ambient, Synth & Night Waves",
                tracks = dailyMix5Tracks,
                gradientColors = listOf(0xFFFF8008L, 0xFFFFC837L)
            )
        )

        val hasUserAffinities = affinities.overallTopArtists.isNotEmpty() || affinities.topLikedArtists.isNotEmpty()

        val result = HomeRecommendationCategories(
            becauseYouLikedTitle = if (hasUserAffinities) "BECAUSE YOU LISTEN TO ${primaryArtist.uppercase()}" else "TOP PICKS • ${primaryArtist.uppercase()}",
            becauseYouLikedSubtitle = "Handpicked tracks & related soundscapes",
            becauseYouLikedTracks = becauseLikedTracks,
            similarArtistTitle = "SIMILAR TO ${secondaryArtist.uppercase()}",
            similarArtistSubtitle = "Popular audio inspired by ${secondaryArtist}",
            similarArtistTracks = similarArtistTracks,
            heavyRotationTracks = affinities.heavyRotationTracks,
            discoveryRadarTracks = discoveryTracks,
            categoryRadarTitle = "CATEGORY RADAR • ${categoryGenre.uppercase()}",
            categoryRadarTracks = categoryTracks,
            vaultFavoritesTracks = affinities.vaultTracks,
            moodTracks = moodTracks,
            trendingTracks = trendingTracks,
            quickPicks = quickPicks,
            dailyMixes = dailyMixes,
            topArtists = affinities.topArtists,
            likedTracks = likedTracks,
            jumpBackInTracks = jumpBackIn,
            timeOfDayTitle = timeTitle,
            timeOfDaySubtitle = timeSubtitle,
            autopilotTargetSinger = primaryArtist.uppercase(),
            autopilotTargetGenre = categoryGenre.uppercase()
        )
        homeFeedCacheManager.saveFeed(selectedMood, result)
        result
    }

    private fun getQueryForMood(mood: String): String {
        return when (mood.lowercase(Locale.ROOT)) {
            "all" -> "Top Global Hits Viral"
            "chill", "relax" -> "Chill Lo-Fi Beats relaxing acoustic"
            "workout", "energize" -> "Workout Energy EDM Gym pump motivation"
            "focus" -> "Deep Focus Ambient Flow electronic"
            "study" -> "Study Lo-Fi Hip Hop instrumental peaceful"
            "sleep" -> "Deep Sleep Ambient Delta Waves relaxing calming"
            "travel" -> "Road Trip Travel Indie Pop acoustic uplifting"
            "night drive", "drive" -> "Night Drive Phonk Synthwave"
            "party" -> "Club Party Dance chartbusters"
            "synthwave" -> "Synthwave Retrowave Cyberpunk 80s"
            "retro" -> "Retro Classic Master Hits 80s 90s"
            "romance", "romantic" -> "Acoustic Romantic Love Songs Bollywood"
            else -> "Top Acoustic Hits"
        }
    }

    private fun computeTimeOfDayInfo(favGenre: String?): Triple<String, String, String> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val style = favGenre ?: "acoustic"
        return when (hour) {
            in 5..11 -> Triple("Good morning", "MORNING ACOUSTIC MATRIX • ${style.uppercase()}", "Morning uplifting $style")
            in 12..16 -> Triple("Good afternoon", "FOCUS & FLOW MATRIX • OPTIMAL", "Focus deep work $style")
            in 17..21 -> Triple("Good evening", "SUNSET ACOUSTIC MATRIX • SYNCHRONIZED", "Sunset relaxing $style")
            else -> Triple("Late Night Protocol", "NIGHT DRIVE PROTOCOL • ACTIVE", "Late night lofi synthwave")
        }
    }

    suspend fun getArtistTracks(artistName: String): List<TrackEntity> = withContext(Dispatchers.IO) {
        val results = streamRepository.searchTracks("$artistName top hits official audio").firstOrNull()?.getOrNull().orEmpty()
        results.filter { it.isSongOnly() }
    }
}
