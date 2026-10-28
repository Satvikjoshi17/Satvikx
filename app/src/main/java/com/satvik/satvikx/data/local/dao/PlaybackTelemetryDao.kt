package com.satvik.satvikx.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.satvik.satvikx.data.local.entity.PlaybackTelemetryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackTelemetryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(telemetry: PlaybackTelemetryEntity)

    @Query("SELECT * FROM playback_telemetry WHERE trackId = :trackId LIMIT 1")
    suspend fun getTelemetry(trackId: String): PlaybackTelemetryEntity?

    @Query("SELECT * FROM playback_telemetry ORDER BY lastPlayedTimestamp DESC LIMIT :limit")
    suspend fun getRecentTelemetry(limit: Int = 100): List<PlaybackTelemetryEntity>

    @Query("SELECT * FROM playback_telemetry WHERE completionCount > 0 OR playCount > 1 ORDER BY (completionCount * 3 + playCount * 2 - skipCount * 4) DESC LIMIT :limit")
    suspend fun getTopEngagedTracks(limit: Int = 50): List<PlaybackTelemetryEntity>

    @Query("SELECT trackId FROM playback_telemetry WHERE skipCount >= 2 AND completionCount == 0")
    suspend fun getFrequentlySkippedTrackIds(): List<String>

    @Query("""
        INSERT INTO playback_telemetry (trackId, title, artist, playCount, completionCount, skipCount, totalListenTimeMs, lastCompletionRate, lastPlayedTimestamp, isLiked)
        VALUES (:trackId, :title, :artist, 1, 0, 0, 0, 0.0, :timestamp, 0)
        ON CONFLICT(trackId) DO UPDATE SET
            title = CASE WHEN :title != '' THEN :title ELSE playback_telemetry.title END,
            artist = CASE WHEN :artist != '' THEN :artist ELSE playback_telemetry.artist END,
            playCount = playback_telemetry.playCount + 1,
            lastPlayedTimestamp = :timestamp
    """)
    suspend fun recordPlayStart(trackId: String, title: String, artist: String, timestamp: Long = System.currentTimeMillis())

    @Query("""
        UPDATE playback_telemetry SET
            completionCount = completionCount + :completed,
            skipCount = skipCount + :skipped,
            totalListenTimeMs = totalListenTimeMs + :listenTimeMs,
            lastCompletionRate = :completionRate,
            lastPlayedTimestamp = :timestamp
        WHERE trackId = :trackId
    """)
    suspend fun updateTrackEngagement(
        trackId: String,
        listenTimeMs: Long,
        completionRate: Float,
        completed: Int,
        skipped: Int,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE playback_telemetry SET isLiked = :isLiked WHERE trackId = :trackId")
    suspend fun updateLikedStatus(trackId: String, isLiked: Boolean)
}
