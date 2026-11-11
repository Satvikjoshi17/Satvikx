package com.satvik.satvikx.data.parser

import com.satvik.satvikx.data.remote.model.LrcLine
import java.util.Locale

/**
 * High-performance parser and serializer for standard (.lrc) synchronized lyric files.
 *
 * Supports:
 * - Standard centisecond timestamps: `[mm:ss.xx]`
 * - High-precision millisecond timestamps: `[mm:ss.xxx]`
 * - Alternate separators: `[mm:ss:xx]` and whole seconds `[mm:ss]`
 * - Multiple timestamps per line: `[00:12.34][00:45.67] Chorus line`
 * - Metadata header stripping: `[ti:...]`, `[ar:...]`, `[al:...]`, `[by:...]`, `[length:...]`
 * - Global playback offset parsing: `[offset:+/-ms]`
 * - Inline syllable/karaoke tag stripping: `<00:12.50>`, `<c>`, `<v Singer>`
 * - Preservation of musical interludes: `[01:30.00]` -> `LrcLine(90000, "")`
 * - Strict chronological sorting by [LrcLine.startTimeMs]
 * - Standard [mm:ss.xx] formatter serialization via [format]
 * - O(log N) binary search for active playback line via [findActiveLineIndex]
 */
object LrcParser {

    /**
     * Matches timestamp tags in square brackets:
     * - Group 1: Minutes (1 to 3 digits)
     * - Group 2: Seconds (00 to 59)
     * - Group 3: Optional fraction digits (1 to 3+ digits)
     */
    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):([0-5]\d)(?:[.:](\d+))?\]""")

    /**
     * Matches standard LRC metadata header tags:
     * e.g. [ti:Title], [ar:Artist], [al:Album], [by:Editor], [offset:+500], [length:03:45]
     */
    private val HEADER_TAG_REGEX = Regex("""^\[\s*([a-zA-Z]+)\s*:\s*(.*)\]$""")

    /**
     * Matches inline/syllable/karaoke tags in angle brackets:
     * e.g. <00:12.50>, <c>, </c>, <v Singer>
     */
    private val INLINE_TAG_REGEX = Regex("""<[^>]*>""")

    /**
     * Normalizes multiple consecutive whitespace characters into a single space.
     */
    private val MULTI_SPACE_REGEX = Regex("""\s+""")

    /**
     * Parses a raw LRC string into a chronologically sorted list of [LrcLine] objects.
     *
     * @param rawLrc Raw LRC content with timestamps and lyric lines.
     * @return List of parsed [LrcLine] instances sorted by [LrcLine.startTimeMs] ascending.
     *         Returns an empty list if the input is blank or contains no valid timestamps.
     */
    fun parse(rawLrc: String?): List<LrcLine> {
        if (rawLrc.isNullOrBlank()) return emptyList()

        val lines = rawLrc.lines()

        // Pass 1: Extract global offset tag if present (e.g. [offset:+500] or [offset:-200])
        var offsetMs = 0L
        for (line in lines) {
            val trimmed = line.trim()
            val headerMatch = HEADER_TAG_REGEX.matchEntire(trimmed)
            if (headerMatch != null && headerMatch.groupValues[1].equals("offset", ignoreCase = true)) {
                val rawOffset = headerMatch.groupValues[2].trim().removePrefix("+")
                offsetMs = rawOffset.toLongOrNull() ?: 0L
                break
            }
        }

        val parsedLines = ArrayList<LrcLine>()

        // Pass 2: Parse timed lyric lines
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Skip standalone metadata header tags
            if (HEADER_TAG_REGEX.matches(trimmed)) continue

            val timestampMatches = TIMESTAMP_REGEX.findAll(line).toList()
            if (timestampMatches.isEmpty()) continue

            // Extract lyric text: strip all timestamp tags, then inline angle tags, then normalize spaces
            val textWithoutTimestamps = TIMESTAMP_REGEX.replace(line, "")
            val cleanText = INLINE_TAG_REGEX.replace(textWithoutTimestamps, "")
                .replace(MULTI_SPACE_REGEX, " ")
                .trim()

            for (match in timestampMatches) {
                val minutes = match.groupValues[1].toLongOrNull() ?: continue
                val seconds = match.groupValues[2].toLongOrNull() ?: continue
                if (seconds !in 0..59) continue

                val fractionStr = match.groupValues.getOrNull(3).orEmpty()
                val fractionMs = if (fractionStr.isNotEmpty()) {
                    fractionStr.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                } else {
                    0L
                }

                val rawTimeMs = (minutes * 60_000L) + (seconds * 1_000L) + fractionMs
                val finalTimeMs = maxOf(0L, rawTimeMs + offsetMs)

                parsedLines.add(LrcLine(startTimeMs = finalTimeMs, text = cleanText))
            }
        }

        return parsedLines.sortedBy { it.startTimeMs }
    }

    /**
     * Serializes a list of [LrcLine] instances back into standard `[mm:ss.xx]` LRC format.
     *
     * @param lines The lyric lines to serialize.
     * @return Formatted LRC string with lines sorted chronologically, separated by `\n`.
     */
    fun format(lines: List<LrcLine>): String {
        if (lines.isEmpty()) return ""

        return lines.sortedBy { it.startTimeMs }
            .joinToString("\n") { line ->
                val safeTimeMs = maxOf(0L, line.startTimeMs)
                val totalSeconds = safeTimeMs / 1000L
                val minutes = totalSeconds / 60L
                val seconds = totalSeconds % 60L
                val centiseconds = (safeTimeMs % 1000L) / 10L

                val timestamp = String.format(Locale.US, "[%02d:%02d.%02d]", minutes, seconds, centiseconds)
                val trimmedText = line.text.trim()
                if (trimmedText.isEmpty()) {
                    timestamp
                } else {
                    "$timestamp $trimmedText"
                }
            }
    }

    /**
     * Checks if the given string contains at least one valid LRC timestamp tag.
     */
    fun isValidLrc(rawLrc: String?): Boolean {
        if (rawLrc.isNullOrBlank()) return false
        return TIMESTAMP_REGEX.containsMatchIn(rawLrc)
    }

    /**
     * Finds the index of the active lyric line for the given playback position in milliseconds.
     * Uses binary search for O(log N) lookup time.
     *
     * @param lines Chronologically sorted list of [LrcLine].
     * @param positionMs Current playback position in milliseconds.
     * @return Index of the active lyric line, or -1 if [positionMs] is before the first line or [lines] is empty.
     */
    fun findActiveLineIndex(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty() || positionMs < lines[0].startTimeMs) return -1
        var low = 0
        var high = lines.lastIndex
        var result = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].startTimeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }
}

