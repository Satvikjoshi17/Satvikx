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

/**
 * Normalizes a song title to its core canonical representation:
 * - Strips bracketed tags: (Official Video), [Audio], (Lyrics), etc.
 * - Strips YouTube channel / artist prefix or suffix if separated by " - ", " | ", " : ", " ~ ".
 * - Strips common buzzwords: "official audio", "lyric video", "full song", "4k", "remastered", etc.
 * - Strips punctuation and excessive whitespace.
 */
fun TrackEntity.cleanSongTitle(): String {
    return cleanTrackTitle(title, artist)
}

fun TrackEntity.cleanArtistName(): String {
    return cleanArtist(artist)
}

fun TrackEntity.canonicalSongKey(): String {
    val cleanT = cleanSongTitle().replace(" ", "")
    val cleanA = cleanArtistName().replace(" ", "")
    return if (cleanT.isNotBlank()) "${cleanT}_$cleanA" else id
}

fun cleanArtist(rawArtist: String): String {
    if (rawArtist.isBlank()) return ""
    return rawArtist
        .replace(" - Topic", "", ignoreCase = true)
        .replace(Regex("""(?i)\b(vevo|official|music|channel|records|entertainment|t-series|sony|zee|tips)\b"""), " ")
        .replace(Regex("""[^a-zA-Z0-9\s]"""), " ")
        .trim().lowercase().replace(Regex("""\s+"""), " ")
}

fun cleanTrackTitle(rawTitle: String, rawArtist: String = ""): String {
    if (rawTitle.isBlank()) return ""
    var t = rawTitle

    // 1. Remove bracketed / parenthetical tags: (Official Video), [Audio], [Lyrics], (from "movie"), etc.
    t = t.replace(Regex("""(?i)\s*[\(\[\{][^\)\]\}]*(?:official|audio|video|music\s*video|visualizer|lyric(?:s)?|full\s*song|full\s*audio|full\s*video|audio\s*song|video\s*song|hd|4k|1080p|remaster(?:ed)?|live|slowed|8d|from\s+["']|with\s+)[^\)\]\}]*[\)\]\}]"""), " ")
    // Remove any remaining empty or small brackets
    t = t.replace(Regex("""\s*[\(\[\{][^\)\]\}]{0,25}[\)\]\}]"""), " ")

    // 2. If title contains dividers like " - ", " | ", " : ", " ~ ", determine which side is the song title
    val separators = listOf(" - ", " — ", " – ", " | ", " : ", " ~ ")
    for (sep in separators) {
        if (t.contains(sep)) {
            val parts = t.split(sep).map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size >= 2) {
                val cleanArt = cleanArtist(rawArtist)
                val p0 = parts[0].lowercase()
                val p1 = parts[1].lowercase()

                if (cleanArt.isNotBlank() && (p0.contains(cleanArt) || cleanArt.contains(p0))) {
                    t = parts.subList(1, parts.size).joinToString(" ")
                    break
                } else if (cleanArt.isNotBlank() && (p1.contains(cleanArt) || cleanArt.contains(p1))) {
                    t = parts[0]
                    break
                } else {
                    t = if (parts[0].split(" ").size <= 2 && parts[1].split(" ").size > 1) parts[1] else parts[0]
                    break
                }
            }
        }
    }

    // 3. Remove common standalone buzzwords and featuring artists
    t = t.replace(Regex("""(?i)\b(official\s*(music\s*)?video|official\s*audio|official\s*visualizer|lyric\s*video|lyrics|audio\s*song|video\s*song|full\s*(video|audio|song)|audio|video)\b"""), " ")
    t = t.replace(Regex("""(?i)\b(feat\.?|ft\.?|featuring)\s+[^,\-\|\(\)]+"""), " ")

    // 4. Strip non-alphanumeric, keeping standard letters & numbers
    t = t.replace(Regex("""[^a-zA-Z0-9\s]"""), " ")

    // 5. Collapse whitespace and lowercase
    return t.trim().lowercase().replace(Regex("""\s+"""), " ")
}

/**
 * Determines whether two TrackEntity objects represent the SAME underlying song,
 * effectively catching duplicates across different YouTube channels, uploaders,
 * lyric videos, audio-only tracks, and video clips.
 */
fun TrackEntity.isSameSong(other: TrackEntity): Boolean {
    if (this.id == other.id) return true

    val cleanA = this.cleanSongTitle()
    val cleanB = other.cleanSongTitle()

    if (cleanA.isBlank() || cleanB.isBlank()) {
        return this.title.equals(other.title, ignoreCase = true)
    }

    // 1. Exact cleaned title match (e.g. "blinding lights" == "blinding lights")
    if (cleanA == cleanB) {
        if (this.durationSeconds > 0 && other.durationSeconds > 0) {
            val diff = Math.abs(this.durationSeconds - other.durationSeconds)
            if (diff <= 55) return true
            return diff <= 90
        }
        return true
    }

    // 2. Compressed alphanumeric match (e.g. "die with a smile" -> "diewithasmile")
    val alphaA = cleanA.replace(" ", "")
    val alphaB = cleanB.replace(" ", "")
    if (alphaA.isNotEmpty() && alphaA == alphaB) {
        return true
    }

    // 3. Substring / Prefix match with high overlap
    val minLen = minOf(alphaA.length, alphaB.length)
    val maxLen = maxOf(alphaA.length, alphaB.length)
    if (minLen >= 5 && maxLen > 0) {
        val ratio = minLen.toFloat() / maxLen.toFloat()
        if (ratio >= 0.75f && (alphaA.contains(alphaB) || alphaB.contains(alphaA))) {
            val durDiff = if (this.durationSeconds > 0 && other.durationSeconds > 0) {
                Math.abs(this.durationSeconds - other.durationSeconds)
            } else 0
            if (durDiff <= 45) {
                return true
            }
        }
    }

    // 4. Token overlap match (Jaccard similarity on significant words >= 3 chars)
    val wordsA = cleanA.split(" ").filter { it.length >= 3 }.toSet()
    val wordsB = cleanB.split(" ").filter { it.length >= 3 }.toSet()
    if (wordsA.isNotEmpty() && wordsB.isNotEmpty()) {
        val intersection = wordsA.intersect(wordsB).size
        val union = wordsA.union(wordsB).size
        val jaccard = intersection.toFloat() / union.toFloat()
        if (jaccard >= 0.8f) {
            val durDiff = if (this.durationSeconds > 0 && other.durationSeconds > 0) {
                Math.abs(this.durationSeconds - other.durationSeconds)
            } else 0
            if (durDiff <= 45) return true
        }
    }

    return false
}

/**
 * Deduplicates a list of tracks so that no two tracks represent the same underlying song,
 * keeping the first high-quality occurrence.
 */
fun List<TrackEntity>.distinctSongs(): List<TrackEntity> {
    val result = mutableListOf<TrackEntity>()
    for (track in this) {
        if (result.none { it.isSameSong(track) }) {
            result.add(track)
        }
    }
    return result
}

/**
 * Filters out any track that represents the same underlying song as baseTrack.
 */
fun List<TrackEntity>.filterNotSameSong(baseTrack: TrackEntity): List<TrackEntity> {
    return this.filter { !it.isSameSong(baseTrack) }
}
