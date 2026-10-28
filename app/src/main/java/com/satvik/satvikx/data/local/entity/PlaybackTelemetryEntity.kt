package com.satvik.satvikx.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Enterprise playback engagement telemetry entity.
 * Captures explicit and implicit behavioral signals (completion rate, skips, listen duration,
 * replay count) used by the AI recommendation engine to score affinity and suppress skips.
 */
@Entity(
    tableName = "playback_telemetry",
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["artist"]),
        Index(value = ["lastPlayedTimestamp"])
    ]
)
data class PlaybackTelemetryEntity(
    @PrimaryKey
    val trackId: String,
    val title: String = "",
    val artist: String = "",
    val playCount: Int = 1,
    val completionCount: Int = 0,
    val skipCount: Int = 0,
    val totalListenTimeMs: Long = 0L,
    val lastCompletionRate: Float = 0f,
    val lastPlayedTimestamp: Long = System.currentTimeMillis(),
    val isLiked: Boolean = false
)
