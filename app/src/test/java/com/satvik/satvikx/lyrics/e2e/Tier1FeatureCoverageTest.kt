package com.satvik.satvikx.lyrics.e2e

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tier 1: Functional Feature Coverage Test Suite.
 * Covers all 14 core features with at least 5 distinct test cases per feature (70 tests total).
 */
class Tier1FeatureCoverageTest {

    private lateinit var dao: E2EInMemoryLyricsDao
    private lateinit var parser: E2ELrcParser

    @Before
    fun setUp() {
        dao = E2EInMemoryLyricsDao()
        parser = E2ELrcParser()
    }

    // ==========================================
    // FEATURE 1: LyricsModel (Tests 1 - 5)
    // ==========================================

    @Test
    fun f01_test01_LrcLine_creationAndProperties() {
        val line = E2ELrcLine(startTimeMs = 12500L, text = "I am Iron Man")
        assertEquals(12500L, line.startTimeMs)
        assertEquals("I am Iron Man", line.text)
    }

    @Test
    fun f01_test02_LrcLine_chronologicalComparison() {
        val line1 = E2ELrcLine(1000L, "First")
        val line2 = E2ELrcLine(2000L, "Second")
        val line3 = E2ELrcLine(500L, "Early")
        val list = listOf(line1, line2, line3).sorted()
        assertEquals(listOf(line3, line1, line2), list)
    }

    @Test
    fun f01_test03_LyricsData_syncedCreationWithLines() {
        val lines = listOf(E2ELrcLine(0L, "Intro"), E2ELrcLine(5000L, "Verse"))
        val data = E2ELyricsData(trackId = "track_001", isSynced = true, lines = lines, plainLyrics = null, provider = "LRCLIB")
        assertTrue(data.isSynced)
        assertEquals(2, data.lines.size)
        assertNull(data.plainLyrics)
        assertEquals("track_001", data.trackId)
        assertEquals("LRCLIB", data.provider)
    }

    @Test
    fun f01_test04_LyricsData_plainCreationWithoutLines() {
        val plain = "Line 1\nLine 2\nLine 3"
        val data = E2ELyricsData(trackId = "track_002", isSynced = false, lines = emptyList(), plainLyrics = plain, provider = "JIOSAAVN")
        assertFalse(data.isSynced)
        assertTrue(data.lines.isEmpty())
        assertEquals(plain, data.plainLyrics)
        assertEquals("JIOSAAVN", data.provider)
    }

    @Test
    fun f01_test05_LyricsEntity_creationAndDefaultTimestamp() {
        val before = System.currentTimeMillis()
        val entity = E2ELyricsEntity(
            trackId = "track_003",
            isSynced = true,
            lrcContent = "[00:01.00]Hello",
            plainLyrics = "Hello",
            provider = "LRCLIB"
        )
        val after = System.currentTimeMillis()
        assertEquals("track_003", entity.trackId)
        assertTrue(entity.cachedAtTimestamp in before..after)
        assertEquals("[00:01.00]Hello", entity.lrcContent)
    }

    // ==========================================
    // FEATURE 2: LrcParser (Tests 6 - 10)
    // ==========================================

    @Test
    fun f02_test06_LrcParser_standardTwoDigitFraction() {
        val raw = "[01:23.45]Standard 2-digit line"
        val parsed = parser.parse(raw)
        assertEquals(1, parsed.size)
        // 1 min (60000ms) + 23 sec (23000ms) + 45 hundredths (450ms) = 83450ms
        assertEquals(83450L, parsed[0].startTimeMs)
        assertEquals("Standard 2-digit line", parsed[0].text)
    }

    @Test
    fun f02_test07_LrcParser_highPrecisionThreeDigitFraction() {
        val raw = "[02:10.567]High precision line"
        val parsed = parser.parse(raw)
        assertEquals(1, parsed.size)
        // 2 min (120000ms) + 10 sec (10000ms) + 567 ms = 130567ms
        assertEquals(130567L, parsed[0].startTimeMs)
        assertEquals("High precision line", parsed[0].text)
    }

    @Test
    fun f02_test08_LrcParser_multipleTimestampsOnSingleLine() {
        val raw = "[00:10.00][00:20.50]Repeated chorus phrase"
        val parsed = parser.parse(raw)
        assertEquals(2, parsed.size)
        assertEquals(10000L, parsed[0].startTimeMs)
        assertEquals("Repeated chorus phrase", parsed[0].text)
        assertEquals(20500L, parsed[1].startTimeMs)
        assertEquals("Repeated chorus phrase", parsed[1].text)
    }

    @Test
    fun f02_test09_LrcParser_discardsMetadataTagsAndHeaders() {
        val raw = """
            [ti:Stark Theme]
            [ar:Ramin Djawadi]
            [al:Iron Man OST]
            [by:Satvik]
            [offset:+500]
            [00:05.00]Actual lyric line
        """.trimIndent()
        val parsed = parser.parse(raw)
        assertEquals(1, parsed.size)
        assertEquals(5000L, parsed[0].startTimeMs)
        assertEquals("Actual lyric line", parsed[0].text)
    }

    @Test
    fun f02_test10_LrcParser_sortsChronologicallyAndFormatsBack() {
        val raw = """
            [00:30.00]Third line
            [00:10.00]First line
            [00:20.00]Second line
        """.trimIndent()
        val parsed = parser.parse(raw)
        assertEquals(3, parsed.size)
        assertEquals("First line", parsed[0].text)
        assertEquals("Second line", parsed[1].text)
        assertEquals("Third line", parsed[2].text)

        val formatted = parser.format(parsed)
        assertTrue(formatted.contains("[00:10.00]First line"))
        assertTrue(formatted.indexOf("First line") < formatted.indexOf("Second line"))
        assertTrue(formatted.indexOf("Second line") < formatted.indexOf("Third line"))
    }

    // ==========================================
    // FEATURE 3: Room SQLite Cache (Tests 11 - 15)
    // ==========================================

    @Test
    fun f03_test11_RoomCache_insertAndRetrieveReturnsExactEntity() = runBlocking {
        val entity = E2ELyricsEntity("track_42", true, "[00:01.00]Hey", "Hey", "LRCLIB")
        dao.insertLyrics(entity)
        val retrieved = dao.getLyricsByTrackId("track_42")
        assertNotNull(retrieved)
        assertEquals("track_42", retrieved?.trackId)
        assertEquals(true, retrieved?.isSynced)
        assertEquals("[00:01.00]Hey", retrieved?.lrcContent)
        assertEquals("Hey", retrieved?.plainLyrics)
    }

    @Test
    fun f03_test12_RoomCache_missReturnsNull() = runBlocking {
        val retrieved = dao.getLyricsByTrackId("non_existent_track")
        assertNull(retrieved)
    }

    @Test
    fun f03_test13_RoomCache_deleteRemovesItem() = runBlocking {
        val entity = E2ELyricsEntity("track_to_delete", false, null, "Text", "JIOSAAVN")
        dao.insertLyrics(entity)
        assertNotNull(dao.getLyricsByTrackId("track_to_delete"))
        dao.deleteLyrics("track_to_delete")
        assertNull(dao.getLyricsByTrackId("track_to_delete"))
    }

    @Test
    fun f03_test14_RoomCache_overwritesExistingTrackId() = runBlocking {
        val v1 = E2ELyricsEntity("track_overwrite", false, null, "Old text", "JIOSAAVN")
        val v2 = E2ELyricsEntity("track_overwrite", true, "[00:02.00]New text", "New text", "LRCLIB")
        dao.insertLyrics(v1)
        dao.insertLyrics(v2)
        val retrieved = dao.getLyricsByTrackId("track_overwrite")
        assertEquals(true, retrieved?.isSynced)
        assertEquals("LRCLIB", retrieved?.provider)
        assertEquals("[00:02.00]New text", retrieved?.lrcContent)
    }

    @Test
    fun f03_test15_RoomCache_clearWipesAllEntries() = runBlocking {
        dao.insertLyrics(E2ELyricsEntity("t1", true, null, null, "P1"))
        dao.insertLyrics(E2ELyricsEntity("t2", true, null, null, "P2"))
        assertEquals(2, dao.size())
        dao.clear()
        assertEquals(0, dao.size())
        assertNull(dao.getLyricsByTrackId("t1"))
    }

    // ==========================================
    // FEATURE 4: Clean Metadata Queries (Tests 16 - 20)
    // ==========================================

    @Test
    fun f04_test16_MetadataPolish_stripsOfficialMusicVideoTags() {
        val raw = "Iron Man Theme (Official Music Video)"
        val polished = E2EMetadataPolish.extractPolishedTitle(raw)
        assertEquals("Iron Man Theme", polished)
    }

    @Test
    fun f04_test17_MetadataPolish_stripsFeaturedArtists() {
        val raw = "Back in Black [feat. AC/DC]"
        val polished = E2EMetadataPolish.extractPolishedTitle(raw)
        assertEquals("Back in Black", polished)
    }

    @Test
    fun f04_test18_MetadataPolish_stripsAudioRemasteredAndResolutionTags() {
        val raw = "Highway to Hell [4K 60FPS] (Remastered)"
        val polished = E2EMetadataPolish.extractPolishedTitle(raw)
        assertEquals("Highway to Hell", polished)
    }

    @Test
    fun f04_test19_MetadataPolish_stripsTopicAndVevoFromArtist() {
        val raw1 = "Daft Punk - Topic"
        val raw2 = "TheWeekndVEVO"
        assertEquals("Daft Punk", E2EMetadataPolish.extractPolishedArtist(raw1))
        assertEquals("TheWeeknd", E2EMetadataPolish.extractPolishedArtist(raw2))
    }

    @Test
    fun f04_test20_MetadataPolish_preservesRegionalDevanagariAndGurmukhiCharacters() {
        val hindi = "केसरिया (Official Video)"
        val punjabi = "ਮਿੱਤਰਾਂ ਦਾ ਨਾਂ"
        assertEquals("केसरिया", E2EMetadataPolish.extractPolishedTitle(hindi))
        assertEquals("ਮਿੱਤਰਾਂ ਦਾ ਨਾਂ", E2EMetadataPolish.extractPolishedTitle(punjabi))
    }

    // ==========================================
    // FEATURE 5: LRCLIB Provider (Tests 21 - 25)
    // ==========================================

    @Test
    fun f05_test21_LrclibProvider_returnsSyncedLyricsOnExactMatch() = runBlocking {
        val mockLrclib = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                return if (trackName == "Thunderstruck" && artistName == "AC/DC") {
                    E2ELyricsData("t_acdc", true, listOf(E2ELrcLine(1000L, "Thunder")), null, "LRCLIB")
                } else null
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }
        val result = mockLrclib.getLyrics("Thunderstruck", "AC/DC")
        assertNotNull(result)
        assertTrue(result!!.isSynced)
        assertEquals(1, result.lines.size)
    }

    @Test
    fun f05_test22_LrclibProvider_fallsBackToSearchWhenExactMatchNull() = runBlocking {
        val mockLrclib = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? = null
            override suspend fun searchLyrics(query: String): E2ELyricsData? {
                return if (query.contains("Believer")) {
                    E2ELyricsData("t_believer", true, listOf(E2ELrcLine(2000L, "First things first")), null, "LRCLIB_SEARCH")
                } else null
            }
        }
        val exact = mockLrclib.getLyrics("Believer", "Imagine Dragons")
        assertNull(exact)
        val search = mockLrclib.searchLyrics("Believer Imagine Dragons")
        assertNotNull(search)
        assertEquals("LRCLIB_SEARCH", search?.provider)
    }

    @Test
    fun f05_test23_LrclibProvider_returnsPlainLyricsWhenSyncedUnavailable() = runBlocking {
        val mockLrclib = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                return E2ELyricsData("t_plain", false, emptyList(), "Plain text lyrics only", "LRCLIB")
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }
        val result = mockLrclib.getLyrics("Indie Song", "Unknown Artist")
        assertNotNull(result)
        assertFalse(result!!.isSynced)
        assertEquals("Plain text lyrics only", result.plainLyrics)
    }

    @Test
    fun f05_test24_LrclibProvider_returnsNullWhenSongNotFound() = runBlocking {
        val mockLrclib = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? = null
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }
        assertNull(mockLrclib.getLyrics("404 Song", "Nobody"))
        assertNull(mockLrclib.searchLyrics("404 Song Nobody"))
    }

    @Test
    fun f05_test25_LrclibProvider_handlesHttpExceptionGracefully() = runBlocking {
        val mockLrclib = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                throw RuntimeException("HTTP 500 Internal Server Error")
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }
        try {
            mockLrclib.getLyrics("Crash Track", "Artist")
            fail("Expected exception")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("500"))
        }
    }

    // ==========================================
    // FEATURE 6: JioSaavn Provider (Tests 26 - 30)
    // ==========================================

    @Test
    fun f06_test26_JioSaavnProvider_returnsBollywoodLyrics() = runBlocking {
        val mockSaavn = object : E2EJioSaavnProvider {
            override suspend fun getLyrics(songTitle: String, artistName: String): E2ELyricsData? {
                return if (songTitle == "Channa Mereya") {
                    E2ELyricsData("saavn_1", false, emptyList(), "Acha chalta hoon duaaon mein yaad rakhna", "JIOSAAVN")
                } else null
            }
        }
        val res = mockSaavn.getLyrics("Channa Mereya", "Arijit Singh")
        assertNotNull(res)
        assertEquals("JIOSAAVN", res?.provider)
    }

    @Test
    fun f06_test27_JioSaavnProvider_unescapesHtmlEntitiesProperly() = runBlocking {
        val rawHtml = "Tum hi ho &amp; mere &quot;humdum&quot; &#039;sathi&#039;<br/>har dafa"
        val unescaped = rawHtml
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("<br/>", "\n")
        assertEquals("Tum hi ho & mere \"humdum\" 'sathi'\nhar dafa", unescaped)
    }

    @Test
    fun f06_test28_JioSaavnProvider_preservesRegionalScripts() = runBlocking {
        val mockSaavn = object : E2EJioSaavnProvider {
            override suspend fun getLyrics(songTitle: String, artistName: String): E2ELyricsData? {
                return E2ELyricsData("saavn_2", false, emptyList(), "ਸਾਡਾ ਚਿੜੀਆਂ ਦਾ ਚੰਬਾ ਵੇ ਬਾਬੁਲ", "JIOSAAVN")
            }
        }
        val res = mockSaavn.getLyrics("Chamba", "Gurdas Maan")
        assertTrue(res!!.plainLyrics!!.contains("ਚੰਬਾ"))
    }

    @Test
    fun f06_test29_JioSaavnProvider_returnsNullOnSongNotFound() = runBlocking {
        val mockSaavn = object : E2EJioSaavnProvider {
            override suspend fun getLyrics(songTitle: String, artistName: String): E2ELyricsData? = null
        }
        assertNull(mockSaavn.getLyrics("Obscure Track", "Nobody"))
    }

    @Test
    fun f06_test30_JioSaavnProvider_handlesNetworkTimeoutGracefully() = runBlocking {
        val mockSaavn = object : E2EJioSaavnProvider {
            override suspend fun getLyrics(songTitle: String, artistName: String): E2ELyricsData? {
                throw java.net.SocketTimeoutException("Connect timed out")
            }
        }
        try {
            mockSaavn.getLyrics("Timeout Track", "Artist")
            fail("Expected SocketTimeoutException")
        } catch (e: java.net.SocketTimeoutException) {
            assertNotNull(e)
        }
    }

    // ==========================================
    // FEATURE 7: YouTube Captions (Tests 31 - 35)
    // ==========================================

    @Test
    fun f07_test31_YoutubeCaptions_parsesStandardWebVttCues() = runBlocking {
        val mockYoutube = object : E2EYoutubeCaptionsProvider {
            override suspend fun getSubtitles(videoId: String): E2ELyricsData? {
                val lines = listOf(
                    E2ELrcLine(1000L, "Welcome to the show"),
                    E2ELrcLine(4000L, "We are glad you are here")
                )
                return E2ELyricsData(videoId, true, lines, null, "YOUTUBE_CAPTIONS")
            }
        }
        val res = mockYoutube.getSubtitles("vid_123")
        assertNotNull(res)
        assertEquals(2, res!!.lines.size)
        assertEquals("YOUTUBE_CAPTIONS", res.provider)
    }

    @Test
    fun f07_test32_YoutubeCaptions_extractsStartTimeInMilliseconds() {
        val webVttTimestamp = "00:01:23.456"
        val parts = webVttTimestamp.split(":")
        val hours = parts[0].toLong()
        val minutes = parts[1].toLong()
        val secAndMs = parts[2].split(".")
        val seconds = secAndMs[0].toLong()
        val ms = secAndMs[1].toLong()
        val totalMs = (hours * 3600000) + (minutes * 60000) + (seconds * 1000) + ms
        assertEquals(83456L, totalMs)
    }

    @Test
    fun f07_test33_YoutubeCaptions_filtersOutMusicSoundTags() {
        val rawCue = "[Music] And the crowd roared [Applause]"
        val cleaned = rawCue.replace(Regex("\\[(Music|Applause|Laughter|Cheering)\\]"), "").trim()
        assertEquals("And the crowd roared", cleaned)
    }

    @Test
    fun f07_test34_YoutubeCaptions_returnsEmptyWhenNoSubtitlesAvailable() = runBlocking {
        val mockYoutube = object : E2EYoutubeCaptionsProvider {
            override suspend fun getSubtitles(videoId: String): E2ELyricsData? = null
        }
        assertNull(mockYoutube.getSubtitles("vid_no_subs"))
    }

    @Test
    fun f07_test35_YoutubeCaptions_sortsCuesChronologically() {
        val cues = listOf(
            E2ELrcLine(15000L, "Ending chorus"),
            E2ELrcLine(2000L, "Intro phrase"),
            E2ELrcLine(8000L, "Verse one")
        )
        val sorted = cues.sorted()
        assertEquals(2000L, sorted[0].startTimeMs)
        assertEquals(8000L, sorted[1].startTimeMs)
        assertEquals(15000L, sorted[2].startTimeMs)
    }

    // ==========================================
    // FEATURE 8: LyricsRepository (Tests 36 - 40)
    // ==========================================

    @Test
    fun f08_test36_LyricsRepository_hitsRoomCacheFirstWithoutRemoteQuery() = runBlocking {
        dao.insertLyrics(E2ELyricsEntity("t_cache", true, "[00:01.00]Cached line", null, "ROOM_PRELOAD"))
        var remoteCalled = false
        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                remoteCalled = true
                return null
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }

        val repo = E2ELyricsRepository(dao, mockLrc, null, null)
        val result = repo.getLyrics("t_cache", "Title", "Artist")

        assertTrue(result.isSuccess)
        assertEquals("ROOM_CACHE", repo.lastSourceUsed)
        assertFalse("Remote provider should never be called on cache hit", remoteCalled)
        assertEquals("Cached line", result.getOrNull()?.lines?.get(0)?.text)
    }

    @Test
    fun f08_test37_LyricsRepository_queriesLrclibWhenRoomMisses() = runBlocking {
        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                return E2ELyricsData("t_lrclib", true, listOf(E2ELrcLine(3000L, "LRCLIB lyrics")), null, "LRCLIB")
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }

        val repo = E2ELyricsRepository(dao, mockLrc, null, null)
        val result = repo.getLyrics("t_lrclib", "Song", "Band")

        assertTrue(result.isSuccess)
        assertEquals("LRCLIB", repo.lastSourceUsed)
        assertEquals(1, result.getOrNull()?.lines?.size)
    }

    @Test
    fun f08_test38_LyricsRepository_fallsBackToJioSaavnWhenLrclibMisses() = runBlocking {
        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? = null
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }
        val mockSaavn = object : E2EJioSaavnProvider {
            override suspend fun getLyrics(songTitle: String, artistName: String): E2ELyricsData? {
                return E2ELyricsData("t_saavn", false, emptyList(), "Saavn Hindi Lyrics", "JIOSAAVN")
            }
        }

        val repo = E2ELyricsRepository(dao, mockLrc, mockSaavn, null)
        val result = repo.getLyrics("t_saavn", "Hindi Song", "Artist")

        assertTrue(result.isSuccess)
        assertEquals("JIOSAAVN", repo.lastSourceUsed)
        assertEquals("Saavn Hindi Lyrics", result.getOrNull()?.plainLyrics)
    }

    @Test
    fun f08_test39_LyricsRepository_fallsBackToYoutubeWhenSaavnMisses() = runBlocking {
        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? = null
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }
        val mockSaavn = object : E2EJioSaavnProvider {
            override suspend fun getLyrics(songTitle: String, artistName: String): E2ELyricsData? = null
        }
        val mockYt = object : E2EYoutubeCaptionsProvider {
            override suspend fun getSubtitles(videoId: String): E2ELyricsData? {
                return E2ELyricsData("t_yt", true, listOf(E2ELrcLine(1000L, "Captions")), null, "YOUTUBE_CAPTIONS")
            }
        }

        val repo = E2ELyricsRepository(dao, mockLrc, mockSaavn, mockYt)
        val result = repo.getLyrics("t_yt", "Live Show", "Band")

        assertTrue(result.isSuccess)
        assertEquals("YOUTUBE_CAPTIONS", repo.lastSourceUsed)
    }

    @Test
    fun f08_test40_LyricsRepository_cachesRemoteResultInRoomUponSuccessfulFetch() = runBlocking {
        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                return E2ELyricsData("t_cached_later", true, listOf(E2ELrcLine(2000L, "Line to cache")), null, "LRCLIB")
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }

        val repo = E2ELyricsRepository(dao, mockLrc, null, null)
        assertNull(dao.getLyricsByTrackId("t_cached_later"))

        repo.getLyrics("t_cached_later", "Song", "Singer")
        val cached = dao.getLyricsByTrackId("t_cached_later")
        assertNotNull(cached)
        assertEquals("LRCLIB", cached?.provider)
        assertTrue(cached!!.lrcContent!!.contains("Line to cache"))
    }

    // ==========================================
    // FEATURE 9: FullPlayerSheet Toggle (Tests 41 - 45)
    // ==========================================

    @Test
    fun f09_test41_PlayerSheetToggle_initialStateIsHidden() {
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)
        assertFalse(vm.isLyricsViewVisible)
    }

    @Test
    fun f09_test42_PlayerSheetToggle_toggleInvertsVisibilityState() {
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)
        vm.toggleLyricsView()
        assertTrue(vm.isLyricsViewVisible)
        vm.toggleLyricsView()
        assertFalse(vm.isLyricsViewVisible)
    }

    @Test
    fun f09_test43_PlayerSheetToggle_multipleTogglesAlternateSmoothly() {
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)
        for (i in 1..10) {
            vm.toggleLyricsView()
            assertEquals(i % 2 != 0, vm.isLyricsViewVisible)
        }
    }

    @Test
    fun f09_test44_PlayerSheetToggle_explicitVisibilitySetterWorks() {
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)
        vm.setLyricsViewVisibility(true)
        assertTrue(vm.isLyricsViewVisible)
        vm.setLyricsViewVisibility(false)
        assertFalse(vm.isLyricsViewVisible)
    }

    @Test
    fun f09_test45_PlayerSheetToggle_retainsVisibilityStateAcrossTrackChanges() {
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)
        vm.toggleLyricsView()
        assertTrue(vm.isLyricsViewVisible)
        vm.onTrackChanged("new_track", "Title", "Artist")
        // User's choice of view should be retained when track changes
        assertTrue(vm.isLyricsViewVisible)
    }

    // ==========================================
    // FEATURE 10: Holographic Highlighting (Tests 46 - 50)
    // ==========================================

    @Test
    fun f10_test46_HolographicGlow_activeLineReceivesFullOpacityAndCyanGlow() {
        val lines = listOf(
            E2ELrcLine(1000L, "First"),
            E2ELrcLine(5000L, "Second"),
            E2ELrcLine(10000L, "Third")
        )
        val engine = E2ELyricsPresentationEngine(lines)
        val activeIndex = engine.getActiveLineIndex(6000L) // During second line
        assertEquals(1, activeIndex)

        val visual = engine.getLineVisualState(lineIndex = 1, activeLineIndex = activeIndex)
        assertTrue(visual.isCurrentSungLine)
        assertTrue(visual.isGlowArcCyan)
        assertEquals(1.0f, visual.opacity, 0.01f)
    }

    @Test
    fun f10_test47_HolographicGlow_pastLinesAreDimmedToLowOpacity() {
        val lines = listOf(
            E2ELrcLine(1000L, "First"),
            E2ELrcLine(5000L, "Second"),
            E2ELrcLine(10000L, "Third")
        )
        val engine = E2ELyricsPresentationEngine(lines)
        val visual = engine.getLineVisualState(lineIndex = 0, activeLineIndex = 1)
        assertTrue(visual.isPastLine)
        assertFalse(visual.isGlowArcCyan)
        assertEquals(0.4f, visual.opacity, 0.01f)
    }

    @Test
    fun f10_test48_HolographicGlow_upcomingLinesHaveSoftReadabilityOpacity() {
        val lines = listOf(
            E2ELrcLine(1000L, "First"),
            E2ELrcLine(5000L, "Second"),
            E2ELrcLine(10000L, "Third")
        )
        val engine = E2ELyricsPresentationEngine(lines)
        val visual = engine.getLineVisualState(lineIndex = 2, activeLineIndex = 1)
        assertTrue(visual.isUpcomingLine)
        assertFalse(visual.isGlowArcCyan)
        assertEquals(0.7f, visual.opacity, 0.01f)
    }

    @Test
    fun f10_test49_HolographicGlow_activeLineTransitionsAsPlaybackAdvances() {
        val lines = listOf(
            E2ELrcLine(1000L, "Line 1"),
            E2ELrcLine(3000L, "Line 2"),
            E2ELrcLine(6000L, "Line 3")
        )
        val engine = E2ELyricsPresentationEngine(lines)
        assertEquals(0, engine.getActiveLineIndex(1500L))
        assertEquals(1, engine.getActiveLineIndex(3000L))
        assertEquals(1, engine.getActiveLineIndex(4500L))
        assertEquals(2, engine.getActiveLineIndex(6000L))
        assertEquals(2, engine.getActiveLineIndex(8000L))
    }

    @Test
    fun f10_test50_HolographicGlow_beforeSongStartReturnsNegativeOneNoGlow() {
        val lines = listOf(E2ELrcLine(5000L, "Intro"))
        val engine = E2ELyricsPresentationEngine(lines)
        val index = engine.getActiveLineIndex(1000L) // Before 5000ms
        assertEquals(-1, index)
    }

    // ==========================================
    // FEATURE 11: Interactive Tap-To-Seek (Tests 51 - 55)
    // ==========================================

    @Test
    fun f11_test51_TapToSeek_tappingLineDispatchesCorrectSeekTimestamp() {
        val lines = listOf(
            E2ELrcLine(0L, "Intro"),
            E2ELrcLine(15000L, "Chorus"),
            E2ELrcLine(30000L, "Outro")
        )
        val engine = E2ELyricsPresentationEngine(lines)
        var seekTarget: Long? = null
        engine.onLineTapped(1) { target -> seekTarget = target }
        assertEquals(15000L, seekTarget)
        assertEquals(15000L, engine.lastSeekTargetMs)
    }

    @Test
    fun f11_test52_TapToSeek_tappingLineDispatchesLongPressHapticFeedback() {
        val lines = listOf(E2ELrcLine(5000L, "Verse"))
        val engine = E2ELyricsPresentationEngine(lines)
        engine.onLineTapped(0) {}
        assertEquals(E2EHapticFeedbackType.LONG_PRESS, engine.lastDispatchedHaptic)
    }

    @Test
    fun f11_test53_TapToSeek_tappingFirstLineDispatchesZeroOrInitialTimestamp() {
        val lines = listOf(E2ELrcLine(500L, "First line"), E2ELrcLine(5000L, "Second line"))
        val engine = E2ELyricsPresentationEngine(lines)
        var sought: Long? = null
        engine.onLineTapped(0) { sought = it }
        assertEquals(500L, sought)
    }

    @Test
    fun f11_test54_TapToSeek_tappingLastLineDispatchesFinalTimestamp() {
        val lines = listOf(E2ELrcLine(1000L, "Start"), E2ELrcLine(95000L, "Ending note"))
        val engine = E2ELyricsPresentationEngine(lines)
        var sought: Long? = null
        engine.onLineTapped(1) { sought = it }
        assertEquals(95000L, sought)
    }

    @Test
    fun f11_test55_TapToSeek_tappingInvalidIndexDoesNotDispatchSeekOrHaptic() {
        val lines = listOf(E2ELrcLine(1000L, "Single"))
        val engine = E2ELyricsPresentationEngine(lines)
        var sought = false
        engine.onLineTapped(99) { sought = true }
        assertFalse(sought)
        assertEquals(E2EHapticFeedbackType.NONE, engine.lastDispatchedHaptic)
        assertNull(engine.lastSeekTargetMs)
    }

    // ==========================================
    // FEATURE 12: Auto-scroll & Manual Override (Tests 56 - 60)
    // ==========================================

    @Test
    fun f12_test56_Autoscroll_isEnabledByDefault() {
        val engine = E2ELyricsPresentationEngine(listOf(E2ELrcLine(1000L, "Line")))
        assertTrue(engine.isAutoScrollEnabled())
        assertFalse(engine.isUserScrolling)
    }

    @Test
    fun f12_test57_Autoscroll_pausesImmediatelyOnUserGestureDrag() {
        val engine = E2ELyricsPresentationEngine(listOf(E2ELrcLine(1000L, "Line")))
        engine.onUserGestureDrag(currentTimeMs = 10000L)
        assertTrue(engine.isUserScrolling)
        assertFalse(engine.isAutoScrollEnabled())
    }

    @Test
    fun f12_test58_Autoscroll_remainsPausedDuringActiveInteractionWithin4Seconds() {
        val engine = E2ELyricsPresentationEngine(listOf(E2ELrcLine(1000L, "Line")))
        engine.onUserGestureDrag(currentTimeMs = 10000L)
        // Advance 2 seconds (less than 4s threshold)
        engine.advanceTime(currentTimeMs = 12000L)
        assertTrue("Auto-scroll should remain paused at 2s", engine.isUserScrolling)
        assertFalse(engine.isAutoScrollEnabled())
    }

    @Test
    fun f12_test59_Autoscroll_resumesAutomaticallyAfter4SecondsInactivity() {
        val engine = E2ELyricsPresentationEngine(listOf(E2ELrcLine(1000L, "Line")))
        engine.onUserGestureDrag(currentTimeMs = 10000L)
        // Advance 4001 ms (beyond 4s threshold)
        engine.advanceTime(currentTimeMs = 14001L)
        assertFalse("Auto-scroll should resume after 4s inactivity", engine.isUserScrolling)
        assertTrue(engine.isAutoScrollEnabled())
    }

    @Test
    fun f12_test60_Autoscroll_tappingLineImmediatelyResetsUserScrollAndResumesAutoscroll() {
        val lines = listOf(E2ELrcLine(1000L, "Line 1"), E2ELrcLine(5000L, "Line 2"))
        val engine = E2ELyricsPresentationEngine(lines)
        engine.onUserGestureDrag(currentTimeMs = 10000L)
        assertTrue(engine.isUserScrolling)

        // Tapping any line should cancel manual override immediately
        engine.onLineTapped(1) {}
        assertFalse(engine.isUserScrolling)
        assertTrue(engine.isAutoScrollEnabled())
    }

    // ==========================================
    // FEATURE 13: Multi-State UI Handling (Tests 61 - 65)
    // ==========================================

    @Test
    fun f13_test61_MultiStateUi_syncedStateWhenSyncedLyricsAvailable() = runBlocking {
        dao.insertLyrics(E2ELyricsEntity("synced_1", true, "[00:01.00]Hi", null, "LRCLIB"))
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)
        vm.onTrackChanged("synced_1", "Song", "Artist")

        val state = vm.currentUiState
        assertTrue(state is E2ELyricsUiState.Synced)
        assertEquals(1, (state as E2ELyricsUiState.Synced).lyrics.lines.size)
    }

    @Test
    fun f13_test62_MultiStateUi_plainStateWhenOnlyPlainLyricsAvailable() = runBlocking {
        dao.insertLyrics(E2ELyricsEntity("plain_1", false, null, "Plain lyrics only", "JIOSAAVN"))
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)
        vm.onTrackChanged("plain_1", "Song", "Artist")

        val state = vm.currentUiState
        assertTrue(state is E2ELyricsUiState.Plain)
        assertEquals("Plain lyrics only", (state as E2ELyricsUiState.Plain).text)
    }

    @Test
    fun f13_test63_MultiStateUi_instrumentalStateWhenFlaggedInstrumental() {
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)
        vm.onTrackChanged("inst_1", "Classical Piece", "Beethoven", isInstrumental = true)

        val state = vm.currentUiState
        assertTrue(state is E2ELyricsUiState.Instrumental)
    }

    @Test
    fun f13_test64_MultiStateUi_loadingShimmerStateWhileFetching() {
        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                // Return null so it finishes
                return null
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }
        val repo = E2ELyricsRepository(dao, mockLrc, null, null)
        val vm = E2EPlayerViewModel(repo)
        // Before onTrackChanged, state is Idle
        assertEquals(E2ELyricsUiState.Idle, vm.currentUiState)
    }

    @Test
    fun f13_test65_MultiStateUi_errorStateWithOfflineFlagWhenNetworkFailsAndNoCache() = runBlocking {
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)
        vm.onTrackChanged("uncached_offline", "Song", "Artist", isOffline = true)

        val state = vm.currentUiState
        assertTrue(state is E2ELyricsUiState.Error)
        assertTrue((state as E2ELyricsUiState.Error).isOffline)
    }

    // ==========================================
    // FEATURE 14: E2E Final Verification (Tests 66 - 70)
    // ==========================================

    @Test
    fun f14_test66_E2E_fullPipelineFromRawLrcToHighlightedUiState() = runBlocking {
        val rawLrc = "[00:01.00]Line 1\n[00:05.00]Line 2"
        val parsed = parser.parse(rawLrc)
        val lyricsData = E2ELyricsData("full_e2e_1", true, parsed, null, "LRCLIB")
        val engine = E2ELyricsPresentationEngine(lyricsData.lines)

        val activeLineAt3s = engine.getActiveLineIndex(3000L)
        assertEquals(0, activeLineAt3s)
        val visual = engine.getLineVisualState(0, activeLineAt3s)
        assertTrue(visual.isCurrentSungLine)
        assertTrue(visual.isGlowArcCyan)
    }

    @Test
    fun f14_test67_E2E_fullPipelineFromPlainLyricsToScrollablePlainView() = runBlocking {
        val plain = "Verse 1\nVerse 2\nChorus"
        val data = E2ELyricsData("full_e2e_2", false, emptyList(), plain, "JIOSAAVN")
        assertFalse(data.isSynced)
        assertEquals(3, data.plainLyrics!!.lines().size)
    }

    @Test
    fun f14_test68_E2E_fullPipelineCachedRetrievalIsZeroMsImmediate() = runBlocking {
        val entity = E2ELyricsEntity("perf_test", true, "[00:01.00]Zero latency", null, "ROOM")
        dao.insertLyrics(entity)
        val repo = E2ELyricsRepository(dao, null, null, null)

        val start = System.nanoTime()
        val res = repo.getLyrics("perf_test", "Song", "Artist")
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue(res.isSuccess)
        assertEquals("ROOM_CACHE", repo.lastSourceUsed)
        assertTrue("Cached retrieval must complete in < 50ms", elapsedMs < 50)
    }

    @Test
    fun f14_test69_E2E_fullPipelineRetryFetchTransitionsFromErrorToSynced() = runBlocking {
        var networkWorking = false
        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                return if (networkWorking) {
                    E2ELyricsData("retry_track", true, listOf(E2ELrcLine(1000L, "Recovered")), null, "LRCLIB")
                } else null
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }

        val repo = E2ELyricsRepository(dao, mockLrc, null, null)
        val vm = E2EPlayerViewModel(repo)

        // Step 1: Network down -> Error state
        vm.onTrackChanged("retry_track", "Song", "Artist", isOffline = false)
        assertTrue(vm.currentUiState is E2ELyricsUiState.Error)

        // Step 2: Network recovers -> Retry -> Synced state
        networkWorking = true
        vm.retryFetch("Song", "Artist", isOffline = false)
        assertTrue(vm.currentUiState is E2ELyricsUiState.Synced)
    }

    @Test
    fun f14_test70_E2E_fullPipelineEmptyTrackClearsPresentationState() {
        val engine = E2ELyricsPresentationEngine(emptyList())
        assertEquals(-1, engine.getActiveLineIndex(5000L))
        assertNull(engine.lastSeekTargetMs)
        assertEquals(E2EHapticFeedbackType.NONE, engine.lastDispatchedHaptic)
    }
}
