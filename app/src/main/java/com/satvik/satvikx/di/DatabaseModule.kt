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
        .addMigrations(MIGRATION_4_5)
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
}
