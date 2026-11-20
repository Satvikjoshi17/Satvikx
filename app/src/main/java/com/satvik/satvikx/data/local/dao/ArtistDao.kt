package com.satvik.satvikx.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.satvik.satvikx.data.local.entity.ArtistCacheEntity

@Dao
interface ArtistDao {

    @Query("SELECT * FROM artist_cache WHERE LOWER(artistName) = LOWER(:name) LIMIT 1")
    suspend fun getArtist(name: String): ArtistCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtist(artist: ArtistCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtists(artists: List<ArtistCacheEntity>)

    @Query("SELECT * FROM artist_cache")
    suspend fun getAllCachedArtists(): List<ArtistCacheEntity>
}

