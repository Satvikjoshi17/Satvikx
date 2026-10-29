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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
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
     * Generates a decentralized Taste Token containing the user's name and taste vector.
     */
    suspend fun generateInvitePayload(): BlendInvitePayload = withContext(Dispatchers.IO) {
        val affinities = recommendationRepository.analyzeUserAffinities()
        val userName = getCurrentUserName()
        val topArtists = affinities.overallTopArtists.take(6)
        val topGenres = affinities.topGenres.take(4)
        val anchorTracks = (affinities.heavyRotationTracks + affinities.vaultTracks)
            .filter { it.isSongOnly() }
            .distinctSongs()
            .take(6)

        BlendInvitePayload(
            userName = userName,
            userAvatar = "",
            topArtists = topArtists,
            topGenres = topGenres,
            anchorTrackIds = anchorTracks.map { it.id },
            anchorTitles = anchorTracks.map { it.title },
            anchorArtists = anchorTracks.map { it.artist }
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

        // 1. Gather User A's Top Anchor Tracks
        val userVault = (affinities.heavyRotationTracks + affinities.vaultTracks)
            .filter { it.isSongOnly() }
            .distinctSongs()

        // 2. Resolve Partner's Anchor Tracks
        val partnerAnchorTracks = mutableListOf<TrackEntity>()
        partnerPayload.anchorTitles.forEachIndexed { i, title ->
            val artist = partnerPayload.anchorArtists.getOrNull(i).orEmpty()
            if (title.isNotBlank()) {
                val searchResult = streamRepository.searchTracks("$title $artist official audio")
                    .firstOrNull()?.getOrNull().orEmpty()
                val match = searchResult.firstOrNull { it.isSongOnly() }
                if (match != null) partnerAnchorTracks.add(match)
            }
        }

        // If partner had few anchor tracks, fetch top hits for their top artists
        if (partnerAnchorTracks.size < 6 && partnerPayload.topArtists.isNotEmpty()) {
            partnerPayload.topArtists.take(3).forEach { artist ->
                val artistHits = streamRepository.searchTracks("$artist top hits official audio")
                    .firstOrNull()?.getOrNull().orEmpty()
                partnerAnchorTracks.addAll(artistHits.filter { it.isSongOnly() }.take(3))
            }
        }

        // 3. Identify Shared Anthem
        var sharedAnthem = userVault.firstOrNull { uTrack ->
            partnerAnchorTracks.any { pTrack -> uTrack.isSameSong(pTrack) }
        }

        // If no direct song overlap in anchors, find a track by a shared artist or top partner artist that user loves
        if (sharedAnthem == null) {
            val commonArtist = affinities.overallTopArtists.firstOrNull { uA ->
                partnerPayload.topArtists.any { pA -> uA.equals(pA, ignoreCase = true) }
            } ?: partnerPayload.topArtists.firstOrNull() ?: affinities.primaryArtist ?: "Arijit Singh"

            val queryHits = streamRepository.searchTracks("$commonArtist top songs audio").firstOrNull()?.getOrNull().orEmpty()
            sharedAnthem = queryHits.firstOrNull { it.isSongOnly() } ?: userVault.firstOrNull()
        }

        // 4. Complementary Discoveries (Mutual Discovery)
        val discoveryGenre = partnerPayload.topGenres.firstOrNull() ?: affinities.primaryGenre ?: "acoustic chill"
        val mutualDiscoveryTracks = streamRepository.searchTracks("$discoveryGenre songs official audio")
            .firstOrNull()?.getOrNull().orEmpty()
            .filter { it.isSongOnly() }
            .distinctSongs()

        // 5. Interleaving Algorithm with Attribution
        val attributedList = mutableListOf<AttributedTrack>()
        val seenTrackKeys = HashSet<String>()

        fun addTrack(track: TrackEntity, broughtBy: String, broughtByName: String) {
            val key = track.cleanSongTitle().replace(" ", "")
            if (key.isNotBlank() && seenTrackKeys.add(key)) {
                attributedList.add(AttributedTrack(track, broughtBy, broughtByName))
            }
        }

        // Add Shared Anthem first as the iconic anchor
        if (sharedAnthem != null) {
            addTrack(sharedAnthem, "both", "Both")
        }

        val uIter = userVault.iterator()
        val pIter = partnerAnchorTracks.distinctSongs().iterator()
        val dIter = mutualDiscoveryTracks.iterator()

        while (attributedList.size < 35 && (uIter.hasNext() || pIter.hasNext() || dIter.hasNext())) {
            if (uIter.hasNext() && attributedList.size < 35) {
                addTrack(uIter.next(), "creator", creatorName)
            }
            if (pIter.hasNext() && attributedList.size < 35) {
                addTrack(pIter.next(), "partner", partnerName)
            }
            if (dIter.hasNext() && attributedList.size < 35 && attributedList.size % 4 == 0) {
                addTrack(dIter.next(), "both", "Both")
            }
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

        // Fetch signature hits for each selected artist
        val artistTracksMap = mutableMapOf<String, List<TrackEntity>>()
        selectedArtists.forEach { artist ->
            val tracks = streamRepository.searchTracks(artist.signatureQuery).firstOrNull()?.getOrNull().orEmpty()
                .filter { it.isSongOnly() }
                .distinctSongs()
            artistTracksMap[artist.name] = tracks
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
