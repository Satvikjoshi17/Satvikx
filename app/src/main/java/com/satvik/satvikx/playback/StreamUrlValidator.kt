package com.satvik.satvikx.playback

import android.net.Uri

/**
 * Validates audio stream URLs to detect expired, invalid, or stale session tokens.
 * Protects against infinite buffering caused by expired YouTube/GoogleVideo CDN tokens.
 */
object StreamUrlValidator {

    /**
     * Checks if a stream URL is null, blank, or has expired.
     * YouTube (googlevideo.com) and dynamic proxy endpoints embed an `expire` timestamp in epoch seconds.
     */
    fun isExpired(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        if (url.startsWith("file:") || url.startsWith("content:")) return false

        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase().orEmpty()

            // 1. Check for explicit `expire` query parameter (standard on googlevideo, invidious, piped)
            val expireParam = uri.getQueryParameter("expire")
            if (!expireParam.isNullOrBlank()) {
                val expireEpochSec = expireParam.toLongOrNull() ?: 0L
                val currentEpochSec = System.currentTimeMillis() / 1000L
                // If current time is within 3 minutes of expiration or past it, treat as expired
                if (currentEpochSec >= (expireEpochSec - 180L)) {
                    return true
                }
            } else if (host.contains("googlevideo.com") || url.contains("videoplayback")) {
                // A googlevideo URL without a valid expire parameter is considered ephemeral/expired
                return true
            }
        } catch (_: Exception) {
            return true
        }

        return false
    }
}
