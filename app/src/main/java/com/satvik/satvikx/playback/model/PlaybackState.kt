package com.satvik.satvikx.playback.model

import androidx.media3.common.Player
import com.satvik.satvikx.data.local.entity.TrackEntity

/**
 * Immutable UI State representing current audio engine playback.
 */
data class PlaybackState(
    val currentTrack: TrackEntity? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playbackPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<TrackEntity> = emptyList(),
    val currentQueueIndex: Int = -1,
    val isConnected: Boolean = false,
    val sleepTimerRemainingSeconds: Long? = null
) {
    val progressFraction: Float
        get() = if (durationMs > 0) (playbackPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    val hasPrevious: Boolean
        get() = currentQueueIndex > 0 || playbackPositionMs > 3000L

    val hasNext: Boolean
        get() = currentQueueIndex in 0 until (queue.size - 1)
}
