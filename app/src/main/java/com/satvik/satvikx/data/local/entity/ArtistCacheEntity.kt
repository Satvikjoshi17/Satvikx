package com.satvik.satvikx.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for caching verified artist portraits and metadata from Deezer.
 * Prevents redundant network calls and guarantees instant (0ms) artist avatar loading.
 */
@Entity(tableName = "artist_cache")
data class ArtistCacheEntity(
    @PrimaryKey
    val artistName: String,
    val deezerId: Long? = null,
    val imageUrl: String? = null,
    val fansCount: Long = 0L,
    val cachedAtTimestamp: Long = System.currentTimeMillis()
)

