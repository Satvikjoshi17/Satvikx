package com.satvik.satvikx.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Enterprise Room SQLite entity caching raw synced LRC timestamps and plain-text lyrics.
 *
 * Persisting raw LRC strings and plain text in SQLite allows instant 0ms offline retrieval
 * on subsequent plays, eliminating redundant network calls across playback sessions.
 *
 * @property trackId Unique identifier of the track (e.g., YouTube video ID, JioSaavn song ID), serving as primary key.
 * @property isSynced Indicates whether this record contains time-synced LRC lines (true) or plain text only (false).
 * @property lrcContent Raw .lrc formatted string containing timestamped lines (e.g., "[00:12.34] Lyrics"), or null if unsynced.
 * @property plainLyrics Plain-text representation of lyrics without timestamp tags, or null if only synced LRC is available.
 * @property provider Upstream source provider identifier (e.g., "LRCLIB", "JIOSAAVN", "YOUTUBE").
 * @property cachedAtTimestamp Epoch millisecond timestamp when this record was cached locally.
 */
@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey
    val trackId: String,
    val isSynced: Boolean,
    val lrcContent: String? = null,
    val plainLyrics: String? = null,
    val provider: String,
    val cachedAtTimestamp: Long = System.currentTimeMillis()
)

