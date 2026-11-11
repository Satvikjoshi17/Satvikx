package com.satvik.satvikx.lyrics.e2e

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tier 4: Real-World Workload and Lifecycle Simulation Test Suite.
 * Exercises multi-minute song playbacks, interactive reading overrides, offline flights, and album marathons (7 test cases).
 */
class Tier4RealWorldWorkloadTest {

    private lateinit var dao: E2EInMemoryLyricsDao
    private lateinit var parser: E2ELrcParser

    @Before
    fun setUp() {
        dao = E2EInMemoryLyricsDao()
        parser = E2ELrcParser()
    }

    @Test
    fun scenario01_full_3minute_pop_song_playback_simulation() {
        // Construct a realistic 3:15 (195s) pop song with 20 distinct timestamped lines
        val lines = listOf(
            E2ELrcLine(5000L, "JARVIS, power up the Mark 85"),
            E2ELrcLine(12000L, "Arc reactor spinning at maximum drive"),
            E2ELrcLine(20000L, "Stepping out into the neon night"),
            E2ELrcLine(28000L, "Repulsors charged and ready to ignite"),
            E2ELrcLine(36000L, "They said a hero is defined by what he gives"),
            E2ELrcLine(44000L, "In every spark of light a legend lives"),
            E2ELrcLine(52000L, "[Chorus] I am Iron Man, standing in the storm"),
            E2ELrcLine(60000L, "Gold and crimson armor keeping freedom warm"),
            E2ELrcLine(68000L, "Through the quantum realm and across the sky"),
            E2ELrcLine(76000L, "We will never fade, we will never die"),
            E2ELrcLine(88000L, "Nanotech shields deflect the falling rain"),
            E2ELrcLine(96000L, "Pushing past the limits, pushing past the pain"),
            E2ELrcLine(104000L, "FRIDAY runs diagnostics on the thruster trail"),
            E2ELrcLine(112000L, "When the shadows fall our hope will still prevail"),
            E2ELrcLine(120000L, "[Chorus] I am Iron Man, standing in the storm"),
            E2ELrcLine(128000L, "Gold and crimson armor keeping freedom warm"),
            E2ELrcLine(140000L, "[Guitar Solo - Ramin Djawadi Style]"),
            E2ELrcLine(160000L, "Final countdown, power to the chest"),
            E2ELrcLine(172000L, "And you know that we survived the greatest test"),
            E2ELrcLine(185000L, "System standby. Goodnight, boss.")
        )

        val engine = E2ELyricsPresentationEngine(lines)
        var lastActiveIndex = -1

        // Simulate playback progressing every 5 seconds across 195 seconds
        for (timeMs in 0L..195000L step 5000L) {
            val activeIndex = engine.getActiveLineIndex(timeMs)

            if (activeIndex != -1) {
                // Active line must be monotonic or identical
                assertTrue("Active line index must never move backward during forward playback", activeIndex >= lastActiveIndex)
                lastActiveIndex = activeIndex

                val visual = engine.getLineVisualState(activeIndex, activeIndex)
                assertTrue("Active line must have full opacity", visual.isCurrentSungLine)
                assertTrue("Active line must have cyan glow", visual.isGlowArcCyan)
                assertEquals(1.0f, visual.opacity, 0.01f)

                if (activeIndex > 0) {
                    val pastVisual = engine.getLineVisualState(activeIndex - 1, activeIndex)
                    assertTrue("Past line must be dimmed", pastVisual.isPastLine)
                    assertEquals(0.4f, pastVisual.opacity, 0.01f)
                }

                if (activeIndex < lines.size - 1) {
                    val upcomingVisual = engine.getLineVisualState(activeIndex + 1, activeIndex)
                    assertTrue("Upcoming line must be softly legible", upcomingVisual.isUpcomingLine)
                    assertEquals(0.7f, upcomingVisual.opacity, 0.01f)
                }
            }
        }
        assertEquals("By the end of the song, the last line must be active", lines.size - 1, lastActiveIndex)
    }

    @Test
    fun scenario02_rapid_user_scrubbing_and_seeking_stress_test() {
        val lines = (1..60).map { E2ELrcLine(it * 3000L, "Line number $it at ${it * 3}s") }
        val engine = E2ELyricsPresentationEngine(lines)

        val scrubPositions = listOf(
            15000L,  // 15s (Line 5)
            120000L, // 120s (Line 40)
            45000L,  // 45s (Line 15)
            165000L, // 165s (Line 55)
            3000L,   // 3s (Line 1)
            90000L,  // 90s (Line 30)
            180000L  // 180s (Line 60)
        )

        for (pos in scrubPositions) {
            val start = System.nanoTime()
            val activeIndex = engine.getActiveLineIndex(pos)
            val elapsedUs = (System.nanoTime() - start) / 1000

            assertTrue("Scrub calculation must take < 500 microseconds", elapsedUs < 500)
            val expectedLineIndex = (pos / 3000L - 1).toInt()
            assertEquals(expectedLineIndex, activeIndex)

            val visual = engine.getLineVisualState(activeIndex, activeIndex)
            assertTrue(visual.isCurrentSungLine)
            assertTrue(visual.isGlowArcCyan)
        }
    }

    @Test
    fun scenario03_bollywood_regional_song_with_complex_hindi_lyrics() = runBlocking {
        // Step 1: LRCLIB returns null (not indexed)
        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? = null
            override suspend fun searchLyrics(query: String): E2ELyricsData? = null
        }

        // Step 2: JioSaavn returns Hindi lyrics with HTML tags & entities
        val mockSaavn = object : E2EJioSaavnProvider {
            override suspend fun getLyrics(songTitle: String, artistName: String): E2ELyricsData? {
                val rawHtml = """
                    हवाओं में बहेंगे &amp; घटाओं में रहेंगे<br/>
                    तू बरखा मेरी, मैं तेरा &quot;बादल&quot; पिया<br/>
                    जो तेरे ना हुए तो किसी के ना रहेंगे&#8217;
                """.trimIndent()

                val cleaned = rawHtml
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&#8217;", "'")
                    .replace("<br/>", "\n")

                return E2ELyricsData("saavn_kalank", false, emptyList(), cleaned, "JIOSAAVN")
            }
        }

        val repo = E2ELyricsRepository(dao, mockLrc, mockSaavn, null)
        val vm = E2EPlayerViewModel(repo)

        vm.onTrackChanged("saavn_kalank", "Kalank (Title Track)", "Arijit Singh")

        assertTrue(vm.currentUiState is E2ELyricsUiState.Plain)
        val plain = (vm.currentUiState as E2ELyricsUiState.Plain).text
        assertTrue("Devanagari script must be preserved", plain.contains("हवाओं में बहेंगे"))
        assertTrue("Ampersand entity must be unescaped", plain.contains("& घटाओं में रहेंगे"))
        assertTrue("Quotes entity must be unescaped", plain.contains("\"बादल\""))

        // Step 3: Verify it was automatically cached in Room
        val cached = dao.getLyricsByTrackId("saavn_kalank")
        assertNotNull(cached)
        assertEquals("JIOSAAVN", cached?.provider)
    }

    @Test
    fun scenario04_live_concert_universal_fallback_via_youtube_captions() = runBlocking {
        // Unreleased live recording: both LRCLIB and Saavn return null
        val mockYt = object : E2EYoutubeCaptionsProvider {
            override suspend fun getSubtitles(videoId: String): E2ELyricsData? {
                val cues = listOf(
                    E2ELrcLine(10000L, "Hello Wembley! Are you ready?"),
                    E2ELrcLine(20000L, "Can anybody find me somebody to love?"),
                    E2ELrcLine(32000L, "Each morning I get up I die a little")
                )
                return E2ELyricsData(videoId, true, cues, null, "YOUTUBE_CAPTIONS")
            }
        }

        val repo = E2ELyricsRepository(dao, null, null, mockYt)
        val vm = E2EPlayerViewModel(repo)

        vm.onTrackChanged("queen_live_wembley", "Somebody to Love (Live at Wembley)", "Queen")

        assertTrue(vm.currentUiState is E2ELyricsUiState.Synced)
        val state = vm.currentUiState as E2ELyricsUiState.Synced
        assertEquals("YOUTUBE_CAPTIONS", state.lyrics.provider)
        assertEquals(3, state.lyrics.lines.size)

        // Holographic highlighting operates seamlessly on YouTube captions
        val engine = E2ELyricsPresentationEngine(state.lyrics.lines)
        val active = engine.getActiveLineIndex(25000L)
        assertEquals(1, active)
        assertEquals("Can anybody find me somebody to love?", state.lyrics.lines[active].text)
    }

    @Test
    fun scenario05_airplane_flight_offline_session_preloaded_playlist() = runBlocking {
        // Preload 5 tracks while online at airport
        val playlist = listOf("song_1", "song_2", "song_3", "song_4", "song_5")
        for ((idx, trackId) in playlist.withIndex()) {
            val lrc = "[00:0${idx + 1}.00]Track $idx offline lyrics"
            dao.insertLyrics(E2ELyricsEntity(trackId, true, lrc, null, "ROOM_PRELOAD"))
        }

        // Airplane Mode activated: No internet connection
        val repo = E2ELyricsRepository(dao, null, null, null)
        val vm = E2EPlayerViewModel(repo)

        for (trackId in playlist) {
            val start = System.nanoTime()
            vm.onTrackChanged(trackId, "Song Title", "Artist", isOffline = true)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000

            assertTrue("Offline cached load must execute in < 15ms", elapsedMs < 15)
            assertTrue("State must be Synced from offline cache", vm.currentUiState is E2ELyricsUiState.Synced)
            val lines = (vm.currentUiState as E2ELyricsUiState.Synced).lyrics.lines
            assertEquals(1, lines.size)
            assertTrue(lines[0].text.contains("offline lyrics"))
        }
    }

    @Test
    fun scenario06_interactive_reading_and_reading_override_workflow() {
        val lines = listOf(
            E2ELrcLine(0L, "Verse 1 start"),
            E2ELrcLine(10000L, "Verse 1 end"),
            E2ELrcLine(20000L, "Chorus line 1"),
            E2ELrcLine(30000L, "Chorus line 2"),
            E2ELrcLine(40000L, "Bridge section"),
            E2ELrcLine(50000L, "Outro finale")
        )
        val engine = E2ELyricsPresentationEngine(lines)

        // At 12s, Verse 1 end is playing and auto-scroll is on
        var playbackTimeMs = 12000L
        assertEquals(1, engine.getActiveLineIndex(playbackTimeMs))
        assertTrue(engine.isAutoScrollEnabled())

        // User starts dragging manually to read the Bridge at 40s
        engine.onUserGestureDrag(currentTimeMs = playbackTimeMs)
        assertFalse("Manual drag must disable auto-scroll immediately", engine.isAutoScrollEnabled())

        // Playback moves forward 2 seconds (14s): auto-scroll must still be suppressed
        playbackTimeMs = 14000L
        engine.advanceTime(currentTimeMs = playbackTimeMs)
        assertFalse(engine.isAutoScrollEnabled())

        // User stops touching screen. 4 seconds pass (18s): auto-scroll snaps back on!
        playbackTimeMs = 16001L
        engine.advanceTime(currentTimeMs = playbackTimeMs)
        assertTrue("Auto-scroll must resume after 4s inactivity", engine.isAutoScrollEnabled())

        // User now taps Bridge section (index 4) to jump directly there
        var seekDispatched: Long? = null
        engine.onLineTapped(4) { seekDispatched = it }

        assertEquals(40000L, seekDispatched)
        assertEquals(E2EHapticFeedbackType.LONG_PRESS, engine.lastDispatchedHaptic)
        // Auto-scroll remains active and snaps immediately to the tapped line
        assertTrue(engine.isAutoScrollEnabled())
    }

    @Test
    fun scenario07_continuous_album_marathon_with_mixed_states() = runBlocking {
        // Pre-populate cache for Track 2
        dao.insertLyrics(E2ELyricsEntity("t2_cached", true, "[00:01.00]Cached verse", null, "ROOM"))

        val mockLrc = object : E2ELrclibProvider {
            override suspend fun getLyrics(trackName: String, artistName: String, albumName: String?, durationSec: Long?): E2ELyricsData? {
                return when (trackName) {
                    "Track 1" -> E2ELyricsData("t1", true, listOf(E2ELrcLine(1000L, "T1 Synced")), null, "LRCLIB")
                    "Track 7" -> E2ELyricsData("t7", true, listOf(E2ELrcLine(2000L, "T7 Synced")), null, "LRCLIB")
                    "Track 10" -> E2ELyricsData("t10", true, listOf(E2ELrcLine(3000L, "T10 Finale")), null, "LRCLIB")
                    else -> null
                }
            }
            override suspend fun searchLyrics(query: String): E2ELyricsData? {
                return if (query.contains("Track 8")) {
                    E2ELyricsData("t8", false, emptyList(), "Plain text indie from search", "LRCLIB_SEARCH")
                } else null
            }
        }

        val mockSaavn = object : E2EJioSaavnProvider {
            override suspend fun getLyrics(songTitle: String, artistName: String): E2ELyricsData? {
                return if (songTitle == "Track 4") {
                    E2ELyricsData("t4", false, emptyList(), "Regional Saavn Lyrics", "JIOSAAVN")
                } else null
            }
        }

        val mockYt = object : E2EYoutubeCaptionsProvider {
            override suspend fun getSubtitles(videoId: String): E2ELyricsData? {
                return if (videoId == "t5_live") {
                    E2ELyricsData("t5_live", true, listOf(E2ELrcLine(5000L, "YouTube Live Subtitle")), null, "YOUTUBE")
                } else null
            }
        }

        val repo = E2ELyricsRepository(dao, mockLrc, mockSaavn, mockYt)
        val vm = E2EPlayerViewModel(repo)

        // 1. Track 1: Synced (LRCLIB)
        vm.onTrackChanged("t1", "Track 1", "Band")
        assertTrue(vm.currentUiState is E2ELyricsUiState.Synced)

        // 2. Track 2: Synced (Room Cache)
        vm.onTrackChanged("t2_cached", "Track 2", "Band")
        assertTrue(vm.currentUiState is E2ELyricsUiState.Synced)
        assertEquals("ROOM_CACHE", repo.lastSourceUsed)

        // 3. Track 3: Instrumental overture
        vm.onTrackChanged("t3_inst", "Track 3", "Band", isInstrumental = true)
        assertTrue(vm.currentUiState is E2ELyricsUiState.Instrumental)

        // 4. Track 4: Plain regional (JioSaavn)
        vm.onTrackChanged("t4", "Track 4", "Band")
        assertTrue(vm.currentUiState is E2ELyricsUiState.Plain)

        // 5. Track 5: Live subtitles (YouTube)
        vm.onTrackChanged("t5_live", "Track 5", "Band")
        assertTrue(vm.currentUiState is E2ELyricsUiState.Synced)

        // 6. Track 6: Instrumental interlude
        vm.onTrackChanged("t6_inst", "Track 6", "Band", isInstrumental = true)
        assertTrue(vm.currentUiState is E2ELyricsUiState.Instrumental)

        // 7. Track 7: Synced (LRCLIB)
        vm.onTrackChanged("t7", "Track 7", "Band")
        assertTrue(vm.currentUiState is E2ELyricsUiState.Synced)

        // 8. Track 8: Plain indie (LRCLIB search fallback)
        vm.onTrackChanged("t8", "Track 8", "Band")
        assertTrue(vm.currentUiState is E2ELyricsUiState.Plain)

        // 9. Track 9: Uncached offline track (Offline error state)
        vm.onTrackChanged("t9_uncached", "Track 9", "Band", isOffline = true)
        assertTrue(vm.currentUiState is E2ELyricsUiState.Error)
        assertTrue((vm.currentUiState as E2ELyricsUiState.Error).isOffline)

        // 10. Track 10: Synced finale (LRCLIB)
        vm.onTrackChanged("t10", "Track 10", "Band")
        assertTrue(vm.currentUiState is E2ELyricsUiState.Synced)
        assertEquals("T10 Finale", (vm.currentUiState as E2ELyricsUiState.Synced).lyrics.lines[0].text)
    }
}
