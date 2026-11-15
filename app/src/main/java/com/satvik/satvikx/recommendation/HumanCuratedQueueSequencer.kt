package com.satvik.satvikx.recommendation

import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.entity.cleanArtistName
import com.satvik.satvikx.data.local.entity.isSameSong
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Human-curated queue sequencing engine.
 *
 * Enforces Spotify/Apple Music-grade cadence, diversity, and vibe flow constraints:
 * 1. No same song repetition (ID and fuzzy canonical title/artist match).
 * 2. No more than 2 consecutive tracks by the exact same artist.
 * 3. Total artist saturation guard: no single artist appears > 3 times across queue (exponential fatigue).
 * 4. No more than 3 consecutive tracks from the exact same genre.
 * 5. Elastic discovery pacing (adaptive based on skip streaks, flow states, and new user status).
 * 6. Smooth DJ energy continuity (prefer energy transitions <= 0.40 between adjacent tracks).
 */
@Singleton
class HumanCuratedQueueSequencer @Inject constructor(
    private val vibeClassifier: SessionVibeClassifier
) {

    fun sequenceQueue(
        scoredCandidates: List<CandidateScore>,
        seedTrack: TrackEntity?,
        limit: Int = 15,
        sessionProfile: ShortTermSessionProfile? = null,
        isNewUser: Boolean = false
    ): List<TrackEntity> {
        val result = mutableListOf<TrackEntity>()

        // Cultural and Vibe Coherence Guard:
        // Filter out scored candidates with severe cultural or genre dissonance relative to the seed track
        val isSeedIndian = seedTrack?.let { CandidateRetrievalEngine.isIndianContext(it) }
        val eligibleScored = scoredCandidates.filter { score ->
            if (isSeedIndian != null) {
                val isCandIndian = CandidateRetrievalEngine.isIndianContext(score.track)
                if (isSeedIndian != isCandIndian) return@filter false
            }
            if (sessionProfile?.currentVibe == SessionVibeMode.DEVOTIONAL) {
                val candVibe = vibeClassifier.classifyTrackVibe(score.track)
                if (candVibe in listOf(SessionVibeMode.GYM_MODE, SessionVibeMode.RAP, SessionVibeMode.PARTY)) return@filter false
            }
            score.finalScore > 0.05f
        }.ifEmpty { scoredCandidates }

        // Partition into familiar vs discovery pools
        val familiarPool = eligibleScored.filter { !it.isDiscovery }.map { it.track }.toMutableList()
        val discoveryPool = eligibleScored.filter { it.isDiscovery }.map { it.track }.toMutableList()

        // Elastic Discovery Cadence calculation:
        // - New users: 90% familiar hits first to build initial trust (every 7th track)
        // - Active skips (>= 2): throttle discovery down to safe comfort zone (every 9th track)
        // - In flow / replaying (>= 1): high receptivity to discovery (every 3rd track)
        // - High exploration temp (> 0.4): more discovery (every 4th track)
        // - Default: standard 1-in-5 (every 5th track: index 4, 9, 14...)
        val discoveryInterval = when {
            isNewUser -> 7
            sessionProfile != null && sessionProfile.consecutiveSkips >= 2 -> 9
            sessionProfile != null && sessionProfile.consecutiveReplays >= 1 -> 3
            sessionProfile != null && sessionProfile.explorationTemperature > 0.4f -> 4
            else -> 5
        }

        while (result.size < limit && (familiarPool.isNotEmpty() || discoveryPool.isNotEmpty())) {
            val currentIndex = result.size
            val shouldInjectDiscovery = (currentIndex + 1) % discoveryInterval == 0

            val chosenTrack = if (shouldInjectDiscovery && discoveryPool.isNotEmpty()) {
                selectBestCandidate(discoveryPool, result, seedTrack)
                    ?: selectBestCandidate(familiarPool, result, seedTrack)
            } else {
                selectBestCandidate(familiarPool, result, seedTrack)
                    ?: selectBestCandidate(discoveryPool, result, seedTrack)
            }

            if (chosenTrack != null) {
                result.add(chosenTrack)
            } else {
                // If strict constraints prevent selection, prioritize non-duplicate items that don't violate artist saturation
                val fallback = (familiarPool + discoveryPool).firstOrNull { cand ->
                    val a = cand.cleanArtistName().lowercase(Locale.ROOT).trim()
                    (seedTrack == null || (!cand.isSameSong(seedTrack) && cand.id != seedTrack.id)) &&
                    result.none { it.isSameSong(cand) || it.id == cand.id } &&
                    (a.isBlank() || result.count { it.cleanArtistName().lowercase(Locale.ROOT).trim() == a } < 3)
                } ?: (familiarPool + discoveryPool).firstOrNull { cand ->
                    (seedTrack == null || (!cand.isSameSong(seedTrack) && cand.id != seedTrack.id)) &&
                    result.none { it.isSameSong(cand) || it.id == cand.id }
                }

                if (fallback != null) {
                    familiarPool.remove(fallback)
                    discoveryPool.remove(fallback)
                    result.add(fallback)
                } else {
                    break
                }
            }
        }

        return result
    }

    private fun selectBestCandidate(
        pool: MutableList<TrackEntity>,
        currentQueue: List<TrackEntity>,
        seedTrack: TrackEntity?
    ): TrackEntity? {
        // Prune seed tracks and tracks already present in currentQueue
        pool.removeAll { cand ->
            (seedTrack != null && (cand.id == seedTrack.id || cand.isSameSong(seedTrack))) ||
            currentQueue.any { it.isSameSong(cand) || it.id == cand.id }
        }

        // Pass 1: Strict constraints + Smooth Energy Transition (energy delta <= 0.40f)
        var chosen = findCandidate(pool, currentQueue, seedTrack, maxEnergyDelta = 0.40f)
        // Pass 2: Relaxed energy transition if smooth path exhausted
        if (chosen == null) {
            chosen = findCandidate(pool, currentQueue, seedTrack, maxEnergyDelta = 1.0f)
        }

        if (chosen != null) {
            pool.remove(chosen)
        }
        return chosen
    }

    private fun findCandidate(
        pool: List<TrackEntity>,
        currentQueue: List<TrackEntity>,
        seedTrack: TrackEntity?,
        maxEnergyDelta: Float
    ): TrackEntity? {
        val lastTrack = currentQueue.lastOrNull() ?: seedTrack
        val lastEnergy = lastTrack?.let { vibeClassifier.estimateEnergyLevel(it) } ?: 0.60f

        for (candidate in pool) {
            // 1. Never queue the seed track (by ID or fuzzy match)
            if (seedTrack != null && (candidate.id == seedTrack.id || candidate.isSameSong(seedTrack))) {
                continue
            }

            // 2. No same song repetition in the active queue
            if (currentQueue.any { it.isSameSong(candidate) || it.id == candidate.id }) {
                continue
            }

            val candArtist = candidate.cleanArtistName().lowercase(Locale.ROOT).trim()
            val candGenres = vibeClassifier.detectGenres("${candidate.title} ${candidate.artist}")
            val candPrimaryGenre = candGenres.firstOrNull() ?: "other"

            // 3. No more than 2 consecutive tracks by the exact same artist
            if (currentQueue.size >= 2) {
                val last1Artist = currentQueue[currentQueue.size - 1].cleanArtistName().lowercase(Locale.ROOT).trim()
                val last2Artist = currentQueue[currentQueue.size - 2].cleanArtistName().lowercase(Locale.ROOT).trim()
                if (last1Artist == last2Artist && last1Artist == candArtist && candArtist.isNotBlank()) {
                    continue // Violates max 2 consecutive artist rule
                }
            }

            // 4. Queue-wide Artist Saturation Guard (Exponential fatigue prevention)
            // No single artist should appear more than 3 times across the upcoming queue
            if (candArtist.isNotBlank()) {
                val artistCount = currentQueue.count { it.cleanArtistName().lowercase(Locale.ROOT).trim() == candArtist }
                if (artistCount >= 3) {
                    continue // Exceeds max 3 tracks per artist in queue
                }
            }

            // 5. No more than 3 consecutive tracks from the exact same genre
            if (currentQueue.size >= 3) {
                val g1 = vibeClassifier.detectGenres("${currentQueue[currentQueue.size - 1].title} ${currentQueue[currentQueue.size - 1].artist}").firstOrNull()
                val g2 = vibeClassifier.detectGenres("${currentQueue[currentQueue.size - 2].title} ${currentQueue[currentQueue.size - 2].artist}").firstOrNull()
                val g3 = vibeClassifier.detectGenres("${currentQueue[currentQueue.size - 3].title} ${currentQueue[currentQueue.size - 3].artist}").firstOrNull()
                if (g1 != null && g1 == g2 && g2 == g3 && g1 == candPrimaryGenre) {
                    continue // Violates max 3 consecutive genre rule
                }
            }

            // 6. Smooth DJ energy continuity
            val candEnergy = vibeClassifier.estimateEnergyLevel(candidate)
            val energyDelta = abs(candEnergy - lastEnergy)
            if (energyDelta > maxEnergyDelta) {
                continue // Energy jump is too abrupt, check next candidate
            }

            return candidate
        }
        return null
    }
}


