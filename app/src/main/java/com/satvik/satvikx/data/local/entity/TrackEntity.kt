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
    // 1. Duration bounds: Standard single songs run between 35 seconds and 12 minutes (720 seconds).
    // Videos longer than 720s (12 min) are almost certainly compilations, DJ sets, or full albums.
    // Zero-duration entries (live broadcasts or unparsed video stream tags) are allowed unless compilation keywords match.
    if (durationSeconds > 720L || (durationSeconds in 1L..35L)) return false

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

    // 5. Jukebox, Album, and Collection markers (note: "full audio" is a single song upload marker, NOT a compilation!)
    if (titleLower.contains("jukebox") ||
        titleLower.contains("full album") ||
        titleLower.contains("complete album") ||
        titleLower.contains("audio jukebox") ||
        titleLower.contains("video jukebox") ||
        titleLower.contains("album songs") ||
        titleLower.contains("all songs of") ||
        titleLower.contains("all songs from") ||
        titleLower.contains("all songs jukebox") ||
        titleLower.contains("songs collection") ||
        titleLower.contains("hit songs collection") ||
        titleLower.contains("collection of") ||
        titleLower.contains("greatest hits collection") ||
        titleLower.contains("greatest hits album") ||
        titleLower.contains("greatest hits jukebox") ||
        titleLower.contains("evergreen hits collection")
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

    // 7. "Best of" / "Top songs" compilations (e.g. "best bollywood songs of 2020", "best of 90s")
    if (titleLower.contains("best songs of") ||
        titleLower.contains("best bollywood songs") ||
        titleLower.contains("best hindi songs") ||
        titleLower.contains("best punjabi songs") ||
        titleLower.contains("best romantic songs") ||
        titleLower.contains("best sad songs") ||
        titleLower.contains("top bollywood songs") ||
        titleLower.contains("top hindi songs") ||
        titleLower.contains("top punjabi songs") ||
        titleLower.contains("top romantic songs") ||
        Regex("\\b(best|top)\\s+\\d+\\s*(songs|hits|tracks)\\b").containsMatchIn(titleLower) ||
        Regex("\\b(best|top)\\s+(of|bollywood|hindi|punjabi|english|romantic|sad|dance|party)\\s+(songs|hits|tracks)\\b").containsMatchIn(titleLower) ||
        Regex("\\bbest of\\s+(19|20)?\\d{2}\\b").containsMatchIn(titleLower) ||
        Regex("\\bbest of\\s+([a-zA-Z\\s]{2,20})\\s+(songs|hits|collection|jukebox)\\b").containsMatchIn(titleLower)
    ) return false

    // 8. Non-music video filters (workouts, exercises, tutorials, vlogs, reviews, gaming, podcasts, movie trailers)
    val nonMusicKeywords = listOf(
        "workout routine", "gym workout", "home workout", "full body workout", "abs workout",
        "chest workout", "bicep workout", "biceps workout", "tricep workout", "leg workout",
        "back workout", "shoulder workout", "exercise routine", "exercises for", "warm up routine",
        "stretching routine", "fat burn", "weight loss", "calisthenics routine", "bodybuilding routine",
        "sets and reps", "sets & reps", "training routine", "workout for beginners", "how to build",
        "how to get", "fitness routine", "workout plan", "tutorial", "how to", "guide for beginners",
        "walkthrough", "step by step", "full course", "gameplay", "playthrough", "unboxing",
        "reaction video", "reaction to", "podcast episode", "full podcast", "interview with",
        "official trailer", "teaser trailer", "full movie", "behind the scenes"
    )
    if (nonMusicKeywords.any { titleLower.contains(it) }) return false

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

fun TrackEntity.coreSongTitle(): String {
    val clean = cleanSongTitle()
    if (clean.isBlank()) return ""
    val words = clean.split(" ").filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> ""
        words.size == 1 -> words[0]
        words.size in 2..3 -> words.joinToString(" ")
        else -> words.take(2).joinToString(" ")
    }
}

fun TrackEntity.canonicalSongKey(): String {
    val cleanT = cleanSongTitle().replace(" ", "")
    val cleanA = cleanArtistName().replace(" ", "")
    return if (cleanT.isNotBlank()) "${cleanT}_$cleanA" else id
}

// Precompiled Static Regular Expressions (Zero Runtime Compilation Allocation -> Zero ANR)
private val REGEX_ARTIST_NOISE = Regex("""(?i)\b(vevo|official|music|channel|records|entertainment|t-series|sony|zee|tips|audio|visualizer|production|productions|media|digital)\b""")
private val REGEX_RECORD_LABELS = Regex("""(?i)\b(t-series|tseries|sony\s*music(\s*india)?|zee\s*music(\s*company)?|yrf|yash\s*raj\s*films|saregama|speed\s*records|tips\s*(official)?|tips\s*music|vevo|white\s*hill\s*music|geet\s*mp3|desi\s*music\s*factory|eros\s*now|times\s*music|aditya\s*music|lahari\s*music|anand\s*audio|spinnin\s*records|ultra\s*music|universal\s*music|warner\s*music|columbia\s*records|atlantic\s*records|def\s*jam)\b""")
private val REGEX_BRACKETS_TAGS = Regex("""(?i)\s*[\(\[\{][^\)\]\}]*(?:official|audio|video|music\s*video|visualizer|lyric(?:s|al)?|full\s*(?:song|audio|video)|audio\s*song|video\s*song|hd|4k|8k|1080p|remaster(?:ed)?|live|slowed|reverb|8d|from\s+["']|with\s+|feat\.?|ft\.?|prod\.?|cover|unplugged|acoustic|dance\s*mix|remix|club\s*mix|reprise|version|edit|mix|soundtrack|ost)[^\)\]\}]*[\)\]\}]""")
private val REGEX_SMALL_BRACKETS = Regex("""\s*[\(\[\{][^\)\]\}]{0,35}[\)\]\}]""")
private val REGEX_SEPARATORS = Regex("""\s*[-—–|•~//]\s*|\s*:\s+""")
private val REGEX_BUZZWORDS = Regex("""(?i)\b(dance\s*mix|club\s*mix|dj\s*mix|remix|acoustic\s*version|acoustic\s*cover|unplugged|slowed\s*reverb|slowed|reverb|sped\s*up|speed\s*up|8d\s*audio|cover|duet|lofi\s*flip|lofi\s*remix|lofi\s*mix|lofi|karaoke|instrumental|teaser|trailer|motion\s*poster|short\s*clip|official\s*(music\s*)?video|official\s*audio|official\s*visualizer|lyric(?:al)?\s*video|lyrics|audio\s*song|video\s*song|full\s*(video|audio|song)|official\s+audio|official\s+video|new\s*song|hit\s*song)\b""")
private val REGEX_FEAT = Regex("""(?i)\b(feat\.?|ft\.?|featuring)\b.*""")
private val REGEX_NON_ALPHANUM = Regex("""[^a-zA-Z0-9\s]""")
private val REGEX_WHITESPACE = Regex("""\s+""")

fun isRecordLabelOrChannel(name: String): Boolean {
    val lower = name.trim().lowercase(java.util.Locale.ROOT)
    return lower.isBlank() ||
        lower == "unknown artist" ||
        REGEX_RECORD_LABELS.containsMatchIn(lower) ||
        lower.contains("vevo") ||
        lower.contains("t-series") ||
        lower.contains("tseries") ||
        lower.contains("sony music") ||
        lower.contains("zee music") ||
        lower.contains("saregama") ||
        lower.contains("speed records") ||
        lower.contains("tips official") ||
        lower.contains("tips music") ||
        lower.contains("white hill") ||
        lower.contains("geet mp3") ||
        lower.contains("desi music factory") ||
        lower.contains("yrf") ||
        lower.contains("records") ||
        lower.contains("entertainment") ||
        lower.contains("channel")
}

/**
 * Extracts true musical performers/artists for a track, bypassing record label names,
 * YouTube distributor accounts, and falls back to artist metadata embedded in the title.
 */
fun TrackEntity.getEffectiveArtists(): List<String> {
    val candidates = mutableListOf<String>()

    // 1. Try splitting the direct artist field
    if (!isRecordLabelOrChannel(artist)) {
        val split = artist.split(",", "&", "feat.", "ft.", "/", ";", "•")
            .map { it.replace(" - Topic", "", ignoreCase = true).trim() }
            .filter { it.length >= 2 && !isRecordLabelOrChannel(it) }
        candidates.addAll(split)
    }

    // 2. If artist field was a record label or gave no valid artists, extract from title segments
    if (candidates.isEmpty()) {
        val titleSegments = title.split("|", " - ", "—", "–", "•", "/", "~", "feat.", "ft.")
            .map { it.replace(Regex("""[\(\[\{].*?[\)\]\}]"""), " ").trim() }
            .filter { it.isNotBlank() }

        for (seg in titleSegments) {
            val s = seg.trim()
            if (s.length in 3..25 && !isRecordLabelOrChannel(s)) {
                val sLower = s.lowercase(java.util.Locale.ROOT)
                if (!sLower.contains("video") && !sLower.contains("audio") &&
                    !sLower.contains("song") && !sLower.contains("official") &&
                    !sLower.contains("teaser") && !sLower.contains("trailer") &&
                    !sLower.contains("album") && !sLower.contains("remix") &&
                    !sLower.contains("full")
                ) {
                    candidates.add(s)
                }
            }
        }
    }

    return candidates.distinct().ifEmpty {
        if (!isRecordLabelOrChannel(artist)) listOf(artist) else emptyList()
    }
}

fun cleanArtist(rawArtist: String): String {
    if (rawArtist.isBlank()) return ""
    return rawArtist
        .replace(" - Topic", "", ignoreCase = true)
        .replace(REGEX_ARTIST_NOISE, " ")
        .replace(REGEX_NON_ALPHANUM, " ")
        .trim().lowercase().replace(REGEX_WHITESPACE, " ")
}

fun hasArtistOverlap(artistA: String, artistB: String): Boolean {
    val cleanA = cleanArtist(artistA)
    val cleanB = cleanArtist(artistB)
    if (cleanA.isBlank() || cleanB.isBlank()) return false
    if (cleanA == cleanB) return true
    if (cleanA.contains(cleanB) || cleanB.contains(cleanA)) return true

    val tokensA = cleanA.split(" ").filter { it.length >= 3 }
    val tokensB = cleanB.split(" ").filter { it.length >= 3 }
    return tokensA.any { a -> tokensB.contains(a) }
}

private fun levenshteinDistance(s1: String, s2: String): Int {
    val m = s1.length
    val n = s2.length
    if (Math.abs(m - n) > 2 || m > 25 || n > 25) return 99
    if (m == 0) return n
    if (n == 0) return m

    var prev = IntArray(n + 1) { it }
    var curr = IntArray(n + 1)

    for (i in 1..m) {
        curr[0] = i
        val c1 = s1[i - 1]
        for (j in 1..n) {
            val cost = if (c1 == s2[j - 1]) 0 else 1
            curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
        }
        val temp = prev
        prev = curr
        curr = temp
    }
    return prev[n]
}

fun cleanTrackTitle(rawTitle: String, rawArtist: String = ""): String {
    if (rawTitle.isBlank()) return ""
    var t = rawTitle

    // 0. Manual HTML entity decoding
    t = t.replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    // 1. Remove YouTube record labels, company, and distributor stamps
    t = t.replace(REGEX_RECORD_LABELS, " ")

    // 2. Remove bracketed / parenthetical tags: (Official Video), [Audio], [Lyrics], etc.
    t = t.replace(REGEX_BRACKETS_TAGS, " ")
    t = t.replace(REGEX_SMALL_BRACKETS, " ")

    // 3. Multi-segment divider parsing: extract the true song title segment
    val segments = t.split(REGEX_SEPARATORS).map { it.trim() }.filter { it.isNotBlank() }
    if (segments.size >= 2) {
        val cleanArt = cleanArtist(rawArtist)
        val s0 = segments[0]
        val s1 = segments[1]
        val isS0Artist = cleanArt.isNotBlank() && (
            s0.equals(cleanArt, ignoreCase = true) ||
            s0.replace(" ", "").equals(cleanArt.replace(" ", ""), ignoreCase = true) ||
            (cleanArt.contains(s0.lowercase()) && s0.length >= 4)
        )
        t = if (isS0Artist) s1 else s0
    }

    // 4. If title begins with artist followed by colon or dash (e.g. "Arijit Singh: Kesariya")
    val cleanArt = cleanArtist(rawArtist)
    if (cleanArt.isNotBlank() && t.lowercase().startsWith(cleanArt)) {
        t = t.substring(cleanArt.length).trim().removePrefix(":").removePrefix("-").trim()
    }

    // 5. Remove common standalone buzzwords and qualifiers
    t = t.replace(REGEX_BUZZWORDS, " ")
    t = t.replace(REGEX_FEAT, " ")

    // 6. Strip non-alphanumeric, keeping standard letters & numbers
    t = t.replace(REGEX_NON_ALPHANUM, " ")

    // 7. Collapse whitespace and lowercase
    return t.trim().lowercase().replace(REGEX_WHITESPACE, " ")
}

/**
 * Determines whether two TrackEntity objects represent the SAME underlying song,
 * catching duplicates across different YouTube channels, uploaders, lyric videos,
 * audio-only tracks, and video clips.
 */
fun TrackEntity.isSameSong(other: TrackEntity): Boolean {
    if (this.id == other.id) return true

    val cleanA = this.cleanSongTitle()
    val cleanB = other.cleanSongTitle()

    if (cleanA.isBlank() || cleanB.isBlank()) {
        return this.title.equals(other.title, ignoreCase = true)
    }

    val durDiff = if (this.durationSeconds > 0 && other.durationSeconds > 0) {
        Math.abs(this.durationSeconds - other.durationSeconds)
    } else 0

    // Strict duration check: if both tracks have known durations and differ by more than 120s, not the same song
    if (this.durationSeconds > 0 && other.durationSeconds > 0 && durDiff > 120) {
        return false
    }

    val artA = cleanArtist(this.artist)
    val artB = cleanArtist(other.artist)
    val isLabelA = REGEX_RECORD_LABELS.containsMatchIn(this.artist)
    val isLabelB = REGEX_RECORD_LABELS.containsMatchIn(other.artist)
    val hasDistinctArtists = artA.isNotBlank() && artB.isNotBlank() && !isLabelA && !isLabelB

    val artistOverlap = hasArtistOverlap(this.artist, other.artist) ||
        (artA.isNotBlank() && other.title.contains(artA, ignoreCase = true)) ||
        (artB.isNotBlank() && this.title.contains(artB, ignoreCase = true))

    // If both tracks have distinct, explicit artists (not record labels) and have zero artist overlap,
    // they are different songs by different artists (e.g. "Millionaire" by Chris Brown vs "Millionaire" by Honey Singh)
    if (hasDistinctArtists && !artistOverlap) {
        return false
    }

    // 1. Exact cleaned title match (e.g. "kesariya" == "kesariya")
    if (cleanA == cleanB) {
        return durDiff <= 90 || this.durationSeconds == 0L || other.durationSeconds == 0L
    }

    // 2. Compressed alphanumeric match (e.g. "blinding lights" -> "blindinglights")
    val alphaA = cleanA.replace(" ", "")
    val alphaB = cleanB.replace(" ", "")
    if (alphaA.isNotEmpty() && alphaA == alphaB) {
        return durDiff <= 90 || this.durationSeconds == 0L || other.durationSeconds == 0L
    }

    val coreA = this.coreSongTitle()
    val coreB = other.coreSongTitle()

    // 3. Core title match (e.g. both core titles are "kesariya" or "starboy" or "deva deva")
    if (coreA.isNotBlank() && coreB.isNotBlank() && coreA == coreB && coreA.length >= 3) {
        if (artistOverlap) return true
        if (durDiff <= 60) return true
    }

    // 4. Substring containment (e.g. "kesariya" is contained in "kesariya brahmastra")
    if (alphaA.length >= 4 && alphaB.length >= 4) {
        if (alphaA.startsWith(alphaB) || alphaB.startsWith(alphaA) || alphaA.contains(alphaB) || alphaB.contains(alphaA)) {
            if (artistOverlap) return true
            if (durDiff <= 60) return true
        }
    }

    // 5. Token overlap match (Jaccard similarity on significant words >= 3 chars)
    val wordsA = cleanA.split(" ").filter { it.length >= 3 }.toSet()
    val wordsB = cleanB.split(" ").filter { it.length >= 3 }.toSet()
    if (wordsA.isNotEmpty() && wordsB.isNotEmpty()) {
        val intersection = wordsA.intersect(wordsB).size
        val union = wordsA.union(wordsB).size
        val jaccard = intersection.toFloat() / union.toFloat()
        if (jaccard >= 0.60f) {
            if (artistOverlap) return true
            if (durDiff <= 60) return true
        }
    }

    // 6. Transliteration / Levenshtein match for titles >= 5 chars by the same artist
    if (artistOverlap && alphaA.length >= 5 && alphaB.length >= 5) {
        val dist = levenshteinDistance(alphaA, alphaB)
        if (dist <= 2 && durDiff <= 60) return true
    }

    return false
}

/**
 * Deduplicates a list of tracks so that no two tracks represent the same underlying song,
 * keeping the first high-quality occurrence.
 * Optimized with O(1) hash indexing to eliminate ANRs.
 */
fun List<TrackEntity>.distinctSongs(): List<TrackEntity> {
    if (this.size <= 1) return this
    val result = mutableListOf<TrackEntity>()
    val seenCleanTitles = HashSet<String>(this.size * 2)
    val seenAlphaTitles = HashSet<String>(this.size * 2)
    val seenCoreWithArtist = HashSet<String>(this.size * 2)

    for (track in this) {
        val cleanT = track.cleanSongTitle()
        val cleanA = track.cleanArtistName()
        val alphaT = cleanT.replace(" ", "")
        val coreT = track.coreSongTitle()

        // 1. O(1) check for exact cleaned title
        if (cleanT.isNotEmpty() && !seenCleanTitles.add(cleanT)) continue

        // 2. O(1) check for compressed alphanumeric title
        if (alphaT.isNotEmpty() && !seenAlphaTitles.add(alphaT)) continue

        // 3. O(1) check for core title + primary artist token
        val primaryArtistToken = cleanA.split(" ").firstOrNull { it.length >= 3 } ?: ""
        if (coreT.length >= 3 && primaryArtistToken.isNotEmpty()) {
            val key = "${coreT}_$primaryArtistToken"
            if (!seenCoreWithArtist.add(key)) continue
        }

        // 4. Secondary deep fuzzy check against the small filtered list
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
