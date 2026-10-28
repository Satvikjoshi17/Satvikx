package com.satvik.satvikx.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a Stark Blend (shared music taste fusion playlist)
 * between the user and a friend, or between the user and curated artists.
 */
@Entity(tableName = "blends")
data class BlendEntity(
    @PrimaryKey
    val blendId: String,
    val title: String, // e.g. "Stark Blend • Satvik & Sarah" or "Stark Blend • Satvik & Arijit Singh"
    val creatorName: String,
    val creatorAvatar: String = "",
    val partnerName: String,
    val partnerAvatar: String = "",
    val matchScore: Int, // e.g. 88%
    val sharedAnthemTitle: String = "",
    val sharedAnthemArtist: String = "",
    val sharedAnthemThumbnail: String = "",
    val topSharedGenre: String = "",
    val inviteCode: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isArtistBlend: Boolean = false
)
