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
        SELECT tracks.* FROM tracks 
        INNER JOIN recent_playback ON tracks.id = recent_playback.trackId 
        ORDER BY recent_playback.playedAtTimestamp DESC 
        LIMIT :limit
    """)
    fun getRecentTracks(limit: Int = 50): Flow<List<TrackEntity>>

    @Query("DELETE FROM recent_playback WHERE trackId = :trackId")
    suspend fun deleteRecentById(trackId: String)

    @Query("DELETE FROM recent_playback")
    suspend fun clearHistory()
}
