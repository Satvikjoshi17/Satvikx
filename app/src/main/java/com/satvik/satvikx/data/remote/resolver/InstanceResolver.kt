package com.satvik.satvikx.data.remote.resolver

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decentralized round-robin HTTP instance resolver.
 * Cycles through prioritized public instances and automatically blacklists
 * throttled (HTTP 429), failing (5xx), or timed-out instances for 15 minutes.
 */
@Singleton
class InstanceResolver @Inject constructor() {

    companion object {
        private const val TAG = "InstanceResolver"
        private const val BLACKLIST_DURATION_MS = 15 * 60 * 1000L // 15 minutes

        private val DEFAULT_PIPED_INSTANCES = listOf(
            "https://api.piped.private.coffee",
            "https://pipedapi.tokhmi.xyz",
            "https://pipedapi.ducks.party",
            "https://piped.video",
            "https://pipedapi.leptons.xyz"
        )

        private val DEFAULT_INVIDIOUS_INSTANCES = listOf(
            "https://invidious.f5.si",
            "https://invidious.nerdvpn.de",
            "https://invidious.tiekoetter.com",
            "https://yt.chocolatemoo53.com",
            "https://iv.ggtyler.dev"
        )
    }

    private val pipedIndex = AtomicInteger(0)
    private val invidiousIndex = AtomicInteger(0)

    // Maps node URL to blacklist expiry timestamp
    private val blacklistedNodes = ConcurrentHashMap<String, Long>()

    /**
     * Retrieves the next healthy Piped instance candidate in round-robin fashion.
     */
    fun getPipedCandidates(): List<String> {
        cleanExpiredBlacklist()
        val healthy = DEFAULT_PIPED_INSTANCES.filter { !isBlacklisted(it) }
        val pool = if (healthy.isNotEmpty()) healthy else DEFAULT_PIPED_INSTANCES

        val startIndex = (pipedIndex.getAndIncrement() and Int.MAX_VALUE) % pool.size
        return pool.drop(startIndex) + pool.take(startIndex)
    }

    /**
     * Retrieves the next healthy Invidious instance candidate in round-robin fashion.
     */
    fun getInvidiousCandidates(): List<String> {
        cleanExpiredBlacklist()
        val healthy = DEFAULT_INVIDIOUS_INSTANCES.filter { !isBlacklisted(it) }
        val pool = if (healthy.isNotEmpty()) healthy else DEFAULT_INVIDIOUS_INSTANCES

        val startIndex = (invidiousIndex.getAndIncrement() and Int.MAX_VALUE) % pool.size
        return pool.drop(startIndex) + pool.take(startIndex)
    }

    /**
     * Reports an instance failure, blacklisting it for 15 minutes.
     */
    fun reportFailure(baseUrl: String, reason: String) {
        val root = normalizeBaseUrl(baseUrl)
        val expiry = System.currentTimeMillis() + BLACKLIST_DURATION_MS
        blacklistedNodes[root] = expiry
        Log.w(TAG, "Instance blacklisted for 15 min: $root | Reason: $reason")
    }

    /**
     * Reports successful communication, removing any blacklist flags.
     */
    fun reportSuccess(baseUrl: String) {
        val root = normalizeBaseUrl(baseUrl)
        blacklistedNodes.remove(root)
    }

    fun isBlacklisted(baseUrl: String): Boolean {
        val root = normalizeBaseUrl(baseUrl)
        val expiry = blacklistedNodes[root] ?: return false
        return if (System.currentTimeMillis() < expiry) {
            true
        } else {
            blacklistedNodes.remove(root)
            false
        }
    }

    private fun cleanExpiredBlacklist() {
        val now = System.currentTimeMillis()
        val it = blacklistedNodes.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            if (now >= entry.value) {
                it.remove()
            }
        }
    }

    private fun normalizeBaseUrl(url: String): String {
        return url.trim().removeSuffix("/")
    }
}
