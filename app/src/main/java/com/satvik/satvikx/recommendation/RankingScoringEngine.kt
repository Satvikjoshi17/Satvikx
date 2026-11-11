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

    /**
     * Scores and ranks candidates, attaching full probability telemetry to each candidate.
     */
    suspend fun scoreAndRankCandidates(
        candidates: List<TrackEntity>,
        sessionProfile: ShortTermSessionProfile,
        longTermProfile: LongTermProfile,
        recentHistory: List<TrackEntity>
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

            val isSeedArtist = cleanArtist.isNotBlank() && (cleanArtist == activeArtist || activeArtist.contains(cleanArtist) || cleanArtist.contains(activeArtist))
            val isCollaborator = cleanArtist in activeCollaborators

            // 1. SessionMatch (30% weight)
            val rawSessionArtistScore = (sessionProfile.sessionArtistWeights[cleanArtist] ?: 0f) / maxSessionArtistWeight
            val sessionArtistScore = when {
                isSeedArtist -> 1.0f
                isCollaborator -> maxOf(rawSessionArtistScore, 0.85f)
                else -> rawSessionArtistScore
            }
            val sessionGenreScore = trackGenres.map { sessionProfile.sessionGenreWeights[it] ?: 0f }.maxOrNull()?.div(maxSessionGenreWeight) ?: 0f
            val vibeBonus = if (trackVibe == sessionProfile.currentVibe) 1.0f else 0.10f
            val energyProximity = (1.0f - abs(trackEnergy - sessionProfile.currentEnergyLevel)).coerceIn(0.1f, 1.0f)

            val sessionMatch = (0.35f * sessionArtistScore + 0.35f * sessionGenreScore + 0.15f * vibeBonus + 0.15f * energyProximity)
                .coerceIn(0.0f, 1.0f)

            // 2. TasteMatch (25% weight)
            val rawLifetimeArtistScore = (longTermProfile.artistScores[cleanArtist] ?: 0f) / maxLongTermArtistScore
            val rawLifetimeGenreScore = trackGenres.map { longTermProfile.genreScores[it] ?: 0f }.maxOrNull()?.div(maxLongTermGenreScore) ?: 0f
            val rawTasteMatch = (0.60f * rawLifetimeArtistScore + 0.40f * rawLifetimeGenreScore).coerceIn(0.0f, 1.0f)

            // Scale down out-of-vibe historical preferences so opposite genres don't hijack the active session
            val vibeCoherence = if (trackVibe == sessionProfile.currentVibe || sessionProfile.currentVibe == SessionVibeMode.DISCOVERY) 1.0f else 0.20f
            val contextualTasteMatch = rawTasteMatch * vibeCoherence

            val tasteMatch = when {
                isSeedArtist -> maxOf(contextualTasteMatch, 0.90f)
                isCollaborator -> maxOf(contextualTasteMatch, 0.75f)
                else -> contextualTasteMatch
            }

            // 3. CompletionProbability (15% weight)
            val artistTelem = telemetryByArtist[cleanArtist].orEmpty()
            val completions = artistTelem.sumOf { it.completionCount }
            val skips = artistTelem.sumOf { it.skipCount }
            val historicalCompletionRate = if (completions + skips > 0) {
                completions.toFloat() / (completions + skips).toFloat()
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
                isSeedArtist || rawLifetimeArtistScore > 0.5f -> 0.50f
                else -> 0.20f
            }.coerceIn(0.05f, 0.95f)

            // 5. DiscoveryPotential (10% weight)
            val isKnownArtist = isSeedArtist ||
                    isCollaborator ||
                    longTermProfile.artistScores.containsKey(cleanArtist) ||
                    sessionProfile.sessionArtistWeights.containsKey(cleanArtist)
            val isDiscoveryCandidate = !isVaultTrack && !isKnownArtist && !recentIds.contains(track.id)
            val discoveryPotential = if (isDiscoveryCandidate) {
                (0.85f + (sessionProfile.explorationTemperature * 0.15f)).coerceAtMost(1.0f)
            } else if (!isVaultTrack && !recentIds.contains(track.id)) {
                0.40f
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
            val isTrending = track.title.contains("Viral", ignoreCase = true) ||
                    track.title.contains("Hits", ignoreCase = true) ||
                    track.title.contains("Hot", ignoreCase = true) ||
                    track.thumbnailUrl.isNotBlank()
            val trendMomentum = if (isTrending) 0.80f else 0.40f

            // 8. SkipProbability (Minus 20% penalty)
            val isRecentlySkipped = sessionProfile.sessionArtistWeights[cleanArtist]?.let { it < 0f } ?: false
            val isBlacklisted = skippedTrackIds.contains(track.id)
            val energyMismatch = abs(trackEnergy - sessionProfile.currentEnergyLevel)

            var skipProb = (1.0f - completionProbability) * 0.50f + (energyMismatch * 0.30f)
            if (isRecentlySkipped) skipProb += 0.40f
            if (isBlacklisted) skipProb += 0.50f
            val skipProbability = skipProb.coerceIn(0.01f, 0.99f)

            // Final Formula
            val finalScore = (0.30f * sessionMatch) +
                             (0.25f * tasteMatch) +
                             (0.15f * completionProbability) +
                             (0.10f * replayProbability) +
                             (0.10f * discoveryPotential) +
                             (0.05f * freshness) +
                             (0.05f * trendMomentum) -
                             (0.20f * skipProbability)

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

