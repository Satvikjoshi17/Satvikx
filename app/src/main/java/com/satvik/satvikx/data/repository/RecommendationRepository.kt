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
import com.satvik.satvikx.recommendation.CuratedDiscoveryCatalog
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

    private val prewarmedCandidateCache = java.util.concurrent.ConcurrentHashMap<String, List<TrackEntity>>()

    /**
     * Pre-warms the candidate pool in the background (triggered at 60% playback progress).
     * Retrieves and caches the 300+ candidate tracks so subsequent queue extensions
     * take 0ms instead of waiting for network harvesting.
     */
    suspend fun prewarmCandidates(seedTrack: TrackEntity) = withContext(Dispatchers.IO) {
        try {
            val sessionProfile = adaptiveSessionManager.getSessionProfile()
            val longTermProfile = userTasteProfiler.computeLongTermProfile()
            val pool = candidateRetrievalEngine.retrieveCandidates(seedTrack, sessionProfile, longTermProfile)
            if (pool.isNotEmpty()) {
                prewarmedCandidateCache[seedTrack.id] = pool
                android.util.Log.d("RecommendationRepo", "Pre-warmed ${pool.size} candidates in background for ${seedTrack.title}")
            }
        } catch (e: Exception) {
            if (e !is kotlinx.coroutines.CancellationException) {
                android.util.Log.w("RecommendationRepo", "Non-fatal background candidate pre-warming error: ${e.message}")
            }
        }
    }

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
        limit: Int = 15,
        excludeTrackIds: Set<String> = emptySet(),
        forceFresh: Boolean = false
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

        // 1. Parallel Candidate Harvesting (Check pre-warmed candidate pool cache first for instant retrieval)
        val cachedPool = if (!forceFresh) prewarmedCandidateCache.remove(baseTrack.id) else null
        val rawCandidates = cachedPool ?: candidateRetrievalEngine.retrieveCandidates(
            seedTrack = baseTrack,
            sessionProfile = sessionProfile,
            longTermProfile = longTermProfile,
            forceFresh = forceFresh
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
            candidate.id !in excludeTrackIds &&
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
                candidate.id !in excludeTrackIds &&
                relaxedRecent.none { it.isSameSong(candidate) }
            }.distinctSongs()
        }

        // If still insufficient (e.g. all candidates in current pool were excluded or network was limited), supplement with fresh seeds
        if (hygieneCandidates.size < 12) {
            val supplementalSeeds = CuratedDiscoveryCatalog.ALL_SEEDS
                .shuffled()
                .map { it.toTrackEntity() }
                .filter { candidate ->
                    candidate.id != baseTrack.id &&
                    !candidate.isSameSong(baseTrack) &&
                    candidate.id !in excludeTrackIds &&
                    candidate.id !in skippedTrackIds
                }
            hygieneCandidates = (hygieneCandidates + supplementalSeeds).distinctSongs()
        }

        // 3. Multi-Objective Probability Ranking
        val nativeRelatedIds = candidateRetrievalEngine.getNativeIdsForSeed(baseTrack.id)
            .ifEmpty { candidateRetrievalEngine.lastHarvestedNativeIds }

        val scoredList = rankingScoringEngine.scoreAndRankCandidates(
            candidates = hygieneCandidates,
            sessionProfile = sessionProfile,
            longTermProfile = longTermProfile,
            recentHistory = recentHistory,
            forceFresh = forceFresh,
            nativeRelatedIds = nativeRelatedIds
        )

        // 4. Human-Curated Queue Sequencing (Elastic Discovery Cadence + Diversity + DJ Flow)
        val sequencedQueue = queueSequencer.sequenceQueue(
            scoredCandidates = scoredList,
            seedTrack = baseTrack,
            limit = limit,
            sessionProfile = sessionProfile,
            isNewUser = longTermProfile.isNewUser
        )

        sequencedQueue.forEach { recordRecommendedTrack(it) }
        sequencedQueue
    }

    /**
     * Fast (< 100ms) Immediate Autopilot Seeds:
     * Immediately returns 3-5 tracks the user actually loves from heavy rotation, liked songs,
     * or recent playback so AI DJ starts playing instantly without waiting for network.
     */
    suspend fun getImmediateAutopilotSeeds(count: Int = 5): List<TrackEntity> = withContext(Dispatchers.IO) {
        val likedPlaylist = playlistDao.getPlaylistByNameSync("Liked Songs")
        val liked = likedPlaylist?.let { playlistDao.getPlaylistWithTracksSync(it.playlistId)?.tracks }.orEmpty()
        val heavy = recentPlaybackDao.getHeavyRotationTracksSync(25)
        val recent = recentPlaybackDao.getRecentTracksSync(25)

        val candidateSeeds = (heavy + liked + recent)
            .filter { it.isSongOnly() }
            .distinctSongs()

        if (candidateSeeds.isNotEmpty()) {
            candidateSeeds.shuffled().take(count)
        } else {
            MASTER_CURATED_TRACKS.shuffled().take(count).map { it.toTrackEntity() }
        }
    }

    /**
     * Continuous Smart Autopilot Generator.
     * Strictly anchors to user's true lifetime favorite artist and highest-affinity tracks,
     * completely eliminating unrelated low-quality uploader channels.
     */
    suspend fun generateAutopilotQueue(limit: Int = 35): List<TrackEntity> = withContext(Dispatchers.IO) {
        val longTermProfile = userTasteProfiler.computeLongTermProfile()
        val sessionProfile = adaptiveSessionManager.getSessionProfile()

        // Prioritize verified favorite artist from longTermProfile over ephemeral uploader/random tracks
        val primaryArtist = longTermProfile.primaryArtist.ifBlank {
            longTermProfile.topArtists.firstOrNull() ?: sessionProfile.sessionTracks.lastOrNull()?.cleanArtistName().orEmpty()
        }.ifBlank { "Arijit Singh" }

        val anchorTrack = longTermProfile.heavyRotationTracks.firstOrNull()
            ?: longTermProfile.vaultTracks.firstOrNull()
            ?: sessionProfile.sessionTracks.firstOrNull()
            ?: TrackEntity(
                id = "anchor_seed",
                title = "$primaryArtist Signature Anthem",
                artist = primaryArtist,
                durationSeconds = 240,
                thumbnailUrl = ""
            )

        generateRecommendationQueue(anchorTrack, limit)
    }

    /**
     * Fetches dedicated mood tracks quickly in a single targeted request.
     */
    suspend fun fetchMoodTracks(mood: String): List<TrackEntity> = withContext(Dispatchers.IO) {
        val query = getQueryForMood(mood)
        val raw = fetchRecommendationFeedTracks(query, mood)
        raw.filter { it.isSongOnly() }.distinctSongs()
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

            val jumpBackIn = recentPlaybackDao.getRecentTracksSync(25)
                .distinctBy { it.id }
                .filter { it.id.isNotBlank() && it.title.isNotBlank() }
                .ifEmpty { cached.jumpBackInTracks }

            val userListened = (jumpBackIn + cached.heavyRotationTracks + likedTracks + cached.vaultFavoritesTracks)
                .filter { it.isSongOnly() }
                .distinctSongs()

            val updatedQuickPicks = if (userListened.isNotEmpty()) {
                (userListened + cached.quickPicks).distinctSongs().take(6)
            } else cached.quickPicks

            val listenedIds = userListened.map { it.id }.toSet()
            val updatedMixes = cached.dailyMixes.map { mix ->
                val unlistened = mix.tracks.filter { it.id !in listenedIds && userListened.none { u -> u.isSameSong(it) } }
                if (unlistened.isNotEmpty()) mix.copy(tracks = unlistened) else mix
            }

            cached.copy(
                timeOfDayTitle = timeTitle,
                timeOfDaySubtitle = timeSubtitle,
                likedTracks = likedTracks,
                jumpBackInTracks = jumpBackIn,
                quickPicks = updatedQuickPicks,
                dailyMixes = updatedMixes
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

    private fun isArtistCoherent(track: TrackEntity, targetArtist: String): Boolean {
        val cleanTarget = targetArtist.trim().lowercase(Locale.ROOT)
        if (cleanTarget.isBlank() || cleanTarget.length < 2) return true
        val trackArtist = track.artist.trim().lowercase(Locale.ROOT)
        val trackTitle = track.title.trim().lowercase(Locale.ROOT)

        // 1. Direct or substring match on artist field or title
        if (trackArtist.contains(cleanTarget) || cleanTarget.contains(trackArtist)) return true
        if (trackTitle.contains(cleanTarget)) return true

        // 2. Check effective artists list
        val effective = track.getEffectiveArtists().map { it.lowercase(Locale.ROOT) }
        if (effective.any { it.contains(cleanTarget) || cleanTarget.contains(it) }) return true

        // 3. Check verified collaborators
        val collaborators = CandidateRetrievalEngine.COLLABORATOR_MAP[cleanTarget]?.map { it.lowercase(Locale.ROOT) } ?: emptyList()
        if (collaborators.any { collab -> trackArtist.contains(collab) || trackTitle.contains(collab) || effective.any { it.contains(collab) } }) {
            return true
        }

        // 4. Guard against first-name token collisions (e.g. "Nisha Chaudhary" when target is "Nisha Zafar")
        val targetTokens = cleanTarget.split(" ").filter { it.length >= 2 }
        if (targetTokens.size >= 2) {
            val lastName = targetTokens.last()
            if (!trackArtist.contains(lastName) && !trackTitle.contains(lastName) && !effective.any { it.contains(lastName) }) {
                return false
            }
        }

        return false
    }

    private suspend fun fetchRecommendationFeedTracks(
        rawQuery: String,
        fallbackKey: String,
        targetArtistFilter: String? = null
    ): List<TrackEntity> {
        val fromSearch = try {
            withTimeoutOrNull(5500L) {
                streamRepository.searchTracks(rawQuery).firstOrNull()?.getOrNull()
            }
        } catch (_: Exception) { null }

        val filteredSearch = if (targetArtistFilter != null && !fromSearch.isNullOrEmpty()) {
            fromSearch.filter { isArtistCoherent(it, targetArtistFilter) }
        } else {
            fromSearch.orEmpty()
        }

        if (filteredSearch.size >= 3) {
            return filteredSearch
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

        val filteredSaavn = if (targetArtistFilter != null && !fromSaavn.isNullOrEmpty()) {
            fromSaavn.filter { isArtistCoherent(it, targetArtistFilter) }
        } else {
            fromSaavn.orEmpty()
        }

        if (filteredSaavn.isNotEmpty()) {
            trackDao.insertTracks(filteredSaavn)
            return filteredSaavn
        }

        if (filteredSearch.isNotEmpty()) {
            return filteredSearch
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

        // 1. Strictly prioritize user's actual favorite artist over hardcoded fallbacks
        val primaryArtist = if (allUserArtists.isNotEmpty()) {
            affinities.primaryArtist ?: allUserArtists[0]
        } else {
            fallbackFlagshipArtists.shuffled().firstOrNull() ?: "Arijit Singh"
        }

        val secondaryArtist = if (allUserArtists.size > 1) {
            affinities.secondaryArtist ?: allUserArtists[1]
        } else if (allUserArtists.isNotEmpty()) {
            val collaborators = CandidateRetrievalEngine.COLLABORATOR_MAP[primaryArtist.lowercase(Locale.ROOT)]
            collaborators?.shuffled()?.firstOrNull()
                ?: fallbackFlagshipArtists.firstOrNull { !it.equals(primaryArtist, ignoreCase = true) }
                ?: "The Weeknd"
        } else {
            fallbackFlagshipArtists.filter { !it.equals(primaryArtist, ignoreCase = true) }.shuffled().firstOrNull() ?: "The Weeknd"
        }

        val categoryGenre = affinities.primaryGenre
            ?: affinities.topGenres.firstOrNull()
            ?: "bollywood"
        val discoveryGenre = affinities.topGenres.getOrNull(1)
            ?: if (categoryGenre.equals("bollywood", ignoreCase = true)) "punjabi" else "acoustic"

        val becauseLikedQuery = "$primaryArtist songs official audio"
        val similarArtistQuery = "$secondaryArtist songs official audio"
        val discoveryQuery = "$discoveryGenre songs official audio"
        val categoryQuery = "$categoryGenre songs official audio"
        val moodQuery = getQueryForMood(selectedMood)

        val isIndian = CandidateRetrievalEngine.isIndianContext(TrackEntity(id = "", title = "", artist = primaryArtist, durationSeconds = 0, thumbnailUrl = ""))

        val becauseLikedDeferred = async(Dispatchers.IO) { fetchRecommendationFeedTracks(becauseLikedQuery, primaryArtist, targetArtistFilter = primaryArtist) }
        val similarArtistDeferred = async(Dispatchers.IO) { fetchRecommendationFeedTracks(similarArtistQuery, secondaryArtist, targetArtistFilter = secondaryArtist) }
        val discoveryDeferred = async(Dispatchers.IO) { fetchRecommendationFeedTracks(discoveryQuery, discoveryGenre) }
        val categoryDeferred = async(Dispatchers.IO) { fetchRecommendationFeedTracks(categoryQuery, categoryGenre) }
        val moodDeferred = async(Dispatchers.IO) { fetchRecommendationFeedTracks(moodQuery, selectedMood) }
        val trendingDeferred = async(Dispatchers.IO) {
            val trendingQueries = if (isIndian) {
                listOf(
                    "Top Hindi Hits Songs 2026",
                    "Trending Bollywood Songs",
                    "Top Punjabi Songs Chartbusters",
                    "Trending Indian Audio Hits"
                )
            } else {
                listOf(
                    "Top Global Hits Music",
                    "Billboard Hot 100 Official Audio",
                    "Trending Hit Songs 2026"
                )
            }
            fetchRecommendationFeedTracks(trendingQueries.random(), primaryArtist)
        }

        val becauseLikedTracks = becauseLikedDeferred.await().filter { it.isSongOnly() }.distinctSongs()
        val similarArtistTracks = similarArtistDeferred.await().filter { it.isSongOnly() }.distinctSongs()
        val discoveryTracks = discoveryDeferred.await().filter { it.isSongOnly() }.distinctSongs()
        val categoryTracks = categoryDeferred.await().filter { it.isSongOnly() }.distinctSongs()
        val moodTracks = moodDeferred.await().filter { it.isSongOnly() }.distinctSongs()
        val trendingTracks = trendingDeferred.await().filter { it.isSongOnly() }.distinctSongs()

        val jumpBackIn = recentPlaybackDao.getRecentTracksSync(25)
            .filter { it.id.isNotBlank() && it.title.isNotBlank() }
            .distinctBy { it.id }

        val likedPlaylist = playlistDao.getPlaylistByNameSync("Liked Songs")
        val likedTracks = likedPlaylist?.let {
            playlistDao.getPlaylistWithTracksSync(it.playlistId)?.tracks
        } ?: emptyList()

        // Assemble Quick Picks strictly prioritizing the songs the user ACTUALLY listens to:
        val userListenedPool = (jumpBackIn + affinities.heavyRotationTracks + likedTracks + affinities.vaultTracks)
            .filter { it.isSongOnly() }
            .distinctSongs()

        val quickPicks = (userListenedPool + becauseLikedTracks + trendingTracks)
            .distinctSongs()
            .take(6)

        // Set of songs the user has already listened to or saved in library:
        val listenedTrackIds = userListenedPool.map { it.id }.toSet()
        val isUnlistened: (TrackEntity) -> Boolean = { track ->
            track.id !in listenedTrackIds && userListenedPool.none { it.isSameSong(track) }
        }

        // Pools of FRESH, unlistened recommendations:
        val unlistenedBecauseLiked = becauseLikedTracks.filter(isUnlistened)
        val unlistenedSimilar = similarArtistTracks.filter(isUnlistened)
        val unlistenedDiscovery = discoveryTracks.filter(isUnlistened)
        val unlistenedCategory = categoryTracks.filter(isUnlistened)
        val unlistenedMood = moodTracks.filter(isUnlistened)
        val unlistenedTrending = trendingTracks.filter(isUnlistened)

        // User listened songs ordered strictly by popularity (play count & repeat factor):
        val heavyRotation = recentPlaybackDao.getHeavyRotationTracksSync(35)
            .filter { it.isSongOnly() }
            .distinctSongs()
        val popularListenedTracks = (heavyRotation + likedTracks + jumpBackIn)
            .filter { it.isSongOnly() }
            .distinctSongs()

        // Daily Mix 1: Unlistened fresh recommendations matching user's top artist
        val dailyMix1Tracks = (unlistenedBecauseLiked + unlistenedSimilar + unlistenedDiscovery + unlistenedTrending)
            .distinctSongs()
            .ifEmpty { (becauseLikedTracks + discoveryTracks + trendingTracks).distinctSongs() }
            .take(20)

        // Daily Mix 2: Unlistened fresh discoveries from related artists & genre
        val dailyMix2Tracks = (unlistenedSimilar + unlistenedDiscovery + unlistenedCategory + unlistenedTrending)
            .distinctSongs()
            .ifEmpty { (similarArtistTracks + discoveryTracks + trendingTracks).distinctSongs() }
            .take(20)

        // Daily Mix 3: High-Popularity Listened Tracks (Most Played & Heavy Rotation)
        val dailyMix3Tracks = popularListenedTracks.take(20)
            .ifEmpty { (jumpBackIn + trendingTracks).distinctSongs().take(20) }

        // Daily Mix 4: Listened Popularity & Repeat Vault Favorites
        val dailyMix4Tracks = popularListenedTracks.drop(5).take(20)
            .ifEmpty { (likedTracks + jumpBackIn).distinctSongs().take(20) }

        // Daily Mix 5: Balanced Mix of Popular Listened + Chill Discovery
        val dailyMix5Tracks = (popularListenedTracks.shuffled().take(8) + unlistenedMood + unlistenedCategory)
            .distinctSongs()
            .ifEmpty { (trendingTracks + categoryTracks + moodTracks).distinctSongs() }
            .take(20)

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
