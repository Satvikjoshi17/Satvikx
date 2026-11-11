package com.satvik.satvikx.data.repository

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Enterprise Unit Test Suite for [BlendTasteCalibrator] and Stark Blend Core Functions.
 */
class BlendCalibrationTest {

    private lateinit var calibrator: BlendTasteCalibrator
    private lateinit var moshi: Moshi

    @Before
    fun setup() {
        calibrator = BlendTasteCalibrator()
        moshi = Moshi.Builder().build()
    }

    @Test
    fun testCleanCodeExtraction_fromVariousPastedFormats() {
        // Direct clean code
        assertEquals("STKBLD:xyz123", calibrator.extractCleanCode("STKBLD:xyz123"))

        // Wrapped in deep links
        assertEquals("STKBLD:xyz123", calibrator.extractCleanCode("satvikx://blend?data=STKBLD:xyz123"))
        assertEquals("STKBLD:xyz123", calibrator.extractCleanCode("https://satvikx.app/blend?token=STKBLD:xyz123"))
        assertEquals("STKBLD:xyz123", calibrator.extractCleanCode("satvikx://blend?code=STKBLD:xyz123"))

        // Inside natural chat messages
        assertEquals("STKBLD:xyz123", calibrator.extractCleanCode("Hey join my blend! Code: STKBLD:xyz123 let's listen!"))
        assertEquals("STKBLD:xyz123", calibrator.extractCleanCode("Here is my Stark token:\nSTKBLD:xyz123\nPaste it in Playlists"))

        // Quoted and bracketed strings
        assertEquals("STKBLD:xyz123", calibrator.extractCleanCode("  \"STKBLD:xyz123\"  "))
        assertEquals("STKBLD:xyz123", calibrator.extractCleanCode("<STKBLD:xyz123>"))
        assertEquals("STKBLD:xyz123", calibrator.extractCleanCode("`STKBLD:xyz123`"))
    }

    @Test
    fun testEncodeAndDecodeInviteToken_roundTrip() {
        val payload = BlendInvitePayload(
            userName = "Satvik",
            userAvatar = "",
            topArtists = listOf("The Weeknd", "Arijit Singh", "Coldplay"),
            topGenres = listOf("Global Pop", "Bollywood", "Synthwave"),
            anchorTrackIds = listOf("34Na4j8AVgA", "BddP6PYo2gs"),
            anchorTitles = listOf("Starboy", "Kesariya"),
            anchorArtists = listOf("The Weeknd, Daft Punk", "Arijit Singh, Pritam")
        )

        val token = calibrator.encodeInviteToken(payload, moshi)
        assertTrue("Token must start with STKBLD: prefix", token.startsWith("STKBLD:"))

        val decoded = calibrator.decodeInviteToken(token, moshi)
        assertNotNull("Decoded payload must not be null", decoded)
        assertEquals("Satvik", decoded?.userName)
        assertEquals(3, decoded?.topArtists?.size)
        assertEquals(listOf("Starboy", "Kesariya"), decoded?.anchorTitles)
    }

    @Test
    fun testDecodeInviteToken_corruptedInputReturnsNull() {
        assertNull(calibrator.decodeInviteToken("STKBLD:not_a_valid_base64_or_gzip", moshi))
        assertNull(calibrator.decodeInviteToken("", moshi))
        assertNull(calibrator.decodeInviteToken("    ", moshi))
    }

    @Test
    fun testTasteMatchScore_highOverlapReachesNineties() {
        val userArtists = listOf("Arijit Singh, Pritam", "The Weeknd", "Coldplay")
        val partnerArtists = listOf("Arijit Singh", "The Weeknd, Daft Punk", "Taylor Swift")
        val userGenres = listOf("Bollywood", "Global Pop", "Synthwave")
        val partnerGenres = listOf("Bollywood Romance", "Pop", "Acoustic")
        val userTracks = listOf("Kesariya", "Starboy", "Yellow")
        val partnerTracks = listOf("Kesariya", "Starboy", "Lover")

        val (score, vibe) = calibrator.calculateTasteMatchScore(
            userArtists = userArtists,
            partnerArtists = partnerArtists,
            userGenres = userGenres,
            partnerGenres = partnerGenres,
            userTrackTitles = userTracks,
            partnerTrackTitles = partnerTracks
        )

        assertTrue("High overlap score should be >= 85%, was: $score%", score >= 85)
        assertTrue("Score should not exceed 98%", score <= 98)
        assertTrue("Vibe should reflect shared genre", vibe == "Bollywood Romance" || vibe == "Global Pop")
    }

    @Test
    fun testTasteMatchScore_disjointTastesStayInFriendlyFloor() {
        val userArtists = listOf("AC/DC", "Linkin Park")
        val partnerArtists = listOf("Rahat Fateh Ali Khan", "Nusrat Fateh Ali Khan")
        val userGenres = listOf("Rock")
        val partnerGenres = listOf("Sufi")

        val (score, _) = calibrator.calculateTasteMatchScore(
            userArtists = userArtists,
            partnerArtists = partnerArtists,
            userGenres = userGenres,
            partnerGenres = partnerGenres
        )

        assertTrue("Disjoint score must stay above floor of 70%, was: $score%", score >= 70)
        assertTrue("Disjoint score must not artificially inflate past 78%, was: $score%", score <= 78)
    }

    @Test
    fun testArtistAffinityCalibration() {
        val arijit = CuratedBlendArtist("arijit", "Arijit Singh", "Bollywood", "Romantic Soul", "Arijit Singh audio")
        val weeknd = CuratedBlendArtist("weeknd", "The Weeknd", "Global Pop", "Dark R&B", "The Weeknd audio")

        // 1. User with heavy listening history for Arijit
        val highAffinity = calibrator.calculateArtistAffinity(
            selectedArtists = listOf(arijit),
            overallTopArtists = listOf("Arijit Singh", "Pritam"),
            userVaultArtists = listOf("Arijit Singh", "Mithoon"),
            topGenres = listOf("Bollywood", "Romance")
        )
        assertTrue("Loved artist affinity should be >= 90%, was: $highAffinity%", highAffinity >= 90)

        // 2. User discovering an artist for the first time
        val discoveryAffinity = calibrator.calculateArtistAffinity(
            selectedArtists = listOf(weeknd),
            overallTopArtists = listOf("AC/DC", "Queen"),
            userVaultArtists = listOf("AC/DC"),
            topGenres = listOf("Rock", "Metal")
        )
        assertTrue("Discovery affinity should be base friendly range [74..78%], was: $discoveryAffinity%", discoveryAffinity in 74..78)
    }

    @Test
    fun testMasterCuratedTracksAndArtists_integrity() {
        assertTrue("Master curated tracks must contain at least 25 seeds", MASTER_CURATED_TRACKS.size >= 25)
        MASTER_CURATED_TRACKS.forEach { track ->
            assertTrue("ID must be valid 11-char YT id: ${track.id}", track.id.length == 11)
            assertTrue("Title must not be blank", track.title.isNotBlank())
            assertTrue("Artist must not be blank", track.artist.isNotBlank())
            assertTrue("Duration must be positive", track.durationSeconds > 60L)
        }
    }
}
