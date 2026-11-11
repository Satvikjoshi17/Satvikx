package com.satvik.satvikx.data.remote.newpipe

import android.util.Log
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.remote.model.AudioStreamResult
import com.satvik.satvikx.data.parser.LrcParser
import com.satvik.satvikx.data.remote.model.LrcLine
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.MediaFormat
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
open class NewPipeYouTubeEngine @Inject constructor(
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
                        // Exclude YouTube Shorts (< 40s or isShortFormContent) unless query explicitly contains "short"
                        if ((item.isShortFormContent || item.duration in 1..40) && !trimmed.contains("short", ignoreCase = true)) {
                            continue
                        }
                        val videoId = item.url?.let { extractVideoIdFromUrl(it) } ?: ""
                        if (videoId.isNotBlank() && tracks.none { it.id == videoId }) {
                            val rawThumb = item.thumbnails.lastOrNull()?.url.orEmpty()
                            val cleanThumb = when {
                                rawThumb.contains("hqdefault.jpg") -> rawThumb.replace("hqdefault.jpg", "mqdefault.jpg")
                                rawThumb.isBlank() -> "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
                                else -> rawThumb
                            }
                            tracks.add(
                                TrackEntity(
                                    id = videoId,
                                    title = item.name.orEmpty().ifBlank { "Unknown Title" },
                                    artist = item.uploaderName.orEmpty().ifBlank { "Unknown Artist" },
                                    durationSeconds = item.duration.coerceAtLeast(0L),
                                    thumbnailUrl = cleanThumb,
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

            // Deep Search: Fetch secondary batch if initial page yielded fewer than 15 items
            if (deepSearch && tracks.size < 15 && page.hasNextPage()) {
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
     * Resolves audio stream URLs directly from YouTube using on-device signature deobfuscation,
     * tailoring the stream selection to the user's storage/quality preference.
     */
    fun resolveAudioStream(videoId: String, targetQuality: String = "high"): AudioStreamResult? {
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

            // Pick stream based on target storage/audio quality
            val selectedAudio = when (targetQuality.lowercase()) {
                "saver", "low", "eco" -> {
                    audioStreams.minByOrNull { it.averageBitrate } ?: audioStreams.first()
                }
                "standard", "medium", "balanced" -> {
                    // Prefer balanced ~128-160 kbps stream
                    audioStreams.filter { it.averageBitrate in 90..160 }.maxByOrNull { it.averageBitrate }
                        ?: audioStreams.minByOrNull { Math.abs(it.averageBitrate - 128) }
                        ?: audioStreams.first()
                }
                else -> {
                    audioStreams.maxByOrNull { it.averageBitrate } ?: audioStreams.first()
                }
            }

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
                streamUrl = selectedAudio.content,
                mimeType = selectedAudio.format?.mimeType ?: "audio/mp4",
                bitrate = selectedAudio.averageBitrate * 1000,
                codec = selectedAudio.format?.name ?: "m4a",
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
    open fun getRelatedTracks(videoId: String): List<TrackEntity> {
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

    /**
     * Extracts time-synchronized subtitle / caption cues from YouTube video stream,
     * converting WebVTT cues into sorted [LrcLine] objects.
     *
     * @param videoId 11-character YouTube video ID or video URL.
     * @return Chronologically sorted list of [LrcLine] instances, or empty list if unavailable.
     */
    open fun getSubtitles(videoId: String): List<LrcLine> {
        val trimmedId = videoId.trim().let { if (it.length > 11) extractVideoIdFromUrl(it) else it }
        if (trimmedId.isEmpty()) return emptyList()

        ensureInitialized()

        return try {
            val service = ServiceList.YouTube
            val linkHandler = service.streamLHFactory.fromId(trimmedId)
            val streamExtractor = service.getStreamExtractor(linkHandler)
            streamExtractor.fetchPage()

            val subtitlesList = try {
                streamExtractor.subtitlesDefault.orEmpty()
            } catch (e: Exception) {
                emptyList()
            }
            if (subtitlesList.isEmpty()) {
                Log.d(TAG, "No subtitle streams found for videoId: $trimmedId")
                return emptyList()
            }

            // Prioritize English/Hindi manual subtitles, then any manual, then auto-generated
            val selectedSub = subtitlesList.firstOrNull { sub ->
                !sub.isAutoGenerated && (sub.languageTag.orEmpty().contains("en", ignoreCase = true) || sub.languageTag.orEmpty().contains("hi", ignoreCase = true))
            } ?: subtitlesList.firstOrNull { sub ->
                !sub.isAutoGenerated
            } ?: subtitlesList.firstOrNull { sub ->
                sub.languageTag.orEmpty().contains("en", ignoreCase = true) || sub.languageTag.orEmpty().contains("hi", ignoreCase = true)
            } ?: subtitlesList.first()

            var subUrl = selectedSub.content.orEmpty().ifBlank { selectedSub.url.orEmpty() }
            if (subUrl.isBlank()) return emptyList()

            // Ensure WebVTT format requested from timedtext API
            if (!subUrl.contains("fmt=")) {
                subUrl += if (subUrl.contains("?")) "&fmt=vtt" else "?fmt=vtt"
            } else if (!subUrl.contains("fmt=vtt")) {
                subUrl = subUrl.replace(Regex("""fmt=[a-zA-Z0-9]+"""), "fmt=vtt")
            }

            val request = Request.Builder()
                .url(subUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                Log.w(TAG, "Failed to download subtitle content for $trimmedId: HTTP ${response.code}")
                return emptyList()
            }

            val webVttContent = response.body!!.string()
            parseWebVtt(webVttContent, isAutoGenerated = selectedSub.isAutoGenerated)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract subtitles for videoId $videoId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Serializes extracted subtitles to standard LRC string format.
     */
    open fun getSubtitlesLrc(videoId: String): String? {
        val lines = getSubtitles(videoId)
        return if (lines.isNotEmpty()) LrcParser.format(lines) else null
    }

    /**
     * Parses standard WebVTT cue blocks into chronologically sorted [LrcLine] instances.
     * Robust against cue identifiers, multi-line cues, HTML markup tags, and sound descriptors.
     */
    open fun parseWebVtt(vttContent: String?, isAutoGenerated: Boolean = false): List<LrcLine> {
        if (vttContent.isNullOrBlank()) return emptyList()

        val lines = vttContent.lines()
        val result = mutableListOf<LrcLine>()
        val cueTimingRegex = Regex("""(?:(\d{1,2}):)?(\d{2}):(\d{2})[.,](\d{3})\s*-->\s*(?:(\d{1,2}):)?(\d{2}):(\d{2})[.,](\d{3})""")
        val soundTagRegex = Regex("""(?i)\[(Music|Applause|Laughter|Cheering|Audio|Silence)\]""")
        val inlineTagRegex = Regex("""<[^>]*>""")

        var currentStartMs: Long? = null
        val currentTextParts = mutableListOf<String>()

        fun emitCurrentCue() {
            val startMs = currentStartMs ?: return
            if (currentTextParts.isNotEmpty()) {
                val rawText = currentTextParts.joinToString(" ")
                val cleanText = inlineTagRegex.replace(rawText, "")
                    .replace(soundTagRegex, "")
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&#039;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace(">>", "")
                    .replace("♪", "")
                    .replace(Regex("""\s+"""), " ")
                    .trim()

                if (cleanText.isNotBlank()) {
                    // YouTube auto-generated speech recognition timestamps systematically lag vocal delivery by ~1000ms.
                    // Advance auto-generated cues by 1000ms so the line glow highlights in real-time with singing.
                    val finalStartMs = if (isAutoGenerated) maxOf(0L, startMs - 1000L) else startMs
                    val lastLine = result.lastOrNull()
                    if (lastLine != null && lastLine.text.equals(cleanText, ignoreCase = true)) {
                        // Suppress duplicate rolling cue from YouTube ASR
                    } else {
                        result.add(LrcLine(startTimeMs = finalStartMs, text = cleanText))
                    }
                }
            }
            currentStartMs = null
            currentTextParts.clear()
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                emitCurrentCue()
                continue
            }

            // Skip WEBVTT header and metadata tags (NOTE, STYLE, REGION)
            if (trimmed.startsWith("WEBVTT", ignoreCase = true) ||
                trimmed.startsWith("NOTE", ignoreCase = true) ||
                trimmed.startsWith("STYLE", ignoreCase = true) ||
                trimmed.startsWith("REGION", ignoreCase = true)) {
                continue
            }

            val timingMatch = cueTimingRegex.find(trimmed)
            if (timingMatch != null) {
                // If there was an un-emitted cue, emit it first
                emitCurrentCue()

                val hours = timingMatch.groupValues[1].toLongOrNull() ?: 0L
                val minutes = timingMatch.groupValues[2].toLongOrNull() ?: 0L
                val seconds = timingMatch.groupValues[3].toLongOrNull() ?: 0L
                val millis = timingMatch.groupValues[4].toLongOrNull() ?: 0L
                currentStartMs = (hours * 3600_000L) + (minutes * 60_000L) + (seconds * 1_000L) + millis

                // Check if there is cue text on the same line after settings
                val lineAfterMatch = trimmed.substring(timingMatch.range.last + 1).trim()
                if (lineAfterMatch.isNotBlank() && !lineAfterMatch.contains("-->") && !lineAfterMatch.startsWith("align:") && !lineAfterMatch.startsWith("position:")) {
                    currentTextParts.add(lineAfterMatch)
                }
            } else if (currentStartMs != null) {
                currentTextParts.add(trimmed)
            }
        }

        // Emit final cue
        emitCurrentCue()

        return result.sorted()
    }
}
