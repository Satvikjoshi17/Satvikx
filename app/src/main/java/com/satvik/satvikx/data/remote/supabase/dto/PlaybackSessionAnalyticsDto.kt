package com.satvik.satvikx.data.remote.supabase.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Detailed playback session log for granular analytics in Supabase.
 * Each entry captures a song listened to by a user with Indian Standard Time (IST).
 */
@Serializable
data class PlaybackSessionAnalyticsDto(
    @SerialName("id")
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("user_name")
    val userName: String = "Stark Operative",
    @SerialName("track_id")
    val trackId: String,
    @SerialName("track_title")
    val trackTitle: String,
    @SerialName("track_artist")
    val trackArtist: String,
    @SerialName("listened_seconds")
    val listenedSeconds: Long,
    @SerialName("track_duration_seconds")
    val trackDurationSeconds: Long,
    @SerialName("completion_percent")
    val completionPercent: Float,
    @SerialName("is_completed")
    val isCompleted: Boolean,
    @SerialName("is_skipped")
    val isSkipped: Boolean,
    @SerialName("timestamp_ist")
    val timestampIst: String
)

