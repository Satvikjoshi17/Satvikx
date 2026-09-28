package com.satvik.satvikx.data.remote.newpipe

import android.util.Log
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.remote.model.AudioStreamResult
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enterprise Native YouTube Scraper powered by NewPipeExtractor.
 * Extracts video streams and searches directly on-device without requiring cookies,
 * browser accounts, or external Python runtimes.
 */
@Singleton
class NewPipeYouTubeEngine @Inject constructor(
    private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "NewPipeYTEngine"
        @Volatile
        private var isInitialized = false
    }

    init {
        ensureInitialized()
    }

    private fun ensureInitialized() {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    try {
                        org.schabi.newpipe.extractor.NewPipe.init(
                            NewPipeDownloader(okHttpClient),
                            org.schabi.newpipe.extractor.localization.Localization.DEFAULT,
                            org.schabi.newpipe.extractor.localization.ContentCountry.DEFAULT
                        )
                        isInitialized = true
                        Log.i(TAG, "NewPipeExtractor initialized successfully")
                    } catch (e: Exception) {
                        System.err.println("Failed to initialize NewPipeExtractor: ${e.message}")
                        e.printStackTrace()
                        Log.e(TAG, "Failed to initialize NewPipeExtractor: ${e.message}", e)
                    }
                }
            }
        }
    }

    /**
     * Searches YouTube for music and video streams natively.
     * Supports deep multi-page search for comprehensive query results.
     */
    fun search(query: String, deepSearch: Boolean = false): List<TrackEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        ensureInitialized()

        return try {
            val service = ServiceList.YouTube
            val linkHandler = service.searchQHFactory.fromQuery(
                trimmed,
                listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                ""
            )
            val searchExtractor = service.getSearchExtractor(linkHandler)
            searchExtractor.fetchPage()

            val page = searchExtractor.initialPage
            val tracks = mutableListOf<TrackEntity>()

            fun extractFromItems(items: List<Any?>) {
                for (item in items) {
                    if (item is StreamInfoItem) {
                        val videoId = item.url?.let { extractVideoIdFromUrl(it) } ?: ""
                        if (videoId.isNotBlank() && tracks.none { it.id == videoId }) {
                            tracks.add(
                                TrackEntity(
                                    id = videoId,
                                    title = item.name.orEmpty().ifBlank { "Unknown Title" },
                                    artist = item.uploaderName.orEmpty().ifBlank { "Unknown Artist" },
                                    durationSeconds = item.duration.coerceAtLeast(0L),
                                    thumbnailUrl = item.thumbnails.lastOrNull()?.url.orEmpty(),
                                    streamUrl = null,
                                    localPath = null,
                                    isDownloaded = false
                                )
                            )
                        }
                    }
                }
            }

            extractFromItems(page.items)

            // Deep Search: Fetch secondary batch of items from YouTube for thoroughness
            if (deepSearch && page.hasNextPage()) {
                try {
                    val nextPage = searchExtractor.getPage(page.nextPage)
                    extractFromItems(nextPage.items)
                } catch (e: Exception) {
                    Log.w(TAG, "Deep page extraction non-fatal: ${e.message}")
                }
            }

            tracks
        } catch (e: Exception) {
            Log.e(TAG, "NewPipe search failed for '$query': ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Resolves audio stream URLs directly from YouTube using on-device signature deobfuscation.
     */
    fun resolveAudioStream(videoId: String): AudioStreamResult? {
        val trimmedId = videoId.trim()
        if (trimmedId.isEmpty()) return null

        ensureInitialized()

        return try {
            val service = ServiceList.YouTube
            val linkHandler = service.streamLHFactory.fromId(trimmedId)
            val streamExtractor = service.getStreamExtractor(linkHandler)
            streamExtractor.fetchPage()

            val audioStreams = streamExtractor.audioStreams.orEmpty()
            if (audioStreams.isEmpty()) {
                Log.w(TAG, "No audio streams found for videoId: $videoId")
                return null
            }

            // Pick highest quality audio stream (m4a / opus)
            val bestAudio = audioStreams.maxByOrNull { it.averageBitrate }
                ?: audioStreams.first()

            val title = streamExtractor.name.orEmpty().ifBlank { "Untitled Audio" }
            val artist = streamExtractor.uploaderName.orEmpty().ifBlank { "Unknown Artist" }
            val duration = streamExtractor.length.coerceAtLeast(0L)
            val thumbnail = streamExtractor.thumbnails.lastOrNull()?.url.orEmpty()

            AudioStreamResult(
                trackId = trimmedId,
                title = title,
                artist = artist,
                durationSeconds = duration,
                thumbnailUrl = thumbnail,
                streamUrl = bestAudio.content,
                mimeType = bestAudio.format?.mimeType ?: "audio/mp4",
                bitrate = bestAudio.averageBitrate * 1000,
                codec = bestAudio.format?.name ?: "m4a",
                resolvedNode = "newpipe_native_extractor"
            )
        } catch (e: Exception) {
            Log.e(TAG, "NewPipe resolution failed for $videoId: ${e.message}", e)
            null
        }
    }

    private fun extractVideoIdFromUrl(url: String): String {
        val regex = Regex("(?:v=|/v/|youtu\\.be/|/embed/|/watch\\?v=|/shorts/)([a-zA-Z0-9_-]{11})")
        return regex.find(url)?.groupValues?.get(1) ?: url.takeLast(11)
    }

    /**
     * Extracts YouTube's algorithmic "Up Next" / Related stream recommendations directly
     * from NewPipe's StreamExtractor page.
     */
    fun getRelatedTracks(videoId: String): List<TrackEntity> {
        val trimmedId = videoId.trim()
        if (trimmedId.isEmpty()) return emptyList()

        ensureInitialized()

        return try {
            val service = ServiceList.YouTube
            val linkHandler = service.streamLHFactory.fromId(trimmedId)
            val streamExtractor = service.getStreamExtractor(linkHandler)
            streamExtractor.fetchPage()

            val relatedItems = streamExtractor.relatedItems?.items.orEmpty()
            val tracks = mutableListOf<TrackEntity>()

            relatedItems.forEach { item ->
                if (item is StreamInfoItem) {
                    val url = item.url.orEmpty()
                    val id = extractVideoIdFromUrl(url)
                    val title = item.name.orEmpty().ifBlank { "Untitled Audio" }
                    val artist = item.uploaderName.orEmpty().ifBlank { "Unknown Artist" }
                    val thumb = item.thumbnails.lastOrNull()?.url.orEmpty()
                    val duration = item.duration.coerceAtLeast(0L)

                    if (id.isNotBlank() && id != trimmedId) {
                        tracks.add(
                            TrackEntity(
                                id = id,
                                title = title,
                                artist = artist,
                                durationSeconds = duration,
                                thumbnailUrl = thumb,
                                streamUrl = null,
                                isDownloaded = false
                            )
                        )
                    }
                }
            }
            tracks
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract related YouTube tracks for $videoId: ${e.message}")
            emptyList()
        }
    }
}
