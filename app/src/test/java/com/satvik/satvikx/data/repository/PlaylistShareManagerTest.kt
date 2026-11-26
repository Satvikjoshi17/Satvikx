package com.satvik.satvikx.data.repository

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Enterprise Unit Test Suite for [PlaylistShareManager].
 * Verifies compression, serialization roundtrips, deep link parsing, and error handling.
 */
class PlaylistShareManagerTest {

    private lateinit var shareManager: PlaylistShareManager
    private lateinit var moshi: Moshi

    @Before
    fun setup() {
        moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        shareManager = PlaylistShareManager(moshi)
    }

    @Test
    fun roundTrip_standardPlaylist_preservesAllMetadata() {
        val tracks = listOf(
            SharedTrackDto("t1", "Kesariya", "Arijit Singh", 268L, "https://example.com/art1.jpg"),
            SharedTrackDto("t2", "Starboy", "The Weeknd", 230L, "https://example.com/art2.jpg"),
            SharedTrackDto("t3", "Zara Sa 💖", "KK", 338L, "")
        )
        val original = SharedPlaylistPayload(
            title = "My Midnight Vibes",
            creatorName = "Satvik",
            tracks = tracks
        )

        val encoded = shareManager.encodePayload(original)
        assertTrue(encoded.startsWith("STKPL:"))

        val decoded = shareManager.decodePayload(encoded)
        assertNotNull(decoded)
        assertEquals("My Midnight Vibes", decoded!!.title)
        assertEquals("Satvik", decoded.creatorName)
        assertEquals(3, decoded.tracks.size)
        assertEquals("Kesariya", decoded.tracks[0].title)
        assertEquals("Arijit Singh", decoded.tracks[0].artist)
        assertEquals("Starboy", decoded.tracks[1].title)
        assertEquals("Zara Sa 💖", decoded.tracks[2].title)
    }

    @Test
    fun roundTrip_devanagariAndSpecialCharacters_preservesUnicode() {
        val tracks = listOf(
            SharedTrackDto("id1", "आरंभ है प्रचंड 🔥", "पीयूष मिश्रा", 300L, ""),
            SharedTrackDto("id2", "Lover (Taylor's Version) [Remix]", "Taylor Swift", 215L, "https://img.com/t.jpg")
        )
        val payload = SharedPlaylistPayload(
            title = "देसी बीट्स 🎧",
            creatorName = "आर्यन",
            tracks = tracks
        )

        val token = shareManager.encodePayload(payload)
        val restored = shareManager.decodePayload(token)

        assertNotNull(restored)
        assertEquals("देसी बीट्स 🎧", restored!!.title)
        assertEquals("आर्यन", restored.creatorName)
        assertEquals(2, restored.tracks.size)
        assertEquals("आरंभ है प्रचंड 🔥", restored.tracks[0].title)
        assertEquals("पीयूष मिश्रा", restored.tracks[0].artist)
    }

    @Test
    fun createShareLink_generatesClickableAppUri() {
        val payload = SharedPlaylistPayload(
            title = "Gym Hype",
            creatorName = "Tony",
            tracks = listOf(SharedTrackDto("id99", "Power", "Kanye", 290L, ""))
        )

        val appLink = shareManager.createShareLink(payload)
        assertTrue(appLink.startsWith("satvikx://playlist?data="))

        val restored = shareManager.decodePayload(appLink)
        assertNotNull(restored)
        assertEquals("Gym Hype", restored!!.title)
        assertEquals("Tony", restored.creatorName)
    }

    @Test
    fun createWebShareLink_generatesValidWebUri() {
        val payload = SharedPlaylistPayload(
            title = "Chill Lofi",
            creatorName = "Sarah",
            tracks = listOf(SharedTrackDto("lofi1", "Rain Sounds", "Lofi Girl", 180L, ""))
        )

        val webLink = shareManager.createWebShareLink(payload)
        assertTrue(webLink.startsWith("https://satvikjoshi17.github.io/Satvikx?data="))

        val restored = shareManager.decodePayload(webLink)
        assertNotNull(restored)
        assertEquals("Chill Lofi", restored!!.title)
    }

    @Test
    fun extractToken_fromMessyChatMessage_extractsCorrectToken() {
        val payload = SharedPlaylistPayload(
            title = "Party Mix",
            creatorName = "Host",
            tracks = listOf(SharedTrackDto("p1", "Levels", "Avicii", 200L, ""))
        )
        val appLink = shareManager.createShareLink(payload)
        val chatMessage = "Hey bro listen to this awesome playlist: $appLink it's so good!"

        val extractedToken = shareManager.extractToken(chatMessage)
        val restored = shareManager.decodePayload(extractedToken)

        assertNotNull(restored)
        assertEquals("Party Mix", restored!!.title)
    }

    @Test
    fun decodePayload_corruptOrEmptyInput_returnsNullGracefully() {
        assertNull(shareManager.decodePayload(""))
        assertNull(shareManager.decodePayload("    "))
        assertNull(shareManager.decodePayload("invalid_gibberish"))
        assertNull(shareManager.decodePayload("STKPL:corrupt_base64_data_!@#$"))
    }

    @Test
    fun handleDeepLink_setsPendingPlaylist() {
        val payload = SharedPlaylistPayload(
            title = "Roadtrip",
            creatorName = "Satvik",
            tracks = listOf(SharedTrackDto("r1", "Highway", "Singer", 240L, ""))
        )
        val link = shareManager.createShareLink(payload)

        shareManager.clearPendingPlaylist()
        assertNull(shareManager.pendingPlaylist.value)

        val handled = shareManager.handleDeepLink(link)
        assertTrue(handled)
        assertNotNull(shareManager.pendingPlaylist.value)
        assertEquals("Roadtrip", shareManager.pendingPlaylist.value!!.title)

        shareManager.clearPendingPlaylist()
        assertNull(shareManager.pendingPlaylist.value)
    }

    @Test
    fun buildShareMessage_containsWebLinkAndCleanToken() {
        val payload = SharedPlaylistPayload(
            title = "Chill Lofi",
            creatorName = "Sarah",
            tracks = listOf(SharedTrackDto("lofi1", "Rain Sounds", "Lofi Girl", 180L, ""))
        )
        val msg = shareManager.buildShareMessage(payload)
        assertTrue(msg.contains("https://satvikjoshi17.github.io/Satvikx?data="))
        assertTrue(msg.contains("STKPL:"))

        val extracted = shareManager.extractToken(msg)
        val decoded = shareManager.decodePayload(extracted)
        assertNotNull(decoded)
        assertEquals("Chill Lofi", decoded!!.title)
    }
}
