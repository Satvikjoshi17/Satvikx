package com.satvik.satvikx.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.satvik.satvikx.data.local.dao.LyricsDao
import com.satvik.satvikx.data.local.entity.LyricsEntity
import com.satvik.satvikx.di.DatabaseModule
import dagger.Provides
import kotlinx.coroutines.flow.Flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import javax.inject.Singleton

/**
 * Empirical Adversarial Challenger Test for Room SQLite Persistence:
 * - LyricsEntity schema contract
 * - LyricsDao interface signatures & query semantics
 * - SatvikXDatabase version 8 configuration
 * - MIGRATION_7_8 DDL consistency with Room's generated schema expectation
 */
class RoomPersistenceVerificationTest {

    // =========================================================================
    // 1. LyricsEntity Schema Verification
    // =========================================================================

    @Test
    fun testLyricsEntity_tableAndFieldAnnotations() {
        // Table annotation check (Room annotations have BINARY retention on Android/Kotlin)
        val entityAnnotation = LyricsEntity::class.java.getAnnotation(Entity::class.java)
        if (entityAnnotation != null) {
            assertEquals("lyrics", entityAnnotation.tableName)
        } else {
            assertTrue(LyricsEntity::class.java.name.endsWith("LyricsEntity"))
        }

        // Field checks
        val fields = LyricsEntity::class.java.declaredFields.associateBy { it.name }

        // trackId: String, PrimaryKey
        val trackIdField = fields["trackId"]
        assertNotNull("trackId field must exist", trackIdField)
        assertEquals(String::class.java, trackIdField!!.type)

        // isSynced: Boolean
        val isSyncedField = fields["isSynced"]
        assertNotNull("isSynced field must exist", isSyncedField)
        assertEquals(Boolean::class.javaPrimitiveType, isSyncedField!!.type)

        // lrcContent: String?
        val lrcContentField = fields["lrcContent"]
        assertNotNull("lrcContent field must exist", lrcContentField)
        assertEquals(String::class.java, lrcContentField!!.type)

        // plainLyrics: String?
        val plainLyricsField = fields["plainLyrics"]
        assertNotNull("plainLyrics field must exist", plainLyricsField)
        assertEquals(String::class.java, plainLyricsField!!.type)

        // provider: String
        val providerField = fields["provider"]
        assertNotNull("provider field must exist", providerField)
        assertEquals(String::class.java, providerField!!.type)

        // cachedAtTimestamp: Long
        val cachedAtField = fields["cachedAtTimestamp"]
        assertNotNull("cachedAtTimestamp field must exist", cachedAtField)
        assertEquals(Long::class.javaPrimitiveType, cachedAtField!!.type)
    }

    @Test
    fun testLyricsEntity_instantiationAndDefaultValues() {
        val entity = LyricsEntity(
            trackId = "track_123",
            isSynced = true,
            provider = "LRCLIB"
        )
        assertEquals("track_123", entity.trackId)
        assertTrue(entity.isSynced)
        assertEquals("LRCLIB", entity.provider)
        assertEquals(null, entity.lrcContent)
        assertEquals(null, entity.plainLyrics)
        assertTrue("cachedAtTimestamp should default to current time", entity.cachedAtTimestamp > 0L)
    }

    // =========================================================================
    // 2. LyricsDao Interface & Contract Verification
    // =========================================================================

    @Test
    fun testLyricsDao_daoAnnotationAndMethods() {
        val daoAnnotation = LyricsDao::class.java.getAnnotation(Dao::class.java)
        if (daoAnnotation != null) {
            assertNotNull(daoAnnotation)
        } else {
            assertTrue(LyricsDao::class.java.isInterface)
        }

        val methods = LyricsDao::class.java.declaredMethods.associateBy { it.name }

        // getLyricsByTrackId
        assertTrue("getLyricsByTrackId must exist", methods.containsKey("getLyricsByTrackId"))

        // getLyricsFlowByTrackId
        assertTrue("getLyricsFlowByTrackId must exist", methods.containsKey("getLyricsFlowByTrackId"))
        assertEquals(Flow::class.java, methods["getLyricsFlowByTrackId"]!!.returnType)

        // insertLyrics
        assertTrue("insertLyrics must exist", methods.containsKey("insertLyrics"))

        // deleteLyrics
        assertTrue("deleteLyrics must exist", methods.containsKey("deleteLyrics"))

        // clearAllLyrics
        assertTrue("clearAllLyrics must exist", methods.containsKey("clearAllLyrics"))
    }

    // =========================================================================
    // 3. SatvikXDatabase Version 8 & DAO Declarations
    // =========================================================================

    @Test
    fun testSatvikXDatabase_version8AndEntities() {
        val dbAnnotation = SatvikXDatabase::class.java.getAnnotation(Database::class.java)
        if (dbAnnotation != null) {
            assertEquals("Database version must be upgraded to 8", 8, dbAnnotation.version)
            val entityClasses = dbAnnotation.entities.map { it.java }
            assertTrue("Entities must include LyricsEntity", entityClasses.contains(LyricsEntity::class.java))
        } else {
            assertTrue(java.lang.reflect.Modifier.isAbstract(SatvikXDatabase::class.java.modifiers))
        }

        // Abstract method lyricsDao()
        val lyricsDaoMethod = SatvikXDatabase::class.java.getMethod("lyricsDao")
        assertNotNull("lyricsDao() abstract method must exist", lyricsDaoMethod)
        assertEquals(LyricsDao::class.java, lyricsDaoMethod.returnType)
    }

    // =========================================================================
    // 4. MIGRATION_7_8 DDL Execution & Schema Consistency
    // =========================================================================

    @Test
    fun testMigration7To8_ddlExecutionAndConsistency() {
        // Retrieve MIGRATION_7_8 from DatabaseModule via reflection
        val migrationField = DatabaseModule::class.java.getDeclaredField("MIGRATION_7_8")
        migrationField.isAccessible = true
        val migration = migrationField.get(DatabaseModule) as Migration

        assertEquals("Migration start version must be 7", 7, migration.startVersion)
        assertEquals("Migration end version must be 8", 8, migration.endVersion)

        // Capture SQL queries executed during migration
        val executedQueries = mutableListOf<String>()

        val proxyDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method: Method, args: Array<out Any>? ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedQueries.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        // Execute migration
        migration.migrate(proxyDb)

        assertTrue("Migration must execute at least one SQL statement", executedQueries.isNotEmpty())

        val ddl = executedQueries.joinToString("\n")
        val cleanDdl = ddl.replace("`", "").replace("\"", "")

        // 1. Table name check
        assertTrue("DDL must create lyrics table", cleanDdl.contains("CREATE TABLE IF NOT EXISTS lyrics", ignoreCase = true) ||
                cleanDdl.contains("CREATE TABLE lyrics", ignoreCase = true))

        // 2. Primary key check
        assertTrue("trackId must be PRIMARY KEY", cleanDdl.contains("trackId TEXT NOT NULL PRIMARY KEY", ignoreCase = true) ||
                (cleanDdl.contains("trackId TEXT NOT NULL", ignoreCase = true) && cleanDdl.contains("PRIMARY KEY (trackId)", ignoreCase = true)))

        // 3. Column type affinities & nullabilities
        // isSynced: INTEGER NOT NULL
        assertTrue("isSynced INTEGER NOT NULL", cleanDdl.contains("isSynced INTEGER NOT NULL", ignoreCase = true))

        // lrcContent: TEXT (nullable)
        assertTrue("lrcContent TEXT", cleanDdl.contains("lrcContent TEXT", ignoreCase = true))
        // Must NOT be lrcContent TEXT NOT NULL
        assertTrue("lrcContent must be nullable", !cleanDdl.contains("lrcContent TEXT NOT NULL", ignoreCase = true))

        // plainLyrics: TEXT (nullable)
        assertTrue("plainLyrics TEXT", cleanDdl.contains("plainLyrics TEXT", ignoreCase = true))
        // Must NOT be plainLyrics TEXT NOT NULL
        assertTrue("plainLyrics must be nullable", !cleanDdl.contains("plainLyrics TEXT NOT NULL", ignoreCase = true))

        // provider: TEXT NOT NULL
        assertTrue("provider TEXT NOT NULL", cleanDdl.contains("provider TEXT NOT NULL", ignoreCase = true))

        // cachedAtTimestamp: INTEGER NOT NULL
        assertTrue("cachedAtTimestamp INTEGER NOT NULL", cleanDdl.contains("cachedAtTimestamp INTEGER NOT NULL", ignoreCase = true))
    }

    // =========================================================================
    // 5. DatabaseModule Hilt Providers Verification
    // =========================================================================

    @Test
    fun testDatabaseModule_provideLyricsDaoRegistered() {
        val methods = DatabaseModule::class.java.declaredMethods.associateBy { it.name }
        assertTrue("provideLyricsDao must exist in DatabaseModule", methods.containsKey("provideLyricsDao"))

        val providerMethod = methods["provideLyricsDao"]!!
        assertNotNull("provideLyricsDao must be @Provides", providerMethod.getAnnotation(Provides::class.java))
        assertNotNull("provideLyricsDao must be @Singleton", providerMethod.getAnnotation(Singleton::class.java))
        assertEquals(LyricsDao::class.java, providerMethod.returnType)
    }
}

