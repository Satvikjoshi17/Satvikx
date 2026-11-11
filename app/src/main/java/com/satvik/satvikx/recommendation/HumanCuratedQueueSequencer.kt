package com.satvik.satvikx.recommendation

import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.entity.cleanArtistName
import com.satvik.satvikx.data.local.entity.isSameSong
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Human-curated queue sequencing engine.
 *
 * Enforces Spotify/Apple Music-grade cadence and diversity constraints:
 * 1. No same song repetition (ID and fuzzy canonical title/artist match).
 * 2. No more than 2 consecutive tracks by the exact same artist.
 * 3. No more than 3 consecutive tracks from the exact same genre.
 * 4. 80% familiar / 20% discovery ratio.
 * 5. Every 5th track (index 4, 9, 14, 19...) injects a high-discovery candidate.
 */
@Singleton
class HumanCuratedQueueSequencer @Inject constructor(
    private val vibeClassifier: SessionVibeClassifier
) {

    fun sequenceQueue(
        scoredCandidates: List<CandidateScore>,
        seedTrack: TrackEntity?,
        limit: Int = 15
    ): List<TrackEntity> {
        val result = mutableListOf<TrackEntity>()

        // Partition into familiar vs discovery pools
        val familiarPool = scoredCandidates.filter { !it.isDiscovery }.map { it.track }.toMutableList()
        val discoveryPool = scoredCandidates.filter { it.isDiscovery }.map { it.track }.toMutableList()

        while (result.size < limit && (familiarPool.isNotEmpty() || discoveryPool.isNotEmpty())) {
            val currentIndex = result.size
            // Every 5th track (0-indexed: 4, 9, 14, 19...) is a discovery candidate
            val shouldInjectDiscovery = (currentIndex + 1) % 5 == 0

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
                // If constraints are too strict for remaining items, take the best non-duplicate item
                val fallback = (familiarPool + discoveryPool).firstOrNull { cand ->
                    seedTrack?.let { !cand.isSameSong(it) && cand.id != it.id } ?: true &&
                    result.none { it.isSameSong(cand) }
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
        val iterator = pool.iterator()
        while (iterator.hasNext()) {
            val candidate = iterator.next()

            // 1. Never queue the seed track (by ID or fuzzy match)
            if (seedTrack != null && (candidate.id == seedTrack.id || candidate.isSameSong(seedTrack))) {
                iterator.remove()
                continue
            }

            // 2. No same song repetition in the active queue
            if (currentQueue.any { it.isSameSong(candidate) || it.id == candidate.id }) {
                iterator.remove()
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

            // 4. No more than 3 consecutive tracks from the exact same genre
            if (currentQueue.size >= 3) {
                val g1 = vibeClassifier.detectGenres("${currentQueue[currentQueue.size - 1].title} ${currentQueue[currentQueue.size - 1].artist}").firstOrNull()
                val g2 = vibeClassifier.detectGenres("${currentQueue[currentQueue.size - 2].title} ${currentQueue[currentQueue.size - 2].artist}").firstOrNull()
                val g3 = vibeClassifier.detectGenres("${currentQueue[currentQueue.size - 3].title} ${currentQueue[currentQueue.size - 3].artist}").firstOrNull()
                if (g1 != null && g1 == g2 && g2 == g3 && g1 == candPrimaryGenre) {
                    continue // Violates max 3 consecutive genre rule
                }
            }

            // Passed all constraints!
            iterator.remove()
            return candidate
        }
        return null
    }
}

