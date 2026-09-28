package com.satvik.satvikx.data.remote.model

/**
 * Resolved audio stream ready for ExoPlayer or background downloading.
 */
data class AudioStreamResult(
    val trackId: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val streamUrl: String,
    val mimeType: String,
    val bitrate: Int,
    val codec: String,
    val resolvedNode: String
)
