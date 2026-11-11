package com.satvik.satvikx.recommendation

import com.satvik.satvikx.data.local.dao.PlaybackTelemetryDao
import com.satvik.satvikx.data.local.dao.PlaylistDao
import com.satvik.satvikx.data.local.dao.RecentPlaybackDao
import com.satvik.satvikx.data.local.dao.TrackDao
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.entity.getEffectiveArtists
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

/**
 * Mines and computes the Long-Term User Taste Profile applying exponential time decay:
 * Weight(t) = exp(-deltaDays / 30.0)
 *
 * Recent signals carry significantly higher mathematical weight than old signals,
 * while continuous replays and likes anchor enduring favorites.
 */
@Singleton
class UserTasteProfiler @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val recentPlaybackDao: RecentPlaybackDao,
    private val playbackTelemetryDao: PlaybackTelemetryDao,
    private val vibeClassifier: SessionVibeClassifier
) {

    companion object {
        const val DECAY_HALF_LIFE_DAYS = 30.0
        const val MILLIS_PER_DAY = 86400000.0

        /**
         * Calculates time-decay weight for a timestamp in milliseconds:
         * Weight = exp(-deltaDays / 30.0)
         */
        fun calculateDecayWeight(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): Float {
            if (timestampMs <= 0L) return 0.5f // Default anchor weight for undated items
            val deltaDays = (nowMs - timestampMs).coerceAtLeast(0L) / MILLIS_PER_DAY
            return exp(-deltaDays / DECAY_HALF_LIFE_DAYS).toFloat().coerceIn(0.01f, 1.0f)
        }
    }

    /**
     * Calculates time-decay weight for a timestamp in milliseconds.
     */
    fun calculateDecayWeight(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): Float =
        Companion.calculateDecayWeight(timestampMs, nowMs)

    /**
     * Generates a fully decay-weighted LongTermProfile.
     */
    suspend fun computeLongTermProfile(): LongTermProfile = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // 1. Liked Songs
        val likedPlaylist = playlistDao.getPlaylistByNameSync("Liked Songs")
        val likedTracks = likedPlaylist?.let {
            playlistDao.getPlaylistWithTracksSync(it.playlistId)?.tracks
        } ?: emptyList()

        // 2. Downloaded Songs
        val downloadedTracks = trackDao.getDownloadedTracksSync()

        // 3. Recent Tracks
        val recentTracks = recentPlaybackDao.getRecentTracksSync(60)

        // 4. Behavioral Telemetry
        val telemetryList = try {
            playbackTelemetryDao.getRecentTelemetry(100)
        } catch (_: Exception) {
            emptyList()
        }

        val artistScores = mutableMapOf<String, Float>()
        val genreScores = mutableMapOf<String, Float>()

        // 1. Liked Tracks Scoring (Base 5.0)
        likedTracks.forEach { track ->
            val decay = calculateDecayWeight(track.addedAtTimestamp, now)
            val weight = 5.0f * decay
            track.getEffectiveArtists().forEach { artist ->
                artistScores[artist] = (artistScores[artist] ?: 0f) + weight
            }
            vibeClassifier.detectGenres("${track.title} ${track.artist}").forEach { g ->
                genreScores[g] = (genreScores[g] ?: 0f) + (3.0f * decay)
            }
        }

        // 2. Downloaded Tracks Scoring (Base 3.5)
        downloadedTracks.forEach { track ->
            val decay = calculateDecayWeight(track.addedAtTimestamp, now)
            val weight = 3.5f * decay
            track.getEffectiveArtists().forEach { artist ->
                artistScores[artist] = (artistScores[artist] ?: 0f) + weight
            }
            vibeClassifier.detectGenres("${track.title} ${track.artist}").forEach { g ->
                genreScores[g] = (genreScores[g] ?: 0f) + (2.0f * decay)
            }
        }

        // 3. Behavioral Telemetry Scoring with Time Decay
        telemetryList.forEach { telem ->
            val decay = calculateDecayWeight(telem.lastPlayedTimestamp, now)
            var telemScore = 0f
            if (telem.completionCount > 0) telemScore += (telem.completionCount * 3.0f * decay)
            if (telem.playCount > 1) telemScore += (telem.playCount * 2.0f * decay)
            if (telem.skipCount > 0) telemScore -= (telem.skipCount * 4.5f * decay) // Strong skip penalty

            val artists = telem.artist.split(",", "&", "feat.", "ft.", "/", ";").map { it.trim() }.filter { it.length > 1 }
            artists.forEach { artist ->
                artistScores[artist] = (artistScores[artist] ?: 0f) + telemScore
            }

            vibeClassifier.detectGenres("${telem.title} ${telem.artist}").forEach { g ->
                val bonus = if (telem.completionCount > 0) (2.0f * decay) else if (telem.skipCount > 0) (-3.0f * decay) else 0f
                genreScores[g] = ((genreScores[g] ?: 0f) + bonus).coerceAtLeast(0f)
            }
        }

        // 4. Recent Tracks Frequency Scoring
        recentTracks.forEachIndexed { index, track ->
            val recencyBase = if (index < 15) 2.5f else 1.2f
            // Assume monotonic distribution across last 48 hours for recents without explicit timestamps
            val syntheticTimestamp = now - (index * 3600000L * 3L)
            val decay = calculateDecayWeight(syntheticTimestamp, now)
            val weight = recencyBase * decay

            track.getEffectiveArtists().forEach { artist ->
                artistScores[artist] = (artistScores[artist] ?: 0f) + weight
            }
            vibeClassifier.detectGenres("${track.title} ${track.artist}").forEach { g ->
                genreScores[g] = (genreScores[g] ?: 0f) + (1.0f * decay)
            }
        }

        // Rank and prune
        val rankedArtists = artistScores.entries
            .filter { it.key.isNotBlank() && it.key.lowercase() != "unknown artist" && it.value > 0f }
            .sortedByDescending { it.value }
            .map { it.key }

        val rankedGenres = genreScores.entries
            .filter { it.value > 0f }
            .sortedByDescending { it.value }
            .map { it.key }

        val vaultCombined = (likedTracks + downloadedTracks).distinctBy { it.id }

        val trackFrequency = recentTracks.groupingBy { it.id }.eachCount()
        val heavyRotation = recentTracks
            .distinctBy { it.id }
            .sortedByDescending { trackFrequency[it.id] ?: 1 }
            .take(15)

        val primaryArtist = rankedArtists.getOrNull(0) ?: "Arijit Singh"
        val secondaryArtist = rankedArtists.getOrNull(1) ?: "The Weeknd"
        val primaryGenre = rankedGenres.firstOrNull() ?: "acoustic"

        LongTermProfile(
            artistScores = artistScores,
            genreScores = genreScores,
            topArtists = rankedArtists,
            topGenres = rankedGenres,
            vaultTracks = vaultCombined,
            heavyRotationTracks = heavyRotation,
            primaryArtist = primaryArtist,
            secondaryArtist = secondaryArtist,
            primaryGenre = primaryGenre
        )
    }
}
