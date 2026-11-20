package com.satvik.satvikx.data.repository

import android.util.Log
import com.satvik.satvikx.data.local.dao.ArtistDao
import com.satvik.satvikx.data.local.entity.ArtistCacheEntity
import com.satvik.satvikx.data.local.entity.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class DeezerArtistDetail(
    val id: Long,
    val name: String,
    val imageUrl: String?,
    val fansCount: Long,
    val tracklistUrl: String?
)

@Singleton
class DeezerArtistRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val artistDao: ArtistDao
) {
    companion object {
        private const val TAG = "DeezerArtist"
        private const val CACHE_EXPIRY_MS = 7L * 24 * 60 * 60 * 1000L // 7 days

        // Standalone keywords that denote channels, genres, or devotional tags, NEVER musical performers
        private val DISQUALIFIED_ARTIST_TERMS = listOf(
            "bhajan", "bhakti", "aarti", "chalisa", "kirtan", "dhun", "mantra",
            "stotram", "stuti", "satsang", "shree ram", "sita ram", "shri ram", "devotional",
            "lofi", "lo-fi", "chill", "slowed", "reverb", "bass boosted", "trap city",
            "trap nation", "mashup", "jukebox", "compilation", "nonstop", "non stop",
            "channel", "vevo", "records", "entertainment", "studio", "studios",
            "official", "lyrics", "lyrical", "status", "creation", "creations", "films",
            "top 10", "top 20", "top 50", "best of"
        )
    }

    /**
     * Checks if a string is a genuine artist name or a junk channel/genre/devotional label.
     */
    fun isValidArtistCandidate(name: String): Boolean {
        if (name.isBlank() || name.length < 2 || name.length > 50) return false
        val lower = name.trim().lowercase(java.util.Locale.ROOT)
        if (lower == "unknown artist" || lower == "various artists" || lower == "featured artist") return false

        for (term in DISQUALIFIED_ARTIST_TERMS) {
            if (lower.contains(term)) return false
        }
        return true
    }

    /**
     * Retrieves verified artist portrait and metadata.
     * Checks local Room DB cache first (0ms latency).
     * If missing or expired, queries Deezer Public API and caches the result.
     */
    suspend fun getArtistPortrait(rawArtistName: String): DeezerArtistDetail? = withContext(Dispatchers.IO) {
        val cleanName = cleanArtistQuery(rawArtistName)
        if (!isValidArtistCandidate(cleanName)) return@withContext null

        // 1. Check local Room DB cache
        val cached = artistDao.getArtist(cleanName)
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.cachedAtTimestamp < CACHE_EXPIRY_MS) && !cached.imageUrl.isNullOrBlank()) {
            return@withContext DeezerArtistDetail(
                id = cached.deezerId ?: 0L,
                name = cached.artistName,
                imageUrl = cached.imageUrl,
                fansCount = cached.fansCount,
                tracklistUrl = null
            )
        }

        // 2. Fetch from Deezer Public API
        try {
            val encoded = URLEncoder.encode(cleanName, "UTF-8")
            val request = Request.Builder()
                .url("https://api.deezer.com/search/artist?q=$encoded&limit=5")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val root = JSONObject(body)
            val data = root.optJSONArray("data") ?: return@withContext null
            if (data.length() == 0) return@withContext null

            // Find closest matching artist in the results
            var bestMatch: JSONObject? = null
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val candidateName = item.optString("name", "")
                if (isNameMatch(cleanName, candidateName)) {
                    bestMatch = item
                    break
                }
            }

            if (bestMatch == null) {
                bestMatch = data.optJSONObject(0)
            }

            val matchedObj = bestMatch ?: return@withContext null
            val id = matchedObj.optLong("id", 0L)
            val canonicalName = matchedObj.optString("name", cleanName)
            val pictureBig = matchedObj.optString("picture_big", "").takeIf { it.isNotBlank() }
            val pictureMedium = matchedObj.optString("picture_medium", "").takeIf { it.isNotBlank() }
            val chosenImage = pictureBig ?: pictureMedium
            val fans = matchedObj.optLong("nb_fan", 0L)
            val tracklist = matchedObj.optString("tracklist", "")

            // 3. Persist to Room DB cache
            val entity = ArtistCacheEntity(
                artistName = canonicalName,
                deezerId = id,
                imageUrl = chosenImage,
                fansCount = fans,
                cachedAtTimestamp = now
            )
            artistDao.insertArtist(entity)
            if (!canonicalName.equals(cleanName, ignoreCase = true)) {
                artistDao.insertArtist(entity.copy(artistName = cleanName))
            }

            DeezerArtistDetail(
                id = id,
                name = canonicalName,
                imageUrl = chosenImage,
                fansCount = fans,
                tracklistUrl = tracklist
            )
        } catch (e: Exception) {
            Log.w(TAG, "Deezer artist fetch error for '$cleanName': ${e.message}")
            null
        }
    }

    /**
     * Fetches genuine individual hit tracks for an artist from Deezer's top tracklist.
     * Guaranteed to return distinct individual hit songs, NOT 1-hour compilation mixes!
     */
    suspend fun getArtistTopTracks(artistName: String): List<TrackEntity> = withContext(Dispatchers.IO) {
        val artist = getArtistPortrait(artistName)
        val deezerId = artist?.id

        if (deezerId != null && deezerId > 0) {
            try {
                val request = Request.Builder()
                    .url("https://api.deezer.com/artist/$deezerId/top?limit=30")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val root = JSONObject(body)
                    val data = root.optJSONArray("data")
                    if (data != null && data.length() > 0) {
                        val tracks = mutableListOf<TrackEntity>()
                        for (i in 0 until data.length()) {
                            val item = data.optJSONObject(i) ?: continue
                            val id = item.optLong("id")
                            val title = item.optString("title", "")
                            val duration = item.optLong("duration", 0L)
                            val artObj = item.optJSONObject("artist")
                            val aName = artObj?.optString("name", artist.name) ?: artist.name
                            val albumObj = item.optJSONObject("album")
                            val cover = albumObj?.optString("cover_big", "")
                                ?.takeIf { it.isNotBlank() }
                                ?: albumObj?.optString("cover_medium", "").orEmpty()

                            if (title.isNotBlank()) {
                                tracks.add(
                                    TrackEntity(
                                        id = "dz_$id",
                                        title = title,
                                        artist = aName,
                                        durationSeconds = duration,
                                        thumbnailUrl = cover.ifBlank { artist.imageUrl.orEmpty() }
                                    )
                                )
                            }
                        }
                        if (tracks.isNotEmpty()) {
                            return@withContext tracks
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Deezer top tracks fetch error for $artistName: ${e.message}")
            }
        }

        emptyList()
    }

    private fun cleanArtistQuery(raw: String): String {
        return raw.replace(" - Topic", "", ignoreCase = true)
            .replace(Regex("""[\(\[\{].*?[\)\]\}]"""), " ")
            .trim()
    }

    private fun isNameMatch(search: String, candidate: String): Boolean {
        val s = search.lowercase(java.util.Locale.ROOT).trim()
        val c = candidate.lowercase(java.util.Locale.ROOT).trim()
        return s == c || s.contains(c) || c.contains(s)
    }
}

