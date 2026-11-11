package com.satvik.satvikx.recommendation

import com.satvik.satvikx.data.local.entity.TrackEntity

/**
 * Recognized acoustic and mental session vibes.
 * Used by the short-term session engine to cluster context and energy.
 */
enum class SessionVibeMode(val displayName: String, val baseEnergy: Float) {
    GYM_MODE("GYM PROTOCOL", 0.90f),
    NIGHT_DRIVE("NIGHT DRIVE", 0.70f),
    LOFI_FOCUS("LO-FI FOCUS", 0.25f),
    ROMANTIC("ROMANTIC VIBE", 0.50f),
    PUNJABI("PUNJABI SWAG", 0.85f),
    RAP("HIP HOP / RAP", 0.80f),
    DEVOTIONAL("DEVOTIONAL / PEACE", 0.30f),
    PARTY("PARTY / EDM", 0.95f),
    DISCOVERY("OPEN RADAR", 0.60f)
}

/**
 * Lifetime user profile modeled with exponential time decay:
 * Weight(t) = exp(-deltaDays / 30.0)
 * Captures historical affinities, lifetime favorites, and permanent anchors.
 */
data class LongTermProfile(
    val artistScores: Map<String, Float> = emptyMap(),
    val genreScores: Map<String, Float> = emptyMap(),
    val topArtists: List<String> = emptyList(),
    val topGenres: List<String> = emptyList(),
    val vaultTracks: List<TrackEntity> = emptyList(),
    val heavyRotationTracks: List<TrackEntity> = emptyList(),
    val primaryArtist: String = "Arijit Singh",
    val secondaryArtist: String = "The Weeknd",
    val primaryGenre: String = "acoustic",
    val profileGeneratedTimestamp: Long = System.currentTimeMillis()
)

/**
 * In-memory real-time session profile reflecting the user's active listening context.
 * Weighted at 55% relative to the 30% long-term profile.
 */
data class ShortTermSessionProfile(
    val sessionTracks: List<TrackEntity> = emptyList(),
    val currentVibe: SessionVibeMode = SessionVibeMode.DISCOVERY,
    val currentEnergyLevel: Float = 0.60f,
    val sessionArtistWeights: Map<String, Float> = emptyMap(),
    val sessionGenreWeights: Map<String, Float> = emptyMap(),
    val consecutiveSkips: Int = 0,
    val consecutiveReplays: Int = 0,
    val explorationTemperature: Float = 0.20f,
    val lastInteractionTimeMs: Long = System.currentTimeMillis()
)

/**
 * Deconstructed probability signals and multi-objective components for a candidate track.
 */
data class CandidateScore(
    val track: TrackEntity,
    val sessionMatch: Float,
    val tasteMatch: Float,
    val completionProbability: Float,
    val replayProbability: Float,
    val discoveryPotential: Float,
    val freshness: Float,
    val trendMomentum: Float,
    val skipProbability: Float,
    val finalScore: Float,
    val isDiscovery: Boolean
)

/**
 * Events that trigger real-time recalculation of the upcoming playback queue.
 */
enum class SessionAdaptationTrigger {
    THREE_SKIPS,
    TWO_REPLAYS,
    MAJOR_GENRE_SWITCH,
    LIKE_EVENT,
    CADENCE_REFILL
}

