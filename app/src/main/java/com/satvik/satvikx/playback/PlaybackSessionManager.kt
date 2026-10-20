package com.satvik.satvikx.playback

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.satvik.satvikx.data.local.dao.TrackDao
import com.satvik.satvikx.data.local.entity.TrackEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encapsulates a restored playback session across app restarts or task removal.
 */
data class RestoredPlaybackSession(
    val track: TrackEntity,
    val queue: List<TrackEntity>,
    val queueIndex: Int,
    val positionMs: Long
)

/**
 * Manages persistent storage of the active playback state so users can seamlessly resume
 * their song and queue exactly where they left off when closing and reopening the app.
 */
@Singleton
class PlaybackSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao
) {

    companion object {
        private const val TAG = "PlaybackSessionManager"
        private const val PREFS_NAME = "satvikx_playback_session"
        private const val KEY_TRACK_ID = "last_track_id"
        private const val KEY_TRACK_TITLE = "last_track_title"
        private const val KEY_TRACK_ARTIST = "last_track_artist"
        private const val KEY_TRACK_DURATION = "last_track_duration"
        private const val KEY_TRACK_THUMBNAIL = "last_track_thumbnail"
        private const val KEY_TRACK_LOCAL_PATH = "last_track_local_path"
        private const val KEY_TRACK_IS_DOWNLOADED = "last_track_is_downloaded"
        private const val KEY_POSITION_MS = "last_position_ms"
        private const val KEY_QUEUE_INDEX = "last_queue_index"
        private const val KEY_QUEUE_IDS = "last_queue_ids"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Synchronously returns the restored playback session from SharedPreferences on app launch.
     * Guaranteed instant, non-blocking UI restoration for the miniplayer and player screen.
     */
    fun getLastPlaybackSession(): RestoredPlaybackSession? {
        val trackId = prefs.getString(KEY_TRACK_ID, null) ?: return null
        val title = prefs.getString(KEY_TRACK_TITLE, "Unknown Title").orEmpty()
        val artist = prefs.getString(KEY_TRACK_ARTIST, "Unknown Artist").orEmpty()
        val duration = prefs.getLong(KEY_TRACK_DURATION, 0L)
        val thumbnail = prefs.getString(KEY_TRACK_THUMBNAIL, "").orEmpty()
        val localPath = prefs.getString(KEY_TRACK_LOCAL_PATH, null)
        val isDownloaded = prefs.getBoolean(KEY_TRACK_IS_DOWNLOADED, false)
        val positionMs = prefs.getLong(KEY_POSITION_MS, 0L).coerceAtLeast(0L)
        val queueIndex = prefs.getInt(KEY_QUEUE_INDEX, 0).coerceAtLeast(0)

        val track = TrackEntity(
            id = trackId,
            title = title,
            artist = artist,
            durationSeconds = duration,
            thumbnailUrl = thumbnail,
            streamUrl = null, // Ephemeral streamUrls must be re-resolved to guarantee freshness
            localPath = localPath,
            isDownloaded = isDownloaded
        )

        return RestoredPlaybackSession(
            track = track,
            queue = listOf(track),
            queueIndex = queueIndex,
            positionMs = positionMs
        )
    }

    /**
     * Asynchronously restores the full queue tracks from Room database using stored queue IDs.
     */
    suspend fun restoreFullQueue(): List<TrackEntity> = withContext(Dispatchers.IO) {
        val rawIds = prefs.getString(KEY_QUEUE_IDS, null) ?: return@withContext emptyList()
        val ids = rawIds.split(",").filter { it.isNotBlank() }
        if (ids.isEmpty()) return@withContext emptyList()

        val tracks = mutableListOf<TrackEntity>()
        for (id in ids) {
            val track = trackDao.getTrackByIdSync(id)
            if (track != null) {
                val clean = if (StreamUrlValidator.isExpired(track.streamUrl)) {
                    track.copy(streamUrl = null)
                } else {
                    track
                }
                tracks.add(clean)
            }
        }
        tracks
    }

    /**
     * Persists the complete playback state for seamless resumption across app restarts.
     */
    fun saveSession(
        track: TrackEntity?,
        queue: List<TrackEntity>,
        queueIndex: Int,
        positionMs: Long
    ) {
        if (track == null) return

        try {
            val queueIds = queue.map { it.id }.take(200).joinToString(",")
            prefs.edit()
                .putString(KEY_TRACK_ID, track.id)
                .putString(KEY_TRACK_TITLE, track.title)
                .putString(KEY_TRACK_ARTIST, track.artist)
                .putLong(KEY_TRACK_DURATION, track.durationSeconds)
                .putString(KEY_TRACK_THUMBNAIL, track.thumbnailUrl)
                .putString(KEY_TRACK_LOCAL_PATH, track.localPath)
                .putBoolean(KEY_TRACK_IS_DOWNLOADED, track.isDownloaded)
                .putLong(KEY_POSITION_MS, positionMs)
                .putInt(KEY_QUEUE_INDEX, queueIndex)
                .putString(KEY_QUEUE_IDS, queueIds)
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist playback session: ${e.message}")
        }
    }

    /**
     * Lightweight update of current playback position during active playback.
     */
    fun savePosition(positionMs: Long) {
        try {
            prefs.edit().putLong(KEY_POSITION_MS, positionMs).apply()
        } catch (_: Exception) {}
    }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
