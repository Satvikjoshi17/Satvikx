package com.satvik.satvikx.recommendation

import com.satvik.satvikx.data.local.dao.PlaybackTelemetryDao
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.entity.cleanArtistName
import com.satvik.satvikx.data.local.entity.cleanSongTitle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Enterprise multi-objective probability ranking engine.
 *
 * Implements the exact formula:
 * FinalScore = 0.30 * SessionMatch
 *            + 0.25 * TasteMatch
 *            + 0.15 * CompletionProbability
 *            + 0.10 * ReplayProbability
 *            + 0.10 * DiscoveryPotential
 *            + 0.05 * Freshness
 *            + 0.05 * TrendMomentum
 *            - 0.20 * SkipProbability
 */
@Singleton
class RankingScoringEngine @Inject constructor(
    private val vibeClassifier: SessionVibeClassifier,
    private val playbackTelemetryDao: PlaybackTelemetryDao
) {

    companion object {
        /**
         * Calculates target circadian energy level based on hour of day (0..23).
         * - Late Night (23-04): Soothing, acoustic, lo-fi, ambient (0.35)
         * - Morning (05-08): Gentle acoustic, refreshing, positive vibes (0.50)
         * - Midday (09-16): Focused, upbeat, steady pop/rhythm (0.65)
         * - Evening / Workout (17-20): High energy, gym, dance, trap (0.80)
         * - Wind Down (21-22): Melodic, romantic, relaxed (0.52)
         */
        fun getCircadianTargetEnergy(hourOfDay: Int): Float {
            return when (hourOfDay) {
                in 23..24, in 0..4 -> 0.35f
                in 5..8 -> 0.50f
                in 9..16 -> 0.65f
                in 17..20 -> 0.80f
                in 21..22 -> 0.52f
                else -> 0.60f
            }
        }
    }

    /**
     * Scores and ranks candidates, attaching full probability telemetry to each candidate.
     */
    suspend fun scoreAndRankCandidates(
        candidates: List<TrackEntity>,
        sessionProfile: ShortTermSessionProfile,
        longTermProfile: LongTermProfile,
        recentHistory: List<TrackEntity>,
        forceFresh: Boolean = false,
        nativeRelatedIds: Set<String> = emptySet()
    ): List<CandidateScore> {
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
        val telemetryByArtist = telemetryList.groupBy { it.artist.lowercase(Locale.ROOT).trim() }

        val isNewUser = longTermProfile.isNewUser || (longTermProfile.artistScores.isEmpty() && longTermProfile.vaultTracks.isEmpty() && recentHistory.isEmpty())
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val circadianEnergyTarget = getCircadianTargetEnergy(currentHour)

        val maxLongTermArtistScore = (longTermProfile.artistScores.values.maxOrNull() ?: 1.0f).coerceAtLeast(1.0f)
        val maxLongTermGenreScore = (longTermProfile.genreScores.values.maxOrNull() ?: 1.0f).coerceAtLeast(1.0f)
        val maxSessionArtistWeight = (sessionProfile.sessionArtistWeights.values.maxOrNull() ?: 1.0f).coerceAtLeast(1.0f)
        val maxSessionGenreWeight = (sessionProfile.sessionGenreWeights.values.maxOrNull() ?: 1.0f).coerceAtLeast(1.0f)

        val vaultTrackIds = longTermProfile.vaultTracks.map { it.id }.toSet()
        val heavyRotationIds = longTermProfile.heavyRotationTracks.map { it.id }.toSet()
        val recentIds = recentHistory.map { it.id }.toSet()
        val sessionIds = sessionProfile.sessionTracks.map { it.id }.toSet()

        val activeTrack = sessionProfile.sessionTracks.lastOrNull()
        val activeArtist = activeTrack?.cleanArtistName()?.lowercase(Locale.ROOT)?.trim().orEmpty()
        val activeCollaborators = CandidateRetrievalEngine.COLLABORATOR_MAP[activeArtist].orEmpty()

        return candidates.map { track ->
            val cleanArtist = track.cleanArtistName().lowercase(Locale.ROOT).trim()
            val trackEnergy = vibeClassifier.estimateEnergyLevel(track)
            val trackVibe = vibeClassifier.classifyTrackVibe(track)
            val trackGenres = vibeClassifier.detectGenres("${track.title} ${track.artist}")
            val isNativeWatchNext = nativeRelatedIds.contains(track.id)

            val topLifetimeArtist = longTermProfile.primaryArtist.lowercase(Locale.ROOT).trim()
            val isLifetimeFavorite = topLifetimeArtist.isNotBlank() && (cleanArtist == topLifetimeArtist || cleanArtist.contains(topLifetimeArtist) || topLifetimeArtist.contains(cleanArtist))
            val isSeedArtist = cleanArtist.isNotBlank() && (cleanArtist == activeArtist || activeArtist.contains(cleanArtist) || cleanArtist.contains(activeArtist))
            val isCollaborator = cleanArtist in activeCollaborators

            // 1. SessionMatch (30% weight)
            val rawSessionArtistScore = (sessionProfile.sessionArtistWeights[cleanArtist] ?: 0f) / maxSessionArtistWeight
            val sessionArtistScore = when {
                isSeedArtist -> 1.0f
                isLifetimeFavorite -> 0.95f
                isCollaborator -> maxOf(rawSessionArtistScore, 0.85f)
                else -> rawSessionArtistScore
            }
            val sessionGenreScore = trackGenres.map { sessionProfile.sessionGenreWeights[it] ?: 0f }.maxOrNull()?.div(maxSessionGenreWeight) ?: 0f
            val vibeBonus = if (trackVibe == sessionProfile.currentVibe) 1.0f else 0.10f
            val energyProximity = (1.0f - abs(trackEnergy - sessionProfile.currentEnergyLevel)).coerceIn(0.1f, 1.0f)

            val rawSessionMatch = (0.35f * sessionArtistScore + 0.35f * sessionGenreScore + 0.15f * vibeBonus + 0.15f * energyProximity)
                .coerceIn(0.0f, 1.0f)
            val sessionMatch = if (isNativeWatchNext) maxOf(rawSessionMatch, 0.90f) else rawSessionMatch

            // 2. TasteMatch (25% weight)
            val rawLifetimeArtistScore = (longTermProfile.artistScores[cleanArtist] ?: 0f) / maxLongTermArtistScore
            val rawLifetimeGenreScore = trackGenres.map { longTermProfile.genreScores[it] ?: 0f }.maxOrNull()?.div(maxLongTermGenreScore) ?: 0f

            val rawTasteMatch = if (isNewUser) {
                // Cold start for new users: derive taste from session seed's genre, active vibe, and circadian energy
                val circadianFit = (1.0f - abs(trackEnergy - circadianEnergyTarget)).coerceIn(0.2f, 1.0f)
                (0.40f * sessionGenreScore + 0.35f * vibeBonus + 0.25f * circadianFit).coerceIn(0.1f, 1.0f)
            } else {
                (0.60f * rawLifetimeArtistScore + 0.40f * rawLifetimeGenreScore).coerceIn(0.0f, 1.0f)
            }

            // Scale down out-of-vibe historical preferences so opposite genres don't hijack the active session
            val vibeCoherence = if (isNativeWatchNext || trackVibe == sessionProfile.currentVibe || sessionProfile.currentVibe == SessionVibeMode.DISCOVERY) 1.0f else 0.20f
            val contextualTasteMatch = rawTasteMatch * vibeCoherence

            val tasteMatch = when {
                isNativeWatchNext -> maxOf(contextualTasteMatch, 0.85f)
                isSeedArtist -> maxOf(contextualTasteMatch, 0.90f)
                isLifetimeFavorite -> maxOf(contextualTasteMatch, 0.92f)
                isCollaborator -> maxOf(contextualTasteMatch, 0.75f)
                isNewUser && track.thumbnailUrl.isNotBlank() -> maxOf(contextualTasteMatch, 0.60f)
                else -> contextualTasteMatch
            }

            // 3. CompletionProbability (15% weight)
            val artistTelem = telemetryByArtist[cleanArtist].orEmpty()
            val completions = artistTelem.sumOf { it.completionCount }
            val skips = artistTelem.sumOf { it.skipCount }
            val historicalCompletionRate = if (completions + skips > 0) {
                completions.toFloat() / (completions + skips).toFloat()
            } else if (isNewUser || isNativeWatchNext) {
                0.80f // Optimistic baseline for proven native watch-next tracks and new users
            } else 0.70f

            val durationSweetSpot = if (track.durationSeconds in 130..320) 0.15f else 0.05f
            val completionProbability = (historicalCompletionRate * 0.70f + durationSweetSpot + (energyProximity * 0.15f))
                .coerceIn(0.05f, 0.98f)

            // 4. ReplayProbability (10% weight)
            val isVaultTrack = vaultTrackIds.contains(track.id)
            val isHeavyRotation = heavyRotationIds.contains(track.id)
            val replayProbability = when {
                isHeavyRotation -> 0.92f
                isVaultTrack -> 0.85f
                completions > 1 -> 0.65f
                isNativeWatchNext -> 0.55f
                isSeedArtist || rawLifetimeArtistScore > 0.5f -> 0.50f
                isNewUser && (isCollaborator || sessionGenreScore > 0.5f) -> 0.45f
                else -> 0.20f
            }.coerceIn(0.05f, 0.95f)

            // 5. DiscoveryPotential (10% weight)
            val isKnownArtist = isSeedArtist ||
                    isCollaborator ||
                    longTermProfile.artistScores.containsKey(cleanArtist) ||
                    sessionProfile.sessionArtistWeights.containsKey(cleanArtist)

            val isTrending = track.title.contains("Viral", ignoreCase = true) ||
                    track.title.contains("Hits", ignoreCase = true) ||
                    track.title.contains("Hot", ignoreCase = true) ||
                    track.thumbnailUrl.isNotBlank()

            // For new users and native graph continuations, popular/curated tracks that align with seed's genre/vibe are familiar core tracks,
            // preventing candidate pool starvation, avoiding unfamiliar discovery classification, and building instant trust
            val isFamiliarCoreForNewUser = isNewUser && (isTrending || sessionGenreScore >= 0.5f || vibeBonus >= 0.8f)

            val isDiscoveryCandidate = !isVaultTrack && !isKnownArtist && !recentIds.contains(track.id) && !isFamiliarCoreForNewUser && !isNativeWatchNext
            val discoveryPotential = if (isDiscoveryCandidate) {
                (0.85f + (sessionProfile.explorationTemperature * 0.15f) + (if (forceFresh) 0.10f else 0f)).coerceAtMost(1.0f)
            } else if (!isVaultTrack && !recentIds.contains(track.id)) {
                if (forceFresh) 0.55f else 0.40f
            } else {
                0.08f
            }

            // 6. Freshness (5% weight)
            val freshness = when {
                sessionIds.contains(track.id) -> 0.05f
                recentIds.contains(track.id) -> 0.20f
                else -> 0.95f
            }

            // 7. TrendMomentum (5% weight)
            val trendMomentum = if (isTrending || isNativeWatchNext) 0.80f else 0.40f

            // 8. SkipProbability (Minus 20% penalty)
            val isRecentlySkipped = sessionProfile.sessionArtistWeights[cleanArtist]?.let { it < 0f } ?: false
            val isBlacklisted = skippedTrackIds.contains(track.id)
            val energyMismatch = abs(trackEnergy - sessionProfile.currentEnergyLevel)

            var skipProb = (1.0f - completionProbability) * 0.50f + (energyMismatch * 0.30f)
            if (isRecentlySkipped) skipProb += 0.40f
            if (isBlacklisted) skipProb += 0.50f
            val skipProbability = skipProb.coerceIn(0.01f, 0.99f)

            val isSeedIndian = activeTrack?.let { CandidateRetrievalEngine.isIndianContext(it) } ?: false
            val isCandIndian = CandidateRetrievalEngine.isIndianContext(track)
            val culturalCoherence = if (activeTrack != null) {
                if (isSeedIndian == isCandIndian) 0.10f else -0.22f
            } else 0.0f

            val vibeMismatchPenalty = if (!isNativeWatchNext && trackVibe != sessionProfile.currentVibe && sessionProfile.currentVibe != SessionVibeMode.DISCOVERY) {
                0.20f
            } else 0.0f

            val nativeWatchNextBonus = if (isNativeWatchNext) 0.15f else 0.0f

            // Circadian alignment bonus when there is an active session playing
            val circadianProximity = (1.0f - abs(trackEnergy - circadianEnergyTarget)).coerceIn(0.1f, 1.0f)
            val circadianBonus = if (activeTrack != null) {
                (circadianProximity - 0.50f) * 0.08f // Gentle nudge: +0.04 to -0.032 based on time of day fit
            } else 0.0f

            // Artist fatigue decay in session: prevents the same artist from dominating consecutive sessions
            val sessionArtistOccurrences = sessionProfile.sessionTracks.count { 
                val a = it.cleanArtistName().lowercase(Locale.ROOT).trim()
                a.isNotBlank() && a == cleanArtist
            }
            val artistFatigueDampener = if (activeTrack != null && sessionArtistOccurrences >= 2) {
                (sessionArtistOccurrences - 1) * 0.07f
            } else 0.0f

            // Dynamic exploration jitter on refresh so close scoring tracks rotate organically
            val explorationJitter = if (forceFresh) (kotlin.random.Random.nextFloat() * 0.12f) else 0.0f

            // Final Formula
            val finalScore = (0.30f * sessionMatch) +
                             (0.25f * tasteMatch) +
                             (0.15f * completionProbability) +
                             (0.10f * replayProbability) +
                             (0.10f * discoveryPotential) +
                             (0.05f * freshness) +
                             (0.05f * trendMomentum) -
                             (0.20f * skipProbability) +
                             culturalCoherence +
                             circadianBonus +
                             nativeWatchNextBonus -
                             artistFatigueDampener -
                             vibeMismatchPenalty +
                             explorationJitter

            CandidateScore(
                track = track,
                sessionMatch = sessionMatch,
                tasteMatch = tasteMatch,
                completionProbability = completionProbability,
                replayProbability = replayProbability,
                discoveryPotential = discoveryPotential,
                freshness = freshness,
                trendMomentum = trendMomentum,
                skipProbability = skipProbability,
                finalScore = finalScore,
                isDiscovery = isDiscoveryCandidate
            )
        }.sortedByDescending { it.finalScore }
    }
}

