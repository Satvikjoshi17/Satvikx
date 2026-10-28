package com.satvik.satvikx.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.satvik.satvikx.data.local.SatvikXDatabase
import com.satvik.satvikx.data.local.dao.PlaylistDao
import com.satvik.satvikx.data.local.dao.RecentPlaybackDao
import com.satvik.satvikx.data.local.dao.SearchHistoryDao
import com.satvik.satvikx.data.local.dao.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `search_history` (
                    `query` TEXT NOT NULL PRIMARY KEY,
                    `timestamp` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playback_telemetry` (
                    `trackId` TEXT NOT NULL PRIMARY KEY,
                    `title` TEXT NOT NULL,
                    `artist` TEXT NOT NULL,
                    `playCount` INTEGER NOT NULL,
                    `completionCount` INTEGER NOT NULL,
                    `skipCount` INTEGER NOT NULL,
                    `totalListenTimeMs` INTEGER NOT NULL,
                    `lastCompletionRate` REAL NOT NULL,
                    `lastPlayedTimestamp` INTEGER NOT NULL,
                    `isLiked` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_telemetry_trackId` ON `playback_telemetry` (`trackId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_telemetry_artist` ON `playback_telemetry` (`artist`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playback_telemetry_lastPlayedTimestamp` ON `playback_telemetry` (`lastPlayedTimestamp`)")
        }
    }

    @Provides
    @Singleton
    fun provideSatvikXDatabase(
        @ApplicationContext context: Context
    ): SatvikXDatabase {
        return Room.databaseBuilder(
            context,
            SatvikXDatabase::class.java,
            SatvikXDatabase.DATABASE_NAME
        )
        .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideTrackDao(database: SatvikXDatabase): TrackDao {
        return database.trackDao()
    }

    @Provides
    @Singleton
    fun providePlaylistDao(database: SatvikXDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    @Singleton
    fun provideRecentPlaybackDao(database: SatvikXDatabase): RecentPlaybackDao {
        return database.recentPlaybackDao()
    }

    @Provides
    @Singleton
    fun provideSearchHistoryDao(database: SatvikXDatabase): SearchHistoryDao {
        return database.searchHistoryDao()
    }

    @Provides
    @Singleton
    fun providePlaybackTelemetryDao(database: SatvikXDatabase): com.satvik.satvikx.data.local.dao.PlaybackTelemetryDao {
        return database.playbackTelemetryDao()
    }
}
