package com.satvik.satvikx.data.remote.model

/**
 * Represents a single parsed line of synchronized lyrics.
 *
 * @property startTimeMs Playback timestamp in milliseconds when this lyric line starts.
 * @property text The sung lyric text or musical notation associated with this timestamp.
 */
data class LrcLine(
    val startTimeMs: Long,
    val text: String
) : Comparable<LrcLine> {
    override fun compareTo(other: LrcLine): Int = this.startTimeMs.compareTo(other.startTimeMs)
}

/**
 * Domain representation of lyrics payload for a track.
 *
 * @property trackId Unique identifier of the track.
 * @property isSynced Indicates whether this lyric payload contains time-synchronized lines.
 * @property lines Chronologically sorted list of [LrcLine] objects if [isSynced] is true.
 * @property plainLyrics Plain text lyrics representation (e.g., from unsynced providers or plain fallback).
 * @property provider Source provider identifier (e.g., "LRCLIB", "JIOSAAVN", "YOUTUBE", "CACHE").
 */
data class LyricsData(
    val trackId: String,
    val isSynced: Boolean,
    val lines: List<LrcLine> = emptyList(),
    val plainLyrics: String? = null,
    val provider: String = "UNKNOWN"
)

