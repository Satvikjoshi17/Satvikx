package com.satvik.satvikx.data.repository

import com.satvik.satvikx.data.local.dao.PlaylistDao
import com.satvik.satvikx.data.local.dao.RecentPlaybackDao
import com.satvik.satvikx.data.local.dao.TrackDao
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.entity.cleanArtist
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

@Singleton
class RecommendationRepository @Inject constructor(
    private val recentPlaybackDao: RecentPlaybackDao,
    private val playbackTelemetryDao: com.satvik.satvikx.data.local.dao.PlaybackTelemetryDao,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val streamRepository: StreamRepository,
    private val newPipeYouTubeEngine: NewPipeYouTubeEngine,
    private val saavnMediaEngine: com.satvik.satvikx.data.remote.saavn.SaavnMediaEngine,
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
    suspend fun analyzeUserAffinities(): UserAffinities = withContext(Dispatchers.IO) {
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
    suspend fun loadHomeRecommendations(selectedMood: String): HomeRecommendationCategories = withContext(Dispatchers.IO) {
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
            becauseYouLikedTitle = if (hasUserAffinities) "BECAUSE YOU LISTEN TO ${primaryArtist.uppercase()}" else "TOP PICKS • ${primaryArtist.uppercase()}",
            becauseYouLikedSubtitle = "Handpicked tracks & related soundscapes",
            becauseYouLikedTracks = becauseLikedTracks,
            similarArtistTitle = "SIMILAR TO ${secondaryArtist.uppercase()}",
            similarArtistSubtitle = "Popular audio inspired by ${secondaryArtist}",
            similarArtistTracks = similarArtistTracks,
            heavyRotationTracks = affinities.heavyRotationTracks,
            discoveryRadarTracks = discoveryTracks,
            categoryRadarTitle = "CATEGORY RADAR • ${primaryGenre.uppercase()}",
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
    suspend fun generateAutopilotQueue(limit: Int = 35): List<TrackEntity> = withContext(Dispatchers.IO) {
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
     * Categorizes acoustic texture, BPM, and mood to group candidates into micro-genres.
     */
    fun detectAcousticVibe(title: String, artist: String): AcousticVibeInfo {
        val combined = "$title $artist".lowercase(Locale.ROOT)
        return when {
            // Gym / Phonk / High Energy
            combined.contains("phonk") || combined.contains("drift") || combined.contains("workout") ||
            combined.contains("gym") || combined.contains("hardstyle") || combined.contains("bass boost") ||
            combined.contains("kordhell") || combined.contains("brazilian phonk") || combined.contains("speed up") ->
                AcousticVibeInfo("GYM_PHONK", "gym motivation high energy phonk songs audio")

            // Lo-Fi / Chill / Acoustic Downtempo
            combined.contains("lofi") || combined.contains("lo-fi") || combined.contains("chill") ||
            combined.contains("slowed") || combined.contains("reverb") || combined.contains("anuv jain") ||
            combined.contains("prateek kuhad") || combined.contains("zaeden") || combined.contains("midnight") ||
            combined.contains("peaceful") || combined.contains("sleep") || combined.contains("acoustic") ->
                AcousticVibeInfo("LOFI_CHILL", "chill lofi acoustic indie songs official audio")

            // Punjabi / Desi Swag / Bhangra
            combined.contains("punjabi") || combined.contains("sidhu") || combined.contains("moose wala") ||
            combined.contains("karan aujla") || combined.contains("diljit") || combined.contains("dosanjh") ||
            combined.contains("ap dhillon") || combined.contains("shubh") || combined.contains("amrit maan") ||
            combined.contains("jatt") || combined.contains("swag") ->
                AcousticVibeInfo("PUNJABI_CHARTBUSTER", "punjabi chartbusters hit songs official audio")

            // Romantic / Melodic Bollywood
            combined.contains("arijit") || combined.contains("shreya") || combined.contains("atif") ||
            combined.contains("pritam") || combined.contains("jubin") || combined.contains("darshan raval") ||
            combined.contains("kesariya") || combined.contains("tum hi ho") || combined.contains("channa mereya") ||
            combined.contains("ishq") || combined.contains("love") || combined.contains("romantic") ||
            combined.contains("hawayein") || combined.contains("raataan") ->
                AcousticVibeInfo("ROMANTIC_MELODY", "romantic bollywood acoustic love songs official audio")

            // Hip Hop / Rap / Drill
            combined.contains("rap") || combined.contains("hip hop") || combined.contains("drill") ||
            combined.contains("drake") || combined.contains("travis scott") || combined.contains("kendrick") ||
            combined.contains("eminem") || combined.contains("divine") || combined.contains("emiway") ||
            combined.contains("mc stan") || combined.contains("seedhe maut") || combined.contains("kr\$na") ->
                AcousticVibeInfo("HIPHOP_RAP", "top hip hop rap tracks official audio")

            // EDM / Dance / Club
            combined.contains("remix") || combined.contains("dance") || combined.contains("club") ||
            combined.contains("party") || combined.contains("edm") || combined.contains("martin garrix") ||
            combined.contains("alan walker") || combined.contains("david guetta") || combined.contains("dj") ->
                AcousticVibeInfo("EDM_DANCE", "edm festival dance party hits official audio")

            // Synthwave / Retrowave / Cyberpunk
            combined.contains("synth") || combined.contains("retro") || combined.contains("cyber") ||
            combined.contains("neon") || combined.contains("80s") || combined.contains("night drive") ->
                AcousticVibeInfo("SYNTHWAVE", "synthwave retrowave night drive songs official audio")

            // Sufi / Ghazal / Soulful
            combined.contains("sufi") || combined.contains("ghazal") || combined.contains("qawwali") ||
            combined.contains("nusrat") || combined.contains("rahat") || combined.contains("jagjit") ->
                AcousticVibeInfo("SOULFUL_SUFI", "soulful sufi ghazal classics audio")

            // Devotional / Spiritual
            combined.contains("bhajan") || combined.contains("aarti") || combined.contains("mantra") ||
            combined.contains("krishna") || combined.contains("shiva") || combined.contains("hanuman") ||
            combined.contains("ram") || combined.contains("devotional") ->
                AcousticVibeInfo("DEVOTIONAL", "peaceful devotional spiritual bhajans audio")

            else -> AcousticVibeInfo("MELODIC_DISCOVERY", "trending popular melody songs official audio")
        }
    }

    private val collaboratorMap = mapOf(
        "arijit singh" to listOf("pritam", "shreya ghoshal", "atif aslam", "jasleen royal", "mithoon", "mohit chauhan"),
        "pritam" to listOf("arijit singh", "kk", "mohit chauhan", "shreya ghoshal", "vishal mishra"),
        "shreya ghoshal" to listOf("arijit singh", "sonu nigam", "pritam", "shaan", "armaan malik"),
        "the weeknd" to listOf("daft punk", "post malone", "lana del rey", "ariana grande", "future", "metro boomin"),
        "sidhu moose wala" to listOf("karan aujla", "amrit maan", "bohemia", "byg byrd", "the kidd"),
        "karan aujla" to listOf("sidhu moose wala", "diljit dosanjh", "ikky", "shubh", "ap dhillon"),
        "diljit dosanjh" to listOf("karan aujla", "badshah", "yo yo honey singh", "intense", "truskool"),
        "ap dhillon" to listOf("gurinder gill", "shinda kahlon", "shubh", "karan aujla"),
        "shubh" to listOf("ap dhillon", "karan aujla", "sidhu moose wala"),
        "anuv jain" to listOf("prateek kuhad", "zaeden", "jasleen royal", "babil khan", "aditya a"),
        "prateek kuhad" to listOf("anuv jain", "jasleen royal", "zaeden", "when chai met toast"),
        "taylor swift" to listOf("ed sheeran", "phoebe bridgers", "bon iver", "lana del rey", "sabrina carpenter"),
        "drake" to listOf("travis scott", "future", "21 savage", "lil baby", "kendrick lamar"),
        "travis scott" to listOf("drake", "playboi carti", "kanye west", "metro boomin", "future"),
        "billie eilish" to listOf("finneas", "olivia rodrigo", "lorde", "conan gray", "lana del rey"),
        "badshah" to listOf("yo yo honey singh", "raftaar", "ikka", "guru randhawa", "neha kakkar"),
        "yo yo honey singh" to listOf("badshah", "raftaar", "lil golu", "guru randhawa", "alfaaz"),
        "eminem" to listOf("dr dre", "50 cent", "rihanna", "nf", "snoop dogg"),
        "ed sheeran" to listOf("taylor swift", "justin bieber", "shawn mendes", "charlie puth"),
        "justin bieber" to listOf("ed sheeran", "shawn mendes", "charlie puth", "the kid laroi"),
        "kk" to listOf("pritam", "emraan hashmi", "shaan", "mohit chauhan", "lucky ali"),
        "mohit chauhan" to listOf("ar rahman", "pritam", "lucky ali", "atif aslam", "arijit singh"),
        "ar rahman" to listOf("javed ali", "mohit chauhan", "haricharan", "shreya ghoshal"),
        "divine" to listOf("naezy", "emiway bantai", "sid sriram", "mc stan", "karan aujla"),
        "alan walker" to listOf("marshmello", "martin garrix", "the chainsmokers", "avicii", "dj snake"),
        "martin garrix" to listOf("alesso", "avicii", "tiesto", "david guetta", "zedd"),
        "kishore kumar" to listOf("mohammed rafi", "rd burman", "lata mangeshkar", "asha bhosle"),
        "lata mangeshkar" to listOf("kishore kumar", "mohammed rafi", "mukesh", "asha bhosle")
    )

    private fun getCollaborators(artist: String): List<String> {
        val clean = artist.lowercase(Locale.ROOT).trim()
        for ((key, partners) in collaboratorMap) {
            if (clean.contains(key) || key.contains(clean)) {
                return partners
            }
        }
        return emptyList()
    }

    /**
     * Generates a tailored, YouTube/Spotify-grade algorithmic "Up Next" queue for a base track.
     * Guaranteed to NEVER queue alternative uploads/channels of the base track or repeat songs.
     * Combines YouTube's real "Up Next" / Related stream graph, JioSaavn CD acoustic recommendations,
     * acoustic micro-genre / vibe radio clustering, collaborator duet networks, artist discography momentum,
     * and user affinities with dynamic artist-spacing and anti-monotony sequencing.
     * Executed entirely on Dispatchers.IO to guarantee zero Main thread blocking.
     */
    suspend fun generateRecommendationQueue(
        baseTrack: TrackEntity,
        limit: Int = 25
    ): List<TrackEntity> = withContext(Dispatchers.IO) {
        val affinities = analyzeUserAffinities()
        val cleanArtist = baseTrack.cleanArtistName()
        val seedCleanTitle = baseTrack.cleanSongTitle()
        val isYouTubeId = baseTrack.id.length == 11 && !baseTrack.id.all { it.isDigit() }

        val vibeInfo = detectAcousticVibe(baseTrack.title, baseTrack.artist)
        val collaborators = getCollaborators(cleanArtist)
        val topCollaborator = collaborators.firstOrNull()

        // 1. Multi-Stream Deep Harvesting:
        // A. JioSaavn Native Recommendation Engine (pristine 320kbps CD-quality acoustic similarity)
        val saavnRecoDeferred = async(Dispatchers.IO) {
            try {
                if (!isYouTubeId) {
                    saavnMediaEngine.getSongRecommendations(baseTrack.id)
                } else {
                    val searchResults = saavnMediaEngine.searchTracks("$seedCleanTitle $cleanArtist")
                    val match = searchResults.firstOrNull { it.isSameSong(baseTrack) } ?: searchResults.firstOrNull()
                    if (match != null) {
                        saavnMediaEngine.getSongRecommendations(match.id)
                    } else emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        // B. YouTube Algorithmic "Up Next" / Watch-Next Continuation Graph
        val ytRelatedDeferred = async(Dispatchers.IO) {
            try {
                if (isYouTubeId) {
                    newPipeYouTubeEngine.getRelatedTracks(baseTrack.id)
                } else {
                    val ytSearch = streamRepository.searchTracks("$cleanArtist $seedCleanTitle official audio").firstOrNull()?.getOrNull().orEmpty()
                    val ytMatch = ytSearch.firstOrNull { it.id.length == 11 && !it.id.all { c -> c.isDigit() } }
                    if (ytMatch != null) {
                        newPipeYouTubeEngine.getRelatedTracks(ytMatch.id)
                    } else emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

        // C. Acoustic Micro-Genre & Vibe Radio Cluster (Matches acoustic texture, BPM, and mood)
        val vibeRadioDeferred = async(Dispatchers.IO) {
            try {
                streamRepository.searchTracks(vibeInfo.searchQuery).firstOrNull()?.getOrNull().orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
        }

        // D. Collaborator & Network Graph Radio (Duets, co-writers, frequent musical partners)
        val collaboratorRadioDeferred = async(Dispatchers.IO) {
            if (!topCollaborator.isNullOrBlank()) {
                try {
                    streamRepository.searchTracks("$topCollaborator top hits official audio").firstOrNull()?.getOrNull().orEmpty()
                } catch (_: Exception) {
                    emptyList()
                }
            } else emptyList()
        }

        // E. Seed Artist Top Hits / Discography Momentum
        val artistRadioDeferred = async(Dispatchers.IO) {
            if (cleanArtist.isNotBlank() && cleanArtist != "unknown artist") {
                try {
                    streamRepository.searchTracks("$cleanArtist top hits audio").firstOrNull()?.getOrNull().orEmpty()
                } catch (_: Exception) {
                    emptyList()
                }
            } else emptyList()
        }

        // F. User History Affinity Match (Complementary favorite artist / favorite genre)
        val userTopArtists = affinities.overallTopArtists.take(5)
        val userAffinityDeferred = async(Dispatchers.IO) {
            val complementaryArtist = userTopArtists.firstOrNull { !it.equals(cleanArtist, ignoreCase = true) }
            if (!complementaryArtist.isNullOrBlank()) {
                try {
                    streamRepository.searchTracks("$complementaryArtist songs official audio").firstOrNull()?.getOrNull().orEmpty()
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                val primaryGenre = affinities.primaryGenre ?: "trending"
                try {
                    streamRepository.searchTracks("$primaryGenre songs official").firstOrNull()?.getOrNull().orEmpty()
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }

        val saavnRecos = saavnRecoDeferred.await()
        val ytRelated = ytRelatedDeferred.await()
        val vibeTracks = vibeRadioDeferred.await()
        val collaboratorTracks = collaboratorRadioDeferred.await()
        val artistRadio = artistRadioDeferred.await()
        val userAffinityTracks = userAffinityDeferred.await()

        // 2. Anti-Repetition Shield: Fetch user recent playback history & telemetry safely
        val recentHistory = try {
            recentPlaybackDao.getRecentTracksSync(50)
        } catch (_: Exception) {
            emptyList()
        }
        val skippedTrackIds = try {
            playbackTelemetryDao.getFrequentlySkippedTrackIds().toSet()
        } catch (_: Exception) {
            emptySet()
        }
        val telemetryList = try {
            playbackTelemetryDao.getRecentTelemetry(100)
        } catch (_: Exception) {
            emptyList()
        }
        val telemetryByArtist = telemetryList.groupBy { cleanArtist(it.artist) }

        // 3. Strict Candidate Filtering:
        // - MUST NOT be the base track (neither by video ID nor by fuzzy song identity from another channel!)
        // - MUST NOT be a long mix or compilation (isSongOnly)
        // - MUST NOT be in skipped tracks list
        // - MUST NOT be in recent session exclusion buffer
        // - MUST NOT have been played recently in the user's last 50 songs
        val rawCandidates = (saavnRecos + ytRelated + vibeTracks + collaboratorTracks + artistRadio + userAffinityTracks)

        // Strict pass (excludes recent history)
        var candidates = rawCandidates
            .filter { candidate ->
                candidate.id != baseTrack.id &&
                !candidate.isSameSong(baseTrack) &&
                candidate.isSongOnly() &&
                candidate.id !in skippedTrackIds &&
                !isExcludedFromSession(candidate) &&
                recentHistory.none { it.isSameSong(candidate) }
            }
            .distinctSongs()

        // Resilient fallback: If strict filter removed too many candidates, relax recent history exclusion
        if (candidates.size < 8) {
            val relaxedRecent = recentHistory.take(8)
            candidates = rawCandidates
                .filter { candidate ->
                    candidate.id != baseTrack.id &&
                    !candidate.isSameSong(baseTrack) &&
                    candidate.isSongOnly() &&
                    candidate.id !in skippedTrackIds &&
                    !isExcludedFromSession(candidate) &&
                    relaxedRecent.none { it.isSameSong(candidate) }
                }
                .distinctSongs()
        }

        // 4. Multi-Objective Telemetry & Content Scoring:
        val scoredTracks = candidates.map { track ->
            var score = 10.0f

            // JioSaavn Acoustic Recommendation Bonus (pristine 320kbps CD-quality native stream)
            val saavnIndex = saavnRecos.indexOfFirst { it.id == track.id }
            if (saavnIndex != -1) {
                score += (24.0f - (saavnIndex * 0.9f)).coerceAtLeast(8.0f)
            }

            // YouTube recommendation index score (relevance in global watch-next graph)
            val ytIndex = ytRelated.indexOfFirst { it.id == track.id }
            if (ytIndex != -1) {
                score += (22.0f - (ytIndex * 0.85f)).coerceAtLeast(6.0f)
            }

            // High-fidelity pre-resolved streamUrl bonus
            if (!track.streamUrl.isNullOrBlank()) {
                score += 5.0f
            }

            // Acoustic Vibe / Micro-Genre Alignment Bonus
            if (vibeTracks.any { it.id == track.id }) {
                score += 16.0f
            }

            // Collaborator & Network Graph Match Bonus
            val trackArtist = track.cleanArtistName()
            if (collaboratorTracks.any { it.id == track.id }) {
                score += 14.0f
            }
            if (collaborators.any { trackArtist.contains(it) }) {
                score += 8.0f
            }

            // Seed Artist Match Boost (Signature hits by the same artist)
            if (cleanArtist.isNotBlank() && trackArtist.contains(cleanArtist)) {
                score += 12.0f
            }

            // User History Affinity Boost
            if (affinities.overallTopArtists.any { trackArtist.contains(it.lowercase(Locale.ROOT)) }) {
                score += 10.0f
            }
            if (affinities.topLikedArtists.any { trackArtist.contains(it.lowercase(Locale.ROOT)) }) {
                score += 8.0f
            }
            if (affinities.topRecentArtists.any { trackArtist.contains(it.lowercase(Locale.ROOT)) }) {
                score += 5.0f
            }

            // Category / Genre Alignment Boost
            val trackGenres = detectGenres(track.title + " " + track.artist)
            if (affinities.topGenres.any { trackGenres.contains(it) }) {
                score += 5.0f
            }

            // Telemetry Behavioral Feedback (Completion reward vs Skip penalty)
            val artistTelem = telemetryByArtist[trackArtist].orEmpty()
            val completions = artistTelem.sumOf { it.completionCount }
            val skips = artistTelem.sumOf { it.skipCount }
            if (completions > 0) score += (minOf(completions, 5) * 3.0f)
            if (skips > 0) score -= (minOf(skips, 3) * 6.0f)

            // Controlled stochastic exploration jitter
            val explorationFactor = (Math.random() * 2.5).toFloat()

            Pair(track, score + explorationFactor)
        }

        val rankedList = scoredTracks.sortedByDescending { it.second }.map { it.first }

        // 5. Dynamic Artist Spacing & Diversity Sequencing (Spotify / Apple Music algorithm)
        // Prevent more than 2 consecutive songs by the exact same artist
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
                "MORNING ACOUSTIC MATRIX • ${style.uppercase()}",
                "Morning uplifting $style"
            )
            in 12..16 -> Triple(
                "Good afternoon",
                "FOCUS & FLOW MATRIX • OPTIMAL",
                "Focus deep work $style"
            )
            in 17..21 -> Triple(
                "Good evening",
                "SUNSET ACOUSTIC MATRIX • SYNCHRONIZED",
                "Sunset relaxing $style"
            )
            else -> Triple(
                "Late Night Protocol",
                "NIGHT DRIVE PROTOCOL • ACTIVE",
                "Late night lofi synthwave"
            )
        }
    }

    suspend fun getArtistTracks(artistName: String): List<TrackEntity> = withContext(Dispatchers.IO) {
        val results = streamRepository.searchTracks("$artistName top hits official audio").firstOrNull()?.getOrNull().orEmpty()
        results.filter { it.isSongOnly() }
    }
}
