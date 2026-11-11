package com.satvik.satvikx.lyrics.e2e

import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * E2E Domain Models and Interface Contracts for the SatvikX Synced Lyrics Feature.
 * Matches specifications in PROJECT.md and ORIGINAL_REQUEST.md.
 */

// ==========================================
// 1. DATA MODELS (Feature 1)
// ==========================================

data class E2ELrcLine(
    val startTimeMs: Long,
    val text: String
) : Comparable<E2ELrcLine> {
    override fun compareTo(other: E2ELrcLine): Int = this.startTimeMs.compareTo(other.startTimeMs)
}

data class E2ELyricsData(
    val trackId: String,
    val isSynced: Boolean,
    val lines: List<E2ELrcLine> = emptyList(),
    val plainLyrics: String? = null,
    val provider: String = "UNKNOWN"
)

data class E2ELyricsEntity(
    val trackId: String,
    val isSynced: Boolean,
    val lrcContent: String?,
    val plainLyrics: String?,
    val provider: String,
    val cachedAtTimestamp: Long = System.currentTimeMillis()
)

// ==========================================
// 2. LRC PARSER CONTRACT & LOGIC (Feature 2)
// ==========================================

interface E2ELrcParserContract {
    fun parse(rawLrc: String): List<E2ELrcLine>
    fun format(lines: List<E2ELrcLine>): String
}

class E2ELrcParser : E2ELrcParserContract {
    // Regex matching [mm:ss.xx] or [mm:ss.xxx] or [hh:mm:ss.xx]
    private val timestampPattern = Pattern.compile("\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\]")
    private val metadataPattern = Pattern.compile("^\\[(ar|ti|al|by|offset|length|re|ve):.*?\\]", Pattern.CASE_INSENSITIVE)

    override fun parse(rawLrc: String): List<E2ELrcLine> {
        if (rawLrc.isBlank()) return emptyList()

        val parsedLines = mutableListOf<E2ELrcLine>()
        val lines = rawLrc.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Discard pure ID/metadata tags if no timestamps present
            if (metadataPattern.matcher(trimmed).find() && !timestampPattern.matcher(trimmed).find()) {
                continue
            }

            // Extract all timestamps in the line (handles multi-timestamp lines)
            val matcher = timestampPattern.matcher(trimmed)
            val timestamps = mutableListOf<Long>()
            var lastMatchEnd = 0

            while (matcher.find()) {
                val min = matcher.group(1).toLong()
                val sec = matcher.group(2).toLong()
                val fracStr = matcher.group(3)
                val ms = if (fracStr.length == 2) fracStr.toLong() * 10 else fracStr.toLong()
                val totalMs = (min * 60 * 1000) + (sec * 1000) + ms
                timestamps.add(totalMs)
                lastMatchEnd = matcher.end()
            }

            if (timestamps.isNotEmpty()) {
                val text = trimmed.substring(lastMatchEnd).trim()
                for (ts in timestamps) {
                    parsedLines.add(E2ELrcLine(startTimeMs = ts, text = text))
                }
            }
        }

        return parsedLines.sorted()
    }

    override fun format(lines: List<E2ELrcLine>): String {
        val sorted = lines.sorted()
        val sb = StringBuilder()
        for (line in sorted) {
            val totalSec = line.startTimeMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val hundredths = (line.startTimeMs % 1000) / 10
            sb.append(String.format("[%02d:%02d.%02d]%s\n", min, sec, hundredths, line.text))
        }
        return sb.toString().trimEnd()
    }
}

// ==========================================
// 3. ROOM SQLITE CACHE CONTRACT (Feature 3)
// ==========================================

interface E2ELyricsDao {
    suspend fun getLyricsByTrackId(trackId: String): E2ELyricsEntity?
    suspend fun insertLyrics(lyrics: E2ELyricsEntity)
    suspend fun deleteLyrics(trackId: String)
    suspend fun clear()
}

class E2EInMemoryLyricsDao : E2ELyricsDao {
    private val store = ConcurrentHashMap<String, E2ELyricsEntity>()

    override suspend fun getLyricsByTrackId(trackId: String): E2ELyricsEntity? {
        return store[trackId]
    }

    override suspend fun insertLyrics(lyrics: E2ELyricsEntity) {
        store[lyrics.trackId] = lyrics
    }

    override suspend fun deleteLyrics(trackId: String) {
        store.remove(trackId)
    }

    override suspend fun clear() {
        store.clear()
    }

    fun size(): Int = store.size
}

// ==========================================
// 4. CLEAN METADATA POLISHING (Feature 4)
// ==========================================

object E2EMetadataPolish {
    private val TITLE_CLEAN_REGEX = Regex(
        "(?i)\\s*[\\[(](?:official\\s+(?:music\\s+)?(?:video|audio)(?:\\s+song)?|official\\s+audio|lyric\\s+video|audio(?:\\s+song)?|video(?:\\s+song)?|lyrics|hd|4k[^\\])]*|hq|remaster(?:ed)?|visualizer|original\\s+mix|extended\\s+mix)[^\\])]*[\\])]"
    )
    private val FEAT_REGEX = Regex("(?i)\\s*[\\[(]?(?:\\bfeat\\.?|\\bft\\.?)\\b\\s*[^)\\]]+[\\])]?")
    private val SPECIAL_CHARS_REGEX = Regex("[^a-zA-Z0-9\\s\\u0900-\\u097F\\u0A00-\\u0A7F]") // Preserves Devanagari & Gurmukhi

    fun extractPolishedTitle(rawTitle: String): String {
        var clean = rawTitle.trim()
        clean = TITLE_CLEAN_REGEX.replace(clean, "")
        clean = FEAT_REGEX.replace(clean, "")
        clean = clean.replace(Regex("\\s+"), " ").trim()
        return clean
    }

    fun extractPolishedArtist(rawArtist: String): String {
        var clean = rawArtist.trim()
        clean = FEAT_REGEX.replace(clean, "")
        clean = clean.replace(Regex("(?i)\\s*-\\s*topic$"), "")
        clean = clean.replace(Regex("(?i)vevo$"), "")
        clean = clean.replace(Regex("\\s+"), " ").trim()
        return clean
    }

    fun sanitizeQuery(query: String): String {
        return SPECIAL_CHARS_REGEX.replace(query, " ").replace(Regex("\\s+"), " ").trim()
    }
}

// ==========================================
// 5. PROVIDER CONTRACTS (Features 5, 6, 7)
// ==========================================

interface E2ELrclibProvider {
    suspend fun getLyrics(trackName: String, artistName: String, albumName: String? = null, durationSec: Long? = null): E2ELyricsData?
    suspend fun searchLyrics(query: String): E2ELyricsData?
}

interface E2EJioSaavnProvider {
    suspend fun getLyrics(songTitle: String, artistName: String): E2ELyricsData?
}

interface E2EYoutubeCaptionsProvider {
    suspend fun getSubtitles(videoId: String): E2ELyricsData?
}

// ==========================================
// 6. MULTI-PROVIDER REPOSITORY (Feature 8)
// ==========================================

class E2ELyricsRepository(
    private val localDao: E2ELyricsDao,
    private val lrclibProvider: E2ELrclibProvider?,
    private val saavnProvider: E2EJioSaavnProvider?,
    private val youtubeProvider: E2EYoutubeCaptionsProvider?,
    private val parser: E2ELrcParser = E2ELrcParser()
) {
    var lastSourceUsed: String? = null
        private set

    suspend fun getLyrics(
        trackId: String,
        title: String,
        artist: String,
        album: String? = null,
        durationMs: Long = 0L,
        isNetworkAvailable: Boolean = true
    ): Result<E2ELyricsData?> {
        // Priority 1: Room DB Cache (0ms offline retrieval)
        val cached = localDao.getLyricsByTrackId(trackId)
        if (cached != null) {
            lastSourceUsed = "ROOM_CACHE"
            val parsedLines = cached.lrcContent?.let { parser.parse(it) } ?: emptyList()
            return Result.success(
                E2ELyricsData(
                    trackId = cached.trackId,
                    isSynced = cached.isSynced,
                    lines = parsedLines,
                    plainLyrics = cached.plainLyrics,
                    provider = cached.provider
                )
            )
        }

        // If offline and not in cache, return null gracefully
        if (!isNetworkAvailable) {
            lastSourceUsed = "OFFLINE_CACHE_MISS"
            return Result.success(null)
        }

        val polishedTitle = E2EMetadataPolish.extractPolishedTitle(title)
        val polishedArtist = E2EMetadataPolish.extractPolishedArtist(artist)
        val durationSec = if (durationMs > 0) durationMs / 1000 else null

        // Priority 2: LRCLIB Provider
        if (lrclibProvider != null) {
            try {
                var lrcData = lrclibProvider.getLyrics(polishedTitle, polishedArtist, album, durationSec)
                if (lrcData == null) {
                    lrcData = lrclibProvider.searchLyrics("$polishedTitle $polishedArtist")
                }
                if (lrcData != null && (lrcData.lines.isNotEmpty() || !lrcData.plainLyrics.isNullOrBlank())) {
                    lastSourceUsed = "LRCLIB"
                    cacheLocally(trackId, lrcData)
                    return Result.success(lrcData)
                }
            } catch (e: Exception) {
                // Graceful fallback to next provider
            }
        }

        // Priority 3: JioSaavn Provider (Regional Indian)
        if (saavnProvider != null) {
            try {
                val saavnData = saavnProvider.getLyrics(polishedTitle, polishedArtist)
                if (saavnData != null && (!saavnData.plainLyrics.isNullOrBlank() || saavnData.lines.isNotEmpty())) {
                    lastSourceUsed = "JIOSAAVN"
                    cacheLocally(trackId, saavnData)
                    return Result.success(saavnData)
                }
            } catch (e: Exception) {
                // Graceful fallback to next provider
            }
        }

        // Priority 4: YouTube Timed Captions Universal Fallback
        if (youtubeProvider != null) {
            try {
                val ytData = youtubeProvider.getSubtitles(trackId)
                if (ytData != null && ytData.lines.isNotEmpty()) {
                    lastSourceUsed = "YOUTUBE_CAPTIONS"
                    cacheLocally(trackId, ytData)
                    return Result.success(ytData)
                }
            } catch (e: Exception) {
                // Handled gracefully
            }
        }

        lastSourceUsed = "CASCADE_EXHAUSTED"
        return Result.success(null)
    }

    private suspend fun cacheLocally(trackId: String, lyrics: E2ELyricsData) {
        val lrcFormatted = if (lyrics.isSynced && lyrics.lines.isNotEmpty()) {
            parser.format(lyrics.lines)
        } else null

        val entity = E2ELyricsEntity(
            trackId = trackId,
            isSynced = lyrics.isSynced,
            lrcContent = lrcFormatted,
            plainLyrics = lyrics.plainLyrics,
            provider = lyrics.provider,
            cachedAtTimestamp = System.currentTimeMillis()
        )
        localDao.insertLyrics(entity)
    }
}

// ==========================================
// 7. PRESENTATION & HOLOGRAPHIC ENGINE (Features 9, 10, 11, 12, 13)
// ==========================================

sealed class E2ELyricsUiState {
    object Idle : E2ELyricsUiState()
    object Loading : E2ELyricsUiState()
    data class Synced(
        val lyrics: E2ELyricsData,
        val activeLineIndex: Int = 0,
        val isGlowActive: Boolean = true
    ) : E2ELyricsUiState()
    data class Plain(val text: String) : E2ELyricsUiState()
    object Instrumental : E2ELyricsUiState()
    data class Error(val message: String, val isOffline: Boolean = false) : E2ELyricsUiState()
}

enum class E2EHapticFeedbackType {
    TICK,
    LONG_PRESS,
    NONE
}

class E2ELyricsPresentationEngine(
    private val lines: List<E2ELrcLine> = emptyList()
) {
    var isUserScrolling: Boolean = false
        private set
    private var lastUserScrollTimestampMs: Long = 0L
    private val OVERRIDE_TIMEOUT_MS = 4000L

    var lastDispatchedHaptic: E2EHapticFeedbackType = E2EHapticFeedbackType.NONE
        private set
    var lastSeekTargetMs: Long? = null
        private set

    /**
     * Computes the 0-based active line index for the given playback position.
     * Returns -1 if playback is before the first lyric line.
     */
    fun getActiveLineIndex(currentPositionMs: Long): Int {
        if (lines.isEmpty()) return -1
        if (currentPositionMs < lines.first().startTimeMs) return -1

        var low = 0
        var high = lines.size - 1
        var result = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].startTimeMs <= currentPositionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    /**
     * Determines line visual attributes for holographic display.
     */
    data class LineVisualState(
        val isCurrentSungLine: Boolean,
        val isPastLine: Boolean,
        val isUpcomingLine: Boolean,
        val opacity: Float,
        val isGlowArcCyan: Boolean
    )

    fun getLineVisualState(lineIndex: Int, activeLineIndex: Int): LineVisualState {
        return when {
            lineIndex == activeLineIndex -> LineVisualState(
                isCurrentSungLine = true,
                isPastLine = false,
                isUpcomingLine = false,
                opacity = 1.0f,
                isGlowArcCyan = true
            )
            lineIndex < activeLineIndex -> LineVisualState(
                isCurrentSungLine = false,
                isPastLine = true,
                isUpcomingLine = false,
                opacity = 0.4f,
                isGlowArcCyan = false
            )
            else -> LineVisualState(
                isCurrentSungLine = false,
                isPastLine = false,
                isUpcomingLine = true,
                opacity = 0.7f,
                isGlowArcCyan = false
            )
        }
    }

    /**
     * User taps a lyric line: triggers seek to timestamp and dispatches haptic feedback.
     */
    fun onLineTapped(lineIndex: Int, onSeekTo: (Long) -> Unit) {
        if (lineIndex in lines.indices) {
            val targetMs = lines[lineIndex].startTimeMs
            lastSeekTargetMs = targetMs
            lastDispatchedHaptic = E2EHapticFeedbackType.LONG_PRESS
            // Tap seeking immediately resets user override so auto-scroll snaps to tapped line
            isUserScrolling = false
            lastUserScrollTimestampMs = 0L
            onSeekTo(targetMs)
        }
    }

    /**
     * User drags or touches the scroll list: pauses auto-scroll for 4 seconds.
     */
    fun onUserGestureDrag(currentTimeMs: Long) {
        isUserScrolling = true
        lastUserScrollTimestampMs = currentTimeMs
    }

    /**
     * Simulates passage of time to check if 4s override has elapsed.
     */
    fun advanceTime(currentTimeMs: Long) {
        if (isUserScrolling && (currentTimeMs - lastUserScrollTimestampMs >= OVERRIDE_TIMEOUT_MS)) {
            isUserScrolling = false
        }
    }

    fun isAutoScrollEnabled(): Boolean = !isUserScrolling
}

// ==========================================
// 8. PLAYER VIEWMODEL SIMULATOR (Feature 9, 13)
// ==========================================

class E2EPlayerViewModel(
    private val repository: E2ELyricsRepository
) {
    var isLyricsViewVisible: Boolean = false
        private set
    var currentUiState: E2ELyricsUiState = E2ELyricsUiState.Idle
        private set
    var currentTrackId: String? = null
        private set

    fun toggleLyricsView() {
        isLyricsViewVisible = !isLyricsViewVisible
    }

    fun setLyricsViewVisibility(visible: Boolean) {
        isLyricsViewVisible = visible
    }

    fun onTrackChanged(trackId: String, title: String, artist: String, isInstrumental: Boolean = false, isOffline: Boolean = false) {
        currentTrackId = trackId
        if (isInstrumental) {
            currentUiState = E2ELyricsUiState.Instrumental
            return
        }

        currentUiState = E2ELyricsUiState.Loading

        runBlocking {
            val result = repository.getLyrics(
                trackId = trackId,
                title = title,
                artist = artist,
                isNetworkAvailable = !isOffline
            )

            result.onSuccess { data ->
                currentUiState = when {
                    data == null && isOffline -> E2ELyricsUiState.Error("Offline: lyrics not cached", isOffline = true)
                    data == null -> E2ELyricsUiState.Error("No lyrics found for track", isOffline = false)
                    data.isSynced && data.lines.isNotEmpty() -> E2ELyricsUiState.Synced(data, activeLineIndex = 0)
                    !data.plainLyrics.isNullOrBlank() -> E2ELyricsUiState.Plain(data.plainLyrics)
                    else -> E2ELyricsUiState.Instrumental
                }
            }.onFailure { error ->
                currentUiState = E2ELyricsUiState.Error(error.message ?: "Failed to fetch lyrics", isOffline = isOffline)
            }
        }
    }

    fun retryFetch(title: String, artist: String, isOffline: Boolean = false) {
        val trackId = currentTrackId ?: return
        onTrackChanged(trackId, title, artist, isInstrumental = false, isOffline = isOffline)
    }
}
