package com.satvik.satvikx.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PipedStreamResponse(
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "uploadDate") val uploadDate: String? = null,
    @Json(name = "uploader") val uploader: String? = null,
    @Json(name = "uploaderUrl") val uploaderUrl: String? = null,
    @Json(name = "uploaderAvatar") val uploaderAvatar: String? = null,
    @Json(name = "thumbnailUrl") val thumbnailUrl: String? = null,
    @Json(name = "duration") val duration: Long? = null,
    @Json(name = "audioStreams") val audioStreams: List<PipedAudioStream>? = null,
    @Json(name = "videoStreams") val videoStreams: List<PipedVideoStream>? = null
)

@JsonClass(generateAdapter = true)
data class PipedAudioStream(
    @Json(name = "url") val url: String? = null,
    @Json(name = "format") val format: String? = null,
    @Json(name = "quality") val quality: String? = null,
    @Json(name = "mimeType") val mimeType: String? = null,
    @Json(name = "codec") val codec: String? = null,
    @Json(name = "audioTrackId") val audioTrackId: String? = null,
    @Json(name = "audioTrackName") val audioTrackName: String? = null,
    @Json(name = "bitrate") val bitrate: Int? = null,
    @Json(name = "contentLength") val contentLength: Long? = null
)

@JsonClass(generateAdapter = true)
data class PipedVideoStream(
    @Json(name = "url") val url: String? = null,
    @Json(name = "format") val format: String? = null,
    @Json(name = "quality") val quality: String? = null,
    @Json(name = "mimeType") val mimeType: String? = null,
    @Json(name = "codec") val codec: String? = null,
    @Json(name = "videoOnly") val videoOnly: Boolean? = false,
    @Json(name = "bitrate") val bitrate: Int? = null
)
