package com.satvik.satvikx.data.repository

import android.net.Uri
import android.util.Log
import com.satvik.satvikx.data.local.dao.TrackDao
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.storage.StorageManager
import com.satvik.satvikx.data.remote.api.DynamicStreamApi
import com.satvik.satvikx.data.remote.model.AudioStreamResult
import com.satvik.satvikx.data.remote.model.PipedAudioStream
import com.satvik.satvikx.data.remote.model.PipedStreamResponse
import com.satvik.satvikx.data.remote.resolver.InstanceResolver
import com.satvik.satvikx.data.remote.newpipe.NewPipeYouTubeEngine
import com.satvik.satvikx.data.remote.saavn.SaavnMediaEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

interface StreamRepository {
    fun searchTracks(query: String): Flow<Result<List<TrackEntity>>>
    fun resolveAudioStream(trackIdOrUrl: String): Flow<Result<AudioStreamResult>>
    fun extractVideoId(input: String): String
}

@Singleton
class StreamRepositoryImpl @Inject constructor(
    private val api: DynamicStreamApi,
    private val instanceResolver: InstanceResolver,
    private val trackDao: TrackDao,
    private val storageManager: StorageManager,
    private val okHttpClient: OkHttpClient,
    private val saavnMediaEngine: SaavnMediaEngine,
    private val newPipeYouTubeEngine: NewPipeYouTubeEngine
) : StreamRepository {

    companion object {
        private const val TAG = "StreamRepository"
        private val YOUTUBE_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")
        private val URL_ID_REGEX = Regex("(?:v=|/v/|youtu\\.be/|/embed/|/watch\\?v=|/shorts/)([a-zA-Z0-9_-]{11})")
    }

    override fun extractVideoId(input: String): String {
        val trimmed = input.trim()
        if (YOUTUBE_ID_REGEX.matches(trimmed)) {
            return trimmed
        }
        val match = URL_ID_REGEX.find(trimmed)
        return match?.groupValues?.get(1) ?: trimmed
    }

    override fun searchTracks(query: String): Flow<Result<List<TrackEntity>>> = flow {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            emit(Result.success(emptyList()))
            return@flow
        }

        // Direct YouTube link or ID lookup
        if (URL_ID_REGEX.containsMatchIn(trimmed) || (YOUTUBE_ID_REGEX.matches(trimmed) && !trimmed.all { it.isDigit() })) {
            val ytId = extractVideoId(trimmed)
            val stream = try {
                newPipeYouTubeEngine.resolveAudioStream(ytId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "NewPipe direct YouTube ID search failed: ${e.message}")
                null
            }
            if (stream != null) {
                val track = TrackEntity(
                    id = stream.trackId,
                    title = stream.title,
                    artist = stream.artist,
                    durationSeconds = stream.durationSeconds,
                    thumbnailUrl = stream.thumbnailUrl,
                    streamUrl = stream.streamUrl,
                    isDownloaded = storageManager.isAudioDownloaded(stream.trackId)
                )
                trackDao.insertTrack(track)
                emit(Result.success(listOf(track)))
                return@flow
            }
        }

        // DEEP FUSED SEARCH: Combine YouTube Engine & Studio Master Engine
        val combinedTracks = mutableListOf<TrackEntity>()
        val seenTrackIds = mutableSetOf<String>()

        // 1. YouTube Deep Multi-Page Search via NewPipeExtractor
        try {
            val ytTracks = newPipeYouTubeEngine.search(trimmed, deepSearch = true)
            for (track in ytTracks) {
                if (seenTrackIds.add(track.id)) {
                    combinedTracks.add(track)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "NewPipe YouTube deep search non-fatal: ${e.message}")
        }

        // 2. High-fidelity studio masters from JioSaavn 320kbps CDN
        try {
            val saavnTracks = saavnMediaEngine.searchTracks(trimmed)
            for (track in saavnTracks) {
                if (seenTrackIds.add(track.id)) {
                    combinedTracks.add(track)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Native Saavn search non-fatal: ${e.message}")
        }

        if (combinedTracks.isNotEmpty()) {
            val processedTracks = combinedTracks.map { track ->
                track.copy(isDownloaded = storageManager.isAudioDownloaded(track.id))
            }
            trackDao.insertTracks(processedTracks)
            emit(Result.success(processedTracks))
            return@flow
        }

        val encodedQuery = try {
            URLEncoder.encode(trimmed, "UTF-8")
        } catch (e: Exception) {
            trimmed
        }

        var fallbackTracks: List<TrackEntity>? = null

        // 3. Try Piped instances with round-robin failover
        val pipedCandidates = instanceResolver.getPipedCandidates()
        for (node in pipedCandidates) {
            val url = "$node/search?q=$encodedQuery&filter=music_songs"
            try {
                val response = api.getPipedSearch(url)
                if (response.isSuccessful && response.body() != null) {
                    val rawItems = response.body()!!.items
                    val tracks = rawItems
                        .filter { it.url != null && (it.type == "stream" || it.duration != null) }
                        .map { item ->
                            val rawUrl = item.url.orEmpty()
                            val id = extractVideoId(rawUrl)
                            TrackEntity(
                                id = id,
                                title = item.title.orEmpty().ifBlank { "Unknown Title" },
                                artist = item.uploaderName.orEmpty().ifBlank { "Unknown Artist" },
                                durationSeconds = item.duration ?: 0L,
                                thumbnailUrl = item.thumbnail.orEmpty(),
                                streamUrl = null,
                                localPath = null,
                                isDownloaded = storageManager.isAudioDownloaded(id)
                            )
                        }

                    if (tracks.isNotEmpty()) {
                        instanceResolver.reportSuccess(node)
                        fallbackTracks = tracks
                        break
                    }
                } else {
                    instanceResolver.reportFailure(node, "HTTP ${response.code()}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                instanceResolver.reportFailure(node, e.message ?: "Network error")
            }
        }

        // 4. Fallback to Invidious instances if Piped fails or returns zero items
        if (fallbackTracks.isNullOrEmpty()) {
            val invidiousCandidates = instanceResolver.getInvidiousCandidates()
            for (node in invidiousCandidates) {
                val url = "$node/api/v1/search?q=$encodedQuery&type=video"
                try {
                    val response = api.getInvidiousSearch(url)
                    if (response.isSuccessful && response.body() != null) {
                        val tracks = response.body()!!.map { item ->
                            val id = item.videoId ?: extractVideoId(item.title.orEmpty())
                            val thumbnail = item.videoThumbnails?.firstOrNull()?.url.orEmpty()
                            TrackEntity(
                                id = id,
                                title = item.title.orEmpty().ifBlank { "Unknown Title" },
                                artist = item.author.orEmpty().ifBlank { "Unknown Artist" },
                                durationSeconds = item.lengthSeconds ?: 0L,
                                thumbnailUrl = thumbnail,
                                streamUrl = null,
                                localPath = null,
                                isDownloaded = storageManager.isAudioDownloaded(id)
                            )
                        }

                        if (tracks.isNotEmpty()) {
                            instanceResolver.reportSuccess(node)
                            fallbackTracks = tracks
                            break
                        }
                    } else {
                        instanceResolver.reportFailure(node, "HTTP ${response.code()}")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    instanceResolver.reportFailure(node, e.message ?: "Network error")
                }
            }
        }

        if (!fallbackTracks.isNullOrEmpty()) {
            trackDao.insertTracks(fallbackTracks)
            emit(Result.success(fallbackTracks))
        } else {
            emit(Result.failure(IOException("All decentralized instances are currently unreachable or rate limited.")))
        }
    }.flowOn(Dispatchers.IO)

    override fun resolveAudioStream(trackIdOrUrl: String): Flow<Result<AudioStreamResult>> = flow {
        val videoId = extractVideoId(trackIdOrUrl)

        // 1. Check if track is already downloaded locally
        if (storageManager.isAudioDownloaded(videoId)) {
            val localFile = storageManager.getTrackAudioFile(videoId)
            val cached = trackDao.getTrackByIdSync(videoId)
            val result = AudioStreamResult(
                trackId = videoId,
                title = cached?.title ?: "Local Track",
                artist = cached?.artist ?: "SatvikX Offline",
                durationSeconds = cached?.durationSeconds ?: 0L,
                thumbnailUrl = cached?.thumbnailUrl.orEmpty(),
                streamUrl = Uri.fromFile(localFile).toString(),
                mimeType = "audio/mp4",
                bitrate = 320000,
                codec = "m4a",
                resolvedNode = "local_storage"
            )
            emit(Result.success(result))
            return@flow
        }

        // 2. Check if trackIdOrUrl is already an HTTP / HTTPS direct stream URL
        if (trackIdOrUrl.startsWith("http://") || trackIdOrUrl.startsWith("https://")) {
            val cached = trackDao.getTrackByIdSync(videoId)
            val result = AudioStreamResult(
                trackId = videoId,
                title = cached?.title ?: "Stream Audio",
                artist = cached?.artist ?: "Unknown Artist",
                durationSeconds = cached?.durationSeconds ?: 0L,
                thumbnailUrl = cached?.thumbnailUrl.orEmpty(),
                streamUrl = trackIdOrUrl,
                mimeType = "audio/mp4",
                bitrate = 320000,
                codec = "m4a",
                resolvedNode = "direct_url"
            )
            emit(Result.success(result))
            return@flow
        }

        val cached = trackDao.getTrackByIdSync(videoId)
        var streamResult: AudioStreamResult? = null

        // 3. Check if cached track entity already has a working streamUrl
        if (cached != null && !cached.streamUrl.isNullOrBlank()) {
            streamResult = AudioStreamResult(
                trackId = cached.id,
                title = cached.title,
                artist = cached.artist,
                durationSeconds = cached.durationSeconds,
                thumbnailUrl = cached.thumbnailUrl,
                streamUrl = cached.streamUrl,
                mimeType = "audio/mp4",
                bitrate = 320000,
                codec = "m4a",
                resolvedNode = "cached_stream_url"
            )
        }

        val isLikelyYouTubeId = YOUTUBE_ID_REGEX.matches(videoId)

        // 4. Resolve via NewPipeExtractor if YouTube ID
        if (streamResult == null && isLikelyYouTubeId) {
            try {
                val ytResult = newPipeYouTubeEngine.resolveAudioStream(videoId)
                if (ytResult != null && !ytResult.streamUrl.isNullOrBlank()) {
                    streamResult = ytResult
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "NewPipe on-device YouTube resolution failed for $videoId: ${e.message}")
            }
        }

        // 5. Resolve via native Saavn engine by trackId
        if (streamResult == null) {
            try {
                val saavnResult = saavnMediaEngine.resolveTrackById(videoId)
                if (saavnResult != null && !saavnResult.streamUrl.isNullOrBlank()) {
                    streamResult = saavnResult
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Native Saavn resolution failed for $videoId: ${e.message}")
            }
        }

        // 6. Direct native on-device YouTube resolution fallback (if not already tried)
        if (streamResult == null && !isLikelyYouTubeId) {
            try {
                val ytResult = newPipeYouTubeEngine.resolveAudioStream(videoId)
                if (ytResult != null && !ytResult.streamUrl.isNullOrBlank()) {
                    streamResult = ytResult
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "NewPipe fallback YouTube resolution failed for $videoId: ${e.message}")
            }
        }

        // 7. Direct client-side Innertube player resolution (Oculus / Android VR profile)
        if (streamResult == null) {
            try {
                val innertube = resolveDirectInnertube(videoId)
                if (innertube != null && !innertube.streamUrl.isNullOrBlank()) {
                    streamResult = innertube
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Innertube resolution failed: ${e.message}")
            }
        }

        // 8. Query Piped instances for direct audio stream
        if (streamResult == null) {
            val pipedCandidates = instanceResolver.getPipedCandidates()
            for (node in pipedCandidates) {
                val url = "$node/streams/$videoId"
                try {
                    val response = api.getPipedStreams(url)
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        val bestStream = selectBestStream(body)

                        if (bestStream != null && !bestStream.url.isNullOrBlank()) {
                            val duration = body.duration ?: 0L
                            val title = body.title.orEmpty().ifBlank { "Untitled Audio" }
                            val artist = body.uploader.orEmpty().ifBlank { "Unknown Artist" }
                            val thumbnail = body.thumbnailUrl.orEmpty()

                            streamResult = AudioStreamResult(
                                trackId = videoId,
                                title = title,
                                artist = artist,
                                durationSeconds = duration,
                                thumbnailUrl = thumbnail,
                                streamUrl = bestStream.url,
                                mimeType = bestStream.mimeType ?: "audio/mp4",
                                bitrate = bestStream.bitrate ?: 128000,
                                codec = bestStream.codec ?: "mp4a",
                                resolvedNode = node
                            )
                            instanceResolver.reportSuccess(node)
                            break
                        }
                    } else {
                        instanceResolver.reportFailure(node, "HTTP ${response.code()}")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    instanceResolver.reportFailure(node, e.message ?: "Network error")
                }
            }
        }

        // 9. Fallback to Invidious if Piped nodes failed
        if (streamResult == null) {
            val invidiousCandidates = instanceResolver.getInvidiousCandidates()
            for (node in invidiousCandidates) {
                val url = "$node/api/v1/videos/$videoId"
                try {
                    val response = api.getInvidiousVideo(url)
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        val audioFormats = body.adaptiveFormats?.filter {
                            it.type?.startsWith("audio/") == true && !it.url.isNullOrBlank()
                        }.orEmpty()

                        val bestFormat = audioFormats.maxByOrNull { format ->
                            format.bitrate?.toIntOrNull() ?: 0
                        }

                        val title = body.title.orEmpty().ifBlank { "Untitled Audio" }
                        val artist = body.author.orEmpty().ifBlank { "Unknown Artist" }
                        val thumbnail = body.videoThumbnails?.firstOrNull()?.url.orEmpty()
                        val duration = body.lengthSeconds ?: 0L

                        if (bestFormat != null && !bestFormat.url.isNullOrBlank()) {
                            streamResult = AudioStreamResult(
                                trackId = videoId,
                                title = title,
                                artist = artist,
                                durationSeconds = duration,
                                thumbnailUrl = thumbnail,
                                streamUrl = bestFormat.url,
                                mimeType = bestFormat.type ?: "audio/mp4",
                                bitrate = bestFormat.bitrate?.toIntOrNull() ?: 128000,
                                codec = bestFormat.encoding ?: "opus",
                                resolvedNode = node
                            )
                            instanceResolver.reportSuccess(node)
                            break
                        } else {
                            val formatStreams = body.formatStreams?.filter { !it.url.isNullOrBlank() }.orEmpty()
                            val chosenFormat = formatStreams.minByOrNull { it.bitrate?.toIntOrNull() ?: Int.MAX_VALUE }
                            if (chosenFormat != null && !chosenFormat.url.isNullOrBlank()) {
                                streamResult = AudioStreamResult(
                                    trackId = videoId,
                                    title = title,
                                    artist = artist,
                                    durationSeconds = duration,
                                    thumbnailUrl = thumbnail,
                                    streamUrl = chosenFormat.url,
                                    mimeType = chosenFormat.type ?: "video/mp4",
                                    bitrate = chosenFormat.bitrate?.toIntOrNull() ?: 128000,
                                    codec = "mp4a",
                                    resolvedNode = node
                                )
                                instanceResolver.reportSuccess(node)
                                break
                            }
                        }
                    } else {
                        instanceResolver.reportFailure(node, "HTTP ${response.code()}")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    instanceResolver.reportFailure(node, e.message ?: "Network error")
                }
            }
        }

        // Persist and emit strictly outside of try blocks
        if (streamResult != null) {
            val track = (cached ?: TrackEntity(
                id = videoId,
                title = streamResult.title,
                artist = streamResult.artist,
                durationSeconds = streamResult.durationSeconds,
                thumbnailUrl = streamResult.thumbnailUrl
            )).copy(streamUrl = streamResult.streamUrl)
            trackDao.insertTrack(track)
            emit(Result.success(streamResult))
        } else {
            emit(Result.failure(IOException("Failed to extract audio stream across all fallback nodes.")))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Dual-stage stream selector:
     * Stage 1: Pure audioStreams sorted by format (m4a/opus) and bitrate.
     * Stage 2: Combined videoStreams with videoOnly == false (contains both audio and video)
     *          which ExoPlayer seamlessly decodes with native audio decoders.
     */
    private fun selectBestStream(response: PipedStreamResponse): PipedAudioStream? {
        val audioStreams = response.audioStreams.orEmpty()
        if (audioStreams.isNotEmpty()) {
            val bestAudio = audioStreams.maxWithOrNull(
                compareBy<PipedAudioStream> { stream ->
                    val mime = stream.mimeType?.lowercase().orEmpty()
                    val format = stream.format?.lowercase().orEmpty()
                    when {
                        mime.contains("audio/mp4") || format == "m4a" -> 2
                        mime.contains("audio/webm") || format == "opus" -> 1
                        else -> 0
                    }
                }.thenBy { it.bitrate ?: 0 }
            )
            if (bestAudio != null && !bestAudio.url.isNullOrBlank()) {
                return bestAudio
            }
        }

        // Fallback to video streams with audio included
        val videoStreamsWithAudio = response.videoStreams?.filter {
            it.videoOnly != true && !it.url.isNullOrBlank()
        }.orEmpty()

        if (videoStreamsWithAudio.isNotEmpty()) {
            val chosen = videoStreamsWithAudio.minByOrNull { it.bitrate ?: Int.MAX_VALUE }
                ?: videoStreamsWithAudio.first()

            return PipedAudioStream(
                url = chosen.url,
                format = chosen.format,
                mimeType = chosen.mimeType ?: "video/mp4",
                codec = chosen.codec ?: "mp4a",
                bitrate = chosen.bitrate ?: 128000
            )
        }

        return null
    }

    /**
     * Native Client-Side Innertube Stream Resolver.
     * Communicates directly with YouTube's Innertube endpoints using the Android VR client profile,
     * which issues unthrottled, direct audio CDN URLs without requiring signatures or server proxies.
     */
    private fun resolveDirectInnertube(videoId: String): AudioStreamResult? {
        return try {
            val apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
            val payload = """
                {
                  "videoId": "$videoId",
                  "context": {
                    "client": {
                      "clientName": "ANDROID_VR",
                      "clientVersion": "1.61.48",
                      "deviceMake": "Oculus",
                      "deviceModel": "Quest 3",
                      "hl": "en",
                      "gl": "US"
                    }
                  }
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?key=$apiKey")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .addHeader("User-Agent", "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; Quest 3) gzip")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val jsonString = response.body!!.string()
                val jsonObj = JSONObject(jsonString)
                val playabilityStatus = jsonObj.optJSONObject("playabilityStatus")?.optString("status")

                if (playabilityStatus == "OK" && jsonObj.has("streamingData")) {
                    val streamingData = jsonObj.getJSONObject("streamingData")
                    val adaptiveFormats = streamingData.optJSONArray("adaptiveFormats")

                    var bestAudioUrl: String? = null
                    var bestBitrate = 0
                    var mime = "audio/mp4"

                    if (adaptiveFormats != null) {
                        for (i in 0 until adaptiveFormats.length()) {
                            val format = adaptiveFormats.getJSONObject(i)
                            val formatMime = format.optString("mimeType")
                            val url = format.optString("url")
                            val bitrate = format.optInt("bitrate", 0)

                            if (formatMime.startsWith("audio/") && url.isNotBlank()) {
                                if (bitrate > bestBitrate) {
                                    bestBitrate = bitrate
                                    bestAudioUrl = url
                                    mime = formatMime
                                }
                            }
                        }
                    }

                    // Fallback to combined formats if adaptive format wasn't direct
                    if (bestAudioUrl.isNullOrBlank()) {
                        val formats = streamingData.optJSONArray("formats")
                        if (formats != null && formats.length() > 0) {
                            for (i in 0 until formats.length()) {
                                val fmt = formats.getJSONObject(i)
                                val url = fmt.optString("url")
                                if (url.isNotBlank()) {
                                    bestAudioUrl = url
                                    bestBitrate = fmt.optInt("bitrate", 128000)
                                    mime = fmt.optString("mimeType", "video/mp4")
                                    break
                                }
                            }
                        }
                    }

                    if (!bestAudioUrl.isNullOrBlank()) {
                        val videoDetails = jsonObj.optJSONObject("videoDetails")
                        val title = videoDetails?.optString("title") ?: "Audio Track"
                        val author = videoDetails?.optString("author") ?: "Unknown Artist"
                        val lengthSeconds = videoDetails?.optLong("lengthSeconds") ?: 0L
                        val thumbnails = videoDetails?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                        val thumbUrl = if (thumbnails != null && thumbnails.length() > 0) {
                            thumbnails.getJSONObject(thumbnails.length() - 1).optString("url")
                        } else ""

                        return AudioStreamResult(
                            trackId = videoId,
                            title = title,
                            artist = author,
                            durationSeconds = lengthSeconds,
                            thumbnailUrl = thumbUrl,
                            streamUrl = bestAudioUrl,
                            mimeType = mime,
                            bitrate = bestBitrate,
                            codec = if (mime.contains("webm")) "opus" else "mp4a",
                            resolvedNode = "native_innertube"
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Direct Innertube resolution exception: ${e.message}")
            null
        }
    }
}
