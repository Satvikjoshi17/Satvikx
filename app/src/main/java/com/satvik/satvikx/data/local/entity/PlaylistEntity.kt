package com.satvik.satvikx.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

const val LIKED_SONGS_PLAYLIST_NAME = "Liked Songs"

fun isLikedPlaylistName(name: String?): Boolean {
    if (name.isNullOrBlank()) return false
    val clean = name.trim().lowercase()
    return clean == "liked songs" || clean == "liked" || clean == "favorites" || clean == "favorite songs"
}

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
