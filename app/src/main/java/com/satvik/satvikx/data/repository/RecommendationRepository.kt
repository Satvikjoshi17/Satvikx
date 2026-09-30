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

        // Fallback curated flagship artists if user has no listening history yet
        val primaryArtist = rankedArtists.getOrNull(0) ?: likedArtists.firstOrNull() ?: "Arijit Singh"
        val secondaryArtist = rankedArtists.getOrNull(1) ?: likedArtists.getOrNull(1) ?: "The Weeknd"
        val primaryGenre = rankedGenres.firstOrNull() ?: "acoustic"

        UserAffinities(
            topLikedArtists = likedArtists,
            topRecentArtists = recentArtists,
            topDownloadedArtists = downloadedArtists,
            overallTopArtists = rankedArtists,
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

        val becauseLikedTracks = becauseLikedDeferred.await().filter { it.isSongOnly() }
        val similarArtistTracks = similarArtistDeferred.await().filter { it.isSongOnly() }
        val discoveryTracks = discoveryDeferred.await().filter { it.isSongOnly() }
        val categoryTracks = categoryDeferred.await().filter { it.isSongOnly() }
        val moodTracks = moodDeferred.await().filter { it.isSongOnly() }
        val trendingTracks = trendingDeferred.await().filter { it.isSongOnly() }

        // Quick Picks: combine recent/heavy rotation or trending (strictly pure songs)
        val quickPicks = (if (affinities.heavyRotationTracks.isNotEmpty()) {
            affinities.heavyRotationTracks
        } else if (affinities.vaultTracks.isNotEmpty()) {
            affinities.vaultTracks
        } else {
            trendingTracks
        }).filter { it.isSongOnly() }.take(6)

        // Spotify-style Daily Mixes (5 personalized algorithmic mixes with vibrant linear gradients)
        val dailyMix1Tracks = (becauseLikedTracks.take(8) + affinities.heavyRotationTracks.filter { it.isSongOnly() }.take(4))
            .distinctBy { it.id }
            .ifEmpty { trendingTracks.take(10) }

        val dailyMix2Tracks = (similarArtistTracks.take(8) + discoveryTracks.take(4))
            .distinctBy { it.id }
            .ifEmpty { trendingTracks.drop(4).take(10) }

        val dailyMix3Tracks = (discoveryTracks.take(8) + moodTracks.take(4))
            .distinctBy { it.id }
            .ifEmpty { trendingTracks.take(10) }

        val dailyMix4Tracks = (categoryTracks.take(8) + trendingTracks.take(4))
            .distinctBy { it.id }
            .ifEmpty { trendingTracks.take(10) }

        val dailyMix5Tracks = (moodTracks.take(8) + categoryTracks.take(4))
            .distinctBy { it.id }
            .ifEmpty { trendingTracks.take(10) }

        val dailyMixes = listOf(
            DailyMix(
                id = "mix_1",
                title = "Daily Mix 1",
                subtitle = "$primaryArtist, Pritam & favorites",
                tracks = dailyMix1Tracks,
                gradientColors = listOf(0xFFFF416CL, 0xFFFF4B2BL)
            ),
            DailyMix(
                id = "mix_2",
                title = "Daily Mix 2",
                subtitle = "$secondaryArtist, Discoveries & more",
                tracks = dailyMix2Tracks,
                gradientColors = listOf(0xFF00C6FFL, 0xFF0072FFL)
            ),
            DailyMix(
                id = "mix_3",
                title = "Chill Mix",
                subtitle = "Lo-Fi, Acoustic & Downtempo",
                tracks = dailyMix3Tracks,
                gradientColors = listOf(0xFF8A2387L, 0xFFE94057L)
            ),
            DailyMix(
                id = "mix_4",
                title = "Energy Mix",
                subtitle = "Gym, Phonk & High Tempo",
                tracks = dailyMix4Tracks,
                gradientColors = listOf(0xFF11998EL, 0xFF38EF7DL)
            ),
            DailyMix(
                id = "mix_5",
                title = "Focus Flow",
                subtitle = "Ambient, Synth & Night Waves",
                tracks = dailyMix5Tracks,
                gradientColors = listOf(0xFFFF8008L, 0xFFFFC837L)
            )
        )

        val hasUserAffinities = affinities.overallTopArtists.isNotEmpty() || affinities.topLikedArtists.isNotEmpty()

        HomeRecommendationCategories(
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
            timeOfDayTitle = timeTitle,
            timeOfDaySubtitle = timeSubtitle,
            autopilotTargetSinger = primaryArtist.uppercase(),
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

        // 2. Artist Discography for the seed artist
        val cleanArtist = baseTrack.artist.replace(" - Topic", "").trim()
        val artistRadioDeferred = async(Dispatchers.IO) {
            if (cleanArtist.isNotBlank() && cleanArtist != "Unknown Artist") {
                streamRepository.searchTracks("$cleanArtist songs official audio").firstOrNull()?.getOrNull().orEmpty()
            } else emptyList()
        }

        // 3. User History Affinity Match:
        // What artists or genres does this user frequently listen to alongside this artist?
        val userTopArtists = affinities.overallTopArtists.take(4)
        val userAffinityDeferred = async(Dispatchers.IO) {
            val complementaryArtist = userTopArtists.firstOrNull { it.lowercase() != cleanArtist.lowercase() }
            if (!complementaryArtist.isNullOrBlank()) {
                streamRepository.searchTracks("$complementaryArtist songs audio").firstOrNull()?.getOrNull().orEmpty()
            } else {
                val primaryGenre = affinities.primaryGenre ?: "trending"
                streamRepository.searchTracks("$primaryGenre songs official").firstOrNull()?.getOrNull().orEmpty()
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
            "all" -> "Top Global Hits Viral"
            "relax", "chill" -> "Chill Lo-Fi Beats relaxing acoustic"
            "workout", "energize" -> "Workout Energy EDM Gym pump motivation"
            "focus" -> "Deep Focus Ambient Study Flow beats"
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
