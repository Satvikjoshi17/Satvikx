package com.satvik.satvikx.lyrics.e2e

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tier 3: Pairwise Cross-Feature Integration Test Suite.
 * Validates interface contracts and end-to-end interactions between adjacent architectural modules (15 test cases).
 */
class Tier3CrossFeatureIntegrationTest {

    private lateinit var dao: E2EInMemoryLyricsDao
    private lateinit var parser: E2ELrcParser

    @Before
    fun setUp() {
        dao = E2EInMemoryLyricsDao()
        parser = E2ELrcParser()
    }

    @Test
    fun cross01_lrclibProvider_to_lrcParser_to_lyricsModel() = runBlocking {
        val rawLrc = "[00:05.00]Intro beat\n[00:15.50]Guitar solo begins"
        val mockLrclib = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                val lines = parser.parse(rawLrc)
                return E2ELyricsData("track_cross_1", true, lines, null, "LRCLIB")
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }

        val data = mockLrclib.getLyrics("Iron Man", "Black Sabbath")
        assertNotNull(data)
        assertTrue(data!!.isSynced)
        assertEquals(2, data.lines.size)
        assertEquals(5000L, data.lines[0].startTimeMs)
        assertEquals(15500L, data.lines[1].startTimeMs)
    }

    @Test
    fun cross02_cleanMetadata_to_lrclibSearch_to_roomCache() = runBlocking {
        val rawTitle = "Starboy (Official Music Video) [4K]"
        val rawArtist = "The Weeknd feat. Daft Punk - Topic"

        val polishedTitle = E2EMetadataPolish.extractPolishedTitle(rawTitle)
        val polishedArtist = E2EMetadataPolish.extractPolishedArtist(rawArtist)
        assertEquals("Starboy", polishedTitle)
        assertEquals("The Weeknd", polishedArtist)

        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? = null
            override suspend fun searchLyrics(query: String): E2ELyricsData? {
                return if (query.contains("Starboy")) {
                    E2ELyricsData("starboy_1", true, listOf(E2ELrcLine(1000L, "Look what you've done")), null, "LRCLIB_SEARCH")
                } else null
            }
        }

        val repo = E2ELyricsRepository(dao, mockLrc, null, null)
        val res = repo.getLyrics("starboy_1", rawTitle, rawArtist)

        assertTrue(res.isSuccess)
        val cached = dao.getLyricsByTrackId("starboy_1")
        assertNotNull(cached)
        assertTrue(cached!!.lrcContent!!.contains("Look what you've done"))
    }

    @Test
    fun cross03_roomCacheHit_to_lrcParser_to_holographicHighlight() = runBlocking {
        val rawLrc = "[00:02.00]First verse\n[00:08.00]Second verse\n[00:15.00]Chorus line"
        dao.insertLyrics(E2ELyricsEntity("t_hit", true, rawLrc, null, "ROOM_PRELOAD"))

        val repo = E2ELyricsRepository(dao, null, null, null)
        val result = repo.getLyrics("t_hit", "Any", "Any")
        assertTrue(result.isSuccess)

        val lyricsData = result.getOrNull()!!
        val engine = E2ELyricsPresentationEngine(lyricsData.lines)

        val activeIndex = engine.getActiveLineIndex(10000L) // During second verse (8s..15s)
        assertEquals(1, activeIndex)
        val visual = engine.getLineVisualState(1, activeIndex)
        assertTrue(visual.isCurrentSungLine)
        assertTrue(visual.isGlowArcCyan)
    }

    @Test
    fun cross04_lrclibFailure_to_jiosaavnFallback_to_plainTextUiState() = runBlocking {
        val failingLrclib = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                throw RuntimeException("LRCLIB unavailable")
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }
        val mockSaavn = object : E2EJioSaavnProvider {
            override suspend fun getLyrics(songTitle: String, artistName: String): E2ELyricsData? {
                return E2ELyricsData("saavn_fall", false, emptyList(), "Kesariya tera ishq hai piya", "JIOSAAVN")
            }
        }

        val repo = E2ELyricsRepository(dao, failingLrclib, mockSaavn, null)
        val vm = E2EPlayerViewModel(repo)
        vm.onTrackChanged("saavn_fall", "Kesariya", "Arijit Singh")

        val state = vm.currentUiState
        assertTrue(state is E2ELyricsUiState.Plain)
        assertEquals("Kesariya tera ishq hai piya", (state as E2ELyricsUiState.Plain).text)
    }

    @Test
    fun cross05_saavnFailure_to_youtubeCaptions_to_syncedUiState() = runBlocking {
        val mockYt = object : E2EYoutubeCaptionsProvider {
            override suspend fun getSubtitles(videoId: String): E2ELyricsData? {
                val lines = listOf(
                    E2ELrcLine(1000L, "Universal fallback line 1"),
                    E2ELrcLine(5000L, "Universal fallback line 2")
                )
                return E2ELyricsData(videoId, true, lines, null, "YOUTUBE_CAPTIONS")
            }
        }

        val repo = E2ELyricsRepository(dao, null, null, mockYt)
        val vm = E2EPlayerViewModel(repo)
        vm.onTrackChanged("yt_track", "Indie Song", "Unknown Artist")

        val state = vm.currentUiState
        assertTrue(state is E2ELyricsUiState.Synced)
        assertEquals(2, (state as E2ELyricsUiState.Synced).lyrics.lines.size)
        assertEquals("YOUTUBE_CAPTIONS", (state).lyrics.provider)
    }

    @Test
    fun cross06_holographicHighlighting_to_tapToSeek_to_playbackPositionUpdate() {
        val lines = listOf(
            E2ELrcLine(0L, "Intro"),
            E2ELrcLine(10000L, "Verse 1"),
            E2ELrcLine(25000L, "Verse 2"),
            E2ELrcLine(40000L, "Chorus")
        )
        val engine = E2ELyricsPresentationEngine(lines)

        // At 12s, Verse 1 is active
        var currentPositionMs = 12000L
        assertEquals(1, engine.getActiveLineIndex(currentPositionMs))

        // User taps Chorus (index 3)
        engine.onLineTapped(3) { soughtMs ->
            currentPositionMs = soughtMs
        }

        assertEquals(40000L, currentPositionMs)
        val newActiveIndex = engine.getActiveLineIndex(currentPositionMs)
        assertEquals(3, newActiveIndex)
        val visual = engine.getLineVisualState(3, newActiveIndex)
        assertTrue(visual.isCurrentSungLine)
    }

    @Test
    fun cross07_tapToSeek_to_manualOverrideCancellation_to_autoscrollSync() {
        val lines = listOf(E2ELrcLine(1000L, "A"), E2ELrcLine(5000L, "B"))
        val engine = E2ELyricsPresentationEngine(lines)

        // User scrolls manually
        engine.onUserGestureDrag(currentTimeMs = 10000L)
        assertFalse(engine.isAutoScrollEnabled())

        // User taps line B
        var tapped = false
        engine.onLineTapped(1) { tapped = true }

        assertTrue(tapped)
        // Manual override should be immediately canceled, auto-scroll re-enabled
        assertTrue(engine.isAutoScrollEnabled())
        assertFalse(engine.isUserScrolling)
    }

    @Test
    fun cross08_userGestureDrag_to_autoscrollPause_to_fourSecondTimeoutResume() {
        val engine = E2ELyricsPresentationEngine(listOf(E2ELrcLine(1000L, "Line")))

        engine.onUserGestureDrag(currentTimeMs = 5000L)
        assertFalse(engine.isAutoScrollEnabled())

        // 3 seconds later (less than 4s): still paused
        engine.advanceTime(currentTimeMs = 8000L)
        assertFalse(engine.isAutoScrollEnabled())

        // 4.1 seconds after initial drag: resumed
        engine.advanceTime(currentTimeMs = 9100L)
        assertTrue(engine.isAutoScrollEnabled())
    }

    @Test
    fun cross09_playerSheetToggle_to_holographicCrossfade_to_highlightPreservation() = runBlocking {
        dao.insertLyrics(E2ELyricsEntity("toggle_test", true, "[00:10.00]Line 1\n[00:20.00]Line 2", null, "LRCLIB"))
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)

        vm.onTrackChanged("toggle_test", "Song", "Artist")
        assertFalse(vm.isLyricsViewVisible) // Default is album art

        // Toggle to lyrics view
        vm.toggleLyricsView()
        assertTrue(vm.isLyricsViewVisible)

        val state = vm.currentUiState
        assertTrue(state is E2ELyricsUiState.Synced)
    }

    @Test
    fun cross10_playerSheetToggle_to_instrumentalTrack_to_instrumentalBadgeDisplay() {
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)

        vm.onTrackChanged("inst_toggle", "Theme", "Ramin Djawadi", isInstrumental = true)
        vm.toggleLyricsView()

        assertTrue(vm.isLyricsViewVisible)
        assertTrue(vm.currentUiState is E2ELyricsUiState.Instrumental)
    }

    @Test
    fun cross11_networkFailure_to_offlineMode_to_roomCacheRetrieval() = runBlocking {
        dao.insertLyrics(E2ELyricsEntity("offline_t", true, "[00:01.00]Offline available", null, "ROOM"))
        val repo = E2ELyricsRepository(dao, null, null, null)

        val result = repo.getLyrics("offline_t", "Song", "Artist", isNetworkAvailable = false)
        assertTrue(result.isSuccess)
        assertEquals("ROOM_CACHE", repo.lastSourceUsed)
        assertEquals("Offline available", result.getOrNull()?.lines?.get(0)?.text)
    }

    @Test
    fun cross12_networkFailureAndRoomMiss_to_errorState_to_retryButtonClick() = runBlocking {
        var networkAvailable = false
        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                return if (networkAvailable) {
                    E2ELyricsData("retry_flow", true, listOf(E2ELrcLine(2000L, "Success on retry")), null, "LRCLIB")
                } else null
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }

        val repo = E2ELyricsRepository(dao, mockLrc, null, null)
        val vm = E2EPlayerViewModel(repo)

        // Offline attempt
        vm.onTrackChanged("retry_flow", "Song", "Artist", isOffline = true)
        assertTrue(vm.currentUiState is E2ELyricsUiState.Error)
        assertTrue((vm.currentUiState as E2ELyricsUiState.Error).isOffline)

        // User reconnects and taps retry
        networkAvailable = true
        vm.retryFetch("Song", "Artist", isOffline = false)
        assertTrue(vm.currentUiState is E2ELyricsUiState.Synced)
        assertEquals("Success on retry", (vm.currentUiState as E2ELyricsUiState.Synced).lyrics.lines[0].text)
    }

    @Test
    fun cross13_trackChangeEvent_to_cacheInvalidation_to_shimmerTransition_to_newLyrics() = runBlocking {
        dao.insertLyrics(E2ELyricsEntity("song_A", true, "[00:01.00]Song A lyrics", null, "LRCLIB"))
        dao.insertLyrics(E2ELyricsEntity("song_B", true, "[00:01.00]Song B lyrics", null, "LRCLIB"))
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)

        vm.onTrackChanged("song_A", "Song A", "Artist")
        val stateA = vm.currentUiState as E2ELyricsUiState.Synced
        assertEquals("Song A lyrics", stateA.lyrics.lines[0].text)

        vm.onTrackChanged("song_B", "Song B", "Artist")
        val stateB = vm.currentUiState as E2ELyricsUiState.Synced
        assertEquals("Song B lyrics", stateB.lyrics.lines[0].text)
    }

    @Test
    fun cross14_multiTimestampLrcLine_to_chronologicalOrdering_to_tapToSeek() {
        val rawLrc = "[00:10.00][00:40.00]Repeated chorus phrase"
        val parsed = parser.parse(rawLrc)
        assertEquals(2, parsed.size)
        assertEquals(10000L, parsed[0].startTimeMs)
        assertEquals(40000L, parsed[1].startTimeMs)

        val engine = E2ELyricsPresentationEngine(parsed)
        var sought1: Long? = null
        var sought2: Long? = null

        engine.onLineTapped(0) { sought1 = it }
        engine.onLineTapped(1) { sought2 = it }

        assertEquals(10000L, sought1)
        assertEquals(40000L, sought2)
    }

    @Test
    fun cross15_cleanMetadataPolish_to_jiosaavnHtmlUnescape_to_lyricsData() = runBlocking {
        val dirtyTitle = "Tum Hi Ho (From \"Aashiqui 2\") [Official Video]"
        val polishedTitle = E2EMetadataPolish.extractPolishedTitle(dirtyTitle)

        val mockSaavn = object : E2EJioSaavnProvider {
            override suspend fun getLyrics(songTitle: String, artistName: String): E2ELyricsData? {
                val rawHtml = "Hum tere bin ab reh nahi sakte &amp; tere bina kya wajood mera"
                val unescaped = rawHtml.replace("&amp;", "&")
                return E2ELyricsData("saavn_tum", false, emptyList(), unescaped, "JIOSAAVN")
            }
        }

        val repo = E2ELyricsRepository(dao, null, mockSaavn, null)
        val res = repo.getLyrics("saavn_tum", dirtyTitle, "Arijit Singh")

        assertTrue(res.isSuccess)
        val plain = res.getOrNull()?.plainLyrics
        assertNotNull(plain)
        assertTrue(plain!!.contains("& tere bina"))
        assertFalse(plain.contains("&amp;"))
    }
}
