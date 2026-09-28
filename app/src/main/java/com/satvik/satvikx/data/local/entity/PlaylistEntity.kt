package com.satvik.satvikx.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a playlist created or managed by the user.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val playlistId: Long = 0L,
    val name: String,
    val createdAtTimestamp: Long = System.currentTimeMillis(),
    val isCustom: Boolean = true
)
