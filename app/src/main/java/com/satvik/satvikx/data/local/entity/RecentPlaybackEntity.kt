package com.satvik.satvikx.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity tracking recent playback history for rapid resumption and recents list.
 * Decoupled from CASCADE triggers to prevent songs from disappearing on track updates.
 */
@Entity(
    tableName = "recent_playback",
    indices = [
        Index(value = ["trackId"]),
        Index(value = ["playedAtTimestamp"]),
        Index(value = ["playCount"])
    ]
)
data class RecentPlaybackEntity(
    @PrimaryKey
    val trackId: String,
    val playedAtTimestamp: Long = System.currentTimeMillis(),
    val playCount: Int = 1
)
