package com.satvik.satvikx.data.repository

import com.satvik.satvikx.data.local.dao.PlaylistDao
import com.satvik.satvikx.data.local.dao.RecentPlaybackDao
import com.satvik.satvikx.data.local.dao.TrackDao
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.entity.isSongOnly
import com.satvik.satvikx.data.remote.newpipe.NewPipeYouTubeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class UserAffinities(
    val topLikedArtists: List<String> = emptyList(),
    val topRecentArtists: List<String> = emptyList(),
    val topDownloadedArtists: List<String> = emptyList(),
    val overallTopArtists: List<String> = emptyList(),
    val topGenres: List<String> = emptyList(),
    val heavyRotationTracks: List<TrackEntity> = emptyList(),
    val vaultTracks: List<TrackEntity> = emptyList(),
    val primaryArtist: String? = null,
    val primaryGenre: String? = null
)

data class HomeRecommendationCategories(
    val becauseYouLikedTitle: String = "BECAUSE YOU LIKED",
    val becauseYouLikedTracks: List<TrackEntity> = emptyList(),
    val heavyRotationTracks: List<TrackEntity> = emptyList(),
    val discoveryRadarTracks: List<TrackEntity> = emptyList(),
    val vaultFavoritesTracks: List<TrackEntity> = emptyList(),
    val moodTracks: List<TrackEntity> = emptyList(),
    val trendingTracks: List<TrackEntity> = emptyList(),
    val quickPicks: List<TrackEntity> = emptyList(),
    val categoryRadarTitle: String = "CATEGORY RADAR",
    val categoryRadarTracks: List<TrackEntity> = emptyList(),
    val timeOfDayTitle: String = "TIME-SHIFTED PROTOCOL",
    val timeOfDaySubtitle: String = "ACOUSTIC FOCUS MATRIX",
    val autopilotTargetSinger: String = "GLOBAL ICONS",
    val autopilotTargetGenre: String = "ALL CATEGORIES"
)

@Singleton
class RecommendationRepository @Inject constructor(
    private val recentPlaybackDao: RecentPlaybackDao,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val streamRepository: StreamRepository,
    private val newPipeYouTubeEngine: NewPipeYouTubeEngine
) {

    private val genreKeywords = listOf(
        "lofi", "chill", "acoustic", "rock", "pop", "hip hop", "rap",
        "edm", "synthwave", "ambient", "romantic", "bollywood",
        "punjabi", "classical", "jazz", "metal", "indie", "workout",
        "retro", "trap", "r&b", "soul", "electronic", "dance",
        "ghazal", "sufi", "devotional", "bhajan", "instrumental"
    )

    /**
     * Mines Liked Songs, Downloaded Tracks, and Recent History to produce a rich affinity profile.
     */
    suspend fun analyzeUserAffinities(): UserAffinities = coroutineScope {
        val likedPlaylist = playlistDao.getPlaylistByNameSync("Liked Songs")
        val likedTracks = likedPlaylist?.let {
            playlistDao.getPlaylistWithTracks(it.playlistId).firstOrNull()?.tracks
        } ?: emptyList()

        val downloadedTracks = trackDao.getDownloadedTracks().firstOrNull() ?: emptyList()
        val recentTracks = recentPlaybackDao.getRecentTracks(60).firstOrNull() ?: emptyList()

        // Weight table: Liked = 5.0, Downloaded = 3.5, Recent = 2.0
        val artistScores = mutableMapOf<String, Float>()
        val likedArtists = mutableListOf<String>()
        val downloadedArtists = mutableListOf<String>()
        val recentArtists = mutableListOf<String>()
        val genreScores = mutableMapOf<String, Int>()

        // 1. Liked Tracks Scoring
        likedTracks.forEach { track ->
            extractArtists(track.artist).forEach { artist ->
                artistScores[artist] = (artistScores[artist] ?: 0f) + 5.0f
                if (!likedArtists.contains(artist)) likedArtists.add(artist)
            }
            detectGenres(track.title + " " + track.artist).forEach { g ->
                genreScores[g] = (genreScores[g] ?: 0) + 3
            }
        }

        // 2. Downloaded Tracks Scoring
        downloadedTracks.forEach { track ->
            extractArtists(track.artist).forEach { artist ->
                artistScores[artist] = (artistScores[artist] ?: 0f) + 3.5f
                if (!downloadedArtists.contains(artist)) downloadedArtists.add(artist)
            }
            detectGenres(track.title + " " + track.artist).forEach { g ->
                genreScores[g] = (genreScores[g] ?: 0) + 2
            }
        }

        // 3. Recent Tracks Frequency Scoring
        recentTracks.forEachIndexed { index, track ->
            val recencyMultiplier = if (index < 15) 2.5f else 1.2f
            extractArtists(track.artist).forEach { artist ->
                artistScores[artist] = (artistScores[artist] ?: 0f) + recencyMultiplier
                if (!recentArtists.contains(artist)) recentArtists.add(artist)
            }
            detectGenres(track.title + " " + track.artist).forEach { g ->
                genreScores[g] = (genreScores[g] ?: 0) + 1
            }
        }

        // 4. Heavy Rotation Tracks (tracks played multiple times or top in history)
        val trackFrequency = recentTracks.groupingBy { it.id }.eachCount()
        val heavyRotation = recentTracks
            .distinctBy { it.id }
            .sortedByDescending { trackFrequency[it.id] ?: 1 }
            .take(15)

        val rankedArtists = artistScores.entries
            .filter { it.key.isNotBlank() && it.key.lowercase() != "unknown artist" }
            .sortedByDescending { it.value }
            .map { it.key }

        val rankedGenres = genreScores.entries
            .sortedByDescending { it.value }
            .map { it.key }

        val vaultCombined = (likedTracks + downloadedTracks).distinctBy { it.id }

        val primaryArtist = rankedArtists.firstOrNull() ?: likedArtists.firstOrNull()
        val primaryGenre = rankedGenres.firstOrNull()

        UserAffinities(
            topLikedArtists = likedArtists,
            topRecentArtists = recentArtists,
            topDownloadedArtists = downloadedArtists,
            overallTopArtists = rankedArtists,
            topGenres = rankedGenres,
            heavyRotationTracks = heavyRotation,
            vaultTracks = vaultCombined,
            primaryArtist = primaryArtist,
            primaryGenre = primaryGenre
        )
    }

    /**
     * Builds categorized recommendation feeds for the Home Screen using the affinity matrix.
     */
    suspend fun loadHomeRecommendations(selectedMood: String): HomeRecommendationCategories = coroutineScope {
        val affinities = analyzeUserAffinities()
        val (timeTitle, timeSubtitle, defaultMoodQuery) = computeTimeOfDayInfo(affinities.topGenres.firstOrNull())

        val topLiked = affinities.topLikedArtists.firstOrNull() ?: affinities.overallTopArtists.firstOrNull()
        val becauseLikedQuery = if (!topLiked.isNullOrBlank()) {
            "$topLiked best songs hits"
        } else {
            "Top Global Hits Master"
        }

        val discoveryGenre = affinities.topGenres.getOrNull(1) ?: affinities.topGenres.firstOrNull() ?: "chill acoustic"
        val discoveryQuery = "$discoveryGenre mix radio essentials"

        val primaryGenre = affinities.primaryGenre ?: "synthwave"
        val categoryQuery = "$primaryGenre top hits mix radio"
        val moodQuery = getQueryForMood(selectedMood)

        // Query recommendation feeds in parallel on IO dispatcher
        val becauseLikedDeferred = async(Dispatchers.IO) {
            streamRepository.searchTracks(becauseLikedQuery).firstOrNull()?.getOrNull().orEmpty()
        }
        val discoveryDeferred = async(Dispatchers.IO) {
            streamRepository.searchTracks(discoveryQuery).firstOrNull()?.getOrNull().orEmpty()
        }
        val categoryDeferred = async(Dispatchers.IO) {
            streamRepository.searchTracks(categoryQuery).firstOrNull()?.getOrNull().orEmpty()
        }
        val moodDeferred = async(Dispatchers.IO) {
            streamRepository.searchTracks(moodQuery).firstOrNull()?.getOrNull().orEmpty()
        }
        val trendingDeferred = async(Dispatchers.IO) {
            streamRepository.searchTracks("Trending Global Hits").firstOrNull()?.getOrNull().orEmpty()
        }

        val becauseLikedTracks = becauseLikedDeferred.await().filter { it.isSongOnly() }
        val discoveryTracks = discoveryDeferred.await().filter { it.isSongOnly() }
        val categoryTracks = categoryDeferred.await().filter { it.isSongOnly() }
        val moodTracks = moodDeferred.await().filter { it.isSongOnly() }
        val trendingTracks = trendingDeferred.await().filter { it.isSongOnly() }

        // Quick Picks: combine recent/heavy rotation or trending
        val quickPicks = if (affinities.heavyRotationTracks.isNotEmpty()) {
            affinities.heavyRotationTracks.take(6)
        } else if (affinities.vaultTracks.isNotEmpty()) {
            affinities.vaultTracks.take(6)
        } else {
            trendingTracks.take(6)
        }

        HomeRecommendationCategories(
            becauseYouLikedTitle = if (!topLiked.isNullOrBlank()) "BECAUSE YOU LIKED ${topLiked.uppercase()}" else "RECOMMENDED FOR YOU",
            becauseYouLikedTracks = becauseLikedTracks,
            heavyRotationTracks = affinities.heavyRotationTracks,
            discoveryRadarTracks = discoveryTracks,
            categoryRadarTitle = "CATEGORY RADAR // ${primaryGenre.uppercase()}",
            categoryRadarTracks = categoryTracks,
            vaultFavoritesTracks = affinities.vaultTracks,
            moodTracks = moodTracks,
            trendingTracks = trendingTracks,
            quickPicks = quickPicks,
            timeOfDayTitle = timeTitle,
            timeOfDaySubtitle = timeSubtitle,
            autopilotTargetSinger = topLiked?.uppercase() ?: "GLOBAL ICONS",
            autopilotTargetGenre = primaryGenre.uppercase()
        )
    }

    /**
     * J.A.R.V.I.S. Smart Autopilot Algorithm.
     * Uses history tracks, top singers, category classification, and time-of-day affinity
     * to sequence a continuous 35+ track intelligent playback queue.
     */
    suspend fun generateAutopilotQueue(limit: Int = 35): List<TrackEntity> = coroutineScope {
        val affinities = analyzeUserAffinities()
        val topSingers = affinities.overallTopArtists.take(4)
        val primaryGenre = affinities.primaryGenre ?: "synthwave"

        // 1. Anchor tracks from user's heavy rotation and vault
        val userVault = (affinities.heavyRotationTracks + affinities.vaultTracks)
            .distinctBy { it.id }
            .shuffled()

        // 2. Fetch radio mixes for top singers
        val singerRadiosDeferred = topSingers.take(2).map { singer ->
            async(Dispatchers.IO) {
                streamRepository.searchTracks("$singer best hits radio mix").firstOrNull()?.getOrNull().orEmpty()
            }
        }

        // 3. Fetch category discovery tracks
        val genreRadioDeferred = async(Dispatchers.IO) {
            streamRepository.searchTracks("$primaryGenre essentials mix radio").firstOrNull()?.getOrNull().orEmpty()
        }

        val trendingDeferred = async(Dispatchers.IO) {
            streamRepository.searchTracks("Global Trending Viral Hits").firstOrNull()?.getOrNull().orEmpty()
        }

        val singerTracks = singerRadiosDeferred.flatMap { it.await() }
        val genreTracks = genreRadioDeferred.await()
        val trendingTracks = trendingDeferred.await()

        val recommendations = (singerTracks + genreTracks + trendingTracks)
            .distinctBy { it.id }
            .filter { it.isSongOnly() }
            .shuffled()

        // 4. Interleaving Algorithm: Anchor favorite -> Recommended discoveries -> High affinity gems
        val autopilotQueue = mutableListOf<TrackEntity>()
        val famIter = userVault.iterator()
        val recIter = recommendations.iterator()

        // Start with familiar anchor
        if (famIter.hasNext()) {
            autopilotQueue.add(famIter.next())
        } else if (recIter.hasNext()) {
            autopilotQueue.add(recIter.next())
        }

        while (autopilotQueue.size < limit && (famIter.hasNext() || recIter.hasNext())) {
            if (recIter.hasNext()) autopilotQueue.add(recIter.next())
            if (recIter.hasNext() && autopilotQueue.size < limit) autopilotQueue.add(recIter.next())
            if (famIter.hasNext() && autopilotQueue.size < limit) autopilotQueue.add(famIter.next())
        }

        if (autopilotQueue.isEmpty()) {
            trendingTracks.filter { it.isSongOnly() }.take(limit)
        } else {
            autopilotQueue.distinctBy { it.id }.filter { it.isSongOnly() }.take(limit)
        }
    }

    /**
     * Generates a tailored, YouTube-like algorithmic "Up Next" queue for a base track.
     * Combines YouTube's real "Up Next" / Related stream recommendations with the user's
     * learned listening history (artists listened to frequently, categories, and likes).
     */
    suspend fun generateRecommendationQueue(
        baseTrack: TrackEntity,
        limit: Int = 25
    ): List<TrackEntity> = coroutineScope {
        val affinities = analyzeUserAffinities()

        // 1. YouTube Algorithmic "Up Next" / Related items for the seed track
        val ytRelatedDeferred = async(Dispatchers.IO) {
            try {
                newPipeYouTubeEngine.getRelatedTracks(baseTrack.id)
            } catch (e: Exception) {
                emptyList()
            }
        }

        // 2. Artist Radio / Discography for the seed artist
        val cleanArtist = baseTrack.artist.replace(" - Topic", "").trim()
        val artistRadioDeferred = async(Dispatchers.IO) {
            if (cleanArtist.isNotBlank() && cleanArtist != "Unknown Artist") {
                streamRepository.searchTracks("$cleanArtist radio mix songs").firstOrNull()?.getOrNull().orEmpty()
            } else emptyList()
        }

        // 3. User History Affinity Match:
        // What artists or genres does this user frequently listen to alongside this artist?
        val userTopArtists = affinities.overallTopArtists.take(4)
        val userAffinityDeferred = async(Dispatchers.IO) {
            val complementaryArtist = userTopArtists.firstOrNull { it.lowercase() != cleanArtist.lowercase() }
            if (!complementaryArtist.isNullOrBlank()) {
                streamRepository.searchTracks("$complementaryArtist best songs").firstOrNull()?.getOrNull().orEmpty()
            } else {
                val primaryGenre = affinities.primaryGenre ?: "trending"
                streamRepository.searchTracks("$primaryGenre radio essentials").firstOrNull()?.getOrNull().orEmpty()
            }
        }

        val ytRelated = ytRelatedDeferred.await()
        val artistRadio = artistRadioDeferred.await()
        val userAffinityTracks = userAffinityDeferred.await()

        // 4. YouTube-Style Learning & Reranking Engine:
        val candidates = (ytRelated + artistRadio + userAffinityTracks)
            .distinctBy { it.id }
            .filter { it.id != baseTrack.id && it.isSongOnly() }

        val scoredTracks = candidates.map { track ->
            var score = 10f

            // YouTube recommendation index score (higher in YouTube's related items = higher relevance)
            val ytIndex = ytRelated.indexOfFirst { it.id == track.id }
            if (ytIndex != -1) {
                score += (25f - (ytIndex * 1.0f)).coerceAtLeast(0f)
            }

            // User History Affinity Boost
            val trackArtist = track.artist.lowercase(Locale.ROOT)
            if (affinities.overallTopArtists.any { trackArtist.contains(it.lowercase(Locale.ROOT)) }) {
                score += 15f
            }
            if (affinities.topLikedArtists.any { trackArtist.contains(it.lowercase(Locale.ROOT)) }) {
                score += 12f
            }
            if (affinities.topRecentArtists.any { trackArtist.contains(it.lowercase(Locale.ROOT)) }) {
                score += 10f
            }

            // Category / Genre Alignment Boost
            val trackGenres = detectGenres(track.title + " " + track.artist)
            if (affinities.topGenres.any { trackGenres.contains(it) }) {
                score += 8f
            }

            // Exploration temperature (stochastic exploration factor like YouTube recommendations)
            val explorationFactor = (Math.random() * 4.0).toFloat()

            Pair(track, score + explorationFactor)
        }

        val rankedQueue = scoredTracks.sortedByDescending { it.second }.map { it.first }

        (listOf(baseTrack) + rankedQueue).take(limit)
    }

    private fun extractArtists(artistStr: String): List<String> {
        if (artistStr.isBlank()) return emptyList()
        return artistStr
            .split(",", "&", "feat.", "ft.", "/", ";", "•")
            .map { it.replace(" - Topic", "").trim() }
            .filter { it.length > 1 }
    }

    private fun detectGenres(text: String): List<String> {
        val lower = text.lowercase(Locale.ROOT)
        return genreKeywords.filter { lower.contains(it) }
    }

    private fun getQueryForMood(mood: String): String {
        return when (mood.lowercase(Locale.ROOT)) {
            "chill" -> "Chill Lo-Fi Beats relaxing"
            "workout" -> "Workout Energy EDM Gym pump motivation"
            "focus" -> "Deep Focus Ambient Study Flow"
            "party" -> "Club Party Dance chartbusters"
            "synthwave" -> "Synthwave Retrowave Cyberpunk 80s"
            "retro" -> "Retro Classic Master Hits 80s 90s"
            "romantic" -> "Acoustic Romantic Love Songs"
            "acoustic" -> "Acoustic Pop Guitar Chill"
            else -> "Top Acoustic Hits"
        }
    }

    private fun computeTimeOfDayInfo(favGenre: String?): Triple<String, String, String> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val style = favGenre ?: "acoustic"
        return when (hour) {
            in 5..11 -> Triple(
                "Good morning, Tony",
                "J.A.R.V.I.S. // MORNING ${style.uppercase()} MATRIX",
                "Morning uplifting $style"
            )
            in 12..16 -> Triple(
                "Good afternoon, Tony",
                "J.A.R.V.I.S. // FOCUS & FLOW TELEMETRY NOMINAL",
                "Focus deep work $style"
            )
            in 17..21 -> Triple(
                "Good evening, Tony",
                "J.A.R.V.I.S. // SUNSET AUDIO TELEMETRY SYNCHRONIZED",
                "Sunset relaxing $style"
            )
            else -> Triple(
                "Late Night Protocol, Tony",
                "J.A.R.V.I.S. // NIGHT DRIVE & SYNTHWAVE CORES ONLINE",
                "Late night lofi synthwave"
            )
        }
    }
}
