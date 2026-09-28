package com.satvik.satvikx.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.satvik.satvikx.data.local.entity.RecentPlaybackEntity
import com.satvik.satvikx.data.local.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for recent playback tracking and playback history queries.
 */
@Dao
interface RecentPlaybackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordRecentPlayback(recent: RecentPlaybackEntity)

    @Query("""
        INSERT INTO recent_playback (trackId, playedAtTimestamp, playCount)
        VALUES (:trackId, :timestamp, 1)
        ON CONFLICT(trackId) DO UPDATE SET
            playedAtTimestamp = :timestamp,
            playCount = playCount + 1
    """)
    suspend fun recordOrIncrementPlayback(trackId: String, timestamp: Long = System.currentTimeMillis())

    @Query("""
        SELECT tracks.* FROM tracks 
        INNER JOIN recent_playback ON tracks.id = recent_playback.trackId 
        ORDER BY recent_playback.playedAtTimestamp DESC 
        LIMIT :limit
    """)
    fun getRecentTracks(limit: Int = 50): Flow<List<TrackEntity>>

    @Query("""
        SELECT tracks.* FROM tracks 
        INNER JOIN recent_playback ON tracks.id = recent_playback.trackId 
        ORDER BY recent_playback.playedAtTimestamp DESC 
        LIMIT :limit
    """)
    suspend fun getRecentTracksSync(limit: Int = 60): List<TrackEntity>

    @Query("""
        SELECT tracks.* FROM tracks 
        INNER JOIN recent_playback ON tracks.id = recent_playback.trackId 
        ORDER BY recent_playback.playCount DESC, recent_playback.playedAtTimestamp DESC 
        LIMIT :limit
    """)
    suspend fun getHeavyRotationTracksSync(limit: Int = 30): List<TrackEntity>

    @Query("SELECT playCount FROM recent_playback WHERE trackId = :trackId LIMIT 1")
    suspend fun getPlayCountForTrack(trackId: String): Int?

    @Query("DELETE FROM recent_playback WHERE trackId = :trackId")
    suspend fun deleteRecentById(trackId: String)

    @Query("DELETE FROM recent_playback")
    suspend fun clearHistory()
}
