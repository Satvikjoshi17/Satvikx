package com.satvik.satvikx.data.remote.supabase.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserPlaylistTrackDto(
    @SerialName("id")
    val id: String? = null,
    @SerialName("playlist_id")
    val playlistId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("track_id")
    val trackId: String,
    @SerialName("title")
    val title: String,
    @SerialName("artist")
    val artist: String,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String = "",
    @SerialName("duration_seconds")
    val durationSeconds: Int = 0,
    @SerialName("added_at")
    val addedAt: String? = null
)
