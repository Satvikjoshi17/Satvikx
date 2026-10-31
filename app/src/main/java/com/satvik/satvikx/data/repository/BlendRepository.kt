package com.satvik.satvikx.data.repository

import android.util.Base64
import com.satvik.satvikx.data.local.dao.BlendDao
import com.satvik.satvikx.data.local.dao.PlaylistDao
import com.satvik.satvikx.data.local.dao.TrackDao
import com.satvik.satvikx.data.local.entity.AttributedTrack
import com.satvik.satvikx.data.local.entity.BlendEntity
import com.satvik.satvikx.data.local.entity.BlendTrackCrossRef
import com.satvik.satvikx.data.local.entity.BlendWithTracks
import com.satvik.satvikx.data.local.entity.PlaylistEntity
import com.satvik.satvikx.data.local.entity.PlaylistTrackCrossRef
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.entity.cleanArtistName
import com.satvik.satvikx.data.local.entity.cleanSongTitle
import com.satvik.satvikx.data.local.entity.distinctSongs
import com.satvik.satvikx.data.local.entity.isSameSong
import com.satvik.satvikx.data.local.entity.isSongOnly
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.util.Locale
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class BlendInvitePayload(
    val userName: String,
    val userAvatar: String = "",
    val topArtists: List<String> = emptyList(),
    val topGenres: List<String> = emptyList(),
    val anchorTrackIds: List<String> = emptyList(),
    val anchorTitles: List<String> = emptyList(),
    val anchorArtists: List<String> = emptyList()
)

data class CuratedBlendArtist(
    val id: String,
    val name: String,
    val category: String, // e.g. "Bollywood", "Global Pop", "Punjabi", "Hip-Hop", "Rock", "Indie"
    val description: String,
    val signatureQuery: String,
    val avatarUrl: String = ""
)

data class CuratedSeedTrack(
    val id: String,
    val title: String,
    val artist: String,
    val genre: String,
    val durationSeconds: Long = 210L
) {
    fun toTrackEntity(): TrackEntity = TrackEntity(
        id = id,
        title = title,
        artist = artist,
        durationSeconds = durationSeconds,
        thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
    )
}

val MASTER_CURATED_TRACKS = listOf(
    // Bollywood & Romance
    CuratedSeedTrack("BddP6PYo2gs", "Kesariya", "Arijit Singh, Pritam", "Bollywood", 268L),
    CuratedSeedTrack("gvyUuxdRdR4", "Tum Hi Ho", "Arijit Singh, Mithoon", "Bollywood", 262L),
    CuratedSeedTrack("k4yXQkGLeAA", "Raataan Lambiyan", "Jubin Nautiyal, Asees Kaur", "Bollywood", 230L),
    CuratedSeedTrack("ilNt2bikxwc", "Apna Bana Le", "Arijit Singh, Sachin-Jigar", "Bollywood", 261L),
    CuratedSeedTrack("vM-u5x9A6iM", "Channa Mereya", "Arijit Singh, Pritam", "Bollywood", 289L),
    CuratedSeedTrack("zC5vV6f1sXU", "Tera Hone Laga Hoon", "Atif Aslam, Alisha Chinai", "Bollywood", 300L),
    CuratedSeedTrack("yIIGQB6EMAM", "Zara Sa", "KK, Pritam", "Bollywood", 338L),
    CuratedSeedTrack("y9v-DPn2qdc", "Deva Deva", "Arijit Singh, Pritam", "Bollywood", 279L),
    CuratedSeedTrack("w9_k_X5i7E4", "Kal Ho Naa Ho", "Sonu Nigam, Shankar-Ehsaan-Loy", "Bollywood", 321L),
    CuratedSeedTrack("C8kSrkz8Hz8", "Agar Tum Saath Ho", "Alka Yagnik, Arijit Singh", "Bollywood", 341L),

    // Global Pop & Dark R&B
    CuratedSeedTrack("34Na4j8AVgA", "Starboy", "The Weeknd, Daft Punk", "Global Pop", 230L),
    CuratedSeedTrack("4NRXx6U8ABQ", "Blinding Lights", "The Weeknd", "Global Pop", 200L),
    CuratedSeedTrack("-BjZmE2gtdo", "Lover", "Taylor Swift", "Global Pop", 221L),
    CuratedSeedTrack("b1kbLwvqugk", "Anti-Hero", "Taylor Swift", "Global Pop", 200L),
    CuratedSeedTrack("OPf0YbXqDm0", "Uptown Funk", "Mark Ronson, Bruno Mars", "Global Pop", 270L),
    CuratedSeedTrack("JGwWNGJdvx8", "Shape of You", "Ed Sheeran", "Global Pop", 233L),
    CuratedSeedTrack("dyRsYkffPrE", "bad guy", "Billie Eilish", "Global Pop", 194L),
    CuratedSeedTrack("yKNxeF4KMsY", "Yellow", "Coldplay", "Global Pop", 269L),
    CuratedSeedTrack("1G4isv_Fylg", "Paradise", "Coldplay", "Global Pop", 278L),
    CuratedSeedTrack("7wtfhZwyrcc", "Believer", "Imagine Dragons", "Global Pop", 204L),
    CuratedSeedTrack("fJ9rUzIMcZQ", "Bohemian Rhapsody", "Queen", "Global Pop", 354L),

    // Punjabi & Urban Desi
    CuratedSeedTrack("KhyOg8nCoXo", "295", "Sidhu Moose Wala", "Punjabi", 270L),
    CuratedSeedTrack("cl0a3iCS42w", "Softly", "Karan Aujla", "Punjabi", 155L),
    CuratedSeedTrack("x8VYWazR5mE", "Lover", "Diljit Dosanjh", "Punjabi", 192L),
    CuratedSeedTrack("VNs_cCtdbPc", "Excuses", "AP Dhillon, Gurinder Gill", "Punjabi", 176L),
    CuratedSeedTrack("1nCqRamlz74", "Elevated", "Shubh", "Punjabi", 201L),
    CuratedSeedTrack("Dcfh9Z81lZk", "No Love", "Shubh", "Punjabi", 170L),
    CuratedSeedTrack("mH_LSC1Y6EQ", "Cheques", "Shubh", "Punjabi", 183L),
    CuratedSeedTrack("x8d1vN_6w9g", "Born to Shine", "Diljit Dosanjh", "Punjabi", 213L),

    // Hip-Hop & Rap
    CuratedSeedTrack("0yW7w8F2TVA", "God's Plan", "Drake", "Hip-Hop", 198L),
    CuratedSeedTrack("tfSS1e3kYeo", "SICKO MODE", "Travis Scott", "Hip-Hop", 312L),
    CuratedSeedTrack("_Yhyp-_hX2s", "Lose Yourself", "Eminem", "Hip-Hop", 326L),
    CuratedSeedTrack("WcIcVapfqXw", "HUMBLE.", "Kendrick Lamar", "Hip-Hop", 177L),
    CuratedSeedTrack("z4hU3mR4x_w", "Mirchi", "DIVINE", "Hip-Hop", 217L),
    CuratedSeedTrack("3r6f40Wl8pM", "Kohinoor", "DIVINE", "Hip-Hop", 202L),

    // Indie & Acoustic Folk
    CuratedSeedTrack("gZd_X8Hw_qE", "Baarishein", "Anuv Jain", "Indie", 208L),
    CuratedSeedTrack("8367ETnagHo", "cold/mess", "Prateek Kuhad", "Indie", 227L),
    CuratedSeedTrack("1A8e2q5y0jY", "Alag Aasmaan", "Anuv Jain", "Indie", 232L),
    CuratedSeedTrack("5GJWxDKyk3U", "Kasoor", "Prateek Kuhad", "Indie", 197L),
    CuratedSeedTrack("K9Jv4f6p2nQ", "tere bina", "Zaeden", "Indie", 188L),

    // Rock & Stadium Anthems
    CuratedSeedTrack("v2AC41dglnM", "Thunderstruck", "AC/DC", "Rock", 292L),
    CuratedSeedTrack("pAgnJDJN4VA", "Back In Black", "AC/DC", "Rock", 255L),
    CuratedSeedTrack("kXYiU_JCYtU", "Numb", "Linkin Park", "Rock", 187L),
    CuratedSeedTrack("eVTXPUF4Oz4", "In the End", "Linkin Park", "Rock", 216L),

    // EDM & Synthwave
    CuratedSeedTrack("60ItHLz5WEA", "Faded", "Alan Walker", "EDM", 212L),
    CuratedSeedTrack("gCYcTST8s48", "Animals", "Martin Garrix", "EDM", 192L),
    CuratedSeedTrack("MV_3Dpw-BRY", "Nightcall", "Kavinsky", "Synthwave", 259L),
    CuratedSeedTrack("rDBbaGCCIhk", "Sunset", "The Midnight", "Synthwave", 326L),

    // Sufi & Classics
    CuratedSeedTrack("pU8x8x6_8u8", "Afreen Afreen", "Rahat Fateh Ali Khan, Momina Mustehsan", "Sufi", 405L),
    CuratedSeedTrack("h_e1gN8pW-k", "Jhuki Jhuki Si Nazar", "Jagjit Singh", "Sufi", 303L)
)

@Singleton
class BlendRepository @Inject constructor(
    private val blendDao: BlendDao,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val recommendationRepository: RecommendationRepository,
    private val streamRepository: StreamRepository,
    private val authRepository: AuthRepository,
    private val moshi: Moshi
) {

    private val payloadAdapter by lazy {
        moshi.adapter(BlendInvitePayload::class.java)
    }

    /**
     * Resolves the current user's personalized display name.
     * Defaults to "Satvik" if not configured by the user.
     */
    fun getCurrentUserName(): String {
        val name = authRepository.currentUser.value?.displayName?.trim()
        return if (!name.isNullOrBlank() && name != "Operative" && name != "Stark Operative") {
            name
        } else {
            "Satvik"
        }
    }

    /**
     * Curated flagship artists wisely selected across every major musical dimension.
     * Allows any user to generate a custom Stark Blend tailored to their personal taste.
     */
    val curatedArtists = listOf(
        // 1. Bollywood & Melodic Romance
        CuratedBlendArtist("arijit", "Arijit Singh", "Bollywood", "Romantic Soul & Melodic Anthems", "Arijit Singh top hits official audio"),
        CuratedBlendArtist("shreya", "Shreya Ghoshal", "Bollywood", "Nightingale of Bollywood & Classics", "Shreya Ghoshal best songs official audio"),
        CuratedBlendArtist("pritam", "Pritam", "Bollywood", "High-Energy Soundtracks & Pop Fusion", "Pritam best bollywood songs audio"),
        CuratedBlendArtist("atif", "Atif Aslam", "Bollywood", "Nostalgic Romance & Soul Ballads", "Atif Aslam romantic songs official audio"),
        CuratedBlendArtist("kk", "KK", "Bollywood", "Timeless Nostalgia & Emraan Hashmi Anthems", "KK hit songs official audio"),

        // 2. Global Pop & Dark R&B
        CuratedBlendArtist("weeknd", "The Weeknd", "Global Pop", "Dark R&B, Synth-Pop & Night Drive", "The Weeknd top songs official audio"),
        CuratedBlendArtist("taylor", "Taylor Swift", "Global Pop", "Storytelling Pop & Iconic Eras", "Taylor Swift greatest hits official audio"),
        CuratedBlendArtist("bruno", "Bruno Mars", "Global Pop", "Funk, Soul & Retro Groove", "Bruno Mars top tracks audio"),
        CuratedBlendArtist("billie", "Billie Eilish", "Global Pop", "Atmospheric Dark-Pop & Bedroom Beats", "Billie Eilish top hits audio"),
        CuratedBlendArtist("ed_sheeran", "Ed Sheeran", "Global Pop", "Acoustic Pop & Global Anthems", "Ed Sheeran best songs audio"),

        // 3. Punjabi Swag & Desi Drill
        CuratedBlendArtist("sidhu", "Sidhu Moose Wala", "Punjabi", "Punjabi Drill & Iconic Global Swag", "Sidhu Moose Wala top songs audio"),
        CuratedBlendArtist("karan", "Karan Aujla", "Punjabi", "Modern Punjabi Flow & Chartbusters", "Karan Aujla hit songs official audio"),
        CuratedBlendArtist("diljit", "Diljit Dosanjh", "Punjabi", "Urban Desi Pop & Global Bhangra", "Diljit Dosanjh best songs audio"),
        CuratedBlendArtist("ap_dhillon", "AP Dhillon", "Punjabi", "Brown Munde & Retro Synth Punjabi", "AP Dhillon hit songs official audio"),
        CuratedBlendArtist("shubh", "Shubh", "Punjabi", "Minimalistic Trap & Global Punjabi", "Shubh songs official audio"),

        // 4. Hip-Hop & Rap
        CuratedBlendArtist("drake", "Drake", "Hip-Hop", "Modern OVO Rap & Melodic Trap", "Drake top songs official audio"),
        CuratedBlendArtist("travis", "Travis Scott", "Hip-Hop", "Psychedelic Trap & High Energy Rages", "Travis Scott best tracks audio"),
        CuratedBlendArtist("eminem", "Eminem", "Hip-Hop", "Lyrical Genius & Rapid Velocity Flow", "Eminem greatest hits audio"),
        CuratedBlendArtist("divine", "DIVINE", "Hip-Hop", "Gully Rap & Hardcore Desi Drill", "DIVINE rap songs official audio"),
        CuratedBlendArtist("kendrick", "Kendrick Lamar", "Hip-Hop", "Conscious Hip-Hop & West Coast Anthems", "Kendrick Lamar top tracks audio"),

        // 5. Indie & Lo-Fi Acoustic
        CuratedBlendArtist("anuv", "Anuv Jain", "Indie", "Acoustic Fingerpicking & Late Night Calm", "Anuv Jain songs official audio"),
        CuratedBlendArtist("prateek", "Prateek Kuhad", "Indie", "Intimate Indie Folk & Melancholic Calm", "Prateek Kuhad best songs audio"),
        CuratedBlendArtist("zaeden", "Zaeden", "Indie", "Indie Pop & Electronic Chillstep", "Zaeden songs official audio"),

        // 6. Classic Hard Rock (Tony Stark's Favorite)
        CuratedBlendArtist("acdc", "AC/DC", "Rock", "Tony Stark's Iron Man Hard Rock Anthem", "AC/DC top hits official audio"),
        CuratedBlendArtist("queen", "Queen", "Rock", "Legendary Stadium Rock & Operatic Power", "Queen greatest hits official audio"),
        CuratedBlendArtist("linkin_park", "Linkin Park", "Rock", "Nu-Metal Energy & Iconic 2000s Rock", "Linkin Park best songs official audio"),

        // 7. EDM & Festival Dance
        CuratedBlendArtist("alan_walker", "Alan Walker", "EDM", "Festival Anthems & Melodic Electro", "Alan Walker top tracks audio"),
        CuratedBlendArtist("martin_garrix", "Martin Garrix", "EDM", "Progressive House & High BPM Drops", "Martin Garrix best songs audio"),

        // 8. Synthwave & Cyberpunk
        CuratedBlendArtist("kavinsky", "Kavinsky", "Synthwave", "Cyberpunk Outrun & Night Drive Electro", "Kavinsky nightcall synthwave audio"),
        CuratedBlendArtist("midnight", "The Midnight", "Synthwave", "80s Nostalgia, Synth & Saxophone Waves", "The Midnight synthwave best songs"),

        // 9. Soulful Sufi & Ghazals
        CuratedBlendArtist("nusrat", "Nusrat Fateh Ali Khan", "Sufi", "Transcendent Qawwali & Divine Soul", "Nusrat Fateh Ali Khan best qawwalis audio"),
        CuratedBlendArtist("jagjit", "Jagjit Singh", "Sufi", "Timeless Ghazals & Poetic Solitude", "Jagjit Singh ghazals audio")
    )

    fun getAllBlends(): Flow<List<BlendEntity>> = blendDao.getAllBlends()

    suspend fun getBlendById(blendId: String): BlendEntity? = withContext(Dispatchers.IO) {
        blendDao.getBlendById(blendId)
    }

    suspend fun deleteBlend(blendId: String) = withContext(Dispatchers.IO) {
        blendDao.deleteBlendTracks(blendId)
        blendDao.deleteBlend(blendId)
    }

    /**
     * Generates a decentralized Taste Token containing the user's name and comprehensive taste vector.
     * Gathers up to 18 authentic tracks across Heavy Rotation, Vault, Liked Songs, Downloads, and Library.
     */
    suspend fun generateInvitePayload(): BlendInvitePayload = withContext(Dispatchers.IO) {
        val affinities = recommendationRepository.analyzeUserAffinities()
        val userName = getCurrentUserName()
        val topArtists = affinities.overallTopArtists.take(6)
        val topGenres = affinities.topGenres.take(4)

        val candidateTracks = mutableListOf<TrackEntity>()
        candidateTracks.addAll(affinities.heavyRotationTracks.filter { it.isSongOnly() })
        candidateTracks.addAll(affinities.vaultTracks.filter { it.isSongOnly() })

        // 1. Liked Songs
        val likedPlaylist = playlistDao.getPlaylistByNameSync("Liked Songs")
        if (likedPlaylist != null) {
            val liked = playlistDao.getPlaylistWithTracks(likedPlaylist.playlistId).firstOrNull()?.tracks.orEmpty()
            candidateTracks.addAll(liked.filter { it.isSongOnly() })
        }

        // 2. Downloaded Songs
        val downloaded = trackDao.getDownloadedTracks().firstOrNull().orEmpty()
        candidateTracks.addAll(downloaded.filter { it.isSongOnly() })

        // 3. Local Library
        val allLocal = trackDao.getAllTracks().firstOrNull().orEmpty()
        candidateTracks.addAll(allLocal.filter { it.isSongOnly() })

        var anchorTracks = candidateTracks.distinctSongs().take(18)

        // If library has no tracks yet (brand new user), seed with timeless curated flagship tracks instantly (0ms)
        if (anchorTracks.isEmpty()) {
            val seedTitles = listOf("Starboy", "Kesariya", "Lover", "Yellow", "Blinding Lights", "Believer")
            val seedArtists = listOf("The Weeknd", "Arijit Singh", "Taylor Swift", "Coldplay", "The Weeknd", "Imagine Dragons")
            val seedIds = listOf("34Na4j8AVgA", "BddP6PYo2gs", "-BjZmE2gtdo", "yKNxeF4KMsY", "4NRXx6U8ABQ", "7wtfhZwyrcc")
            seedTitles.forEachIndexed { idx, title ->
                candidateTracks.add(
                    TrackEntity(
                        id = seedIds[idx],
                        title = title,
                        artist = seedArtists[idx],
                        durationSeconds = 210L,
                        thumbnailUrl = "https://i.ytimg.com/vi/${seedIds[idx]}/hqdefault.jpg"
                    )
                )
            }
            anchorTracks = candidateTracks.take(18)
        }

        BlendInvitePayload(
            userName = userName,
            userAvatar = "",
            topArtists = topArtists.ifEmpty { listOf("The Weeknd", "Arijit Singh", "Taylor Swift", "Coldplay") },
            topGenres = topGenres.ifEmpty { listOf("Pop", "Bollywood", "Acoustic", "Rock") },
            anchorTrackIds = anchorTracks.map { it.id },
            anchorTitles = anchorTracks.map { it.title },
            anchorArtists = anchorTracks.map { it.artist }
        )
    }

    /**
     * Fallback payload generator when unexpected exceptions occur, guaranteeing 100% availability.
     */
    fun generateFallbackInvitePayload(): BlendInvitePayload {
        val userName = getCurrentUserName()
        return BlendInvitePayload(
            userName = userName,
            userAvatar = "",
            topArtists = listOf("The Weeknd", "Arijit Singh", "Taylor Swift", "Coldplay"),
            topGenres = listOf("Pop", "Bollywood", "Acoustic", "Rock"),
            anchorTrackIds = listOf("34Na4j8AVgA", "BddP6PYo2gs", "-BjZmE2gtdo", "yKNxeF4KMsY"),
            anchorTitles = listOf("Starboy", "Kesariya", "Lover", "Yellow"),
            anchorArtists = listOf("The Weeknd", "Arijit Singh", "Taylor Swift", "Coldplay")
        )
    }

    /**
     * Extracts strictly the clean Blend code (e.g. "STKBLD:...") from any input string,
     * stripping any surrounding chat text, explanations, or deep link wrappers.
     */
    fun extractCleanCode(rawInput: String): String {
        if (rawInput.isBlank()) return ""
        val trimmed = rawInput.trim()

        // 1. If it's a URL or deep link (e.g. satvikx://blend?data=...)
        val fromQuery = when {
            trimmed.contains("data=") -> trimmed.substringAfter("data=").substringBefore("&").substringBefore(" ").substringBefore("\n")
            trimmed.contains("token=") -> trimmed.substringAfter("token=").substringBefore("&").substringBefore(" ").substringBefore("\n")
            trimmed.contains("code=") -> trimmed.substringAfter("code=").substringBefore("&").substringBefore(" ").substringBefore("\n")
            else -> null
        }

        var candidate = (fromQuery ?: trimmed).trim()

        // Handle URL decoding if encoded (e.g. %3A for :)
        try {
            candidate = URLDecoder.decode(candidate, "UTF-8")
        } catch (_: Exception) {}

        // 2. If it contains STKBLD:
        if (candidate.contains("STKBLD:")) {
            val afterPrefix = candidate.substringAfter("STKBLD:")
                .substringBefore(" ")
                .substringBefore("\n")
                .substringBefore("\"")
                .substringBefore("'")
                .trim()
            val cleanToken = afterPrefix.replace("\\s+".toRegex(), "").trim('"', '\'', '`', '<', '>', '(', ')')
            return "STKBLD:$cleanToken"
        }

        // 3. Otherwise clean whitespace/quotes
        val cleanCandidate = candidate.replace("\\s+".toRegex(), "").trim('"', '\'', '`', '<', '>', '(', ')')
        return if (cleanCandidate.startsWith("STKBLD:")) cleanCandidate else cleanCandidate
    }

    /**
     * Encodes a Blend invite payload into a portable URL-safe token.
     * Uses GZIP compression to keep the code ultra-compact and easy to copy.
     */
    fun encodeInviteToken(payload: BlendInvitePayload): String {
        val json = payloadAdapter.toJson(payload)
        val byteStream = ByteArrayOutputStream()
        GZIPOutputStream(byteStream).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        val compressed = byteStream.toByteArray()
        val base64 = Base64.encodeToString(compressed, Base64.NO_WRAP or Base64.URL_SAFE)
        return "STKBLD:$base64"
    }

    /**
     * Decodes a portable invite token back into a BlendInvitePayload.
     * Supports GZIP-compressed tokens, uncompressed legacy tokens, deep link URLs,
     * and auto-extracts the code even if pasted inside surrounding chat text.
     */
    fun decodeInviteToken(rawToken: String): BlendInvitePayload? {
        if (rawToken.isBlank()) return null

        val cleanCode = extractCleanCode(rawToken)
        val cleanBase64 = if (cleanCode.startsWith("STKBLD:")) {
            cleanCode.removePrefix("STKBLD:")
        } else {
            cleanCode
        }

        if (cleanBase64.isBlank()) return null

        val bytes = try {
            Base64.decode(cleanBase64, Base64.NO_WRAP or Base64.URL_SAFE)
        } catch (_: Exception) {
            try {
                Base64.decode(cleanBase64, Base64.DEFAULT)
            } catch (_: Exception) {
                return null
            }
        }

        val json = try {
            if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                String(bytes, Charsets.UTF_8)
            }
        } catch (_: Exception) {
            return null
        }

        return try {
            payloadAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Saves a synthesized Stark Blend as a permanent custom playlist in the user's library.
     */
    suspend fun saveBlendAsPlaylist(blendWithTracks: BlendWithTracks): Long = withContext(Dispatchers.IO) {
        val playlist = PlaylistEntity(
            name = blendWithTracks.blend.title,
            createdAtTimestamp = System.currentTimeMillis(),
            isCustom = true
        )
        val playlistId = playlistDao.insertPlaylist(playlist)
        val refs = blendWithTracks.attributedTracks.mapIndexed { idx, item ->
            PlaylistTrackCrossRef(
                playlistId = playlistId,
                trackId = item.track.id,
                position = idx,
                addedAtTimestamp = System.currentTimeMillis()
            )
        }
        playlistDao.insertPlaylistTrackCrossRefs(refs)
        playlistId
    }

    /**
     * Calculates mathematical taste match compatibility between 0% and 100%.
     * Minimum floor of 65% ensures encouraging, positive social feedback.
     */
    private fun calculateTasteMatchScore(
        userArtists: List<String>,
        partnerArtists: List<String>,
        userGenres: List<String>,
        partnerGenres: List<String>
    ): Pair<Int, String> {
        val uA = userArtists.map { it.lowercase(Locale.ROOT).trim() }.toSet()
        val pA = partnerArtists.map { it.lowercase(Locale.ROOT).trim() }.toSet()

        val uG = userGenres.map { it.lowercase(Locale.ROOT).trim() }.toSet()
        val pG = partnerGenres.map { it.lowercase(Locale.ROOT).trim() }.toSet()

        val sharedArtists = uA.intersect(pA)
        val sharedGenres = uG.intersect(pG)

        val artistOverlapScore = if (uA.isNotEmpty() && pA.isNotEmpty()) {
            (2.0f * sharedArtists.size) / (uA.size + pA.size)
        } else 0.5f

        val genreOverlapScore = if (uG.isNotEmpty() && pG.isNotEmpty()) {
            (2.0f * sharedGenres.size) / (uG.size + pG.size)
        } else 0.6f

        val rawScore = (0.55f * artistOverlapScore) + (0.45f * genreOverlapScore)
        // Scale to a realistic rewarding range of 68% to 98%
        val finalScore = (68 + (rawScore * 30)).toInt().coerceIn(68, 98)

        val sharedTopic = sharedGenres.firstOrNull()?.replaceFirstChar { it.uppercase() }
            ?: sharedArtists.firstOrNull()?.replaceFirstChar { it.uppercase() }
            ?: "Melodic Harmony"

        return Pair(finalScore, sharedTopic)
    }

    /**
     * Synthesizes a Stark Blend between the current user and a friend from an invite token.
     * Crafts the perfect song mashup:
     * - Pinpoints the true Shared Anthem (exact song match or top shared artist bridge)
     * - Resolves partner anchor tracks instantly without blocking network lag
     * - Enriches User B's vault with up to 18 authentic tracks across liked, downloaded, and rotation
     * - Seamlessly pulls complementary discovery tracks for shared tastes in parallel
     * - Curates an authentic, balanced 35-track mashup with vivid color-coded attribution
     */
    suspend fun synthesizeFriendBlend(partnerPayload: BlendInvitePayload): BlendWithTracks = withContext(Dispatchers.IO) {
        val affinities = recommendationRepository.analyzeUserAffinities()
        val creatorName = getCurrentUserName()
        val partnerName = partnerPayload.userName.trim().ifBlank { "Friend" }

        val (matchScore, topSharedGenre) = calculateTasteMatchScore(
            userArtists = affinities.overallTopArtists,
            partnerArtists = partnerPayload.topArtists,
            userGenres = affinities.topGenres,
            partnerGenres = partnerPayload.topGenres
        )

        // 1. Gather User B's Deep Library (Up to 18 authentic tracks)
        val userCandidates = mutableListOf<TrackEntity>()
        userCandidates.addAll(affinities.heavyRotationTracks.filter { it.isSongOnly() })
        userCandidates.addAll(affinities.vaultTracks.filter { it.isSongOnly() })

        val likedPlaylist = playlistDao.getPlaylistByNameSync("Liked Songs")
        if (likedPlaylist != null) {
            val liked = playlistDao.getPlaylistWithTracks(likedPlaylist.playlistId).firstOrNull()?.tracks.orEmpty()
            userCandidates.addAll(liked.filter { it.isSongOnly() })
        }
        val downloaded = trackDao.getDownloadedTracks().firstOrNull().orEmpty()
        userCandidates.addAll(downloaded.filter { it.isSongOnly() })
        val allLocal = trackDao.getAllTracks().firstOrNull().orEmpty()
        userCandidates.addAll(allLocal.filter { it.isSongOnly() })

        var userVault = userCandidates.distinctSongs().take(18).toMutableList()

        // If User B has fewer than 18 tracks, augment instantly from Curated Master Vault (0ms delay)
        if (userVault.size < 18) {
            val needed = 18 - userVault.size
            val matchingCurated = MASTER_CURATED_TRACKS.filter { seed ->
                val matchesGenre = affinities.topGenres.any { seed.genre.contains(it, ignoreCase = true) }
                val matchesArtist = affinities.overallTopArtists.any { seed.artist.contains(it, ignoreCase = true) }
                matchesGenre || matchesArtist
            }.map { it.toTrackEntity() }

            val fallbackCurated = (matchingCurated + MASTER_CURATED_TRACKS.map { it.toTrackEntity() })
                .filter { seed -> userVault.none { it.isSameSong(seed) } }
                .take(needed)

            userVault.addAll(fallbackCurated)
            userVault = userVault.distinctSongs().take(18).toMutableList()
        }

        // 2. Resolve Partner's Anchor Tracks directly from payload with zero network delay
        val partnerAnchorTracks = mutableListOf<TrackEntity>()
        partnerPayload.anchorTitles.forEachIndexed { i, title ->
            val artist = partnerPayload.anchorArtists.getOrNull(i).orEmpty()
            val trackId = partnerPayload.anchorTrackIds.getOrNull(i).orEmpty()
            if (title.isNotBlank()) {
                val cached = if (trackId.isNotBlank()) trackDao.getTrackByIdSync(trackId) else null
                val curatedMatch = MASTER_CURATED_TRACKS.firstOrNull { it.title.equals(title.trim(), ignoreCase = true) }

                val effectiveId = when {
                    trackId.length == 11 && !trackId.contains("saavn") && !trackId.startsWith("partner_") -> trackId
                    curatedMatch != null -> curatedMatch.id
                    trackId.isNotBlank() -> trackId
                    else -> "partner_${UUID.randomUUID().toString().take(11)}"
                }
                val effectiveThumb = when {
                    effectiveId.length == 11 && !effectiveId.startsWith("partner_") && !effectiveId.contains("saavn") ->
                        "https://i.ytimg.com/vi/$effectiveId/hqdefault.jpg"
                    curatedMatch != null -> "https://i.ytimg.com/vi/${curatedMatch.id}/hqdefault.jpg"
                    else -> ""
                }

                val resolvedTrack = cached ?: TrackEntity(
                    id = effectiveId,
                    title = title,
                    artist = artist.ifBlank { curatedMatch?.artist ?: "Featured Artist" },
                    durationSeconds = curatedMatch?.durationSeconds ?: 210L,
                    thumbnailUrl = effectiveThumb
                )
                partnerAnchorTracks.add(resolvedTrack)
            }
        }

        // If partner had fewer than 18 tracks, augment instantly from Curated Master Vault (0ms delay)
        if (partnerAnchorTracks.size < 18) {
            val needed = 18 - partnerAnchorTracks.size
            val matchingPartnerSeeds = MASTER_CURATED_TRACKS.filter { seed ->
                val matchesGenre = partnerPayload.topGenres.any { seed.genre.contains(it, ignoreCase = true) }
                val matchesArtist = partnerPayload.topArtists.any { seed.artist.contains(it, ignoreCase = true) }
                matchesGenre || matchesArtist
            }.map { it.toTrackEntity() }

            val fallbackPartnerSeeds = (matchingPartnerSeeds + MASTER_CURATED_TRACKS.map { it.toTrackEntity() })
                .filter { seed -> partnerAnchorTracks.none { it.isSameSong(seed) } && userVault.none { it.isSameSong(seed) } }
                .take(needed)

            partnerAnchorTracks.addAll(fallbackPartnerSeeds)
        }

        // 3. Identify Shared Artists
        val sharedArtists = affinities.overallTopArtists.filter { uA ->
            partnerPayload.topArtists.any { pA ->
                uA.contains(pA, ignoreCase = true) || pA.contains(uA, ignoreCase = true)
            }
        }

        // 4. Identify Shared Anthem
        // Priority 1: Exact song both users have in their vault / anchors
        var sharedAnthem = userVault.firstOrNull { uTrack ->
            partnerAnchorTracks.any { pTrack -> uTrack.isSameSong(pTrack) }
        }

        // Priority 2: Song by a shared artist both users love
        if (sharedAnthem == null && sharedArtists.isNotEmpty()) {
            sharedAnthem = userVault.firstOrNull { uTrack ->
                sharedArtists.any { a -> uTrack.artist.contains(a, ignoreCase = true) }
            } ?: partnerAnchorTracks.firstOrNull { pTrack ->
                sharedArtists.any { a -> pTrack.artist.contains(a, ignoreCase = true) }
            } ?: MASTER_CURATED_TRACKS.firstOrNull { seed ->
                sharedArtists.any { a -> seed.artist.contains(a, ignoreCase = true) }
            }?.toTrackEntity()
        }

        // Priority 3: Curated flagship anthem for their top shared genre
        if (sharedAnthem == null) {
            sharedAnthem = MASTER_CURATED_TRACKS.firstOrNull { seed ->
                seed.genre.equals(topSharedGenre, ignoreCase = true)
            }?.toTrackEntity()
        }

        // Priority 4: Top anchor from partner or user
        if (sharedAnthem == null) {
            sharedAnthem = partnerAnchorTracks.firstOrNull() ?: userVault.firstOrNull() ?: MASTER_CURATED_TRACKS.first().toTrackEntity()
        }

        // 5. Complementary Mutual Discoveries (instant curated resolution + optional bounded 600ms live probe)
        val mutualDiscoveryTracks = mutableListOf<TrackEntity>()

        val curatedDiscoveries = MASTER_CURATED_TRACKS.filter { seed ->
            val matchesSharedGenre = seed.genre.equals(topSharedGenre, ignoreCase = true) ||
                    partnerPayload.topGenres.any { seed.genre.contains(it, ignoreCase = true) } ||
                    affinities.topGenres.any { seed.genre.contains(it, ignoreCase = true) }
            val matchesArtist = sharedArtists.any { a -> seed.artist.contains(a, ignoreCase = true) }
            matchesSharedGenre || matchesArtist
        }.map { it.toTrackEntity() }
            .filter { seed -> userVault.none { it.isSameSong(seed) } && partnerAnchorTracks.none { it.isSameSong(seed) } }

        mutualDiscoveryTracks.addAll(curatedDiscoveries)

        // Bounded live probe strictly capped at 600ms so synthesis NEVER delays
        try {
            val liveTracks = withTimeoutOrNull(600L) {
                val query = if (sharedArtists.isNotEmpty()) {
                    "${sharedArtists.first()} top songs audio"
                } else {
                    "$topSharedGenre hit songs audio"
                }
                streamRepository.searchTracks(query)
                    .firstOrNull()?.getOrNull().orEmpty()
                    .filter { it.isSongOnly() }
                    .take(4)
            }
            if (!liveTracks.isNullOrEmpty()) {
                mutualDiscoveryTracks.addAll(0, liveTracks)
            }
        } catch (_: Exception) {}

        // 6. Intelligent Curation & Interleaving (Target: Exactly 35 tracks)
        val attributedList = mutableListOf<AttributedTrack>()
        val seenTrackKeys = HashSet<String>()

        fun addTrack(track: TrackEntity, broughtBy: String, broughtByName: String): Boolean {
            val key = track.cleanSongTitle().replace(" ", "").lowercase(Locale.ROOT)
            if (key.isNotBlank() && seenTrackKeys.add(key)) {
                attributedList.add(AttributedTrack(track, broughtBy, broughtByName))
                return true
            }
            return false
        }

        // 1st Track: The Shared Anthem ("Both")
        addTrack(sharedAnthem, "both", "Both")

        // Separate user and partner tracks into distinct iterators
        val uTracks = userVault.filter { !sharedAnthem.isSameSong(it) }
        val pTracks = partnerAnchorTracks.filter { !sharedAnthem.isSameSong(it) }
        val dTracks = mutualDiscoveryTracks.distinctSongs().filter { !sharedAnthem.isSameSong(it) }

        val uIter = uTracks.iterator()
        val pIter = pTracks.iterator()
        val dIter = dTracks.iterator()

        // Interleave in a studio-quality mashup pattern
        while (attributedList.size < 35 && (uIter.hasNext() || pIter.hasNext() || dIter.hasNext())) {
            if (uIter.hasNext() && attributedList.size < 35) {
                addTrack(uIter.next(), "creator", creatorName)
            }
            if (pIter.hasNext() && attributedList.size < 35) {
                addTrack(pIter.next(), "partner", partnerName)
            }
            // Insert a shared/discovery song every 4 tracks
            if (dIter.hasNext() && attributedList.size < 35 && (attributedList.size % 4 == 0)) {
                addTrack(dIter.next(), "both", "Both")
            }
        }

        // If still under 35 tracks, fill remaining slots with any unused tracks + curated vault
        val allRemaining = (dTracks + pTracks + uTracks + MASTER_CURATED_TRACKS.map { it.toTrackEntity() })
        for (track in allRemaining) {
            if (attributedList.size >= 35) break
            val brought = when {
                pTracks.any { it.isSameSong(track) } -> Pair("partner", partnerName)
                uTracks.any { it.isSameSong(track) } -> Pair("creator", creatorName)
                else -> Pair("both", "Both")
            }
            addTrack(track, brought.first, brought.second)
        }

        val blendId = "bld_" + UUID.randomUUID().toString().take(8)
        val title = "Stark Blend • $creatorName & $partnerName"

        val blendEntity = BlendEntity(
            blendId = blendId,
            title = title,
            creatorName = creatorName,
            creatorAvatar = "",
            partnerName = partnerName,
            partnerAvatar = "",
            matchScore = matchScore,
            sharedAnthemTitle = sharedAnthem?.title.orEmpty(),
            sharedAnthemArtist = sharedAnthem?.artist.orEmpty(),
            sharedAnthemThumbnail = sharedAnthem?.thumbnailUrl.orEmpty(),
            topSharedGenre = topSharedGenre,
            inviteCode = "BLD-${partnerName.uppercase().take(4)}-${(100..999).random()}",
            createdAt = System.currentTimeMillis(),
            isArtistBlend = false
        )

        // Batch insert tracks in room database
        trackDao.insertTracks(attributedList.map { it.track })

        blendDao.insertBlend(blendEntity)

        val crossRefs = attributedList.mapIndexed { idx, item ->
            BlendTrackCrossRef(
                blendId = blendId,
                trackId = item.track.id,
                position = idx,
                broughtBy = item.broughtBy,
                broughtByName = item.broughtByName
            )
        }
        blendDao.insertBlendTracks(crossRefs)

        BlendWithTracks(blendEntity, attributedList)
    }

    /**
     * Synthesizes a Stark Blend between the user and 1 to 3 curated flagship artists.
     */
    suspend fun synthesizeArtistBlend(selectedArtists: List<CuratedBlendArtist>): BlendWithTracks = withContext(Dispatchers.IO) {
        val affinities = recommendationRepository.analyzeUserAffinities()
        val creatorName = getCurrentUserName()
        val artistsNames = selectedArtists.joinToString(" & ") { it.name }

        // Fetch user favorites
        val userVault = (affinities.heavyRotationTracks + affinities.vaultTracks)
            .filter { it.isSongOnly() }
            .distinctSongs()

        // Fetch signature hits for each selected artist concurrently with fast safety timeout + curated fallback
        val artistTracksMap = coroutineScope {
            selectedArtists.map { artist ->
                async {
                    val liveHits = try {
                        withTimeoutOrNull(2000L) {
                            streamRepository.searchTracks(artist.signatureQuery).firstOrNull()?.getOrNull().orEmpty()
                                .filter { it.isSongOnly() }
                                .distinctSongs()
                        } ?: emptyList()
                    } catch (_: Exception) {
                        emptyList()
                    }

                    val fallbackTracks = MASTER_CURATED_TRACKS
                        .filter { it.artist.contains(artist.name, ignoreCase = true) || it.genre.equals(artist.category, ignoreCase = true) }
                        .map { it.toTrackEntity() }

                    val finalTracks = (liveHits + fallbackTracks).distinctSongs()
                    artist.name to finalTracks
                }
            }.awaitAll().toMap()
        }

        // Calculate realistic affinity score
        val matchingAffinities = selectedArtists.count { a ->
            affinities.overallTopArtists.any { it.contains(a.name, ignoreCase = true) }
        }
        val matchScore = (76 + (matchingAffinities * 8) + (Math.random() * 8).toInt()).coerceIn(75, 96)

        // Identify Shared Anthem
        val firstArtistTracks = artistTracksMap[selectedArtists.first().name].orEmpty()
        val sharedAnthem = userVault.firstOrNull { uTrack ->
            firstArtistTracks.any { aTrack -> uTrack.isSameSong(aTrack) }
        } ?: firstArtistTracks.firstOrNull() ?: userVault.firstOrNull()

        val attributedList = mutableListOf<AttributedTrack>()
        val seenTrackKeys = HashSet<String>()

        fun addTrack(track: TrackEntity, broughtBy: String, broughtByName: String) {
            val key = track.cleanSongTitle().replace(" ", "")
            if (key.isNotBlank() && seenTrackKeys.add(key)) {
                attributedList.add(AttributedTrack(track, broughtBy, broughtByName))
            }
        }

        if (sharedAnthem != null) {
            addTrack(sharedAnthem, "both", "Both")
        }

        val uIter = userVault.iterator()
        val allArtistTracks = artistTracksMap.values.flatten().distinctSongs().shuffled().iterator()

        while (attributedList.size < 35 && (uIter.hasNext() || allArtistTracks.hasNext())) {
            if (uIter.hasNext() && attributedList.size < 35) {
                addTrack(uIter.next(), "creator", creatorName)
            }
            if (allArtistTracks.hasNext() && attributedList.size < 35) {
                val aTrack = allArtistTracks.next()
                val artistBrought = selectedArtists.firstOrNull { aTrack.artist.contains(it.name, ignoreCase = true) }?.name ?: selectedArtists.first().name
                addTrack(aTrack, "partner", artistBrought)
            }
        }

        val blendId = "bld_art_" + UUID.randomUUID().toString().take(8)
        val title = "Stark Blend • $creatorName & $artistsNames"

        val blendEntity = BlendEntity(
            blendId = blendId,
            title = title,
            creatorName = creatorName,
            creatorAvatar = "",
            partnerName = artistsNames,
            partnerAvatar = selectedArtists.firstOrNull()?.avatarUrl.orEmpty(),
            matchScore = matchScore,
            sharedAnthemTitle = sharedAnthem?.title.orEmpty(),
            sharedAnthemArtist = sharedAnthem?.artist.orEmpty(),
            sharedAnthemThumbnail = sharedAnthem?.thumbnailUrl.orEmpty(),
            topSharedGenre = selectedArtists.first().category,
            inviteCode = "BLD-ART-${(1000..9999).random()}",
            createdAt = System.currentTimeMillis(),
            isArtistBlend = true
        )

        for (item in attributedList) {
            trackDao.insertTrack(item.track)
        }

        blendDao.insertBlend(blendEntity)

        val crossRefs = attributedList.mapIndexed { idx, item ->
            BlendTrackCrossRef(
                blendId = blendId,
                trackId = item.track.id,
                position = idx,
                broughtBy = item.broughtBy,
                broughtByName = item.broughtByName
            )
        }
        blendDao.insertBlendTracks(crossRefs)

        BlendWithTracks(blendEntity, attributedList)
    }

    /**
     * Reads a blend and resolves all attributed tracks from Room.
     */
    suspend fun getBlendWithTracks(blendId: String): BlendWithTracks? = withContext(Dispatchers.IO) {
        val blend = blendDao.getBlendById(blendId) ?: return@withContext null
        val refs = blendDao.getBlendTracks(blendId)
        val tracksMap = mutableMapOf<String, TrackEntity>()

        refs.forEach { ref ->
            val track = trackDao.getTrackByIdSync(ref.trackId)
            if (track != null) {
                tracksMap[ref.trackId] = track
            }
        }

        val attributed = refs.mapNotNull { ref ->
            tracksMap[ref.trackId]?.let { track ->
                AttributedTrack(
                    track = track,
                    broughtBy = ref.broughtBy,
                    broughtByName = ref.broughtByName
                )
            }
        }

        BlendWithTracks(blend, attributed)
    }
}
