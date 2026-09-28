package com.satvik.satvikx.updater.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubReleaseResponse(
    @Json(name = "tag_name") val tagName: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "body") val body: String? = null,
    @Json(name = "assets") val assets: List<GitHubReleaseAsset>? = null
)

@JsonClass(generateAdapter = true)
data class GitHubReleaseAsset(
    @Json(name = "name") val name: String? = null,
    @Json(name = "browser_download_url") val browserDownloadUrl: String? = null,
    @Json(name = "size") val size: Long? = null,
    @Json(name = "content_type") val contentType: String? = null
)
