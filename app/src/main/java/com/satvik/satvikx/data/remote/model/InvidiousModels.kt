package com.satvik.satvikx.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class InvidiousSearchResultItem(
    @Json(name = "type") val type: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "videoId") val videoId: String? = null,
    @Json(name = "author") val author: String? = null,
    @Json(name = "lengthSeconds") val lengthSeconds: Long? = null,
    @Json(name = "videoThumbnails") val videoThumbnails: List<InvidiousThumbnail>? = null
)

@JsonClass(generateAdapter = true)
data class InvidiousThumbnail(
    @Json(name = "quality") val quality: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "width") val width: Int? = null,
    @Json(name = "height") val height: Int? = null
)

@JsonClass(generateAdapter = true)
data class InvidiousVideoResponse(
    @Json(name = "title") val title: String? = null,
    @Json(name = "videoId") val videoId: String? = null,
    @Json(name = "author") val author: String? = null,
    @Json(name = "lengthSeconds") val lengthSeconds: Long? = null,
    @Json(name = "adaptiveFormats") val adaptiveFormats: List<InvidiousAdaptiveFormat>? = null,
    @Json(name = "formatStreams") val formatStreams: List<InvidiousFormatStream>? = null,
    @Json(name = "videoThumbnails") val videoThumbnails: List<InvidiousThumbnail>? = null
)

@JsonClass(generateAdapter = true)
data class InvidiousAdaptiveFormat(
    @Json(name = "url") val url: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "encoding") val encoding: String? = null,
    @Json(name = "quality") val quality: String? = null,
    @Json(name = "bitrate") val bitrate: String? = null,
    @Json(name = "container") val container: String? = null,
    @Json(name = "audioQuality") val audioQuality: String? = null
)

@JsonClass(generateAdapter = true)
data class InvidiousFormatStream(
    @Json(name = "url") val url: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "quality") val quality: String? = null,
    @Json(name = "container") val container: String? = null,
    @Json(name = "bitrate") val bitrate: String? = null,
    @Json(name = "resolution") val resolution: String? = null
)
