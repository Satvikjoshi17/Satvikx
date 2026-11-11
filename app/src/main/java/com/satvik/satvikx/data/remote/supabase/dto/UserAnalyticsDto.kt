package com.satvik.satvikx.data.remote.supabase.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Enterprise analytics DTO capturing user-wise metrics in Supabase.
 * Formatted with India Standard Time (IST) and human-readable metrics
 * for clear visibility in the Supabase Dashboard.
 */
@Serializable
data class UserAnalyticsDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("display_name")
    val displayName: String = "Stark Operative",
    @SerialName("email")
    val email: String? = null,
    @SerialName("is_guest")
    val isGuest: Boolean = false,
    @SerialName("avatar")
    val avatar: String = "⚡",
    @SerialName("total_listening_seconds")
    val totalListeningSeconds: Long = 0L,
    @SerialName("listening_time_human")
    val listeningTimeHuman: String = "0s",
    @SerialName("total_songs_played")
    val totalSongsPlayed: Int = 0,
    @SerialName("total_completed_songs")
    val totalCompletedSongs: Int = 0,
    @SerialName("total_skips")
    val totalSkips: Int = 0,
    @SerialName("last_played_song")
    val lastPlayedSong: String = "",
    @SerialName("last_played_artist")
    val lastPlayedArtist: String = "",
    @SerialName("top_artists")
    val topArtists: String = "",
    @SerialName("top_genres")
    val topGenres: String = "",
    @SerialName("liked_songs_count")
    val likedSongsCount: Int = 0,
    @SerialName("playlists_count")
    val playlistsCount: Int = 0,
    @SerialName("blends_count")
    val blendsCount: Int = 0,
    @SerialName("device_info")
    val deviceInfo: String = "",
    @SerialName("app_version")
    val appVersion: String = "1.0.17",
    @SerialName("timezone")
    val timezone: String = "Asia/Kolkata (IST)",
    @SerialName("last_active_ist")
    val lastActiveIst: String,
    @SerialName("updated_at_ist")
    val updatedAtIst: String
)

