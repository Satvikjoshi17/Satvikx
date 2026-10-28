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
    @SerialName("created_at")
    val createdAt: String? = null
)
