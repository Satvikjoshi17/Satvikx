package com.satvik.satvikx.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PipedSearchWrapper(
    @Json(name = "items") val items: List<PipedSearchResultItem> = emptyList(),
    @Json(name = "nextpage") val nextPage: String? = null
)

@JsonClass(generateAdapter = true)
data class PipedSearchResultItem(
    @Json(name = "url") val url: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "thumbnail") val thumbnail: String? = null,
    @Json(name = "uploaderName") val uploaderName: String? = null,
    @Json(name = "uploaderUrl") val uploaderUrl: String? = null,
    @Json(name = "uploaderAvatar") val uploaderAvatar: String? = null,
    @Json(name = "duration") val duration: Long? = null,
    @Json(name = "views") val views: Long? = null,
    @Json(name = "uploadedDate") val uploadedDate: String? = null
)
