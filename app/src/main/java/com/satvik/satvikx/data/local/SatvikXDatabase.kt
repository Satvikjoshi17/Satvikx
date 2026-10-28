package com.satvik.satvikx.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.satvik.satvikx.data.local.dao.PlaylistDao
import com.satvik.satvikx.data.local.dao.RecentPlaybackDao
import com.satvik.satvikx.data.local.dao.SearchHistoryDao
import com.satvik.satvikx.data.local.dao.TrackDao
import com.satvik.satvikx.data.local.entity.PlaylistEntity
import com.satvik.satvikx.data.local.entity.PlaylistTrackCrossRef
import com.satvik.satvikx.data.local.entity.RecentPlaybackEntity
import com.satvik.satvikx.data.local.entity.SearchHistoryEntity
import com.satvik.satvikx.data.local.entity.TrackEntity

/**
 * Enterprise Room Database for SatvikX offline-first audio caching and persistence.
 */
@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        RecentPlaybackEntity::class,
        SearchHistoryEntity::class,
        com.satvik.satvikx.data.local.entity.PlaybackTelemetryEntity::class,
        com.satvik.satvikx.data.local.entity.BlendEntity::class,
        com.satvik.satvikx.data.local.entity.BlendTrackCrossRef::class
    ],
    version = 7,
    exportSchema = false
)
abstract class SatvikXDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun recentPlaybackDao(): RecentPlaybackDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun playbackTelemetryDao(): com.satvik.satvikx.data.local.dao.PlaybackTelemetryDao

    abstract fun blendDao(): com.satvik.satvikx.data.local.dao.BlendDao

    companion object {
        const val DATABASE_NAME = "satvikx_music.db"
    }
}


