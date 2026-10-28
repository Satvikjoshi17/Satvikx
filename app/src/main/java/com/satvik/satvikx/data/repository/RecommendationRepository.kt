package com.satvik.satvikx.data.repository

import com.satvik.satvikx.data.local.dao.PlaylistDao
import com.satvik.satvikx.data.local.dao.RecentPlaybackDao
import com.satvik.satvikx.data.local.dao.TrackDao
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.entity.cleanArtistName
import com.satvik.satvikx.data.local.entity.cleanSongTitle
import com.satvik.satvikx.data.local.entity.distinctSongs
import com.satvik.satvikx.data.local.entity.isSameSong
import com.satvik.satvikx.data.local.entity.isSongOnly
import com.satvik.satvikx.data.remote.newpipe.NewPipeYouTubeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
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

@Singleton
class RecommendationRepository @Inject constructor(
    private val recentPlaybackDao: RecentPlaybackDao,
    private val playbackTelemetryDao: com.satvik.satvikx.data.local.dao.PlaybackTelemetryDao,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val streamRepository: StreamRepository,
    private val newPipeYouTubeEngine: NewPipeYouTubeEngine,
    private val homeFeedCacheManager: HomeFeedCacheManager
) {

    // Anti-looping ring buffers: tracks recently recommended / queued in this session (by ID and canonical title)
    private val sessionExclusionIds = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val sessionExclusionKeys = java.util.Collections.synchronizedList(mutableListOf<String>())

    fun recordRecommendedTrack(track: TrackEntity) {
        synchronized(sessionExclusionIds) {
            sessionExclusionIds.add(track.id)
            if (sessionExclusionIds.size > 120) sessionExclusionIds.removeAt(0)
        }
        val key = track.cleanSongTitle().replace(" ", "")
        if (key.isNotBlank()) {
            synchronized(sessionExclusionKeys) {
                sessionExclusionKeys.add(key)
                if (sessionExclusionKeys.size > 120) sessionExclusionKeys.removeAt(0)
            }
        }
    }

    fun recordRecommendedTrack(trackId: String) {
        synchronized(sessionExclusionIds) {
            sessionExclusionIds.add(trackId)
            if (sessionExclusionIds.size > 120) sessionExclusionIds.removeAt(0)
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
        val telemetryList = try {
            playbackTelemetryDao.getRecentTelemetry(100)
        } catch (_: Exception) {
            emptyList()
        }

        // Weight table: Liked = 5.0, Downloaded = 3.5, Recent = 2.0
        // Telemetry bonuses: Completion >= 70% (+3.0), Replay > 1 (+4.0), Skips (-4.5 penalty)
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

        // 3. Behavioral Telemetry Scoring (Completion & Replay Bonus, Skip Penalty)
        telemetryList.forEach { telem ->
            extractArtists(telem.artist).forEach { artist ->
                var telemScore = 0f
                if (telem.completionCount > 0) telemScore += (telem.completionCount * 3.0f)
                if (telem.playCount > 1) telemScore += (telem.playCount * 1.5f)
                if (telem.skipCount > 0) telemScore -= (telem.skipCount * 4.0f) // Penalize skips
                artistScores[artist] = (artistScores[artist] ?: 0f) + telemScore
            }
            detectGenres(telem.title + " " + telem.artist).forEach { g ->
                val bonus = if (telem.completionCount > 0) 2 else if (telem.skipCount > 0) -2 else 0
                genreScores[g] = ((genreScores[g] ?: 0) + bonus).coerceAtLeast(0)
            }
        }

        // 4. Recent Tracks Frequency Scoring
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

        // 5. Heavy Rotation Tracks (tracks played multiple times or top in history)
        val trackFrequency = recentTracks.groupingBy { it.id }.eachCount()
        val heavyRotation = recentTracks
            .distinctBy { it.id }
            .sortedByDescending { trackFrequency[it.id] ?: 1 }
            .take(15)

        val rankedArtists = artistScores.entries
            .filter { it.key.isNotBlank() && it.key.lowercase() != "unknown artist" && it.value > 0f }
            .sortedByDescending { it.value }
            .map { it.key }

        val rankedGenres = genreScores.entries
            .sortedByDescending { it.value }
            .map { it.key }

        val vaultCombined = (likedTracks + downloadedTracks).distinctBy { it.id }

        // Fallback curated flagship artists if user has no listening history yet
        val primaryArtist = rankedArtists.getOrNull(0) ?: likedArtists.firstOrNull() ?: "Arijit Singh"
        val secondaryArtist = rankedArtists.getOrNull(1) ?: likedArtists.getOrNull(1) ?: "The Weeknd"
        val primaryGenre = rankedGenres.firstOrNull() ?: "acoustic"

        val artistThumbnails = mutableMapOf<String, String>()
        (recentTracks + likedTracks + downloadedTracks).forEach { track ->
            extractArtists(track.artist).forEach { artist ->
                if (!artistThumbnails.containsKey(artist) && track.thumbnailUrl.isNotBlank()) {
                    artistThumbnails[artist] = track.thumbnailUrl
                }
            }
        }
        val topArtistAffinities = rankedArtists.take(8).map { artist ->
            ArtistAffinity(name = artist, imageUrl = artistThumbnails[artist])
        }

        UserAffinities(
            topLikedArtists = likedArtists,
            topRecentArtists = recentArtists,
            topDownloadedArtists = downloadedArtists,
            overallTopArtists = rankedArtists,
            topArtists = topArtistAffinities,
            topGenres = rankedGenres,
            heavyRotationTracks = heavyRotation,
            vaultTracks = vaultCombined,
            primaryArtist = primaryArtist,
            secondaryArtist = secondaryArtist,
            primaryGenre = primaryGenre
        )
    }

    /**
     * Builds categorized recommendation feeds for the Home Screen using the affinity matrix.
     */
    suspend fun loadHomeRecommendations(selectedMood: String): HomeRecommendationCategories = coroutineScope {
        val affinities = analyzeUserAffinities()
        val (timeTitle, timeSubtitle, defaultMoodQuery) = computeTimeOfDayInfo(affinities.topGenres.firstOrNull())

        val primaryArtist = affinities.primaryArtist ?: "Arijit Singh"
        val secondaryArtist = affinities.secondaryArtist ?: "The Weeknd"

        val becauseLikedQuery = "$primaryArtist songs official audio"
        val similarArtistQuery = "$secondaryArtist songs official audio"

        val discoveryGenre = affinities.topGenres.getOrNull(1) ?: affinities.topGenres.firstOrNull() ?: "chill acoustic"
        val discoveryQuery = "$discoveryGenre songs official audio"

        val primaryGenre = affinities.primaryGenre ?: "synthwave"
        val categoryQuery = "$primaryGenre songs official audio"
        val moodQuery = getQueryForMood(selectedMood)

        // Query recommendation feeds in parallel on IO dispatcher
        val becauseLikedDeferred = async(Dispatchers.IO) {
            streamRepository.searchTracks(becauseLikedQuery).firstOrNull()?.getOrNull().orEmpty()
        }
        val similarArtistDeferred = async(Dispatchers.IO) {
            streamRepository.searchTracks(similarArtistQuery).firstOrNull()?.getOrNull().orEmpty()
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
            streamRepository.searchTracks("Top Global Hits Music Official Audio").firstOrNull()?.getOrNull().orEmpty()
        }

        val becauseLikedTracks = becauseLikedDeferred.await().filter { it.isSongOnly() }.distinctSongs()
        val similarArtistTracks = similarArtistDeferred.await().filter { it.isSongOnly() }.distinctSongs()
        val discoveryTracks = discoveryDeferred.await().filter { it.isSongOnly() }.distinctSongs()
        val categoryTracks = categoryDeferred.await().filter { it.isSongOnly() }.distinctSongs()
        val moodTracks = moodDeferred.await().filter { it.isSongOnly() }.distinctSongs()
        val trendingTracks = trendingDeferred.await().filter { it.isSongOnly() }.distinctSongs()

        // Quick Picks: combine recent/heavy rotation or trending (strictly pure songs)
        val quickPicks = (if (affinities.heavyRotationTracks.isNotEmpty()) {
            affinities.heavyRotationTracks
        } else if (affinities.vaultTracks.isNotEmpty()) {
            affinities.vaultTracks
        } else {
            trendingTracks
        }).filter { it.isSongOnly() }.distinctSongs().take(6)

        // Spotify-style Daily Mixes (5 personalized algorithmic mixes with vibrant linear gradients)
        val dailyMix1Tracks = (becauseLikedTracks.take(8) + affinities.heavyRotationTracks.filter { it.isSongOnly() }.take(4))
            .distinctSongs()
            .ifEmpty { trendingTracks.take(10) }

        val dailyMix2Tracks = (similarArtistTracks.take(8) + discoveryTracks.take(4))
            .distinctSongs()
            .ifEmpty { trendingTracks.drop(4).take(10) }

        val dailyMix3Tracks = (discoveryTracks.take(8) + moodTracks.take(4))
            .distinctSongs()
            .ifEmpty { trendingTracks.take(10) }

        val dailyMix4Tracks = (categoryTracks.take(8) + trendingTracks.take(4))
            .distinctSongs()
            .ifEmpty { trendingTracks.take(10) }

        val dailyMix5Tracks = (moodTracks.take(8) + categoryTracks.take(4))
            .distinctSongs()
            .ifEmpty { trendingTracks.take(10) }

        val likedPlaylist = playlistDao.getPlaylistByNameSync("Liked Songs")
        val likedTracks = likedPlaylist?.let {
            playlistDao.getPlaylistWithTracks(it.playlistId).firstOrNull()?.tracks
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
            becauseYouLikedTitle = if (hasUserAffinities) "BECAUSE YOU LISTEN TO ${primaryArtist.uppercase()}" else "TOP PICKS // ${primaryArtist.uppercase()}",
            becauseYouLikedSubtitle = "Handpicked tracks & related soundscapes",
            becauseYouLikedTracks = becauseLikedTracks,
            similarArtistTitle = "SIMILAR TO ${secondaryArtist.uppercase()}",
            similarArtistSubtitle = "Popular audio inspired by ${secondaryArtist}",
            similarArtistTracks = similarArtistTracks,
            heavyRotationTracks = affinities.heavyRotationTracks,
            discoveryRadarTracks = discoveryTracks,
            categoryRadarTitle = "CATEGORY RADAR // ${primaryGenre.uppercase()}",
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
            autopilotTargetGenre = primaryGenre.uppercase()
        )
        homeFeedCacheManager.saveFeed(selectedMood, result)
        result
    }

    /**
     * Verifies if recommendations for this mood are fresh within the cache TTL window.
     */
    fun isHomeCacheFresh(selectedMood: String): Boolean = homeFeedCacheManager.isCacheFresh(selectedMood)

    /**
     * Reads cached home recommendations instantaneously (0-5ms) and updates real-time
     * local data (liked songs, recents, current time-of-day greeting) without blocking.
     */
    suspend fun getCachedRecommendations(selectedMood: String): HomeRecommendationCategories? = withContext(Dispatchers.IO) {
        val cached = homeFeedCacheManager.getCachedFeed(selectedMood) ?: return@withContext null
        try {
            val (timeTitle, timeSubtitle, _) = computeTimeOfDayInfo(cached.autopilotTargetGenre.lowercase())
            val likedPlaylist = playlistDao.getPlaylistByNameSync("Liked Songs")
            val likedTracks = likedPlaylist?.let {
                playlistDao.getPlaylistWithTracks(it.playlistId).firstOrNull()?.tracks
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
            .filter { it.isSongOnly() }
            .distinctSongs()
            .shuffled()

        // 2. Fetch tracks for top singers
        val singerRadiosDeferred = topSingers.take(2).map { singer ->
            async(Dispatchers.IO) {
                streamRepository.searchTracks("$singer songs official audio").firstOrNull()?.getOrNull().orEmpty()
            }
        }

        // 3. Fetch category discovery tracks
        val genreRadioDeferred = async(Dispatchers.IO) {
            streamRepository.searchTracks("$primaryGenre songs official").firstOrNull()?.getOrNull().orEmpty()
        }

        val trendingDeferred = async(Dispatchers.IO) {
            streamRepository.searchTracks("Global Trending Songs Audio").firstOrNull()?.getOrNull().orEmpty()
        }

        val singerTracks = singerRadiosDeferred.flatMap { it.await() }
        val genreTracks = genreRadioDeferred.await()
        val trendingTracks = trendingDeferred.await()

        val recommendations = (singerTracks + genreTracks + trendingTracks)
            .filter { it.isSongOnly() }
            .distinctSongs()
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

        val finalQueue = if (autopilotQueue.isEmpty()) {
            trendingTracks.filter { it.isSongOnly() }.distinctSongs().take(limit)
        } else {
            autopilotQueue.distinctSongs().take(limit)
        }
        finalQueue.forEach { recordRecommendedTrack(it) }
        finalQueue
    }

    /**
     * Generates a tailored, YouTube/Spotify-grade algorithmic "Up Next" queue for a base track.
     * Guaranteed to NEVER queue alternative uploads/channels of the base track or repeat songs.
     * Combines YouTube's real "Up Next" / Related stream recommendations, similar artist radio mixes,
     * artist discography hits, and user affinities with dynamic artist-spacing and anti-monotony sequencing.
     */
    suspend fun generateRecommendationQueue(
        baseTrack: TrackEntity,
        limit: Int = 25
    ): List<TrackEntity> = coroutineScope {
        val affinities = analyzeUserAffinities()
        val cleanArtist = baseTrack.cleanArtistName()
        val seedCleanTitle = baseTrack.cleanSongTitle()

        // 1. YouTube Algorithmic "Up Next" / Related items for the seed track
        val ytRelatedDeferred = async(Dispatchers.IO) {
            try {
                newPipeYouTubeEngine.getRelatedTracks(baseTrack.id)
            } catch (e: Exception) {
                emptyList()
            }
        }

        // 2. Artist Top Hits (distinct hits by the seed artist to keep listening momentum)
        val artistRadioDeferred = async(Dispatchers.IO) {
            if (cleanArtist.isNotBlank() && cleanArtist != "unknown artist") {
                streamRepository.searchTracks("$cleanArtist top hits audio").firstOrNull()?.getOrNull().orEmpty()
            } else emptyList()
        }

        // 3. Similar / Radio Mix (discovering related artists that match this song's vibe & soundscape)
        val similarRadioDeferred = async(Dispatchers.IO) {
            if (cleanArtist.isNotBlank() && cleanArtist != "unknown artist") {
                val query = if (seedCleanTitle.isNotBlank()) "$cleanArtist $seedCleanTitle radio mix" else "$cleanArtist radio mix"
                streamRepository.searchTracks(query).firstOrNull()?.getOrNull().orEmpty()
            } else emptyList()
        }

        // 4. User History Affinity Match:
        // What artists or genres does this user frequently listen to alongside this artist?
        val userTopArtists = affinities.overallTopArtists.take(4)
        val userAffinityDeferred = async(Dispatchers.IO) {
            val complementaryArtist = userTopArtists.firstOrNull { !it.equals(cleanArtist, ignoreCase = true) }
            if (!complementaryArtist.isNullOrBlank()) {
                streamRepository.searchTracks("$complementaryArtist songs audio").firstOrNull()?.getOrNull().orEmpty()
            } else {
                val primaryGenre = affinities.primaryGenre ?: "trending"
                streamRepository.searchTracks("$primaryGenre songs official").firstOrNull()?.getOrNull().orEmpty()
            }
        }

        val ytRelated = ytRelatedDeferred.await()
        val artistRadio = artistRadioDeferred.await()
        val similarRadioTracks = similarRadioDeferred.await()
        val userAffinityTracks = userAffinityDeferred.await()

        // 5. Load frequently skipped tracks to suppress them from the auto-queue
        val skippedTrackIds = try {
            playbackTelemetryDao.getFrequentlySkippedTrackIds().toSet()
        } catch (_: Exception) {
            emptySet()
        }

        // 6. Strict Anti-Duplicate & Anti-Repetition Candidate Filtering:
        // - MUST NOT be the base track (neither by video ID nor by fuzzy song identity from another channel!)
        // - MUST NOT be a long mix or compilation (isSongOnly)
        // - MUST NOT be in skipped tracks list
        // - MUST NOT be in recent session buffer
        val rawCandidates = (ytRelated + similarRadioTracks + artistRadio + userAffinityTracks)
        val candidates = rawCandidates
            .filter { candidate ->
                candidate.id != baseTrack.id &&
                !candidate.isSameSong(baseTrack) && // <- ELIMINATES ALTERNATE CHANNEL/UPLOAD DUPLICATES!
                candidate.isSongOnly() &&
                candidate.id !in skippedTrackIds &&
                !isExcludedFromSession(candidate)
            }
            .distinctSongs() // <- DEDUPLICATES ALTERNATE UPLOADS OF CANDIDATE SONGS TOO!

        val scoredTracks = candidates.map { track ->
            var score = 10f

            // YouTube recommendation index score (higher in YouTube's related items = higher relevance)
            val ytIndex = ytRelated.indexOfFirst { it.id == track.id }
            if (ytIndex != -1) {
                score += (25f - (ytIndex * 1.0f)).coerceAtLeast(0f)
            }

            // Seed Artist Match Boost (hits by the same artist)
            val trackArtist = track.cleanArtistName()
            if (cleanArtist.isNotBlank() && trackArtist.contains(cleanArtist)) {
                score += 14f
            }

            // User History Affinity Boost
            if (affinities.overallTopArtists.any { trackArtist.contains(it.lowercase(Locale.ROOT)) }) {
                score += 12f
            }
            if (affinities.topLikedArtists.any { trackArtist.contains(it.lowercase(Locale.ROOT)) }) {
                score += 10f
            }
            if (affinities.topRecentArtists.any { trackArtist.contains(it.lowercase(Locale.ROOT)) }) {
                score += 8f
            }

            // Category / Genre Alignment Boost
            val trackGenres = detectGenres(track.title + " " + track.artist)
            if (affinities.topGenres.any { trackGenres.contains(it) }) {
                score += 6f
            }

            // Controlled stochastic exploration
            val explorationFactor = (Math.random() * 3.0).toFloat()

            Pair(track, score + explorationFactor)
        }

        val rankedList = scoredTracks.sortedByDescending { it.second }.map { it.first }

        // 7. Dynamic Artist Spacing & Diversity Sequencing
        // Spotify / Apple Music style: prevent more than 2 consecutive songs by the exact same artist
        val diversifiedQueue = mutableListOf<TrackEntity>()
        val pool = rankedList.toMutableList()

        while (pool.isNotEmpty() && diversifiedQueue.size < limit) {
            val lastArtist = diversifiedQueue.lastOrNull()?.cleanArtistName()
            val secondLastArtist = if (diversifiedQueue.size >= 2) diversifiedQueue[diversifiedQueue.size - 2].cleanArtistName() else null

            val pickIndex = if (lastArtist != null && secondLastArtist != null && lastArtist == secondLastArtist && lastArtist.isNotBlank()) {
                pool.indexOfFirst { cand ->
                    cand.cleanArtistName() != lastArtist
                }.takeIf { it != -1 } ?: 0
            } else {
                0
            }

            val chosen = pool.removeAt(pickIndex)
            diversifiedQueue.add(chosen)
        }

        // Commit top recommendations and seed track to session exclusion buffer
        recordRecommendedTrack(baseTrack)
        diversifiedQueue.forEach { recordRecommendedTrack(it) }

        diversifiedQueue
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
            in 5..11 -> Triple(
                "Good morning",
                "J.A.R.V.I.S. // MORNING ${style.uppercase()} MATRIX",
                "Morning uplifting $style"
            )
            in 12..16 -> Triple(
                "Good afternoon",
                "J.A.R.V.I.S. // FOCUS & FLOW TELEMETRY NOMINAL",
                "Focus deep work $style"
            )
            in 17..21 -> Triple(
                "Good evening",
                "J.A.R.V.I.S. // SUNSET AUDIO TELEMETRY SYNCHRONIZED",
                "Sunset relaxing $style"
            )
            else -> Triple(
                "Late Night Protocol",
                "J.A.R.V.I.S. // NIGHT DRIVE & SYNTHWAVE CORES ONLINE",
                "Late night lofi synthwave"
            )
        }
    }

    suspend fun getArtistTracks(artistName: String): List<TrackEntity> {
        val results = streamRepository.searchTracks("$artistName top hits official audio").firstOrNull()?.getOrNull().orEmpty()
        return results.filter { it.isSongOnly() }
    }
}
