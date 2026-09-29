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
 * full album, compilation, or listicle (e.g., "Top 10 songs", "Best Bollywood songs of 2020", etc.).
 */
fun TrackEntity.isSongOnly(): Boolean {
    // 1. Duration filter: Tracks longer than 11 minutes (660 seconds) are almost always compilations,
    // DJ sets, podcasts, or full albums.
    if (durationSeconds > 660L) return false

    val titleLower = title.lowercase()

    // 2. Explicit listicle regex: "top 10", "top 20", "top 50", "top 100", "top 5", "top 15"
    if (Regex("\\btop\\s*\\d+\\b").containsMatchIn(titleLower)) return false

    // 3. Multi-hour / long mix markers: "1 hour", "2 hours", "3 hrs", "100 songs", "50 songs"
    if (Regex("\\b\\d+\\s*(hour|hr)s?\\b").containsMatchIn(titleLower)) return false
    if (Regex("\\b\\d{2,}\\s*songs?\\b").containsMatchIn(titleLower)) return false

    // 4. Jukebox & full album markers
    if (titleLower.contains("jukebox") ||
        titleLower.contains("full album") ||
        titleLower.contains("complete album") ||
        titleLower.contains("audio jukebox") ||
        titleLower.contains("video jukebox")
    ) return false

    // 5. Non-stop and collection markers
    if (titleLower.contains("non stop") ||
        titleLower.contains("non-stop") ||
        titleLower.contains("nonstop") ||
        titleLower.contains("songs collection") ||
        titleLower.contains("hit songs collection") ||
        titleLower.contains("megamix")
    ) return false

    // 6. "Best of" / Year compilation markers (e.g. "best bollywood songs of 2020", "best of 90s", "best songs of")
    if (titleLower.contains("best songs of") ||
        titleLower.contains("best bollywood") ||
        titleLower.contains("best hindi songs") ||
        titleLower.contains("best punjabi songs") ||
        titleLower.contains("greatest hits of") ||
        Regex("\\b(best|top)\\s+.*(songs|sogs|hits|tracks)\\s+of\\b").containsMatchIn(titleLower)
    ) return false

    // "best of 2020", "best of 90s", etc.
    if (Regex("\\bbest of\\s+(19|20)?\\d{2}\\b").containsMatchIn(titleLower)) return false

    // If it contains "best of" and exceeds 6 minutes (360s) or has 0s duration, it is an artist collection mix
    if (titleLower.contains("best of") && (durationSeconds > 360L || durationSeconds == 0L)) {
        return false
    }

    // Long mashup mixes (> 6 minutes)
    if (titleLower.contains("mashup") && (durationSeconds > 360L || durationSeconds == 0L)) {
        return false
    }

    // "greatest hits" longer than 6 minutes
    if (titleLower.contains("greatest hits") && (durationSeconds > 360L || durationSeconds == 0L)) {
        return false
    }

    // "all songs"
    if (titleLower.contains("all songs") && (durationSeconds > 300L || durationSeconds == 0L)) {
        return false
    }

    return true
}
