package com.satvik.satvikx.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.satvik.satvikx.data.local.entity.LyricsEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local lyrics persistence and caching.
 */
@Dao
interface LyricsDao {

    /**
     * Fetch cached lyrics for a specific track by its trackId.
     * Returns null if no cached lyrics exist for the given track.
     */
    @Query("SELECT * FROM lyrics WHERE trackId = :trackId LIMIT 1")
    suspend fun getLyricsByTrackId(trackId: String): LyricsEntity?

    /**
     * Observe cached lyrics changes for a specific track as a reactive Flow.
     */
    @Query("SELECT * FROM lyrics WHERE trackId = :trackId LIMIT 1")
    fun getLyricsFlowByTrackId(trackId: String): Flow<LyricsEntity?>

    /**
     * Insert or update cached lyrics for a track.
     * Uses OnConflictStrategy.REPLACE to cleanly update stale or plain lyrics with synced lyrics.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(lyrics: LyricsEntity)

    /**
     * Delete cached lyrics for a specific track.
     */
    @Query("DELETE FROM lyrics WHERE trackId = :trackId")
    suspend fun deleteLyrics(trackId: String)

    /**
     * Clear all cached lyrics from the database.
     */
    @Query("DELETE FROM lyrics")
    suspend fun clearAllLyrics()
}

