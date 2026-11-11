package com.satvik.satvikx.data.repository

import com.satvik.satvikx.data.local.dao.LyricsDao
import com.satvik.satvikx.data.local.entity.LyricsEntity
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.parser.LrcParser
import com.satvik.satvikx.data.remote.model.LrcLine
import com.satvik.satvikx.data.remote.model.LyricsData
import com.satvik.satvikx.data.remote.newpipe.NewPipeYouTubeEngine
import com.satvik.satvikx.data.remote.saavn.SaavnMediaEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Enterprise Unit Test Suite for [LyricsRepository] and the 4-Tier Fallback Cascade:
 *
 * Tier 1: Local Room SQLite Cache (`LyricsDao`)
 * Tier 2: LRCLIB API (`GET /api/get` + `GET /api/search` fallback)
 * Tier 3: JioSaavn Lyrics (`SaavnMediaEngine`)
 * Tier 4: YouTube Timed Captions (`NewPipeYouTubeEngine`)
 */
class LyricsRepositoryTest {

    private lateinit var fakeDao: FakeLyricsDao
    private lateinit var mockInterceptor: MockHttpInterceptor
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var saavnEngine: FakeSaavnMediaEngine
    private lateinit var ytEngine: FakeNewPipeYouTubeEngine
    private lateinit var repository: LyricsRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeLyricsDao()
        mockInterceptor = MockHttpInterceptor()
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(mockInterceptor)
            .build()
        saavnEngine = FakeSaavnMediaEngine(okHttpClient)
        ytEngine = FakeNewPipeYouTubeEngine(okHttpClient)
        repository = LyricsRepositoryImpl(
            lyricsDao = fakeDao,
            okHttpClient = okHttpClient,
            saavnMediaEngine = saavnEngine,
            newPipeYouTubeEngine = ytEngine,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        )
    }

    // =========================================================================
    // 1. Tier 1: Local Room SQLite Cache Tests
    // =========================================================================

    @Test
    fun test01_roomCacheHit_returnsCachedLyricsInstantlyWithoutNetworkCalls() = runBlocking {
        val cachedEntity = LyricsEntity(
            trackId = "track_123",
            isSynced = true,
            lrcContent = "[00:15.20] First cached line\n[00:20.50] Second cached line",
            plainLyrics = "First cached line\nSecond cached line",
            provider = "LRCLIB"
        )
        fakeDao.insertLyrics(cachedEntity)

        var remoteCalled = false
        mockInterceptor.handler = { req ->
            remoteCalled = true
            createResponse(req, 500, "Should not hit network")
        }

        val track = TrackEntity(
            id = "track_123",
            title = "Cached Song",
            artist = "Cached Artist",
            durationSeconds = 180,
            thumbnailUrl = "https://example.com/thumb.jpg"
        )

        val result = repository.getLyrics(track)

        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertEquals("track_123", data?.trackId)
        assertTrue(data!!.isSynced)
        assertEquals(2, data.lines.size)
        assertEquals(15200L, data.lines[0].startTimeMs)
        assertEquals("First cached line", data.lines[0].text)
        assertEquals("LRCLIB", data.provider)
        assertFalse("Remote endpoint should never be queried on cache hit", remoteCalled)
    }

    // =========================================================================
    // 2. Tier 2: LRCLIB API Query & Search Fallback Tests
    // =========================================================================

    @Test
    fun test02_roomCacheMiss_queriesLrclibGetAndCachesInRoom() = runBlocking {
        val rawLrc = "[00:10.00] In the beginning\\n[00:25.50] There was sound"
        mockInterceptor.handler = { req ->
            val url = req.url.toString()
            if (url.contains("/api/get")) {
                val json = """
                    {
                        "id": 999,
                        "trackName": "Kesariya",
                        "artistName": "Arijit Singh",
                        "duration": 268.0,
                        "instrumental": false,
                        "plainLyrics": "In the beginning\nThere was sound",
                        "syncedLyrics": "$rawLrc"
                    }
                """.trimIndent()
                createResponse(req, 200, json)
            } else {
                createResponse(req, 404, "Not Found")
            }
        }

        val track = TrackEntity(
            id = "vid_kesariya",
            title = "Kesariya",
            artist = "Arijit Singh",
            durationSeconds = 268,
            thumbnailUrl = "https://example.com/thumb.jpg"
        )

        val result = repository.getLyrics(track)

        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertEquals("vid_kesariya", data?.trackId)
        assertTrue(data!!.isSynced)
        assertEquals(2, data.lines.size)
        assertEquals(10000L, data.lines[0].startTimeMs)
        assertEquals("In the beginning", data.lines[0].text)
        assertEquals("LRCLIB", data.provider)

        // Verify cached in Room SQLite
        val cached = fakeDao.getLyricsByTrackId("vid_kesariya")
        assertNotNull("Should persist to local Room cache", cached)
        assertEquals("LRCLIB", cached?.provider)
        assertTrue(cached!!.isSynced)
        assertTrue(cached.lrcContent!!.contains("[00:10.00] In the beginning"))
    }

    @Test
    fun test03_lrclibGet404_fallsBackToLrclibSearchAndCaches() = runBlocking {
        val rawLrc = "[00:05.00] First things first\\n[00:12.00] I say all the words inside my head"
        mockInterceptor.handler = { req ->
            val url = req.url.toString()
            if (url.contains("/api/get")) {
                createResponse(req, 404, """{"message":"Not found"}""")
            } else if (url.contains("/api/search")) {
                val json = """
                    [
                        {
                            "id": 888,
                            "trackName": "Believer",
                            "artistName": "Imagine Dragons",
                            "duration": 204.0,
                            "instrumental": false,
                            "plainLyrics": "First things first",
                            "syncedLyrics": "$rawLrc"
                        }
                    ]
                """.trimIndent()
                createResponse(req, 200, json)
            } else {
                createResponse(req, 404, "Not Found")
            }
        }

        val track = TrackEntity(
            id = "vid_believer",
            title = "Believer",
            artist = "Imagine Dragons",
            durationSeconds = 204,
            thumbnailUrl = "https://example.com/thumb.jpg"
        )

        val result = repository.getLyrics(track)

        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertEquals("vid_believer", data?.trackId)
        assertTrue(data!!.isSynced)
        assertEquals(2, data.lines.size)
        assertEquals("First things first", data.lines[0].text)
        assertEquals("LRCLIB", data.provider)

        // Check Room persistence
        val cached = fakeDao.getLyricsByTrackId("vid_believer")
        assertNotNull(cached)
        assertEquals("LRCLIB", cached?.provider)
    }

    @Test
    fun test04_lrclibInstrumental_returnsInstrumentalData() = runBlocking {
        mockInterceptor.handler = { req ->
            if (req.url.toString().contains("/api/get")) {
                val json = """
                    {
                        "id": 111,
                        "trackName": "Interstellar Main Theme",
                        "artistName": "Hans Zimmer",
                        "duration": 245.0,
                        "instrumental": true,
                        "plainLyrics": null,
                        "syncedLyrics": null
                    }
                """.trimIndent()
                createResponse(req, 200, json)
            } else {
                createResponse(req, 404, "Not Found")
            }
        }

        val track = TrackEntity(
            id = "vid_interstellar",
            title = "Interstellar Main Theme",
            artist = "Hans Zimmer",
            durationSeconds = 245,
            thumbnailUrl = "https://example.com/thumb.jpg"
        )

        val result = repository.getLyrics(track)

        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertFalse(data!!.isSynced)
        assertTrue(data.lines.isEmpty())
        assertNull(data.plainLyrics)
    }

    // =========================================================================
    // 3. Tier 3: JioSaavn Provider Tests
    // =========================================================================

    @Test
    fun test05_lrclibFailure_fallsBackToJioSaavnAndCachesPlainLyrics() = runBlocking {
        mockInterceptor.handler = { req ->
            createResponse(req, 404, """{"error":"Track not found in LRCLIB"}""")
        }

        saavnEngine.mockSearchResults = listOf(
            TrackEntity(
                id = "saavn_song_456",
                title = "Tum Hi Ho",
                artist = "Arijit Singh",
                durationSeconds = 262,
                thumbnailUrl = "https://example.com/tum_hi_ho.jpg"
            )
        )
        saavnEngine.mockLyricsResponse = "Hum tere bin ab reh nahi sakte<br/>Tere bina kya wajood mera"

        val track = TrackEntity(
            id = "yt_tum_hi_ho",
            title = "Tum Hi Ho",
            artist = "Arijit Singh",
            durationSeconds = 262,
            thumbnailUrl = "https://example.com/thumb.jpg"
        )

        val result = repository.getLyrics(track)

        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertEquals("yt_tum_hi_ho", data?.trackId)
        assertFalse("JioSaavn provides unsynced plain lyrics", data!!.isSynced)
        assertEquals("JIOSAAVN", data.provider)
        assertEquals("Hum tere bin ab reh nahi sakte\nTere bina kya wajood mera", data.plainLyrics)

        // Verify Room persistence
        val cached = fakeDao.getLyricsByTrackId("yt_tum_hi_ho")
        assertNotNull(cached)
        assertEquals("JIOSAAVN", cached?.provider)
        assertFalse(cached!!.isSynced)
        assertNull(cached.lrcContent)
        assertEquals("Hum tere bin ab reh nahi sakte\nTere bina kya wajood mera", cached.plainLyrics)
    }

    @Test
    fun test06_jioSaavnHtmlSanitization_unescapesEntitiesAndReplacesLineBreaks() {
        val raw = "Tum hi ho &amp; mere &quot;humdum&quot; &#039;sathi&#039;<br />Pal bhar ke liye<br/>Koi humein pyaar kar le&copy;"
        val cleaned = saavnEngine.cleanSaavnLyrics(raw)
        assertEquals("Tum hi ho & mere \"humdum\" 'sathi'\nPal bhar ke liye\nKoi humein pyaar kar le", cleaned)
    }

    // =========================================================================
    // 4. Tier 4: YouTube Timed Captions Tests
    // =========================================================================

    @Test
    fun test07_saavnFailure_fallsBackToYouTubeCaptionsAndCaches() = runBlocking {
        // LRCLIB 404
        mockInterceptor.handler = { req -> createResponse(req, 404, "Not Found") }
        // JioSaavn empty
        saavnEngine.mockSearchResults = emptyList()
        saavnEngine.mockLyricsResponse = null

        // YouTube captions available
        ytEngine.mockSubtitles = listOf(
            LrcLine(5000L, "Live acoustic intro"),
            LrcLine(15000L, "Singing with the crowd")
        )

        val track = TrackEntity(
            id = "vid_youtube1",
            title = "Obscure Live Performance",
            artist = "Indie Band",
            durationSeconds = 300,
            thumbnailUrl = "https://example.com/thumb.jpg"
        )

        val result = repository.getLyrics(track)

        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertEquals("vid_youtube1", data?.trackId)
        assertTrue(data!!.isSynced)
        assertEquals("YOUTUBE", data.provider)
        assertEquals(2, data.lines.size)
        assertEquals("Live acoustic intro", data.lines[0].text)

        // Verify Room persistence
        val cached = fakeDao.getLyricsByTrackId("vid_youtube1")
        assertNotNull(cached)
        assertEquals("YOUTUBE", cached?.provider)
        assertTrue(cached!!.isSynced)
        assertTrue(cached.lrcContent!!.contains("[00:05.00] Live acoustic intro"))
    }

    // =========================================================================
    // 5. Cascade Exhausted & Edge Cases
    // =========================================================================

    @Test
    fun test08_allProvidersExhausted_returnsNullGracefully() = runBlocking {
        mockInterceptor.handler = { req -> createResponse(req, 404, "Not Found") }
        saavnEngine.mockSearchResults = emptyList()
        saavnEngine.mockLyricsResponse = null
        ytEngine.mockSubtitles = emptyList()

        val track = TrackEntity(
            id = "no_lyrics_track",
            title = "Instrumental Track",
            artist = "Artist",
            durationSeconds = 120,
            thumbnailUrl = "https://example.com/thumb.jpg"
        )

        val result = repository.getLyrics(track)
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun test09_offlineMode_returnsCachedOrNullWithoutRemoteCalls() = runBlocking {
        var remoteCalled = false
        mockInterceptor.handler = { req ->
            remoteCalled = true
            createResponse(req, 200, "{}")
        }

        // Test 1: Cache miss while offline
        val resultMiss = repository.getLyrics(
            trackId = "offline_miss",
            title = "Offline Song",
            artist = "Offline Artist",
            isNetworkAvailable = false
        )
        assertTrue(resultMiss.isSuccess)
        assertNull(resultMiss.getOrNull())
        assertFalse("Should never call remote API when offline", remoteCalled)

        // Test 2: Cache hit while offline
        fakeDao.insertLyrics(
            LyricsEntity(
                trackId = "offline_hit",
                isSynced = false,
                plainLyrics = "Offline lyrics saved earlier",
                provider = "ROOM_CACHE"
            )
        )

        val resultHit = repository.getLyrics(
            trackId = "offline_hit",
            title = "Offline Song",
            artist = "Offline Artist",
            isNetworkAvailable = false
        )
        assertTrue(resultHit.isSuccess)
        assertNotNull(resultHit.getOrNull())
        assertEquals("Offline lyrics saved earlier", resultHit.getOrNull()?.plainLyrics)
        assertFalse(remoteCalled)
    }

    @Test
    fun test10_malformedJsonFromLrclib_doesNotCrashAndCascadesToNextProvider() = runBlocking {
        mockInterceptor.handler = { req ->
            createResponse(req, 200, "MALFORMED_NON_JSON_CORRUPTED_RESPONSE{][}")
        }

        saavnEngine.mockSearchResults = listOf(
            TrackEntity(
                id = "saavn_fallback_id",
                title = "Song Title",
                artist = "Artist",
                durationSeconds = 200,
                thumbnailUrl = ""
            )
        )
        saavnEngine.mockLyricsResponse = "Clean Saavn lyrics text"

        val track = TrackEntity(
            id = "track_corrupt_lrclib",
            title = "Song Title",
            artist = "Artist",
            durationSeconds = 200,
            thumbnailUrl = ""
        )

        val result = repository.getLyrics(track)
        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertEquals("JIOSAAVN", data?.provider)
        assertEquals("Clean Saavn lyrics text", data?.plainLyrics)
    }

    @Test
    fun test11_http500FromSaavn_doesNotCrashAndCascadesToYouTube() = runBlocking {
        mockInterceptor.handler = { req -> createResponse(req, 404, "Not Found") }
        saavnEngine.shouldThrowException = true

        ytEngine.mockSubtitles = listOf(
            LrcLine(1000L, "YouTube captions after Saavn crash")
        )

        val track = TrackEntity(
            id = "track_saavn_500",
            title = "Crashing Song",
            artist = "Crashing Artist",
            durationSeconds = 150,
            thumbnailUrl = ""
        )

        val result = repository.getLyrics(track)
        assertTrue(result.isSuccess)
        val data = result.getOrNull()
        assertNotNull(data)
        assertEquals("YOUTUBE", data?.provider)
        assertEquals("YouTube captions after Saavn crash", data?.lines?.get(0)?.text)
    }

    @Test
    fun test12_cleanMetadataPolishing_stripsYouTubeClutterBeforeLrclibQuery() = runBlocking {
        var requestedTitle = ""
        var requestedArtist = ""

        mockInterceptor.handler = { req ->
            val url = req.url.toString()
            if (url.contains("/api/get")) {
                requestedTitle = req.url.queryParameter("track_name").orEmpty()
                requestedArtist = req.url.queryParameter("artist_name").orEmpty()
                createResponse(req, 200, """{"id":1,"trackName":"Kesariya","artistName":"Arijit Singh","syncedLyrics":"[00:01.00]Lyric"}""")
            } else {
                createResponse(req, 404, "Not Found")
            }
        }

        val track = TrackEntity(
            id = "vid_cluttered",
            title = "Kesariya - Brahmastra | Official Video | 4K | T-Series",
            artist = "T-Series",
            durationSeconds = 268,
            thumbnailUrl = ""
        )

        repository.getLyrics(track)
        assertEquals("Kesariya", requestedTitle)
    }

    @Test
    fun test13_getCachedLyricsAndClearCachedLyrics_operatesDirectlyOnDao() = runBlocking {
        val entity = LyricsEntity(
            trackId = "direct_dao_track",
            isSynced = true,
            lrcContent = "[00:01.00]Direct test",
            provider = "LRCLIB"
        )
        fakeDao.insertLyrics(entity)

        val retrieved = repository.getCachedLyrics("direct_dao_track")
        assertNotNull(retrieved)
        assertEquals(1, retrieved?.lines?.size)
        assertEquals("Direct test", retrieved?.lines?.get(0)?.text)

        repository.clearCachedLyrics("direct_dao_track")
        val afterClear = repository.getCachedLyrics("direct_dao_track")
        assertNull(afterClear)
    }

    // =========================================================================
    // 6. WebVTT Parser Empirical Tests
    // =========================================================================

    @Test
    fun test14_webVttParser_standardAndHighPrecisionTimestamps() {
        val vtt = """
            WEBVTT
            Kind: captions
            Language: en

            00:00:15.200 --> 00:00:19.450
            First line with hours

            01:23.456 --> 01:28.789
            Second line without hours

            00:02:10,500 --> 00:02:15,000
            Third line with comma separator
        """.trimIndent()

        val parsed = ytEngine.parseWebVtt(vtt)
        assertEquals(3, parsed.size)
        assertEquals(15200L, parsed[0].startTimeMs)
        assertEquals("First line with hours", parsed[0].text)
        assertEquals(83456L, parsed[1].startTimeMs)
        assertEquals("Second line without hours", parsed[1].text)
        assertEquals(130500L, parsed[2].startTimeMs)
        assertEquals("Third line with comma separator", parsed[2].text)
    }

    @Test
    fun test15_webVttParser_soundAnnotationsAndInlineTagsCleaned() {
        val vtt = """
            WEBVTT

            1
            00:00:05.000 --> 00:00:08.000
            <c.yellow>[Music] Welcome &amp; hello</c> <00:00:06.500>everyone! [Applause]
        """.trimIndent()

        val parsed = ytEngine.parseWebVtt(vtt)
        assertEquals(1, parsed.size)
        assertEquals(5000L, parsed[0].startTimeMs)
        assertEquals("Welcome & hello everyone!", parsed[0].text)
    }

    // =========================================================================
    // Helpers & Fakes
    // =========================================================================

    private fun createResponse(request: Request, code: Int, bodyString: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
            .body(bodyString.toResponseBody("application/json".toMediaType()))
            .build()
    }
}

class FakeLyricsDao : LyricsDao {
    private val store = ConcurrentHashMap<String, LyricsEntity>()

    override suspend fun getLyricsByTrackId(trackId: String): LyricsEntity? = store[trackId]

    override fun getLyricsFlowByTrackId(trackId: String): Flow<LyricsEntity?> = flowOf(store[trackId])

    override suspend fun insertLyrics(lyrics: LyricsEntity) {
        store[lyrics.trackId] = lyrics
    }

    override suspend fun deleteLyrics(trackId: String) {
        store.remove(trackId)
    }

    override suspend fun clearAllLyrics() {
        store.clear()
    }
}

class MockHttpInterceptor : Interceptor {
    var handler: ((Request) -> Response)? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return handler?.invoke(request) ?: Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
    }
}

class FakeSaavnMediaEngine(okHttpClient: OkHttpClient) : SaavnMediaEngine(okHttpClient) {
    var mockSearchResults: List<TrackEntity> = emptyList()
    var mockLyricsResponse: String? = null
    var shouldThrowException: Boolean = false

    override fun searchTracks(query: String): List<TrackEntity> {
        if (shouldThrowException) throw RuntimeException("Simulated Saavn network error")
        return mockSearchResults
    }

    override fun getLyricsSync(songIdOrLyricsId: String): String? {
        if (shouldThrowException) throw RuntimeException("Simulated Saavn lyrics error")
        return mockLyricsResponse?.let { cleanSaavnLyrics(it) }
    }
}

class FakeNewPipeYouTubeEngine(okHttpClient: OkHttpClient) : NewPipeYouTubeEngine(okHttpClient) {
    var mockSubtitles: List<LrcLine> = emptyList()

    override fun getSubtitles(videoId: String): List<LrcLine> {
        return mockSubtitles
    }
}
