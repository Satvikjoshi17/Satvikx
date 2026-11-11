package com.satvik.satvikx.recommendation

import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.entity.cleanArtistName
import com.satvik.satvikx.data.local.entity.isSameSong
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time adaptive session state manager.
 *
 * Implements instant event-driven feedback loops:
 * - SKIPS: Penalizes artist score, lowers genre confidence, raises exploration temperature.
 * - REPLAYS: Boosts artist & session affinity.
 * - LIKES: Strongly increases artist weight and triggers discovery exposure.
 * - COMPLETIONS: Increases ranking confidence and cools exploration temperature.
 *
 * Detects adaptation triggers:
 * - 3 skips
 * - 2 consecutive replays
 * - major genre switch
 * - like event
 */
@Singleton
class AdaptiveSessionManager @Inject constructor(
    private val vibeClassifier: SessionVibeClassifier
) {

    private val lock = Any()

    private val sessionTracks = mutableListOf<TrackEntity>()
    private val sessionArtistWeights = mutableMapOf<String, Float>()
    private val sessionGenreWeights = mutableMapOf<String, Float>()

    private var currentVibe: SessionVibeMode = SessionVibeMode.DISCOVERY
    private var currentEnergyLevel: Float = 0.60f
    private var consecutiveSkips: Int = 0
    private var consecutiveReplays: Int = 0
    private var explorationTemperature: Float = 0.20f
    private var lastPlayedTrack: TrackEntity? = null

    /**
     * Snapshots the active session profile.
     */
    fun getSessionProfile(): ShortTermSessionProfile = synchronized(lock) {
        ShortTermSessionProfile(
            sessionTracks = sessionTracks.toList(),
            currentVibe = currentVibe,
            currentEnergyLevel = currentEnergyLevel,
            sessionArtistWeights = sessionArtistWeights.toMap(),
            sessionGenreWeights = sessionGenreWeights.toMap(),
            consecutiveSkips = consecutiveSkips,
            consecutiveReplays = consecutiveReplays,
            explorationTemperature = explorationTemperature,
            lastInteractionTimeMs = System.currentTimeMillis()
        )
    }

    /**
     * Called whenever a new track starts playing.
     * Evaluates replays, updates energy curves, and checks for major genre shifts.
     */
    fun onTrackStarted(track: TrackEntity): SessionAdaptationTrigger? = synchronized(lock) {
        val last = lastPlayedTrack
        lastPlayedTrack = track

        // Manage bounded session history (last 20 tracks)
        sessionTracks.add(track)
        if (sessionTracks.size > 20) sessionTracks.removeAt(0)

        val trackVibe = vibeClassifier.classifyTrackVibe(track)
        val trackEnergy = vibeClassifier.estimateEnergyLevel(track)
        val cleanArtist = track.cleanArtistName().lowercase(Locale.ROOT).trim()
        val genres = vibeClassifier.detectGenres("${track.title} ${track.artist}")

        // Seed positive session engagement for active artist & genres
        sessionArtistWeights[cleanArtist] = (sessionArtistWeights[cleanArtist] ?: 0f) + 8.0f
        genres.forEach { g -> sessionGenreWeights[g] = (sessionGenreWeights[g] ?: 0f) + 4.0f }

        // Exponential moving average for energy level
        currentEnergyLevel = (0.65f * currentEnergyLevel) + (0.35f * trackEnergy)

        // Check for replay
        var trigger: SessionAdaptationTrigger? = null
        if (last != null && track.isSameSong(last)) {
            consecutiveReplays++
            consecutiveSkips = 0
            sessionArtistWeights[cleanArtist] = (sessionArtistWeights[cleanArtist] ?: 0f) + 3.0f
            if (consecutiveReplays >= 2) {
                trigger = SessionAdaptationTrigger.TWO_REPLAYS
            }
        } else {
            consecutiveReplays = 0
        }

        // Check for major genre switch
        if (trackVibe != currentVibe && trackVibe != SessionVibeMode.DISCOVERY) {
            val previousVibe = currentVibe
            currentVibe = trackVibe
            if (previousVibe != SessionVibeMode.DISCOVERY) {
                trigger = SessionAdaptationTrigger.MAJOR_GENRE_SWITCH
            }
        }

        trigger
    }

    /**
     * Real-time learning handler on track skip.
     * Lowers artist score, lowers genre confidence, increases diversity.
     */
    fun onTrackSkipped(track: TrackEntity, listenedDurationMs: Long): SessionAdaptationTrigger? = synchronized(lock) {
        consecutiveSkips++
        consecutiveReplays = 0

        val cleanArtist = track.cleanArtistName().lowercase(Locale.ROOT).trim()
        val genres = vibeClassifier.detectGenres("${track.title} ${track.artist}")

        // Immediate negative feedback penalty
        sessionArtistWeights[cleanArtist] = (sessionArtistWeights[cleanArtist] ?: 0f) - 4.5f
        genres.forEach { g ->
            sessionGenreWeights[g] = (sessionGenreWeights[g] ?: 0f) - 3.0f
        }

        // Increase diversity exploration temperature
        explorationTemperature = (explorationTemperature + 0.15f).coerceAtMost(0.90f)

        if (consecutiveSkips >= 3) {
            consecutiveSkips = 0 // Reset after trigger to avoid infinite cascading loops
            SessionAdaptationTrigger.THREE_SKIPS
        } else {
            null
        }
    }

    /**
     * Real-time learning handler on track like event.
     * Strongly increases artist weight and triggers discovery exposure.
     */
    fun onTrackLiked(track: TrackEntity): SessionAdaptationTrigger = synchronized(lock) {
        val cleanArtist = track.cleanArtistName().lowercase(Locale.ROOT).trim()
        val genres = vibeClassifier.detectGenres("${track.title} ${track.artist}")

        // Strongly increase artist weight (+6.0f)
        sessionArtistWeights[cleanArtist] = (sessionArtistWeights[cleanArtist] ?: 0f) + 6.0f
        genres.forEach { g ->
            sessionGenreWeights[g] = (sessionGenreWeights[g] ?: 0f) + 3.5f
        }

        SessionAdaptationTrigger.LIKE_EVENT
    }

    /**
     * Real-time learning handler on track replay.
     */
    fun onTrackReplayed(track: TrackEntity): SessionAdaptationTrigger? = synchronized(lock) {
        consecutiveReplays++
        consecutiveSkips = 0

        val cleanArtist = track.cleanArtistName().lowercase(Locale.ROOT).trim()
        sessionArtistWeights[cleanArtist] = (sessionArtistWeights[cleanArtist] ?: 0f) + 4.0f

        if (consecutiveReplays >= 2) {
            consecutiveReplays = 0
            SessionAdaptationTrigger.TWO_REPLAYS
        } else {
            null
        }
    }

    /**
     * Real-time learning handler on track completion.
     */
    fun onTrackCompleted(track: TrackEntity) = synchronized(lock) {
        consecutiveSkips = 0
        val cleanArtist = track.cleanArtistName().lowercase(Locale.ROOT).trim()
        sessionArtistWeights[cleanArtist] = (sessionArtistWeights[cleanArtist] ?: 0f) + 2.5f

        // Cool down exploration temperature towards optimal baseline
        explorationTemperature = (explorationTemperature - 0.05f).coerceAtLeast(0.15f)
    }

    fun resetSession() = synchronized(lock) {
        sessionTracks.clear()
        sessionArtistWeights.clear()
        sessionGenreWeights.clear()
        currentVibe = SessionVibeMode.DISCOVERY
        currentEnergyLevel = 0.60f
        consecutiveSkips = 0
        consecutiveReplays = 0
        explorationTemperature = 0.20f
        lastPlayedTrack = null
    }
}

