package com.satvik.satvikx.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.satvik.satvikx.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local track caching and persistence.
 */
@Dao
interface TrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Delete
    suspend fun deleteTrack(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: String)

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    fun getTrackById(trackId: String): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE id = :trackId LIMIT 1")
    suspend fun getTrackByIdSync(trackId: String): TrackEntity?

    @Query("SELECT * FROM tracks ORDER BY addedAtTimestamp DESC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isDownloaded = 1 ORDER BY addedAtTimestamp DESC")
    fun getDownloadedTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchTracks(query: String): Flow<List<TrackEntity>>

    @Query("UPDATE tracks SET isDownloaded = :isDownloaded, localPath = :localPath WHERE id = :trackId")
    suspend fun updateDownloadStatus(trackId: String, isDownloaded: Boolean, localPath: String?)

    @Query("UPDATE tracks SET streamUrl = :streamUrl WHERE id = :trackId")
    suspend fun updateStreamUrl(trackId: String, streamUrl: String?)

    @Query("DELETE FROM tracks WHERE isDownloaded = 0")
    suspend fun clearNonDownloadedCache()

    @Query("SELECT DISTINCT artist FROM tracks WHERE artist != '' AND artist != 'Unknown Artist' ORDER BY addedAtTimestamp DESC LIMIT :limit")
    suspend fun getRecentArtists(limit: Int = 5): List<String>
}

