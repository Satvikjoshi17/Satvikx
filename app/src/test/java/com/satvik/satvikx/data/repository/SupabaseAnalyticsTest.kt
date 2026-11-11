package com.satvik.satvikx.data.repository

import com.satvik.satvikx.data.remote.supabase.dto.PlaybackSessionAnalyticsDto
import com.satvik.satvikx.data.remote.supabase.dto.ProfileDto
import com.satvik.satvikx.data.remote.supabase.dto.UserAnalyticsDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SupabaseAnalyticsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun formatListeningSecondsHuman(totalSeconds: Long): String {
        if (totalSeconds <= 0L) return "0s"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    private fun getIndiaTimestamp(timeMs: Long): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a 'IST'", Locale.ENGLISH)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        return sdf.format(Date(timeMs))
    }

    @Test
    fun `formatListeningSecondsHuman handles various durations correctly`() {
        assertEquals("0s", formatListeningSecondsHuman(0L))
        assertEquals("0s", formatListeningSecondsHuman(-50L))
        assertEquals("45s", formatListeningSecondsHuman(45L))
        assertEquals("1m 0s", formatListeningSecondsHuman(60L))
        assertEquals("2m 5s", formatListeningSecondsHuman(125L))
        assertEquals("1h 0m 0s", formatListeningSecondsHuman(3600L))
        assertEquals("2h 37m 32s", formatListeningSecondsHuman(9452L))
        assertEquals("24h 0m 0s", formatListeningSecondsHuman(86400L))
    }

    @Test
    fun `getIndiaTimestamp formats with Indian Standard Time UTC plus 5 30`() {
        // Fixed UTC timestamp: 2026-11-11 07:00:00 UTC -> 12:30:00 PM IST
        val fixedUtcEpochMs = 1794380400000L
        val formatted = getIndiaTimestamp(fixedUtcEpochMs)
        assertTrue("Timestamp should contain IST", formatted.contains("IST"))
        assertTrue("Timestamp should contain date components", formatted.contains("11") && formatted.contains("Nov"))
    }

    @Test
    fun `UserAnalyticsDto serialization and deserialization round trip`() {
        val dto = UserAnalyticsDto(
            userId = "test-user-12345",
            displayName = "Satvik Operative",
            email = "satvik@example.com",
            isGuest = false,
            avatar = "⚡",
            totalListeningSeconds = 9452L,
            listeningTimeHuman = "2h 37m 32s",
            totalSongsPlayed = 42,
            totalCompletedSongs = 38,
            totalSkips = 4,
            lastPlayedSong = "Starboy",
            lastPlayedArtist = "The Weeknd",
            topArtists = "The Weeknd, Daft Punk, Arijit Singh",
            topGenres = "Pop, Synthwave, Bollywood",
            likedSongsCount = 15,
            playlistsCount = 3,
            blendsCount = 2,
            deviceInfo = "Nothing A142 (Android 14, API 34)",
            appVersion = "1.0.17",
            timezone = "Asia/Kolkata (IST)",
            lastActiveIst = "11 Nov 2026, 01:45:10 PM IST",
            updatedAtIst = "11 Nov 2026, 01:45:10 PM IST"
        )

        val serialized = json.encodeToString(dto)
        assertTrue(serialized.contains("\"user_id\":\"test-user-12345\""))
        assertTrue(serialized.contains("\"total_listening_seconds\":9452"))
        assertTrue(serialized.contains("\"listening_time_human\":\"2h 37m 32s\""))
        assertTrue(serialized.contains("\"last_active_ist\":\"11 Nov 2026, 01:45:10 PM IST\""))

        val decoded = json.decodeFromString<UserAnalyticsDto>(serialized)
        assertEquals(dto.userId, decoded.userId)
        assertEquals(dto.totalListeningSeconds, decoded.totalListeningSeconds)
        assertEquals(dto.listeningTimeHuman, decoded.listeningTimeHuman)
        assertEquals(dto.lastActiveIst, decoded.lastActiveIst)
        assertEquals(dto.deviceInfo, decoded.deviceInfo)
    }

    @Test
    fun `PlaybackSessionAnalyticsDto serialization round trip`() {
        val session = PlaybackSessionAnalyticsDto(
            userId = "user-abc-99",
            userName = "Satvik",
            trackId = "track_yt_01",
            trackTitle = "Blinding Lights",
            trackArtist = "The Weeknd",
            listenedSeconds = 195L,
            trackDurationSeconds = 200L,
            completionPercent = 97.5f,
            isCompleted = true,
            isSkipped = false,
            timestampIst = "11 Nov 2026, 02:00:00 PM IST"
        )

        val serialized = json.encodeToString(session)
        assertTrue(serialized.contains("\"track_title\":\"Blinding Lights\""))
        assertTrue(serialized.contains("\"listened_seconds\":195"))
        assertTrue(serialized.contains("\"timestamp_ist\":\"11 Nov 2026, 02:00:00 PM IST\""))

        val decoded = json.decodeFromString<PlaybackSessionAnalyticsDto>(serialized)
        assertEquals("Blinding Lights", decoded.trackTitle)
        assertEquals(195L, decoded.listenedSeconds)
        assertEquals(true, decoded.isCompleted)
    }

    @Test
    fun `ProfileDto backward compatible with new analytics fields`() {
        val profile = ProfileDto(
            id = "user_777",
            displayName = "Iron Man",
            avatarUrl = "⚡",
            isGuest = false,
            listeningSeconds = 1200L,
            listeningTimeHuman = "20m 0s",
            lastActiveIst = "11 Nov 2026, 01:30:00 PM IST",
            deviceModel = "Pixel 8 Pro (Android 14, API 34)"
        )

        val serialized = json.encodeToString(profile)
        assertTrue(serialized.contains("\"listening_time_human\":\"20m 0s\""))
        assertTrue(serialized.contains("\"last_active_ist\":\"11 Nov 2026, 01:30:00 PM IST\""))
        assertTrue(serialized.contains("\"device_model\":\"Pixel 8 Pro (Android 14, API 34)\""))

        val decoded = json.decodeFromString<ProfileDto>(serialized)
        assertEquals("20m 0s", decoded.listeningTimeHuman)
        assertEquals("Pixel 8 Pro (Android 14, API 34)", decoded.deviceModel)

        // Verify deserializing legacy profile json without new fields
        val legacyJson = """{"id":"user_legacy","display_name":"Tony","avatar_url":"⚡","is_guest":true,"listening_seconds":500}"""
        val legacyDecoded = json.decodeFromString<ProfileDto>(legacyJson)
        assertEquals("user_legacy", legacyDecoded.id)
        assertEquals(500L, legacyDecoded.listeningSeconds)
        assertEquals(null, legacyDecoded.listeningTimeHuman)
        assertEquals(null, legacyDecoded.lastActiveIst)
    }
}

