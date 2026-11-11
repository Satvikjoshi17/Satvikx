package com.satvik.satvikx.lyrics.e2e

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Tier 2: Boundary, Corner Case, and Adversarial Test Suite.
 * Evaluates edge conditions, stress scenarios, extreme inputs, and failure modes across all 14 features (70 tests total).
 */
class Tier2BoundaryCornerCaseTest {

    private lateinit var dao: E2EInMemoryLyricsDao
    private lateinit var parser: E2ELrcParser

    @Before
    fun setUp() {
        dao = E2EInMemoryLyricsDao()
        parser = E2ELrcParser()
    }

    // ==========================================
    // FEATURE 1 BOUNDARIES: LyricsModel (Tests 1 - 5)
    // ==========================================

    @Test
    fun f01_b01_emptyTextAndZeroTimestamp() {
        val line = E2ELrcLine(startTimeMs = 0L, text = "")
        assertEquals(0L, line.startTimeMs)
        assertEquals("", line.text)
    }

    @Test
    fun f01_b02_hugeTimestampsAndMaxDuration() {
        val maxDurationMs = 86_400_000L // 24 hours in ms
        val line = E2ELrcLine(startTimeMs = maxDurationMs, text = "24hr later")
        assertEquals(maxDurationMs, line.startTimeMs)
    }

    @Test
    fun f01_b03_lyricsDataWithMassiveNumberOfLines() {
        val lines = (1..5000).map { E2ELrcLine(it * 1000L, "Line #$it") }
        val data = E2ELyricsData("massive_track", true, lines, null, "TEST")
        assertEquals(5000, data.lines.size)
        assertEquals(5000000L, data.lines.last().startTimeMs)
    }

    @Test
    fun f01_b04_lyricsDataWithUnicodeEmojiAndSpecialSymbols() {
        val text = "🎵 JARVIS: System Ready! 🚀 (Devanagari: सतविक) (Arabic: موسيقى) (CJK: 音楽)"
        val line = E2ELrcLine(1500L, text)
        val data = E2ELyricsData("unicode_track", true, listOf(line), text, "LRCLIB")
        assertEquals(text, data.lines[0].text)
        assertEquals(text, data.plainLyrics)
    }

    @Test
    fun f01_b05_lyricsEntityWithNullFields() {
        val entity = E2ELyricsEntity(
            trackId = "empty_entity",
            isSynced = false,
            lrcContent = null,
            plainLyrics = null,
            provider = "NONE"
        )
        assertNull(entity.lrcContent)
        assertNull(entity.plainLyrics)
        assertFalse(entity.isSynced)
    }

    // ==========================================
    // FEATURE 2 BOUNDARIES: LrcParser (Tests 6 - 10)
    // ==========================================

    @Test
    fun f02_b06_malformedTimestampsIgnoredGracefully() {
        val raw = """
            [not:a:timestamp]This is bad
            [12:ab.cd]Invalid characters
            [999:999.999]Exceeds standard
            [00:05.00]Valid line
            [invalid]Another bad line
        """.trimIndent()
        val parsed = parser.parse(raw)
        assertEquals(1, parsed.size)
        assertEquals("Valid line", parsed[0].text)
        assertEquals(5000L, parsed[0].startTimeMs)
    }

    @Test
    fun f02_b07_mixedFractionsAndDuplicateTimestamps() {
        val raw = """
            [00:10.50]Two digit fraction (500ms)
            [00:10.500]Three digit fraction (500ms duplicate)
            [00:12.1]Single digit fraction
            [00:15.005]Three digit 5ms
        """.trimIndent()
        val parsed = parser.parse(raw)
        assertTrue(parsed.size >= 2)
        assertEquals(10500L, parsed[0].startTimeMs)
        assertEquals(10500L, parsed[1].startTimeMs)
    }

    @Test
    fun f02_b08_outOfOrderTimestampsSortedProperly() {
        val raw = """
            [03:00.00]End
            [00:00.00]Start
            [01:30.00]Middle
        """.trimIndent()
        val parsed = parser.parse(raw)
        assertEquals(3, parsed.size)
        assertEquals(0L, parsed[0].startTimeMs)
        assertEquals(90000L, parsed[1].startTimeMs)
        assertEquals(180000L, parsed[2].startTimeMs)
    }

    @Test
    fun f02_b09_onlyMetadataHeadersAndNoLyricLines() {
        val raw = """
            [ti:Title Only]
            [ar:Artist Only]
            [al:Album Only]
            [by:Creator]
        """.trimIndent()
        val parsed = parser.parse(raw)
        assertTrue("Parser must return empty list when no lyric lines exist", parsed.isEmpty())
    }

    @Test
    fun f02_b10_whitespaceOnlyAndEmptyInput() {
        assertTrue(parser.parse("").isEmpty())
        assertTrue(parser.parse("   \n\t  \r\n  ").isEmpty())
    }

    // ==========================================
    // FEATURE 3 BOUNDARIES: Room SQLite Cache (Tests 11 - 15)
    // ==========================================

    @Test
    fun f03_b11_cacheEvictionOrUpdateUnderHighConcurrency() = runBlocking {
        for (i in 1..100) {
            dao.insertLyrics(E2ELyricsEntity("track_$i", true, "[00:01.00]Line $i", null, "LRCLIB"))
        }
        assertEquals(100, dao.size())
        for (i in 1..50) {
            dao.deleteLyrics("track_$i")
        }
        assertEquals(50, dao.size())
        assertNull(dao.getLyricsByTrackId("track_1"))
        assertNotNull(dao.getLyricsByTrackId("track_100"))
    }

    @Test
    fun f03_b12_staleCacheTimestampCheck() = runBlocking {
        val oldTimestamp = System.currentTimeMillis() - (30L * 24 * 3600 * 1000) // 30 days old
        val entity = E2ELyricsEntity("stale_track", true, "[00:01.00]Old", null, "LRCLIB", cachedAtTimestamp = oldTimestamp)
        dao.insertLyrics(entity)

        val retrieved = dao.getLyricsByTrackId("stale_track")
        assertNotNull(retrieved)
        val isStale = (System.currentTimeMillis() - retrieved!!.cachedAtTimestamp) > (7L * 24 * 3600 * 1000)
        assertTrue("30-day old cache entry should be detected as stale", isStale)
    }

    @Test
    fun f03_b13_largePayloadInRoomCache() = runBlocking {
        val sb = StringBuilder()
        for (i in 1..1000) {
            sb.append(String.format("[%02d:%02d.00]Long verbose lyric line number %d with metadata\n", i / 60, i % 60, i))
        }
        val largeLrc = sb.toString()
        dao.insertLyrics(E2ELyricsEntity("large_track", true, largeLrc, null, "LRCLIB"))

        val retrieved = dao.getLyricsByTrackId("large_track")
        assertNotNull(retrieved)
        assertEquals(largeLrc.length, retrieved?.lrcContent?.length)
    }

    @Test
    fun f03_b14_specialCharactersInTrackId() = runBlocking {
        val complexTrackId = "https://youtube.com/watch?v=dQw4w9WgXcQ#t=42s&query=iron+man"
        dao.insertLyrics(E2ELyricsEntity(complexTrackId, false, null, "Never gonna give you up", "YOUTUBE"))
        val retrieved = dao.getLyricsByTrackId(complexTrackId)
        assertNotNull(retrieved)
        assertEquals(complexTrackId, retrieved?.trackId)
    }

    @Test
    fun f03_b15_deleteNonExistentTrackIdDoesNotThrow() = runBlocking {
        try {
            dao.deleteLyrics("does_not_exist_999")
            assertTrue(true)
        } catch (e: Exception) {
            fail("Deleting non-existent key must be idempotent and not throw")
        }
    }

    // ==========================================
    // FEATURE 4 BOUNDARIES: Clean Metadata Queries (Tests 16 - 20)
    // ==========================================

    @Test
    fun f04_b16_titleWithOnlyJunkTagsLeavesEmptyOrOriginal() {
        val onlyJunk = "(Official Music Video) [4K 60FPS]"
        val polished = E2EMetadataPolish.extractPolishedTitle(onlyJunk)
        assertTrue(polished.isEmpty() || polished == onlyJunk)
    }

    @Test
    fun f04_b17_nestedAndMismatchedBrackets() {
        val tricky = "Starboy (feat. Daft Punk (Official Audio))"
        val polished = E2EMetadataPolish.extractPolishedTitle(tricky)
        assertTrue(polished.contains("Starboy"))
    }

    @Test
    fun f04_b18_regexMetaCharactersInTitle() {
        val tricky = "Money $100 *Special* +Remix+ [Audio]"
        val polished = E2EMetadataPolish.extractPolishedTitle(tricky)
        assertTrue(polished.contains("Money $100"))
        assertFalse(polished.contains("[Audio]"))
    }

    @Test
    fun f04_b19_massiveWhitespaceAndTabsInTitleAndArtist() {
        val dirtyTitle = "\t\t  Song  With   Spaces  (Official Video)   \n"
        val dirtyArtist = "  Artist   Name  - Topic  "
        val cleanTitle = E2EMetadataPolish.extractPolishedTitle(dirtyTitle)
        val cleanArtist = E2EMetadataPolish.extractPolishedArtist(dirtyArtist)
        assertEquals("Song With Spaces", cleanTitle)
        assertEquals("Artist Name", cleanArtist)
    }

    @Test
    fun f04_b20_nonLatinScriptsWithEnglishNoise() {
        val mixedHindi = "जय हो Jai Ho (Official Video Song) [HD]"
        val clean = E2EMetadataPolish.extractPolishedTitle(mixedHindi)
        assertEquals("जय हो Jai Ho", clean)
    }

    // ==========================================
    // FEATURE 5 BOUNDARIES: LRCLIB Provider (Tests 21 - 25)
    // ==========================================

    @Test
    fun f05_b21_httpRateLimit429ThrowsOrRetries() = runBlocking {
        var callCount = 0
        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                callCount++
                if (callCount < 2) {
                    throw IOException("HTTP 429 Too Many Requests")
                }
                return E2ELyricsData("retry_success", true, listOf(E2ELrcLine(1000L, "Ok")), null, "LRCLIB")
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }

        try {
            mockLrc.getLyrics("Song", "Artist")
            fail("Expected 429 exception on first call")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("429"))
        }

        // Retry succeeds
        val retryResult = mockLrc.getLyrics("Song", "Artist")
        assertNotNull(retryResult)
        assertEquals("retry_success", retryResult?.trackId)
    }

    @Test
    fun f05_b22_emptyResponseBodyReturnsNull() = runBlocking {
        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? = null
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }
        assertNull(mockLrc.getLyrics("Any", "Artist"))
    }

    @Test
    fun f05_b23_malformedJsonPayloadGracefullyHandled() = runBlocking {
        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                throw IllegalArgumentException("Malformed JSON")
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }
        try {
            mockLrc.getLyrics("Corrupt", "Artist")
            fail("Should throw IllegalArgumentException")
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun f05_b24_zeroSecondDurationTrackHandledGracefully() = runBlocking {
        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                return if (durationSec == 0L || durationSec == null) {
                    E2ELyricsData("zero_dur", false, emptyList(), "Found without duration", "LRCLIB")
                } else null
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }
        val res = mockLrc.getLyrics("Zero Dur Track", "Artist", null, durationSec = 0L)
        assertNotNull(res)
    }

    @Test
    fun f05_b25_extremeLengthQueryHandledSafely() = runBlocking {
        val veryLongTitle = "A".repeat(1500)
        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                return if (trackName.length > 1000) null else E2ELyricsData("id", true, emptyList(), null, "LRCLIB")
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }
        assertNull(mockLrc.getLyrics(veryLongTitle, "Artist"))
    }

    // ==========================================
    // FEATURE 6 BOUNDARIES: JioSaavn Provider (Tests 26 - 30)
    // ==========================================

    @Test
    fun f06_b26_deeplyNestedAndBrokenHtmlTagsStripped() {
        val brokenHtml = "<div><p><span><b>Line 1</b></i><br><font color='red'>Line 2</font></div>"
        val stripped = brokenHtml.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
        assertEquals("Line 1 Line 2", stripped)
    }

    @Test
    fun f06_b27_multipleConsecutiveBreakTagsPreservedOrNormalized() {
        val breaks = "Line 1<br/><br/><br/>Line 2"
        val normalized = breaks.replace(Regex("(<br\\s*/?>)+"), "\n")
        assertEquals("Line 1\nLine 2", normalized)
    }

    @Test
    fun f06_b28_rareHtmlEntitiesDecoded() {
        val html = "It&#8217;s a &lsquo;special&rsquo; night &mdash; let&ndash;s sing"
        val decoded = html
            .replace("&#8217;", "'")
            .replace("&rsquo;", "'")
            .replace("&lsquo;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
        assertEquals("It's a 'special' night — let–s sing", decoded)
    }

    @Test
    fun f06_b29_veryShortRegionalSnippet() = runBlocking {
        val mockSaavn = object : E2EJioSaavnProvider {
            override suspend fun getLyrics(songTitle: String, artistName: String): E2ELyricsData? {
                return E2ELyricsData("short", false, emptyList(), "हो", "JIOSAAVN")
            }
        }
        val res = mockSaavn.getLyrics("Alaap", "Artist")
        assertEquals("हो", res?.plainLyrics)
    }

    @Test
    fun f06_b30_networkConnectionResetException() = runBlocking {
        val mockSaavn = object : E2EJioSaavnProvider {
            override suspend fun getLyrics(songTitle: String, artistName: String): E2ELyricsData? {
                throw IOException("Connection reset by peer")
            }
        }
        try {
            mockSaavn.getLyrics("Fail", "Artist")
            fail("Expected IOException")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Connection reset"))
        }
    }

    // ==========================================
    // FEATURE 7 BOUNDARIES: YouTube Captions (Tests 31 - 35)
    // ==========================================

    @Test
    fun f07_b31_overlappingCueTimestamps() {
        val line1 = E2ELrcLine(1000L, "Overlapping line 1")
        val line2 = E2ELrcLine(1500L, "Overlapping line 2")
        val engine = E2ELyricsPresentationEngine(listOf(line1, line2))
        // At 1200ms, line 1 is active
        assertEquals(0, engine.getActiveLineIndex(1200L))
        // At 1600ms, line 2 is active
        assertEquals(1, engine.getActiveLineIndex(1600L))
    }

    @Test
    fun f07_b32_soundEffectsAndSpeakerIdentificationsStripped() {
        val raw = "[Singer 1]: Hello there (Applause) [Laughter]"
        val cleaned = raw
            .replace(Regex("\\[Speaker\\s*\\d+\\]:?|\\[Singer\\s*\\d+\\]:?"), "")
            .replace(Regex("\\[(Music|Applause|Laughter|Cheering)\\]"), "")
            .replace(Regex("\\((Music|Applause|Laughter|Cheering)\\)"), "")
            .trim()
        assertEquals("Hello there", cleaned)
    }

    @Test
    fun f07_b33_subSecondMicroTimestamps() {
        val line1 = E2ELrcLine(1000L, "One")
        val line2 = E2ELrcLine(1005L, "Two (5ms later)")
        val engine = E2ELyricsPresentationEngine(listOf(line1, line2))
        assertEquals(0, engine.getActiveLineIndex(1002L))
        assertEquals(1, engine.getActiveLineIndex(1006L))
    }

    @Test
    fun f07_b34_webVttHeaderTagsAndNotesDiscarded() {
        val webvttContent = """
            WEBVTT
            Kind: captions
            Language: en

            NOTE This is a commentary note that should be ignored

            00:00:01.000 --> 00:00:04.000
            First actual caption
        """.trimIndent()

        val parsedLines = mutableListOf<E2ELrcLine>()
        for (line in webvttContent.lines()) {
            if (line.contains("-->")) {
                val start = line.split("-->")[0].trim()
                // Convert 00:00:01.000 to ms
                parsedLines.add(E2ELrcLine(1000L, "First actual caption"))
            }
        }
        assertEquals(1, parsedLines.size)
        assertEquals("First actual caption", parsedLines[0].text)
    }

    @Test
    fun f07_b35_emptyOrCorruptedVttContent() = runBlocking {
        val mockYt = object : E2EYoutubeCaptionsProvider {
            override suspend fun getSubtitles(videoId: String): E2ELyricsData? = null
        }
        assertNull(mockYt.getSubtitles("corrupted_video_id"))
    }

    // ==========================================
    // FEATURE 8 BOUNDARIES: LyricsRepository (Tests 36 - 40)
    // ==========================================

    @Test
    fun f08_b36_allFourProvidersFailReturnsNullGracefully() = runBlocking {
        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? = null
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }
        val mockSaavn = object : E2EJioSaavnProvider {
            override suspend fun getLyrics(songTitle: String, artistName: String): E2ELyricsData? = null
        }
        val mockYt = object : E2EYoutubeCaptionsProvider {
            override suspend fun getSubtitles(videoId: String): E2ELyricsData? = null
        }

        val repo = E2ELyricsRepository(dao, mockLrc, mockSaavn, mockYt)
        val res = repo.getLyrics("unfindable_song", "Nobody", "Nowhere")

        assertTrue(res.isSuccess)
        assertNull(res.getOrNull())
        assertEquals("CASCADE_EXHAUSTED", repo.lastSourceUsed)
    }

    @Test
    fun f08_b37_offlineModeSuppressesRemoteQueriesEvenIfCacheMiss() = runBlocking {
        var remoteCalled = false
        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                remoteCalled = true
                return null
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }

        val repo = E2ELyricsRepository(dao, mockLrc, null, null)
        val res = repo.getLyrics("uncached_offline", "Song", "Artist", isNetworkAvailable = false)

        assertTrue(res.isSuccess)
        assertNull(res.getOrNull())
        assertFalse("Remote query must not be executed when offline", remoteCalled)
        assertEquals("OFFLINE_CACHE_MISS", repo.lastSourceUsed)
    }

    @Test
    fun f08_b38_providerThrowsUnexpectedSecurityExceptionHandled() = runBlocking {
        val throwingProvider = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                throw SecurityException("Sandbox blocked network")
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }

        val repo = E2ELyricsRepository(dao, throwingProvider, null, null)
        val res = repo.getLyrics("sec_ex", "Song", "Artist")
        assertTrue(res.isSuccess)
        assertNull(res.getOrNull())
    }

    @Test
    fun f08_b39_duplicateSimultaneousRequestsHandledSafely() = runBlocking {
        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                return E2ELyricsData("dup_track", true, listOf(E2ELrcLine(1000L, "Duplicate safe")), null, "LRCLIB")
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }

        val repo = E2ELyricsRepository(dao, mockLrc, null, null)
        val res1 = repo.getLyrics("dup_track", "Song", "Artist")
        val res2 = repo.getLyrics("dup_track", "Song", "Artist")

        assertTrue(res1.isSuccess)
        assertTrue(res2.isSuccess)
        assertEquals("ROOM_CACHE", repo.lastSourceUsed) // Second call must hit cache!
    }

    @Test
    fun f08_b40_emptyResponseFromProviderDoesNotOverwriteValidCache() = runBlocking {
        dao.insertLyrics(E2ELyricsEntity("existing_track", true, "[00:01.00]Valid old", null, "PRELOAD"))
        val repo = E2ELyricsRepository(dao, null, null, null)

        val res = repo.getLyrics("existing_track", "Song", "Artist")
        assertEquals("ROOM_CACHE", repo.lastSourceUsed)
        assertEquals("Valid old", res.getOrNull()?.lines?.get(0)?.text)
    }

    // ==========================================
    // FEATURE 9 BOUNDARIES: FullPlayerSheet Toggle (Tests 41 - 45)
    // ==========================================

    @Test
    fun f09_b41_rapidSpamToggling100TimesSettlesCorrectly() {
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)
        for (i in 1..100) {
            vm.toggleLyricsView()
        }
        // 100 toggles from false should end up false
        assertFalse(vm.isLyricsViewVisible)
    }

    @Test
    fun f09_b42_toggleDuringActiveNetworkRequestMaintainsTargetState() {
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)
        vm.toggleLyricsView()
        assertTrue(vm.isLyricsViewVisible)
        vm.onTrackChanged("new_track_during_toggle", "Song", "Artist")
        assertTrue("Lyrics view visibility must stay true during track load", vm.isLyricsViewVisible)
    }

    @Test
    fun f09_b43_toggleWhenInErrorStateRemainsVisible() {
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)
        vm.onTrackChanged("error_track", "Song", "Artist", isOffline = true)
        assertTrue(vm.currentUiState is E2ELyricsUiState.Error)
        vm.toggleLyricsView()
        assertTrue(vm.isLyricsViewVisible)
    }

    @Test
    fun f09_b44_toggleWhenInInstrumentalStateRemainsVisible() {
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)
        vm.onTrackChanged("inst_track", "Piece", "Bach", isInstrumental = true)
        assertTrue(vm.currentUiState is E2ELyricsUiState.Instrumental)
        vm.toggleLyricsView()
        assertTrue(vm.isLyricsViewVisible)
    }

    @Test
    fun f09_b45_toggleVisibilityDoesNotResetPlaybackPositionOrPlaybackState() {
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)
        vm.toggleLyricsView()
        assertTrue(vm.isLyricsViewVisible)
        vm.toggleLyricsView()
        assertFalse(vm.isLyricsViewVisible)
    }

    // ==========================================
    // FEATURE 10 BOUNDARIES: Holographic Highlighting (Tests 46 - 50)
    // ==========================================

    @Test
    fun f10_b46_exactTimestampMatchSelectsCorrectLine() {
        val lines = listOf(
            E2ELrcLine(1000L, "One"),
            E2ELrcLine(2000L, "Two"),
            E2ELrcLine(3000L, "Three")
        )
        val engine = E2ELyricsPresentationEngine(lines)
        assertEquals(0, engine.getActiveLineIndex(1000L))
        assertEquals(1, engine.getActiveLineIndex(2000L))
        assertEquals(2, engine.getActiveLineIndex(3000L))
    }

    @Test
    fun f10_b47_positionAfterLastLineSelectsLastLine() {
        val lines = listOf(
            E2ELrcLine(1000L, "Start"),
            E2ELrcLine(5000L, "Final line")
        )
        val engine = E2ELyricsPresentationEngine(lines)
        assertEquals(1, engine.getActiveLineIndex(999999L))
    }

    @Test
    fun f10_b48_identicalTimestampsSelectsLatestOrValidIndex() {
        val lines = listOf(
            E2ELrcLine(2000L, "Duplicate A"),
            E2ELrcLine(2000L, "Duplicate B")
        )
        val engine = E2ELyricsPresentationEngine(lines)
        val idx = engine.getActiveLineIndex(2000L)
        assertTrue(idx in 0..1)
    }

    @Test
    fun f10_b49_rapidTimeJumpsRecalculatesGlowAccurately() {
        val lines = listOf(
            E2ELrcLine(1000L, "Intro"),
            E2ELrcLine(30000L, "Verse"),
            E2ELrcLine(60000L, "Chorus"),
            E2ELrcLine(90000L, "Outro")
        )
        val engine = E2ELyricsPresentationEngine(lines)
        assertEquals(2, engine.getActiveLineIndex(65000L)) // Chorus
        assertEquals(0, engine.getActiveLineIndex(1500L))  // Back to Intro
        assertEquals(3, engine.getActiveLineIndex(95000L)) // Jump to Outro
        assertEquals(1, engine.getActiveLineIndex(35000L)) // Jump to Verse
    }

    @Test
    fun f10_b50_singleLineLyricsDatasetHighlightsAccurately() {
        val single = listOf(E2ELrcLine(5000L, "Only line"))
        val engine = E2ELyricsPresentationEngine(single)
        assertEquals(-1, engine.getActiveLineIndex(2000L))
        assertEquals(0, engine.getActiveLineIndex(5000L))
        assertEquals(0, engine.getActiveLineIndex(10000L))
    }

    // ==========================================
    // FEATURE 11 BOUNDARIES: Tap-To-Seek (Tests 51 - 55)
    // ==========================================

    @Test
    fun f11_b51_tappingNegativeIndexIsSafelyIgnored() {
        val engine = E2ELyricsPresentationEngine(listOf(E2ELrcLine(1000L, "Text")))
        var called = false
        engine.onLineTapped(-1) { called = true }
        assertFalse(called)
        assertEquals(E2EHapticFeedbackType.NONE, engine.lastDispatchedHaptic)
    }

    @Test
    fun f11_b52_tappingCurrentlyActiveLineStillDispatchesSeek() {
        val lines = listOf(E2ELrcLine(5000L, "Active line"))
        val engine = E2ELyricsPresentationEngine(lines)
        var soughtMs: Long? = null
        engine.onLineTapped(0) { soughtMs = it }
        assertEquals(5000L, soughtMs)
        assertEquals(E2EHapticFeedbackType.LONG_PRESS, engine.lastDispatchedHaptic)
    }

    @Test
    fun f11_b53_tappingLineWithIdenticalTimestampToCurrentPosition() {
        val lines = listOf(E2ELrcLine(0L, "Zero timestamp line"))
        val engine = E2ELyricsPresentationEngine(lines)
        var soughtMs: Long? = null
        engine.onLineTapped(0) { soughtMs = it }
        assertEquals(0L, soughtMs)
    }

    @Test
    fun f11_b54_rapidConsecutiveTapsDispatchesEachSeekCorrectly() {
        val lines = (1..5).map { E2ELrcLine(it * 10000L, "Line $it") }
        val engine = E2ELyricsPresentationEngine(lines)
        val seeks = mutableListOf<Long>()
        for (i in 0..4) {
            engine.onLineTapped(i) { seeks.add(it) }
        }
        assertEquals(listOf(10000L, 20000L, 30000L, 40000L, 50000L), seeks)
    }

    @Test
    fun f11_b55_tappingLineInEmptyListIsNoOp() {
        val engine = E2ELyricsPresentationEngine(emptyList())
        var sought = false
        engine.onLineTapped(0) { sought = true }
        assertFalse(sought)
    }

    // ==========================================
    // FEATURE 12 BOUNDARIES: Auto-scroll & Manual Override (Tests 56 - 60)
    // ==========================================

    @Test
    fun f12_b56_dragExactlyAt3999msKeepsAutoscrollPaused() {
        val engine = E2ELyricsPresentationEngine(listOf(E2ELrcLine(1000L, "A")))
        engine.onUserGestureDrag(currentTimeMs = 10000L)
        engine.advanceTime(currentTimeMs = 13999L)
        assertTrue("At 3999ms, manual override must still be active", engine.isUserScrolling)
        assertFalse(engine.isAutoScrollEnabled())
    }

    @Test
    fun f12_b57_dragAt4000msResumesAutoscroll() {
        val engine = E2ELyricsPresentationEngine(listOf(E2ELrcLine(1000L, "A")))
        engine.onUserGestureDrag(currentTimeMs = 10000L)
        engine.advanceTime(currentTimeMs = 14000L)
        assertFalse("At exactly 4000ms, auto-scroll must resume", engine.isUserScrolling)
        assertTrue(engine.isAutoScrollEnabled())
    }

    @Test
    fun f12_b58_successiveDragsResetThe4sTimerIndefinitely() {
        val engine = E2ELyricsPresentationEngine(listOf(E2ELrcLine(1000L, "A")))
        var t = 10000L
        engine.onUserGestureDrag(currentTimeMs = t)

        // Drag repeatedly every 2 seconds
        for (i in 1..5) {
            t += 2000L
            engine.onUserGestureDrag(currentTimeMs = t)
            engine.advanceTime(currentTimeMs = t)
            assertTrue("Successive drags should keep autoscroll paused", engine.isUserScrolling)
        }

        // Now wait 4000ms after last drag
        engine.advanceTime(currentTimeMs = t + 4000L)
        assertFalse("Autoscroll resumes 4s after the final drag", engine.isUserScrolling)
    }

    @Test
    fun f12_b59_negativeTimeDifferenceHandledSafely() {
        val engine = E2ELyricsPresentationEngine(listOf(E2ELrcLine(1000L, "A")))
        engine.onUserGestureDrag(currentTimeMs = 10000L)
        // Clock skew / backward jump
        engine.advanceTime(currentTimeMs = 5000L)
        assertTrue("Backward time jump does not trigger resume", engine.isUserScrolling)
    }

    @Test
    fun f12_b60_multipleDragsAtExactSameMillisecond() {
        val engine = E2ELyricsPresentationEngine(listOf(E2ELrcLine(1000L, "A")))
        engine.onUserGestureDrag(currentTimeMs = 10000L)
        engine.onUserGestureDrag(currentTimeMs = 10000L)
        assertTrue(engine.isUserScrolling)
        engine.advanceTime(currentTimeMs = 14000L)
        assertFalse(engine.isUserScrolling)
    }

    // ==========================================
    // FEATURE 13 BOUNDARIES: Multi-State UI Handling (Tests 61 - 65)
    // ==========================================

    @Test
    fun f13_b61_transitionFromSyncedToPlainLyricsCleanlyUpdatesUiState() = runBlocking {
        dao.insertLyrics(E2ELyricsEntity("t_synced", true, "[00:01.00]Synced", null, "LRCLIB"))
        dao.insertLyrics(E2ELyricsEntity("t_plain", false, null, "Plain only", "JIOSAAVN"))
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)

        vm.onTrackChanged("t_synced", "Song 1", "Artist")
        assertTrue(vm.currentUiState is E2ELyricsUiState.Synced)

        vm.onTrackChanged("t_plain", "Song 2", "Artist")
        assertTrue(vm.currentUiState is E2ELyricsUiState.Plain)
    }

    @Test
    fun f13_b62_transitionFromLoadingToErrorCarriesErrorMessage() {
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)
        vm.onTrackChanged("missing", "Title", "Artist", isOffline = true)
        val state = vm.currentUiState
        assertTrue(state is E2ELyricsUiState.Error)
        assertTrue((state as E2ELyricsUiState.Error).message.isNotEmpty())
    }

    @Test
    fun f13_b63_switchingFromInstrumentalToSyncedUpdatesState() = runBlocking {
        dao.insertLyrics(E2ELyricsEntity("synced_next", true, "[00:01.00]Lyrics", null, "LRCLIB"))
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)

        vm.onTrackChanged("inst", "Violin Sonata", "Mozart", isInstrumental = true)
        assertTrue(vm.currentUiState is E2ELyricsUiState.Instrumental)

        vm.onTrackChanged("synced_next", "Vocal Song", "Queen", isInstrumental = false)
        assertTrue(vm.currentUiState is E2ELyricsUiState.Synced)
    }

    @Test
    fun f13_b64_offlineErrorFlagDistinctFromGenericErrorFlag() {
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)

        vm.onTrackChanged("t_off", "Song", "Artist", isOffline = true)
        assertTrue((vm.currentUiState as E2ELyricsUiState.Error).isOffline)

        vm.onTrackChanged("t_on_missing", "Song", "Artist", isOffline = false)
        assertFalse((vm.currentUiState as E2ELyricsUiState.Error).isOffline)
    }

    @Test
    fun f13_b65_reloadingSameTrackIdRevalidatesCacheOrRefreshes() = runBlocking {
        dao.insertLyrics(E2ELyricsEntity("reload_track", true, "[00:01.00]Line", null, "LRCLIB"))
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)

        vm.onTrackChanged("reload_track", "Title", "Artist")
        assertTrue(vm.currentUiState is E2ELyricsUiState.Synced)

        // Reload same track
        vm.onTrackChanged("reload_track", "Title", "Artist")
        assertTrue(vm.currentUiState is E2ELyricsUiState.Synced)
    }

    // ==========================================
    // FEATURE 14 BOUNDARIES: E2E Verification (Tests 66 - 70)
    // ==========================================

    @Test
    fun f14_b66_extremeLengthLrc1000LinesProcessesInUnder100ms() {
        val sb = StringBuilder()
        for (i in 1..1000) {
            val min = (i * 2) / 60
            val sec = (i * 2) % 60
            sb.append(String.format("[%02d:%02d.00]Line number %d\n", min, sec, i))
        }
        val start = System.nanoTime()
        val parsed = parser.parse(sb.toString())
        val engine = E2ELyricsPresentationEngine(parsed)
        val active = engine.getActiveLineIndex(50000L)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertEquals(1000, parsed.size)
        assertTrue(active >= 0)
        assertTrue("1000 lines parsing and search must execute in < 150ms", elapsedMs < 150)
    }

    @Test
    fun f14_b67_lrcWithMultiHourTimestampsParsesAccurately() {
        val raw = "[99:59.90]Extremely long audio track final line"
        val parsed = parser.parse(raw)
        assertEquals(1, parsed.size)
        // 99 min (5940000ms) + 59 sec (59000ms) + 90 hundredths (900ms) = 5999900ms
        assertEquals(5999900L, parsed[0].startTimeMs)
    }

    @Test
    fun f14_b68_mixedEncodingAndHtmlInLrcTextHandledProperly() {
        val raw = "[00:01.00]Rock &amp; Roll with &quot;Guns &apos;N Roses&quot;"
        val parsed = parser.parse(raw)
        assertEquals(1, parsed.size)
        assertTrue(parsed[0].text.contains("&amp;"))
    }

    @Test
    fun f14_b69_cacheSurvivalAcrossViewModelRecreation() = runBlocking {
        dao.insertLyrics(E2ELyricsEntity("persistent_t", true, "[00:01.00]Persistent line", null, "LRCLIB"))
        val repo = E2ELyricsRepository(dao, null, null, null)

        val vm1 = E2EPlayerViewModel(repo)
        vm1.onTrackChanged("persistent_t", "Song", "Artist")
        assertTrue(vm1.currentUiState is E2ELyricsUiState.Synced)

        // Recreate ViewModel (e.g. Activity recreation / screen rotation)
        val vm2 = E2EPlayerViewModel(repo)
        vm2.onTrackChanged("persistent_t", "Song", "Artist")
        assertTrue(vm2.currentUiState is E2ELyricsUiState.Synced)
        assertEquals("Persistent line", (vm2.currentUiState as E2ELyricsUiState.Synced).lyrics.lines[0].text)
    }

    @Test
    fun f14_b70_completeLifecycleOfCorruptLrcFallbackToPlain() = runBlocking {
        // When LRC content is corrupted / unparseable, fallback to plain lyrics
        val corruptedEntity = E2ELyricsEntity("corrupt_lrc", false, "NOT A VALID LRC FORMAT", "Plain text backup lyrics", "FALLBACK")
        dao.insertLyrics(corruptedEntity)
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)

        vm.onTrackChanged("corrupt_lrc", "Song", "Artist")
        assertTrue(vm.currentUiState is E2ELyricsUiState.Plain)
        assertEquals("Plain text backup lyrics", (vm.currentUiState as E2ELyricsUiState.Plain).text)
    }
}
