package com.satvik.satvikx.data.remote.supabase.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    @SerialName("id")
    val id: String,
    @SerialName("display_name")
    val displayName: String = "Stark Operative",
    @SerialName("avatar_url")
    val avatarUrl: String = "",
    @SerialName("is_guest")
    val isGuest: Boolean = false,
    @SerialName("listening_seconds")
    val listeningSeconds: Long = 0L,
    @SerialName("listening_time_human")
    val listeningTimeHuman: String? = null,
    @SerialName("last_active_ist")
    val lastActiveIst: String? = null,
    @SerialName("device_model")
    val deviceModel: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null
)
