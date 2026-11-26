package com.satvik.satvikx.data.repository

import android.util.Base64
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class SharedPlaylistPayload(
    val version: Int = 1,
    val title: String,
    val creatorName: String = "SatvikX User",
    val createdAt: Long = System.currentTimeMillis(),
    val tracks: List<SharedTrackDto>
)

@JsonClass(generateAdapter = true)
data class SharedTrackDto(
    val id: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long = 0L,
    val thumbnailUrl: String = ""
) {
    fun toTrackEntity(): TrackEntity = TrackEntity(
        id = id,
        title = title,
        artist = artist,
        durationSeconds = durationSeconds,
        thumbnailUrl = thumbnailUrl
    )
}

fun TrackEntity.toSharedTrackDto(): SharedTrackDto = SharedTrackDto(
    id = id,
    title = title,
    artist = artist,
    durationSeconds = durationSeconds,
    thumbnailUrl = thumbnailUrl ?: ""
)

/**
 * High-performance, self-contained sharing & deep-linking engine for SatvikX Playlists.
 * Encodes playlist tracks into compact, compressed URL-safe tokens with zero server dependencies.
 */
@Singleton
class PlaylistShareManager @Inject constructor(
    private val moshi: Moshi
) {

    companion object {
        const val TOKEN_PREFIX = "STKPL:"
        const val SCHEME_SATVIKX = "satvikx"
        const val HOST_PLAYLIST = "playlist"
        const val WEB_BASE_URL = "https://satvikx.app/playlist"
    }

    private val _pendingPlaylist = MutableStateFlow<SharedPlaylistPayload?>(null)
    val pendingPlaylist: StateFlow<SharedPlaylistPayload?> = _pendingPlaylist.asStateFlow()

    fun setPendingPlaylist(payload: SharedPlaylistPayload?) {
        _pendingPlaylist.value = payload
    }

    fun clearPendingPlaylist() {
        _pendingPlaylist.value = null
    }

    fun handleDeepLink(rawInput: String): Boolean {
        val decoded = decodePayload(rawInput)
        if (decoded != null && decoded.tracks.isNotEmpty()) {
            _pendingPlaylist.value = decoded
            return true
        }
        return false
    }

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
     * Compresses and encodes a playlist payload into an ultra-compact STKPL token.
     */
    fun encodePayload(payload: SharedPlaylistPayload): String {
        val adapter = moshi.adapter(SharedPlaylistPayload::class.java)
        val json = adapter.toJson(payload)
        val byteStream = ByteArrayOutputStream()
        GZIPOutputStream(byteStream).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        val compressed = byteStream.toByteArray()
        val base64 = safeBase64Encode(compressed)
        return "$TOKEN_PREFIX$base64"
    }

    /**
     * Decodes and decompresses a shared playlist token or raw deep-link text.
     */
    fun decodePayload(rawInput: String): SharedPlaylistPayload? {
        if (rawInput.isBlank()) return null
        val cleanToken = extractToken(rawInput)
        if (cleanToken.isBlank()) return null
        val cleanBase64 = cleanToken.removePrefix(TOKEN_PREFIX)
        val compressed = safeBase64Decode(cleanBase64) ?: return null
        return try {
            val json = GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val adapter = moshi.adapter(SharedPlaylistPayload::class.java)
            adapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a direct clickable app deep-link (satvikx://playlist?data=...).
     */
    fun createShareLink(payload: SharedPlaylistPayload): String {
        val token = encodePayload(payload)
        val encodedToken = try {
            URLEncoder.encode(token, "UTF-8")
        } catch (_: Exception) {
            token
        }
        return "satvikx://playlist?data=$encodedToken"
    }

    /**
     * Generates a universal web link fallback (https://satvikx.app/playlist?data=...).
     */
    fun createWebShareLink(payload: SharedPlaylistPayload): String {
        val token = encodePayload(payload)
        val encodedToken = try {
            URLEncoder.encode(token, "UTF-8")
        } catch (_: Exception) {
            token
        }
        return "$WEB_BASE_URL?data=$encodedToken"
    }

    /**
     * Robust parser that extracts the STKPL token from messy chat messages, deep-links, or raw codes.
     */
    fun extractToken(input: String): String {
        val trimmed = input.trim()

        // 1. Check if it's a URL with query parameters like data= or token= or code=
        val queryParamMatch = Regex("""[?&](?:data|token|code)=([^&\s]+)""").find(trimmed)
        if (queryParamMatch != null) {
            val rawParam = queryParamMatch.groupValues[1]
            val decoded = try {
                URLDecoder.decode(rawParam, "UTF-8")
            } catch (_: Exception) {
                rawParam
            }
            if (decoded.startsWith(TOKEN_PREFIX)) return decoded
            if (decoded.length > 20) return "$TOKEN_PREFIX$decoded"
        }

        // 2. Check if text contains STKPL:<base64>
        val prefixIndex = trimmed.indexOf(TOKEN_PREFIX)
        if (prefixIndex != -1) {
            val substring = trimmed.substring(prefixIndex)
            val endDelimiters = listOf(" ", "\n", "\r", "\t", "&", "\"", "'", "<", ">", ")")
            var endIndex = substring.length
            for (delim in endDelimiters) {
                val idx = substring.indexOf(delim)
                if (idx != -1 && idx < endIndex) {
                    endIndex = idx
                }
            }
            return substring.substring(0, endIndex).trim()
        }

        // 3. Fallback: maybe clean token without prefix
        return if (trimmed.startsWith(TOKEN_PREFIX)) trimmed else "$TOKEN_PREFIX$trimmed"
    }

    /**
     * Prepares a high-converting, friendly share message for WhatsApp, Telegram, Instagram, etc.
     */
    fun buildShareMessage(payload: SharedPlaylistPayload): String {
        val appLink = createShareLink(payload)
        val trackCount = payload.tracks.size
        val countText = if (trackCount == 1) "1 song" else "$trackCount songs"

        return buildString {
            appendLine("🎵 Listen to my playlist \"${payload.title}\" on SatvikX!")
            appendLine("Curated by ${payload.creatorName} ($countText)")
            appendLine()
            appendLine("Tap to open & import:")
            appendLine(appLink)
        }
    }
}
