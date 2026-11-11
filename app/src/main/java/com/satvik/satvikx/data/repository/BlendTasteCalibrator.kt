package com.satvik.satvikx.data.repository

import android.util.Base64
import com.squareup.moshi.Moshi
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enterprise Taste Calibration & Cryptographic Token Engine for Stark Blend.
 *
 * Provides:
 * 1. Normalized fuzzy artist and collaboration matching.
 * 2. Canonical musical genre normalization and vibration clustering.
 * 3. Multi-tier deterministic affinity scoring calibrated between 70% and 98%.
 * 4. Compact GZIP-compressed decentralized token serialization and deserialization.
 * 5. Robust multi-format chat/deep-link string sanitization.
 */
@Singleton
class BlendTasteCalibrator @Inject constructor() {

    fun safeBase64Encode(bytes: ByteArray): String {
        val androidResult = try {
            Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
        } catch (_: Throwable) {
            null
        }
        if (!androidResult.isNullOrBlank()) {
            return androidResult
        }
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun safeBase64Decode(str: String): ByteArray? {
        val androidResult = try {
            Base64.decode(str, Base64.NO_WRAP or Base64.URL_SAFE)
        } catch (_: Throwable) {
            null
        }
        if (androidResult != null && androidResult.isNotEmpty()) {
            return androidResult
        }
        return try {
            java.util.Base64.getUrlDecoder().decode(str)
        } catch (_: Throwable) {
            try {
                java.util.Base64.getDecoder().decode(str)
            } catch (_: Throwable) {
                null
            }
        }
    }

    /**
     * Extracts normalized individual artist tokens, gracefully splitting collaborations
     * ("Arijit Singh, Pritam", "The Weeknd ft. Daft Punk", etc.) to guarantee high-precision matching.
     */
    fun extractNormalizedArtists(artists: List<String>): Set<String> {
        return artists.flatMap { raw ->
            raw.split(",", "&", "feat.", "ft.", "/", "•", "|", ";")
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotBlank() && it.length > 1 }
        }.toSet()
    }

    /**
     * Normalizes musical genres to canonical sonic categories for accurate taste vectors.
     */
    fun normalizeGenre(genre: String): String {
        val g = genre.lowercase(Locale.ROOT).trim().replace("-", "").replace(" ", "")
        return when {
            g.contains("bollywood") || g.contains("desi") || g.contains("filmi") || g.contains("hindi") -> "bollywood"
            g.contains("punjabi") || g.contains("bhangra") -> "punjabi"
            g.contains("hiphop") || g.contains("rap") || g.contains("trap") || g.contains("drill") -> "hiphop"
            g.contains("pop") -> "pop"
            g.contains("rock") || g.contains("metal") -> "rock"
            g.contains("indie") || g.contains("folk") || g.contains("acoustic") -> "indie"
            g.contains("edm") || g.contains("house") || g.contains("electronic") || g.contains("dance") -> "edm"
            g.contains("synthwave") || g.contains("synth") || g.contains("retro") || g.contains("cyberpunk") -> "synthwave"
            g.contains("sufi") || g.contains("qawwali") || g.contains("ghazal") -> "sufi"
            g.contains("rnb") || g.contains("soul") -> "r&b"
            else -> g
        }
    }

    /**
     * Formats normalized genres into polished user-facing display titles.
     */
    fun formatGenreForDisplay(normalized: String): String {
        return when (normalized) {
            "bollywood" -> "Bollywood Romance"
            "punjabi" -> "Punjabi Swag"
            "hiphop" -> "Hip-Hop & Trap"
            "pop" -> "Global Pop"
            "rock" -> "Classic Rock"
            "indie" -> "Indie Acoustic"
            "edm" -> "Electronic Dance"
            "synthwave" -> "Synthwave"
            "sufi" -> "Soulful Sufi"
            "r&b" -> "R&B Soul"
            else -> normalized.replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Calculates mathematical taste match compatibility between 70% and 98%.
     * Employs multi-tier calibration across normalized artists, genres, and exact song overlaps.
     */
    fun calculateTasteMatchScore(
        userArtists: List<String>,
        partnerArtists: List<String>,
        userGenres: List<String>,
        partnerGenres: List<String>,
        userTrackTitles: List<String> = emptyList(),
        partnerTrackTitles: List<String> = emptyList()
    ): Pair<Int, String> {
        val uA = extractNormalizedArtists(userArtists)
        val pA = extractNormalizedArtists(partnerArtists)

        val uG = userGenres.map { normalizeGenre(it) }.filter { it.isNotBlank() }.toSet()
        val pG = partnerGenres.map { normalizeGenre(it) }.filter { it.isNotBlank() }.toSet()

        val uT = userTrackTitles.map { it.trim().lowercase(Locale.ROOT) }.filter { it.length > 2 }.toSet()
        val pT = partnerTrackTitles.map { it.trim().lowercase(Locale.ROOT) }.filter { it.length > 2 }.toSet()

        // 1. Shared Artists (with cross-containment checks)
        val sharedArtists = uA.filter { userArtist ->
            pA.any { partnerArtist ->
                userArtist == partnerArtist || userArtist.contains(partnerArtist) || partnerArtist.contains(userArtist)
            }
        }.toSet()

        val artistOverlapScore = if (uA.isNotEmpty() && pA.isNotEmpty()) {
            (2.0f * sharedArtists.size) / (uA.size + pA.size)
        } else 0.45f

        // 2. Shared Genres
        val sharedGenres = uG.intersect(pG)
        val genreOverlapScore = if (uG.isNotEmpty() && pG.isNotEmpty()) {
            (2.0f * sharedGenres.size) / (uG.size + pG.size)
        } else 0.55f

        // 3. Shared Song Overlap
        val sharedTracks = uT.filter { uSong ->
            pT.any { pSong ->
                uSong == pSong || (uSong.length > 4 && pSong.contains(uSong)) || (pSong.length > 4 && uSong.contains(pSong))
            }
        }.toSet()

        val trackOverlapBonus = if (sharedTracks.isNotEmpty()) {
            (sharedTracks.size * 0.15f).coerceAtMost(0.40f)
        } else 0.0f

        val weightedScore = (0.42f * artistOverlapScore) + (0.38f * genreOverlapScore) + (0.20f * (trackOverlapBonus / 0.40f).coerceIn(0f, 1f))

        // Calibrated friendly range: 70% to 98%
        val finalScore = (70 + (weightedScore * 28)).toInt().coerceIn(70, 98)

        // Select the richest shared vibe display name
        val topSharedVibe = when {
            sharedGenres.isNotEmpty() -> formatGenreForDisplay(sharedGenres.first())
            sharedArtists.isNotEmpty() -> sharedArtists.first().split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            userGenres.isNotEmpty() -> formatGenreForDisplay(normalizeGenre(userGenres.first()))
            partnerGenres.isNotEmpty() -> formatGenreForDisplay(normalizeGenre(partnerGenres.first()))
            else -> "Harmonic Fusion"
        }

        return Pair(finalScore, topSharedVibe)
    }

    /**
     * Calculates calibrated affinity score for curated artist blend.
     */
    fun calculateArtistAffinity(
        selectedArtists: List<CuratedBlendArtist>,
        overallTopArtists: List<String>,
        userVaultArtists: List<String>,
        topGenres: List<String>
    ): Int {
        var affinityBonus = 0
        selectedArtists.forEach { artist ->
            val normArtist = artist.name.lowercase(Locale.ROOT)
            val normCategory = normalizeGenre(artist.category)

            if (overallTopArtists.any { it.lowercase(Locale.ROOT).contains(normArtist) || normArtist.contains(it.lowercase(Locale.ROOT)) }) {
                affinityBonus += 8
            }
            if (userVaultArtists.any { it.lowercase(Locale.ROOT).contains(normArtist) }) {
                affinityBonus += 6
            }
            if (topGenres.any { normalizeGenre(it) == normCategory }) {
                affinityBonus += 5
            }
        }
        return (74 + affinityBonus).coerceIn(74, 98)
    }

    /**
     * Extracts strictly the clean Blend code (e.g. "STKBLD:...") from any input string.
     */
    fun extractCleanCode(rawInput: String): String {
        if (rawInput.isBlank()) return ""
        val trimmed = rawInput.trim()

        val fromQuery = when {
            trimmed.contains("data=") -> trimmed.substringAfter("data=").substringBefore("&").substringBefore(" ").substringBefore("\n")
            trimmed.contains("token=") -> trimmed.substringAfter("token=").substringBefore("&").substringBefore(" ").substringBefore("\n")
            trimmed.contains("code=") -> trimmed.substringAfter("code=").substringBefore("&").substringBefore(" ").substringBefore("\n")
            else -> null
        }

        var candidate = (fromQuery ?: trimmed).trim()

        try {
            candidate = URLDecoder.decode(candidate, "UTF-8")
        } catch (_: Exception) {}

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

        val cleanCandidate = candidate.replace("\\s+".toRegex(), "").trim('"', '\'', '`', '<', '>', '(', ')')
        return if (cleanCandidate.startsWith("STKBLD:")) cleanCandidate else cleanCandidate
    }

    /**
     * Encodes a Blend invite payload into a portable URL-safe GZIP token.
     */
    fun encodeInviteToken(payload: BlendInvitePayload, moshi: Moshi): String {
        val adapter = moshi.adapter(BlendInvitePayload::class.java)
        val json = adapter.toJson(payload)
        val byteStream = ByteArrayOutputStream()
        GZIPOutputStream(byteStream).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        val compressed = byteStream.toByteArray()
        val base64 = safeBase64Encode(compressed)
        return "STKBLD:$base64"
    }

    /**
     * Decodes a portable invite token back into a BlendInvitePayload.
     */
    fun decodeInviteToken(rawToken: String, moshi: Moshi): BlendInvitePayload? {
        if (rawToken.isBlank()) return null

        val cleanCode = extractCleanCode(rawToken)
        val cleanBase64 = if (cleanCode.startsWith("STKBLD:")) {
            cleanCode.removePrefix("STKBLD:")
        } else {
            cleanCode
        }

        if (cleanBase64.isBlank()) return null

        val bytes = safeBase64Decode(cleanBase64) ?: return null

        val json = try {
            if (bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0x1f && (bytes[1].toInt() and 0xFF) == 0x8b) {
                GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                String(bytes, Charsets.UTF_8)
            }
        } catch (_: Exception) {
            return null
        }

        return try {
            moshi.adapter(BlendInvitePayload::class.java).fromJson(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

