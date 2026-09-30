package com.satvik.satvikx.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an audio track stored locally or cached in SatvikX.
 */
@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val streamUrl: String? = null,
    val localPath: String? = null,
    val isDownloaded: Boolean = false,
    val addedAtTimestamp: Long = System.currentTimeMillis()
)

/**
 * Determines whether a track is an individual song rather than a long video mix,
 * full album, compilation, mashup, or listicle (e.g., "Top 10 songs", "Best Bollywood songs of 2020", etc.).
 */
fun TrackEntity.isSongOnly(allowMashup: Boolean = false): Boolean {
    // 1. Duration bounds: Standard single songs run between 35 seconds and 7.5 minutes (450 seconds).
    // Videos longer than 450s (7.5 min) or 0s (live/stream) are almost certainly compilations, DJ sets, or albums.
    if (durationSeconds <= 35L || durationSeconds > 450L) return false

    val titleLower = title.lowercase()

    // 2. Mashup detection (strictly excluded unless the user explicitly searched for mashup)
    if (!allowMashup && (
        titleLower.contains("mashup") ||
        titleLower.contains("mash-up") ||
        titleLower.contains("mash up")
    )) {
        return false
    }

    // 3. Explicit listicle regex: "top 10", "top 20", "top 50", "top 100", "top 5", "top 15", "top 3"
    if (Regex("\\btop\\s*\\d+\\b").containsMatchIn(titleLower)) return false

    // 4. Multi-hour / long mix markers: "1 hour", "2 hours", "30 min", "1 hr", "100 songs", "50 songs"
    if (Regex("\\b\\d+\\s*(hour|hr|minute|min)s?\\b").containsMatchIn(titleLower)) return false
    if (Regex("\\b\\d{2,}\\s*(songs|sogs|tracks|hits)\\b").containsMatchIn(titleLower)) return false

    // 5. Jukebox, Album, and Collection markers
    if (titleLower.contains("jukebox") ||
        titleLower.contains("full album") ||
        titleLower.contains("complete album") ||
        titleLower.contains("full audio") ||
        titleLower.contains("audio jukebox") ||
        titleLower.contains("video jukebox") ||
        titleLower.contains("album songs") ||
        titleLower.contains("all songs") ||
        titleLower.contains("songs collection") ||
        titleLower.contains("hit songs collection") ||
        titleLower.contains("collection of") ||
        titleLower.contains("greatest hits") ||
        titleLower.contains("evergreen hits")
    ) return false

    // 6. Non-stop, megamix, dj mix, continuous mix
    if (titleLower.contains("non stop") ||
        titleLower.contains("non-stop") ||
        titleLower.contains("nonstop") ||
        titleLower.contains("megamix") ||
        titleLower.contains("continuous mix") ||
        titleLower.contains("party mix") ||
        titleLower.contains("dj mix") ||
        titleLower.contains("club mix") ||
        titleLower.contains("dj remix collection")
    ) return false

    // 7. "Best of" / "Top songs" compilations (e.g. "best bollywood songs of 2020", "best of 90s", "top songs of 2024")
    if (titleLower.contains("best songs of") ||
        titleLower.contains("best of ") ||
        titleLower.contains("best of:") ||
        titleLower.contains("best bollywood") ||
        titleLower.contains("best hindi") ||
        titleLower.contains("best punjabi") ||
        titleLower.contains("best english") ||
        titleLower.contains("best romantic") ||
        titleLower.contains("best sad") ||
        titleLower.contains("top bollywood") ||
        titleLower.contains("top hindi") ||
        titleLower.contains("top punjabi") ||
        titleLower.contains("top romantic") ||
        titleLower.contains("top songs") ||
        titleLower.contains("top hits") ||
        Regex("\\b(best|top)\\s+.*(songs|sogs|hits|tracks|audio)\\b").containsMatchIn(titleLower)
    ) return false

    // "best of 2020", "best of 90s", etc.
    if (Regex("\\bbest of\\s+(19|20)?\\d{2}\\b").containsMatchIn(titleLower)) return false

    return true
}
