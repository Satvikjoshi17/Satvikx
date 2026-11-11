package com.satvik.satvikx.data.repository

import android.util.Log
import com.satvik.satvikx.data.local.dao.LyricsDao
import com.satvik.satvikx.data.local.entity.LyricsEntity
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.entity.extractPolishedArtist
import com.satvik.satvikx.data.local.entity.extractPolishedTitle
import com.satvik.satvikx.data.parser.LrcParser
import com.satvik.satvikx.data.remote.model.LrcLine
import com.satvik.satvikx.data.remote.model.LyricsData
import com.satvik.satvikx.data.remote.newpipe.NewPipeYouTubeEngine
import com.satvik.satvikx.data.remote.saavn.SaavnMediaEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enterprise Repository orchestrating a 4-tier cascading fallback pipeline for synchronized
 * and plain-text lyrics retrieval across decentralized global sources:
 *
 * 1. Room SQLite Database: Local cache lookup (`LyricsDao`) for instant 0ms offline retrieval.
 * 2. LRCLIB API: Primary global time-synced lyrics source (`GET /api/get` + `GET /api/search` fallback).
 * 3. JioSaavn API: Regional Indian / Bollywood plain-text lyrics source (`SaavnMediaEngine`).
 * 4. YouTube Timed Captions: Universal fallback extracting WebVTT cues (`NewPipeYouTubeEngine`).
 *
 * All successful remote retrievals are persistently cached in Room SQLite to ensure offline availability.
 * Exception safety is strictly guaranteed: all network/parsing failures are handled without crashing.
 */
interface LyricsRepository {

    /**
     * Resolves lyrics for the given [track] using the 4-tier cascade.
     *
     * @param track The track entity whose lyrics are requested.
     * @return [Result] wrapping [LyricsData] or null if no lyrics are available.
     */
    suspend fun getLyrics(track: TrackEntity): Result<LyricsData?>

    /**
     * Resolves lyrics for the specified track metadata, supporting network availability checks.
     *
     * @param trackId Unique identifier of the track.
     * @param title Raw or formatted song title.
     * @param artist Artist or channel name.
     * @param album Optional album name for precise matching.
     * @param durationMs Track duration in milliseconds.
     * @param isNetworkAvailable Flag indicating whether network connectivity is available.
     * @return [Result] wrapping [LyricsData] or null if not found.
     */
    suspend fun getLyrics(
        trackId: String,
        title: String,
        artist: String,
        album: String? = null,
        durationMs: Long = 0L,
        isNetworkAvailable: Boolean = true
    ): Result<LyricsData?>

    /**
     * Retrieves cached lyrics directly from local Room SQLite without network calls.
     */
    suspend fun getCachedLyrics(trackId: String): LyricsData?

    /**
     * Purges cached lyrics for a specific track.
     */
    suspend fun clearCachedLyrics(trackId: String)
}

@Singleton
class LyricsRepositoryImpl(
    private val lyricsDao: LyricsDao,
    private val okHttpClient: OkHttpClient,
    private val saavnMediaEngine: SaavnMediaEngine,
    private val newPipeYouTubeEngine: NewPipeYouTubeEngine,
    private val ioDispatcher: CoroutineDispatcher
) : LyricsRepository {

    @Inject
    constructor(
        lyricsDao: LyricsDao,
        okHttpClient: OkHttpClient,
        saavnMediaEngine: SaavnMediaEngine,
        newPipeYouTubeEngine: NewPipeYouTubeEngine
    ) : this(lyricsDao, okHttpClient, saavnMediaEngine, newPipeYouTubeEngine, Dispatchers.IO)

    companion object {
        private const val TAG = "LyricsRepository"
        private const val LRCLIB_BASE_URL = "https://lrclib.net"
        private const val USER_AGENT = "SatvikX/1.0 (https://github.com/satvik/satvikx)"
    }

    override suspend fun getLyrics(track: TrackEntity): Result<LyricsData?> = withContext(ioDispatcher) {
        getLyrics(
            trackId = track.id,
            title = track.title,
            artist = track.artist,
            album = null,
            durationMs = track.durationSeconds * 1000L,
            isNetworkAvailable = true
        )
    }

    override suspend fun getLyrics(
        trackId: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long,
        isNetworkAvailable: Boolean
    ): Result<LyricsData?> = withContext(ioDispatcher) {
        val trimmedTrackId = trackId.trim()
        if (trimmedTrackId.isEmpty()) {
            return@withContext Result.success(null)
        }

        // ==========================================
        // TIER 1: Room SQLite Database Cache (0ms Offline Instant Load)
        // ==========================================
        try {
            val cached = lyricsDao.getLyricsByTrackId(trimmedTrackId)
            if (cached != null) {
                Log.d(TAG, "Cache HIT for track $trimmedTrackId from provider ${cached.provider}")
                val parsedLines = if (cached.isSynced && !cached.lrcContent.isNullOrBlank()) {
                    LrcParser.parse(cached.lrcContent)
                } else {
                    emptyList()
                }
                return@withContext Result.success(
                    LyricsData(
                        trackId = cached.trackId,
                        isSynced = cached.isSynced,
                        lines = parsedLines,
                        plainLyrics = cached.plainLyrics,
                        provider = cached.provider
                    )
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Error querying local Room lyrics cache: ${e.message}")
        }

        // If offline and cache missed, return null gracefully
        if (!isNetworkAvailable) {
            Log.d(TAG, "Network unavailable and cache miss for track $trimmedTrackId")
            return@withContext Result.success(null)
        }

        val cleanTitle = extractPolishedTitle(title, artist)
        val cleanArtist = extractPolishedArtist(title, artist)
        val durationSec = if (durationMs > 0) durationMs / 1000L else null

        // ==========================================
        // TIER 2: LRCLIB API Query & Search Fallback
        // ==========================================
        try {
            val lrclibResult = fetchFromLrclib(cleanTitle, cleanArtist, album, durationSec)
            if (lrclibResult != null) {
                val isInstrumental = !lrclibResult.isSynced && lrclibResult.lines.isEmpty() && lrclibResult.plainLyrics == null && lrclibResult.provider == "LRCLIB"
                val hasContent = (lrclibResult.isSynced && lrclibResult.lines.isNotEmpty()) ||
                        (!lrclibResult.plainLyrics.isNullOrBlank()) ||
                        isInstrumental
                if (hasContent) {
                    Log.i(TAG, "Fetched lyrics from LRCLIB for '$cleanTitle' by '$cleanArtist' (instrumental=$isInstrumental)")
                    cacheLyrics(trimmedTrackId, lrclibResult)
                    return@withContext Result.success(lrclibResult.copy(trackId = trimmedTrackId))
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "LRCLIB query failed: ${e.message}")
        }

        // ==========================================
        // TIER 3: JioSaavn Lyrics API (Bollywood / Regional Indian)
        // ==========================================
        try {
            val saavnResult = fetchFromJioSaavn(trimmedTrackId, cleanTitle, cleanArtist)
            if (saavnResult != null && !saavnResult.plainLyrics.isNullOrBlank()) {
                Log.i(TAG, "Fetched plain lyrics from JioSaavn for '$cleanTitle'")
                cacheLyrics(trimmedTrackId, saavnResult)
                return@withContext Result.success(saavnResult.copy(trackId = trimmedTrackId))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "JioSaavn query failed: ${e.message}")
        }

        // ==========================================
        // TIER 4: YouTube Timed Captions Fallback (NewPipeExtractor WebVTT)
        // ==========================================
        try {
            val ytResult = fetchFromYouTubeCaptions(trimmedTrackId)
            if (ytResult != null && ytResult.lines.isNotEmpty()) {
                Log.i(TAG, "Fetched timed captions from YouTube for '$trimmedTrackId'")
                cacheLyrics(trimmedTrackId, ytResult)
                return@withContext Result.success(ytResult.copy(trackId = trimmedTrackId))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "YouTube captions query failed: ${e.message}")
        }

        // Cascade exhausted: no lyrics available
        Log.d(TAG, "All lyrics providers exhausted for track $trimmedTrackId ($cleanTitle - $cleanArtist)")
        Result.success(null)
    }

    override suspend fun getCachedLyrics(trackId: String): LyricsData? = withContext(ioDispatcher) {
        val cached = lyricsDao.getLyricsByTrackId(trackId) ?: return@withContext null
        val parsedLines = if (cached.isSynced && !cached.lrcContent.isNullOrBlank()) {
            LrcParser.parse(cached.lrcContent)
        } else {
            emptyList()
        }
        LyricsData(
            trackId = cached.trackId,
            isSynced = cached.isSynced,
            lines = parsedLines,
            plainLyrics = cached.plainLyrics,
            provider = cached.provider
        )
    }

    override suspend fun clearCachedLyrics(trackId: String) = withContext(ioDispatcher) {
        lyricsDao.deleteLyrics(trackId)
    }

    private fun fetchFromLrclib(
        title: String,
        artist: String,
        album: String?,
        durationSec: Long?
    ): LyricsData? {
        if (title.isBlank()) return null

        // Step 2a: Try GET /api/get
        val exactMatch = queryLrclibGet(title, artist, album, durationSec)
        if (exactMatch != null) return exactMatch

        // Step 2b: Search Fallback GET /api/search
        return queryLrclibSearch(title, artist, durationSec)
    }

    private fun queryLrclibGet(
        title: String,
        artist: String,
        album: String?,
        durationSec: Long?
    ): LyricsData? {
        return try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            var url = "$LRCLIB_BASE_URL/api/get?track_name=$encodedTitle&artist_name=$encodedArtist"
            if (!album.isNullOrBlank()) {
                url += "&album_name=${URLEncoder.encode(album, "UTF-8")}"
            }
            if (durationSec != null && durationSec > 0) {
                url += "&duration=$durationSec"
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                return null
            }

            val body = response.body!!.string()
            parseLrclibResponse(body)
        } catch (e: Exception) {
            Log.d(TAG, "Lrclib /api/get non-fatal error: ${e.message}")
            null
        }
    }

    private fun queryLrclibSearch(
        title: String,
        artist: String,
        durationSec: Long?
    ): LyricsData? {
        return try {
            val query = if (artist.isNotBlank()) "$title $artist" else title
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$LRCLIB_BASE_URL/api/search?q=$encodedQuery"

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/json")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                return null
            }

            val body = response.body!!.string().trim()
            if (!body.startsWith("[")) return null

            val jsonArray = JSONArray(body)
            if (jsonArray.length() == 0) return null

            var bestSynced: LyricsData? = null
            var bestPlain: LyricsData? = null
            var minDurationDelta = Double.MAX_VALUE

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(i) ?: continue
                val parsed = parseLrclibItem(item) ?: continue

                val itemDuration = item.optDouble("duration", 0.0)
                val durationDelta = if (durationSec != null && durationSec > 0 && itemDuration > 0) {
                    Math.abs(itemDuration - durationSec)
                } else 0.0

                // Reject candidate if duration disparity exceeds 25 seconds to prevent pairing with incompatible versions/edits
                if (durationSec != null && durationSec > 0 && itemDuration > 0 && durationDelta > 25.0) {
                    continue
                }

                if (parsed.isSynced && parsed.lines.isNotEmpty()) {
                    if (durationDelta < minDurationDelta) {
                        minDurationDelta = durationDelta
                        bestSynced = parsed
                    }
                } else if (bestPlain == null && !parsed.plainLyrics.isNullOrBlank()) {
                    bestPlain = parsed
                }
            }

            bestSynced ?: bestPlain
        } catch (e: Exception) {
            Log.d(TAG, "Lrclib /api/search non-fatal error: ${e.message}")
            null
        }
    }

    private fun parseLrclibResponse(jsonStr: String): LyricsData? {
        val trimmed = jsonStr.trim()
        if (!trimmed.startsWith("{")) return null
        val json = JSONObject(trimmed)
        return parseLrclibItem(json)
    }

    private fun parseLrclibItem(json: JSONObject): LyricsData? {
        val isInstrumental = json.optBoolean("instrumental", false)
        val rawSynced = json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        val rawPlain = json.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }

        if (isInstrumental) {
            return LyricsData(
                trackId = "",
                isSynced = false,
                lines = emptyList(),
                plainLyrics = null,
                provider = "LRCLIB"
            )
        }

        if (rawSynced != null) {
            val lines = LrcParser.parse(rawSynced)
            if (lines.isNotEmpty()) {
                return LyricsData(
                    trackId = "",
                    isSynced = true,
                    lines = lines,
                    plainLyrics = rawPlain,
                    provider = "LRCLIB"
                )
            }
        }

        if (rawPlain != null) {
            return LyricsData(
                trackId = "",
                isSynced = false,
                lines = emptyList(),
                plainLyrics = rawPlain,
                provider = "LRCLIB"
            )
        }

        return null
    }

    private fun fetchFromJioSaavn(
        trackId: String,
        cleanTitle: String,
        cleanArtist: String
    ): LyricsData? {
        // Step 3a: Try direct song ID if it is a JioSaavn song ID (e.g. not a YouTube 11-char ID)
        val isYouTubeId = trackId.length == 11 && !trackId.contains(" ")
        if (!isYouTubeId) {
            val directLyrics = saavnMediaEngine.getLyricsSync(trackId)
            if (!directLyrics.isNullOrBlank()) {
                return LyricsData(
                    trackId = trackId,
                    isSynced = false,
                    lines = emptyList(),
                    plainLyrics = directLyrics,
                    provider = "JIOSAAVN"
                )
            }
        }

        // Step 3b: Search JioSaavn catalog by clean title + artist
        val query = if (cleanArtist.isNotBlank()) "$cleanTitle $cleanArtist" else cleanTitle
        val saavnTracks = saavnMediaEngine.searchTracks(query)
        if (saavnTracks.isEmpty()) return null

        for (candidate in saavnTracks.take(2)) {
            val candidateLyrics = saavnMediaEngine.getLyricsSync(candidate.id)
            if (!candidateLyrics.isNullOrBlank()) {
                return LyricsData(
                    trackId = trackId,
                    isSynced = false,
                    lines = emptyList(),
                    plainLyrics = candidateLyrics,
                    provider = "JIOSAAVN"
                )
            }
        }

        return null
    }

    private fun fetchFromYouTubeCaptions(trackId: String): LyricsData? {
        val lines = newPipeYouTubeEngine.getSubtitles(trackId)
        if (lines.isEmpty()) return null

        val plain = lines.joinToString("\n") { it.text }
        return LyricsData(
            trackId = trackId,
            isSynced = true,
            lines = lines,
            plainLyrics = plain,
            provider = "YOUTUBE"
        )
    }

    private suspend fun cacheLyrics(trackId: String, data: LyricsData) {
        try {
            val lrcContent = if (data.isSynced && data.lines.isNotEmpty()) {
                LrcParser.format(data.lines)
            } else null

            val entity = LyricsEntity(
                trackId = trackId,
                isSynced = data.isSynced,
                lrcContent = lrcContent,
                plainLyrics = data.plainLyrics,
                provider = data.provider,
                cachedAtTimestamp = System.currentTimeMillis()
            )
            lyricsDao.insertLyrics(entity)
            Log.d(TAG, "Cached lyrics in Room for track $trackId (provider: ${data.provider}, isSynced: ${data.isSynced})")
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to cache lyrics for $trackId in Room: ${e.message}")
        }
    }
}
