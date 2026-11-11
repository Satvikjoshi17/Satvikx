package com.satvik.satvikx

import java.io.File

fun main() {
    println("=================================================================")
    println("  SATVIK-X ROOM PERSISTENCE & MIGRATION_7_8 EMPIRICAL HARNESS   ")
    println("=================================================================")

    var passed = 0
    var total = 0

    fun check(name: String, condition: Boolean, detail: String = "") {
        total++
        if (condition) {
            passed++
            println("  [PASS] $name")
        } else {
            System.err.println("  [FAIL] $name: $detail")
            throw AssertionError("Check failed: $name - $detail")
        }
    }

    // 1. Verify LyricsEntity Source Code & Schema Contract
    val entityFile = File("app/src/main/java/com/satvik/satvikx/data/local/entity/LyricsEntity.kt")
    check("LyricsEntity.kt exists", entityFile.exists())
    val entityContent = entityFile.readText()
    check("LyricsEntity has @Entity(tableName = \"lyrics\")", entityContent.contains("@Entity(tableName = \"lyrics\")"))
    check("LyricsEntity has @PrimaryKey val trackId: String", entityContent.contains("@PrimaryKey") && entityContent.contains("val trackId: String"))
    check("LyricsEntity has val isSynced: Boolean", entityContent.contains("val isSynced: Boolean"))
    check("LyricsEntity has val lrcContent: String?", entityContent.contains("val lrcContent: String?"))
    check("LyricsEntity has val plainLyrics: String?", entityContent.contains("val plainLyrics: String?"))
    check("LyricsEntity has val provider: String", entityContent.contains("val provider: String"))
    check("LyricsEntity has val cachedAtTimestamp: Long", entityContent.contains("val cachedAtTimestamp: Long"))

    // 2. Verify LyricsDao Interface Contract & SQL Queries
    val daoFile = File("app/src/main/java/com/satvik/satvikx/data/local/dao/LyricsDao.kt")
    check("LyricsDao.kt exists", daoFile.exists())
    val daoContent = daoFile.readText()
    check("LyricsDao has @Dao", daoContent.contains("@Dao"))
    check("LyricsDao has getLyricsByTrackId query", daoContent.contains("@Query(\"SELECT * FROM lyrics WHERE trackId = :trackId LIMIT 1\")"))
    check("LyricsDao has getLyricsFlowByTrackId query", daoContent.contains("fun getLyricsFlowByTrackId(trackId: String): Flow<LyricsEntity?>"))
    check("LyricsDao has insertLyrics with OnConflictStrategy.REPLACE", daoContent.contains("@Insert(onConflict = OnConflictStrategy.REPLACE)"))
    check("LyricsDao has deleteLyrics query", daoContent.contains("@Query(\"DELETE FROM lyrics WHERE trackId = :trackId\")"))
    check("LyricsDao has clearAllLyrics query", daoContent.contains("@Query(\"DELETE FROM lyrics\")"))

    // 3. Verify SatvikXDatabase Version Upgrade & Registration
    val dbFile = File("app/src/main/java/com/satvik/satvikx/data/local/SatvikXDatabase.kt")
    check("SatvikXDatabase.kt exists", dbFile.exists())
    val dbContent = dbFile.readText()
    check("SatvikXDatabase version = 8", dbContent.contains("version = 8"))
    check("SatvikXDatabase entities includes LyricsEntity::class", dbContent.contains("LyricsEntity::class"))
    check("SatvikXDatabase declares abstract fun lyricsDao(): LyricsDao", dbContent.contains("abstract fun lyricsDao(): LyricsDao"))

    // 4. Verify DatabaseModule MIGRATION_7_8 DDL
    val moduleFile = File("app/src/main/java/com/satvik/satvikx/di/DatabaseModule.kt")
    check("DatabaseModule.kt exists", moduleFile.exists())
    val moduleContent = moduleFile.readText()
    check("DatabaseModule defines MIGRATION_7_8", moduleContent.contains("val MIGRATION_7_8 = object : Migration(7, 8)"))
    check("MIGRATION_7_8 registers in addMigrations", moduleContent.contains("addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)"))
    check("DatabaseModule provides LyricsDao", moduleContent.contains("fun provideLyricsDao(database: SatvikXDatabase): LyricsDao") && moduleContent.contains("database.lyricsDao()"))

    // 5. Verify Room Generated Implementation (SatvikXDatabase_Impl.java) Schema Expectation
    val implFile = File("app/build/kspCaches/debug/backups/java/byRounds/1/com/satvik/satvikx/data/local/SatvikXDatabase_Impl.java")
    check("SatvikXDatabase_Impl.java exists", implFile.exists())
    val implContent = implFile.readText()

    // Extract createAllTables DDL from Room processor
    check("Room createAllTables creates lyrics table", implContent.contains("CREATE TABLE IF NOT EXISTS `lyrics` (`trackId` TEXT NOT NULL, `isSynced` INTEGER NOT NULL, `lrcContent` TEXT, `plainLyrics` TEXT, `provider` TEXT NOT NULL, `cachedAtTimestamp` INTEGER NOT NULL, PRIMARY KEY(`trackId`))"))

    // Extract Room TableInfo validation columns
    check("Room TableInfo validates trackId column (TEXT, notNull=true, pk=1)",
        implContent.contains("_columnsLyrics.put(\"trackId\", new TableInfo.Column(\"trackId\", \"TEXT\", true, 1, null, TableInfo.CREATED_FROM_ENTITY))"))
    check("Room TableInfo validates isSynced column (INTEGER, notNull=true, pk=0)",
        implContent.contains("_columnsLyrics.put(\"isSynced\", new TableInfo.Column(\"isSynced\", \"INTEGER\", true, 0, null, TableInfo.CREATED_FROM_ENTITY))"))
    check("Room TableInfo validates lrcContent column (TEXT, notNull=false, pk=0)",
        implContent.contains("_columnsLyrics.put(\"lrcContent\", new TableInfo.Column(\"lrcContent\", \"TEXT\", false, 0, null, TableInfo.CREATED_FROM_ENTITY))"))
    check("Room TableInfo validates plainLyrics column (TEXT, notNull=false, pk=0)",
        implContent.contains("_columnsLyrics.put(\"plainLyrics\", new TableInfo.Column(\"plainLyrics\", \"TEXT\", false, 0, null, TableInfo.CREATED_FROM_ENTITY))"))
    check("Room TableInfo validates provider column (TEXT, notNull=true, pk=0)",
        implContent.contains("_columnsLyrics.put(\"provider\", new TableInfo.Column(\"provider\", \"TEXT\", true, 0, null, TableInfo.CREATED_FROM_ENTITY))"))
    check("Room TableInfo validates cachedAtTimestamp column (INTEGER, notNull=true, pk=0)",
        implContent.contains("_columnsLyrics.put(\"cachedAtTimestamp\", new TableInfo.Column(\"cachedAtTimestamp\", \"INTEGER\", true, 0, null, TableInfo.CREATED_FROM_ENTITY))"))

    check("Room TableInfo expects 0 foreign keys on lyrics table",
        implContent.contains("new HashSet<TableInfo.ForeignKey>(0)"))
    check("Room TableInfo expects 0 indices on lyrics table",
        implContent.contains("new HashSet<TableInfo.Index>(0)"))

    // 6. Verify Generated LyricsDao_Impl.java Query Implementation
    val daoImplFile = File("app/build/generated/ksp/debug/java/com/satvik/satvikx/data/local/dao/LyricsDao_Impl.java")
    check("LyricsDao_Impl.java exists", daoImplFile.exists())
    val daoImplContent = daoImplFile.readText()

    check("LyricsDao_Impl uses INSERT OR REPLACE INTO `lyrics`",
        daoImplContent.contains("INSERT OR REPLACE INTO `lyrics` (`trackId`,`isSynced`,`lrcContent`,`plainLyrics`,`provider`,`cachedAtTimestamp`) VALUES (?,?,?,?,?,?)"))
    check("LyricsDao_Impl binds trackId at index 1", daoImplContent.contains("statement.bindString(1, entity.getTrackId())"))
    check("LyricsDao_Impl binds isSynced as Long 1 or 0 at index 2", daoImplContent.contains("statement.bindLong(2, _tmp)"))
    check("LyricsDao_Impl binds lrcContent or null at index 3", daoImplContent.contains("statement.bindNull(3)") && daoImplContent.contains("statement.bindString(3, entity.getLrcContent())"))
    check("LyricsDao_Impl binds plainLyrics or null at index 4", daoImplContent.contains("statement.bindNull(4)") && daoImplContent.contains("statement.bindString(4, entity.getPlainLyrics())"))
    check("LyricsDao_Impl binds provider at index 5", daoImplContent.contains("statement.bindString(5, entity.getProvider())"))
    check("LyricsDao_Impl binds cachedAtTimestamp at index 6", daoImplContent.contains("statement.bindLong(6, entity.getCachedAtTimestamp())"))

    check("LyricsDao_Impl executes DELETE FROM lyrics WHERE trackId = ?",
        daoImplContent.contains("DELETE FROM lyrics WHERE trackId = ?"))
    check("LyricsDao_Impl executes DELETE FROM lyrics",
        daoImplContent.contains("DELETE FROM lyrics"))
    check("LyricsDao_Impl executes SELECT * FROM lyrics WHERE trackId = ? LIMIT 1",
        daoImplContent.contains("SELECT * FROM lyrics WHERE trackId = ? LIMIT 1"))

    // 7. MIGRATION_7_8 DDL Exact Column & Nullability Verification
    // Extract DDL string from DatabaseModule.kt
    val ddlRegex = Regex("""CREATE TABLE IF NOT EXISTS `lyrics`\s*\(([^)]+)\)""", RegexOption.DOT_MATCHES_ALL)
    val match = ddlRegex.find(moduleContent)
    check("MIGRATION_7_8 contains valid CREATE TABLE statement", match != null)
    val columnsBlock = match!!.groupValues[1]

    val lines = columnsBlock.lines().map { it.trim().trimEnd(',') }.filter { it.isNotEmpty() }
    check("MIGRATION_7_8 defines exactly 6 columns", lines.size == 6)

    val colDefs = lines.associate { line ->
        val parts = line.split("\\s+".toRegex())
        val name = parts[0].replace("`", "")
        name to line.replace("`", "")
    }

    check("MIGRATION_7_8 trackId matches TEXT NOT NULL PRIMARY KEY",
        colDefs["trackId"] == "trackId TEXT NOT NULL PRIMARY KEY")
    check("MIGRATION_7_8 isSynced matches INTEGER NOT NULL",
        colDefs["isSynced"] == "isSynced INTEGER NOT NULL")
    check("MIGRATION_7_8 lrcContent matches TEXT",
        colDefs["lrcContent"] == "lrcContent TEXT")
    check("MIGRATION_7_8 plainLyrics matches TEXT",
        colDefs["plainLyrics"] == "plainLyrics TEXT")
    check("MIGRATION_7_8 provider matches TEXT NOT NULL",
        colDefs["provider"] == "provider TEXT NOT NULL")
    check("MIGRATION_7_8 cachedAtTimestamp matches INTEGER NOT NULL",
        colDefs["cachedAtTimestamp"] == "cachedAtTimestamp INTEGER NOT NULL")

    println("\n=================================================================")
    println("  ROOM PERSISTENCE EMPIRICAL CHECKS PASSED: $passed / $total     ")
    println("  VERDICT: APPROVE                                               ")
    println("=================================================================")
}

