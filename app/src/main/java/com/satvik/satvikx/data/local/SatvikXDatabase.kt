package com.satvik.satvikx.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.satvik.satvikx.data.local.dao.PlaylistDao
import com.satvik.satvikx.data.local.dao.RecentPlaybackDao
import com.satvik.satvikx.data.local.dao.TrackDao
import com.satvik.satvikx.data.local.entity.PlaylistEntity
import com.satvik.satvikx.data.local.entity.PlaylistTrackCrossRef
import com.satvik.satvikx.data.local.entity.RecentPlaybackEntity
import com.satvik.satvikx.data.local.entity.TrackEntity

/**
 * Enterprise Room Database for SatvikX offline-first audio caching and persistence.
 */
@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        RecentPlaybackEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class SatvikXDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun recentPlaybackDao(): RecentPlaybackDao

    companion object {
        const val DATABASE_NAME = "satvikx_music.db"
    }
}


