package com.satvik.satvikx.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.satvik.satvikx.data.local.dao.RecentPlaybackDao
import com.satvik.satvikx.data.local.dao.TrackDao
import com.satvik.satvikx.data.local.entity.RecentPlaybackEntity
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.entity.cleanArtistName
import com.satvik.satvikx.data.local.entity.cleanSongTitle
import com.satvik.satvikx.data.local.entity.distinctSongs
import com.satvik.satvikx.data.local.entity.hasArtistOverlap
import com.satvik.satvikx.data.local.entity.isSameSong
import com.satvik.satvikx.data.local.entity.isSongOnly
import com.satvik.satvikx.data.local.storage.StorageManager
import com.satvik.satvikx.data.remote.saavn.SaavnMediaEngine
import com.satvik.satvikx.data.repository.RecommendationRepository
import com.satvik.satvikx.recommendation.SessionAdaptationTrigger
import com.satvik.satvikx.data.repository.StreamRepository
import com.satvik.satvikx.playback.model.PlaybackState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Enterprise client-side connection manager binding ViewModels to the MediaSessionService
 * via AndroidX MediaController asynchronously, emitting a real-time StateFlow<PlaybackState>.
 *
 * Includes state persistence across app closing/restarting, expired stream token validation,
 * active buffering timeout watchdog, and auto-recovery.
 */
@Singleton
class PlaybackConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val streamRepository: StreamRepository,
    private val trackDao: TrackDao,
    private val recentPlaybackDao: RecentPlaybackDao,
    private val playbackTelemetryDao: com.satvik.satvikx.data.local.dao.PlaybackTelemetryDao,
    private val storageManager: StorageManager,
    private val playbackSessionManager: PlaybackSessionManager,
    private val recommendationRepositoryProvider: Provider<RecommendationRepository>,
    private val saavnMediaEngine: SaavnMediaEngine,
    private val authRepositoryProvider: Provider<com.satvik.satvikx.data.repository.AuthRepository>,
    private val supabaseAnalyticsRepositoryProvider: Provider<com.satvik.satvikx.data.repository.SupabaseAnalyticsRepository>
) {

    companion object {
        private const val TAG = "PlaybackConnManager"
        private const val DEFAULT_CROSSFADE_SECONDS = 4
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentPlaylist = mutableListOf<TrackEntity>()
    private var progressJob: Job? = null
    private var prefetchJob: Job? = null
    private var bufferingWatchdogJob: Job? = null
    private var lastSaveTimeMs = 0L

    // Behavioral telemetry tracking fields
    private var activeTrackId: String? = null
    private var activeTrackStartTimeMs: Long = 0L
    private var activeTrackTotalListenedMs: Long = 0L
    private var lastObservedPositionMs: Long = 0L

    // Crossfade volume management
    private var isCrossfadingOut = false
    private var isCrossfadingIn = false

    // Playlist playback lock: when true, prevents recommendations from mutating or appending to the queue
    private var isPlaylistPlayback = false
    private var activePlaylistId: Long? = null

    // Background pre-warming state tracking: ensures candidate harvesting triggers once per track at 60% progress
    private var lastPrewarmedTrackId: String? = null

    init {
        restoreLastSession()
        initializeController()
    }

    /**
     * Instantly restores the last played song and queue from SharedPreferences upon app startup.
     * Ensures the player UI (miniplayer & full player) is immediately populated with the song
     * and saved playback position so the user can easily resume listening.
     */
    private fun restoreLastSession() {
        val session = playbackSessionManager.getLastPlaybackSession()
        if (session != null) {
            currentPlaylist = session.queue.toMutableList()
            isPlaylistPlayback = session.isPlaylist
            _playbackState.update {
                it.copy(
                    currentTrack = session.track,
                    queue = session.queue,
                    currentQueueIndex = session.queueIndex,
                    playbackPositionMs = session.positionMs,
                    durationMs = session.track.durationSeconds * 1000L,
                    isPlaying = false,
                    isBuffering = false,
                    isPlaylistPlayback = isPlaylistPlayback
                )
            }
            scope.launch {
                val fullQueue = playbackSessionManager.restoreFullQueue()
                if (fullQueue.isNotEmpty()) {
                    currentPlaylist = fullQueue.toMutableList()
                    isPlaylistPlayback = session.isPlaylist
                    val safeIdx = session.queueIndex.coerceIn(0, fullQueue.size - 1)
                    _playbackState.update {
                        it.copy(
                            queue = fullQueue,
                            currentQueueIndex = safeIdx,
                            isPlaylistPlayback = isPlaylistPlayback
                        )
                    }
                }
            }
        }
    }

    private fun ensureServiceStarted() {
        try {
            val intent = Intent(context, PlaybackService::class.java)
            context.startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Explicit service start non-fatal: ${e.message}")
        }
    }

    private fun initializeController() {
        ensureServiceStarted()
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                try {
                    mediaController = controllerFuture?.get()
                    mediaController?.addListener(PlayerListener())
                    _playbackState.update { it.copy(isConnected = true) }
                    syncPlaybackState()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect to MediaSession: ${e.message}", e)
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    /**
     * Plays a track immediately. If a playlist is supplied, replaces the current queue.
     * The mini player UI appears instantaneously with track metadata & buffering state.
     */
    fun playTrack(
        track: TrackEntity,
        playlist: List<TrackEntity> = emptyList(),
        isUserPlaylist: Boolean = false,
        playlistId: Long? = null
    ) {
        val filteredPlaylist = if (playlist.isNotEmpty()) {
            val clean = playlist
                .filter { it.id == track.id || (!it.isSameSong(track) && it.isSongOnly()) }
                .distinctSongs()
            clean
        } else {
            emptyList()
        }
        val isPlaylist = isUserPlaylist
        isPlaylistPlayback = isPlaylist
        activePlaylistId = if (isPlaylist) playlistId else null
        val initialQueue = if (filteredPlaylist.isNotEmpty()) filteredPlaylist else listOf(track)
        val initialIndex = initialQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        ensureServiceStarted()

        // 1. Immediately update UI state so Spotify-style bottom mini-player pops up instantly!
        _playbackState.update {
            it.copy(
                currentTrack = track,
                queue = initialQueue,
                currentQueueIndex = initialIndex,
                isPlaying = true,
                isBuffering = true,
                isPlaylistPlayback = isPlaylist
            )
        }

        scope.launch {
            val controller = getOrAwaitController()

            // 2. Ensure we have a valid non-expired stream URL or local path for the target track
            val playableTrack = resolveTrackForPlayback(track)
            if (playableTrack == null) {
                _playbackState.update { it.copy(isBuffering = false, isPlaying = false) }
                return@launch
            }

            val queueToSet = if (playlist.isNotEmpty()) {
                playlist.map { if (it.id == playableTrack.id) playableTrack else it }
            } else {
                listOf(playableTrack)
            }

            currentPlaylist = queueToSet.toMutableList()
            val targetIndex = currentPlaylist.indexOfFirst { it.id == playableTrack.id }.coerceAtLeast(0)

            // Proactively resolve the immediate next track so gapless transition never encounters Uri.EMPTY
            val nextTrack = currentPlaylist.getOrNull(targetIndex + 1)
            if (nextTrack != null && !nextTrack.isDownloaded && (nextTrack.streamUrl.isNullOrBlank() || StreamUrlValidator.isExpired(nextTrack.streamUrl))) {
                val resolvedNext = resolveTrackForPlayback(nextTrack)
                if (resolvedNext != null) {
                    currentPlaylist[targetIndex + 1] = resolvedNext
                }
            }

            val mediaItems = currentPlaylist.map { buildMediaItem(it) }

            controller?.let { c ->
                c.volume = 1.0f // Ensure clean nominal volume
                c.setMediaItems(mediaItems, targetIndex, 0L)
                c.prepare()
                c.play()
            }

            // Guarantee track persistence in local database and register in playback telemetry history
            trackDao.insertTrack(playableTrack)
            recentPlaybackDao.recordOrIncrementPlayback(playableTrack.id)
            try {
                recommendationRepositoryProvider.get().notifyPlaybackRecorded()
            } catch (_: Exception) {}

            // Telemetry: record start of track listening
            recordTrackStartTelemetry(playableTrack)

            // If queue is single track and NOT playing a playlist, asynchronously load YouTube-like up next recommendations
            if (!isPlaylistPlayback && currentPlaylist.size <= 1) {
                extendAutoplayQueue(playableTrack, forceFresh = true)
            }

            _playbackState.update {
                it.copy(
                    currentTrack = playableTrack,
                    queue = currentPlaylist.toList(),
                    currentQueueIndex = targetIndex,
                    isPlaying = true,
                    isBuffering = false,
                    isPlaylistPlayback = isPlaylistPlayback
                )
            }
            startProgressUpdates()
            prefetchAdjacentTracks(targetIndex)
            savePlaybackSession()
        }
    }

    /**
     * Resumes playback from a restored session (e.g. when app reopens and user taps Play).
     * Re-resolves stream URL to avoid expired tokens and resumes at the saved position.
     */
    fun resumePlayback(
        track: TrackEntity,
        playlist: List<TrackEntity> = emptyList(),
        startPositionMs: Long = 0L,
        isUserPlaylist: Boolean = false,
        playlistId: Long? = null
    ) {
        ensureServiceStarted()
        val filteredPlaylist = if (playlist.isNotEmpty()) {
            playlist.filter { it.id == track.id || it.isSongOnly() }
        } else {
            emptyList()
        }
        val isPlaylist = isUserPlaylist
        isPlaylistPlayback = isPlaylist
        activePlaylistId = if (isPlaylist) playlistId else null
        val initialQueue = if (filteredPlaylist.isNotEmpty()) filteredPlaylist else listOf(track)
        val initialIndex = initialQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        _playbackState.update {
            it.copy(
                currentTrack = track,
                queue = initialQueue,
                currentQueueIndex = initialIndex,
                isPlaying = true,
                isBuffering = true,
                playbackPositionMs = startPositionMs,
                isPlaylistPlayback = isPlaylist
            )
        }

        scope.launch {
            val controller = getOrAwaitController()
            val playableTrack = withContext(Dispatchers.IO) { resolveTrackForPlayback(track) }
            if (playableTrack == null) {
                _playbackState.update { it.copy(isBuffering = false, isPlaying = false) }
                return@launch
            }

            val queueToSet = if (playlist.isNotEmpty()) {
                playlist.map { if (it.id == playableTrack.id) playableTrack else it }
            } else {
                listOf(playableTrack)
            }

            currentPlaylist = queueToSet.toMutableList()
            val targetIndex = currentPlaylist.indexOfFirst { it.id == playableTrack.id }.coerceAtLeast(0)
            val mediaItems = currentPlaylist.map { buildMediaItem(it) }

            controller?.let { c ->
                c.setMediaItems(mediaItems, targetIndex, startPositionMs)
                c.prepare()
                c.play()
            }

            withContext(Dispatchers.IO) {
                trackDao.insertTrack(playableTrack)
                recentPlaybackDao.recordOrIncrementPlayback(playableTrack.id)
                try {
                    recommendationRepositoryProvider.get().notifyPlaybackRecorded()
                } catch (_: Exception) {}
            }

            if (!isPlaylistPlayback && currentPlaylist.size <= 1) {
                extendAutoplayQueue(playableTrack, forceFresh = true)
            }

            _playbackState.update {
                it.copy(
                    currentTrack = playableTrack,
                    queue = currentPlaylist.toList(),
                    currentQueueIndex = targetIndex,
                    isPlaying = true,
                    isBuffering = false,
                    isPlaylistPlayback = isPlaylistPlayback
                )
            }
            startProgressUpdates()
            prefetchAdjacentTracks(targetIndex)
            savePlaybackSession()
        }
    }

    private suspend fun getOrAwaitController(): MediaController? {
        mediaController?.let { return it }
        val future = controllerFuture ?: run {
            initializeController()
            controllerFuture
        } ?: return null

        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                val controller = future.get(5, java.util.concurrent.TimeUnit.SECONDS)
                mediaController = controller
                controller
            } catch (e: Exception) {
                Log.e(TAG, "MediaController await error: ${e.message}", e)
                mediaController
            }
        }
    }

    fun playPause() {
        val controller = mediaController
        if (controller != null) {
            if (controller.currentMediaItem == null && _playbackState.value.currentTrack != null) {
                // Resume from restored session state
                val track = _playbackState.value.currentTrack!!
                val queue = _playbackState.value.queue.ifEmpty { listOf(track) }
                val startPos = _playbackState.value.playbackPositionMs
                resumePlayback(track, queue, startPos)
                return
            }
            if (controller.isPlaying) {
                controller.pause()
            } else {
                if (controller.playbackState == Player.STATE_IDLE) {
                    controller.prepare()
                }
                controller.play()
            }
        } else {
            _playbackState.value.currentTrack?.let { track ->
                resumePlayback(track, _playbackState.value.queue, _playbackState.value.playbackPositionMs)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController
        if (controller != null && controller.currentMediaItem != null) {
            controller.seekTo(positionMs)
        }
        _playbackState.update { it.copy(playbackPositionMs = positionMs) }
        playbackSessionManager.savePosition(positionMs)
    }

    fun skipToNext() {
        scope.launch {
            val controller = mediaController ?: return@launch
            finalizeActiveTrackTelemetry(isSkipped = true)
            val nextIndex = controller.currentMediaItemIndex + 1
            if (nextIndex in currentPlaylist.indices) {
                val nextTrack = currentPlaylist[nextIndex]
                val resolved = resolveTrackForPlayback(nextTrack) ?: nextTrack
                currentPlaylist[nextIndex] = resolved
                controller.replaceMediaItem(nextIndex, buildMediaItem(resolved))
                controller.seekToNextMediaItem()
                controller.play()
            }
        }
    }

    fun skipToPrevious() {
        scope.launch {
            val controller = mediaController ?: return@launch
            if (controller.currentPosition > 3000L) {
                controller.seekTo(0L)
                return@launch
            }
            finalizeActiveTrackTelemetry(isSkipped = true)
            val prevIndex = controller.currentMediaItemIndex - 1
            if (prevIndex in currentPlaylist.indices) {
                val prevTrack = currentPlaylist[prevIndex]
                val resolved = resolveTrackForPlayback(prevTrack) ?: prevTrack
                currentPlaylist[prevIndex] = resolved
                controller.replaceMediaItem(prevIndex, buildMediaItem(resolved))
                controller.seekToPreviousMediaItem()
                controller.play()
            } else {
                controller.seekTo(0L)
            }
        }
    }

    fun toggleShuffle() {
        mediaController?.let { controller ->
            val nextMode = !controller.shuffleModeEnabled
            controller.shuffleModeEnabled = nextMode
            _playbackState.update { it.copy(shuffleModeEnabled = nextMode) }
        }
    }

    fun toggleRepeat() {
        mediaController?.let { controller ->
            val nextMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            controller.repeatMode = nextMode
            _playbackState.update { it.copy(repeatMode = nextMode) }
        }
    }

    fun addToQueue(track: TrackEntity) {
        scope.launch {
            val resolved = resolveTrackForPlayback(track) ?: track
            currentPlaylist.add(resolved)
            mediaController?.addMediaItem(buildMediaItem(resolved))
            _playbackState.update { it.copy(queue = currentPlaylist.toList()) }
            savePlaybackSession()
        }
    }

    /**
     * Replaces the upcoming queue items with freshly generated algorithmic recommendations,
     * maintaining uninterrupted playback of the current track and preserving playback position.
     * Skipped entirely if currently playing a user playlist.
     */
    fun updateUpcomingQueue(newQueue: List<TrackEntity>) {
        if (isPlaylistPlayback) {
            Log.d(TAG, "updateUpcomingQueue ignored: playlist playback is active")
            return
        }
        scope.launch {
            val controller = mediaController ?: return@launch
            val currentIdx = controller.currentMediaItemIndex
            val currentTrack = if (currentIdx in currentPlaylist.indices) currentPlaylist[currentIdx] else _playbackState.value.currentTrack
            if (currentTrack == null) return@launch

            val filteredUpcoming = newQueue
                .filter { candidate ->
                    candidate.id != currentTrack.id &&
                    !candidate.isSameSong(currentTrack) &&
                    candidate.isSongOnly()
                }
                .distinctSongs()
            if (filteredUpcoming.isEmpty()) return@launch

            // Avoid redundant player modifications if upcoming tracks are already the same
            val existingUpcomingIds = if (currentIdx + 1 < currentPlaylist.size) {
                currentPlaylist.subList(currentIdx + 1, currentPlaylist.size).map { it.id }
            } else emptyList()

            val newUpcomingIds = filteredUpcoming.map { it.id }
            if (existingUpcomingIds == newUpcomingIds) {
                return@launch
            }

            // Keep all tracks played so far including the current track, and replace only what comes next
            val updatedPlaylist = (currentPlaylist.take(currentIdx + 1) + filteredUpcoming).toMutableList()
            currentPlaylist = updatedPlaylist

            // Remove only upcoming items from ExoPlayer and append the new ones so the playing track NEVER pauses or hitches!
            val totalItems = controller.mediaItemCount
            if (totalItems > currentIdx + 1) {
                controller.removeMediaItems(currentIdx + 1, totalItems)
            }
            val newMediaItems = filteredUpcoming.map { buildMediaItem(it) }
            controller.addMediaItems(newMediaItems)

            _playbackState.update {
                it.copy(
                    queue = currentPlaylist.toList(),
                    currentQueueIndex = currentIdx
                )
            }
            savePlaybackSession()
            prefetchAdjacentTracks(currentIdx)
        }
    }

    /**
     * Refreshes the upcoming recommendations queue on demand using the active seed track and vibe.
     * Guaranteed to produce fresh, non-duplicate recommendations distinct from current upcoming queue.
     * Skipped entirely if currently playing a user playlist.
     */
    suspend fun refreshQueueRecommendations(): Boolean = withContext(Dispatchers.Main) {
        if (isPlaylistPlayback) {
            Log.d(TAG, "refreshQueueRecommendations ignored: playlist playback is active")
            return@withContext false
        }
        val controller = mediaController ?: return@withContext false
        val currentIdx = controller.currentMediaItemIndex
        val currentTrack = if (currentIdx in currentPlaylist.indices) currentPlaylist[currentIdx] else _playbackState.value.currentTrack
        if (currentTrack == null) return@withContext false

        // Collect existing upcoming track IDs to explicitly avoid recommending the exact same upcoming tracks
        val existingUpcoming = if (currentIdx + 1 < currentPlaylist.size) {
            currentPlaylist.subList(currentIdx + 1, currentPlaylist.size)
        } else emptyList()
        val excludeIds = existingUpcoming.map { it.id }.toSet()

        val freshRecs = withContext(Dispatchers.IO) {
            try {
                recommendationRepositoryProvider.get().generateRecommendationQueue(
                    baseTrack = currentTrack,
                    limit = 15,
                    excludeTrackIds = excludeIds,
                    forceFresh = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate fresh recommendations: ${e.message}")
                emptyList()
            }
        }

        if (freshRecs.isEmpty()) return@withContext false

        val filteredUpcoming = freshRecs
            .filter { candidate ->
                candidate.id != currentTrack.id &&
                !candidate.isSameSong(currentTrack) &&
                candidate.isSongOnly()
            }
            .distinctSongs()

        if (filteredUpcoming.isEmpty()) return@withContext false

        val existingUpcomingIds = existingUpcoming.map { it.id }
        val newUpcomingIds = filteredUpcoming.map { it.id }
        if (existingUpcomingIds == newUpcomingIds) {
            return@withContext false
        }

        // Keep all tracks played so far including the current track, and replace only what comes next
        val updatedPlaylist = (currentPlaylist.take(currentIdx + 1) + filteredUpcoming).toMutableList()
        currentPlaylist = updatedPlaylist

        // Remove only upcoming items from ExoPlayer and append the new ones so the playing track NEVER pauses or hitches!
        val totalItems = controller.mediaItemCount
        if (totalItems > currentIdx + 1) {
            controller.removeMediaItems(currentIdx + 1, totalItems)
        }
        val newMediaItems = filteredUpcoming.map { buildMediaItem(it) }
        controller.addMediaItems(newMediaItems)

        _playbackState.update {
            it.copy(
                queue = currentPlaylist.toList(),
                currentQueueIndex = currentIdx
            )
        }
        savePlaybackSession()
        prefetchAdjacentTracks(currentIdx)
        true
    }

    fun playNext(track: TrackEntity) {
        scope.launch {
            val controller = mediaController ?: return@launch
            val resolved = resolveTrackForPlayback(track) ?: track
            val insertIndex = (controller.currentMediaItemIndex + 1).coerceAtMost(currentPlaylist.size)
            currentPlaylist.add(insertIndex, resolved)
            controller.addMediaItem(insertIndex, buildMediaItem(resolved))
            _playbackState.update { it.copy(queue = currentPlaylist.toList()) }
            savePlaybackSession()
        }
    }

    fun playTrackAtIndex(index: Int) {
        if (index in currentPlaylist.indices) {
            scope.launch {
                val controller = mediaController ?: return@launch
                val track = currentPlaylist[index]
                val isExpired = StreamUrlValidator.isExpired(track.streamUrl)
                val resolved = if (!track.isDownloaded && (track.streamUrl.isNullOrBlank() || isExpired)) {
                    resolveTrackForPlayback(track, forceFresh = isExpired) ?: track
                } else {
                    track
                }
                currentPlaylist[index] = resolved
                controller.replaceMediaItem(index, buildMediaItem(resolved))
                controller.seekToDefaultPosition(index)
                controller.play()
                prefetchAdjacentTracks(index)
            }
        }
    }

    fun removeFromQueue(index: Int) {
        if (index !in currentPlaylist.indices) return
        scope.launch {
            val controller = mediaController
            val currentIndex = controller?.currentMediaItemIndex ?: _playbackState.value.currentQueueIndex

            if (currentPlaylist.size <= 1) {
                currentPlaylist.clear()
                activePlaylistId = null
                isPlaylistPlayback = false
                controller?.stop()
                controller?.clearMediaItems()
                _playbackState.update {
                    it.copy(
                        currentTrack = null,
                        isPlaying = false,
                        isBuffering = false,
                        queue = emptyList(),
                        currentQueueIndex = -1,
                        playbackPositionMs = 0L,
                        durationMs = 0L,
                        isPlaylistPlayback = false
                    )
                }
                savePlaybackSession()
                return@launch
            }

            currentPlaylist.removeAt(index)
            controller?.removeMediaItem(index)

            val updatedIdx = controller?.currentMediaItemIndex ?: currentIndex.coerceIn(-1, currentPlaylist.size - 1)
            val activeTrack = currentPlaylist.getOrNull(updatedIdx)

            _playbackState.update {
                it.copy(
                    currentTrack = activeTrack ?: it.currentTrack,
                    queue = currentPlaylist.toList(),
                    currentQueueIndex = updatedIdx
                )
            }
            savePlaybackSession()
        }
    }

    /**
     * Removes all occurrences of a track from active playback queue and ExoPlayer timeline.
     * If the track being removed is currently playing, advances smoothly to the next song,
     * or stops playback cleanly if it was the only track left in the queue.
     */
    fun removeTrackFromActivePlayback(trackId: String) {
        scope.launch {
            if (currentPlaylist.none { it.id == trackId }) return@launch

            val controller = mediaController
            val currentIndex = controller?.currentMediaItemIndex ?: _playbackState.value.currentQueueIndex
            val isCurrentTrackRemoved = currentIndex in currentPlaylist.indices && currentPlaylist[currentIndex].id == trackId

            // If the queue only contains this track (or all remaining tracks are this track), stop playback completely
            if (currentPlaylist.all { it.id == trackId }) {
                currentPlaylist.clear()
                activePlaylistId = null
                isPlaylistPlayback = false
                controller?.stop()
                controller?.clearMediaItems()
                _playbackState.update {
                    it.copy(
                        currentTrack = null,
                        isPlaying = false,
                        isBuffering = false,
                        queue = emptyList(),
                        currentQueueIndex = -1,
                        playbackPositionMs = 0L,
                        durationMs = 0L,
                        isPlaylistPlayback = false
                    )
                }
                savePlaybackSession()
                return@launch
            }

            // Remove non-current occurrences from highest index to lowest so preceding indices do not shift
            val nonCurrentIndices = currentPlaylist.indices
                .filter { it != currentIndex && currentPlaylist[it].id == trackId }
                .sortedDescending()

            for (idx in nonCurrentIndices) {
                currentPlaylist.removeAt(idx)
                controller?.removeMediaItem(idx)
            }

            // If the currently playing track is being deleted, remove it as well
            if (isCurrentTrackRemoved) {
                val activeIdx = controller?.currentMediaItemIndex ?: currentIndex
                if (activeIdx in currentPlaylist.indices) {
                    currentPlaylist.removeAt(activeIdx)
                    controller?.removeMediaItem(activeIdx)
                }
            }

            val updatedIdx = controller?.currentMediaItemIndex ?: _playbackState.value.currentQueueIndex.coerceIn(-1, currentPlaylist.size - 1)
            val activeTrack = currentPlaylist.getOrNull(updatedIdx)

            _playbackState.update {
                it.copy(
                    currentTrack = activeTrack ?: it.currentTrack,
                    queue = currentPlaylist.toList(),
                    currentQueueIndex = updatedIdx
                )
            }
            savePlaybackSession()
        }
    }

    fun onPlaylistDeleted(playlistId: Long) {
        if (activePlaylistId == playlistId) {
            scope.launch {
                val controller = mediaController
                currentPlaylist.clear()
                activePlaylistId = null
                isPlaylistPlayback = false
                controller?.stop()
                controller?.clearMediaItems()
                _playbackState.update {
                    it.copy(
                        currentTrack = null,
                        isPlaying = false,
                        isBuffering = false,
                        queue = emptyList(),
                        currentQueueIndex = -1,
                        playbackPositionMs = 0L,
                        durationMs = 0L,
                        isPlaylistPlayback = false
                    )
                }
                savePlaybackSession()
            }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in currentPlaylist.indices || toIndex !in currentPlaylist.indices || fromIndex == toIndex) {
            return
        }
        val item = currentPlaylist.removeAt(fromIndex)
        currentPlaylist.add(toIndex, item)
        mediaController?.let { controller ->
            controller.moveMediaItem(fromIndex, toIndex)
            _playbackState.update {
                it.copy(
                    queue = currentPlaylist.toList(),
                    currentQueueIndex = controller.currentMediaItemIndex
                )
            }
            savePlaybackSession()
        }
    }

    private var sleepTimerJob: Job? = null

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _playbackState.update { it.copy(sleepTimerRemainingSeconds = null) }
            return
        }
        sleepTimerJob = scope.launch {
            var remainingSec = minutes * 60L
            while (isActive && remainingSec > 0) {
                _playbackState.update { it.copy(sleepTimerRemainingSeconds = remainingSec) }
                delay(1000)
                remainingSec--
            }
            if (isActive) {
                mediaController?.pause()
                _playbackState.update { it.copy(sleepTimerRemainingSeconds = null, isPlaying = false) }
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _playbackState.update { it.copy(sleepTimerRemainingSeconds = null) }
    }

    /**
     * Resolves a TrackEntity to a playable destination with active stream URL validation.
     * Prevents playback failures by guaranteeing URLs are fresh and non-expired.
     */
    private suspend fun resolveTrackForPlayback(track: TrackEntity, forceFresh: Boolean = false): TrackEntity? = withContext(Dispatchers.IO) {
        // 1. Check if downloaded locally
        if (storageManager.isAudioDownloaded(track.id)) {
            val file = storageManager.getTrackAudioFile(track.id)
            if (file.exists() && file.length() > 0L) {
                return@withContext track.copy(
                    isDownloaded = true,
                    localPath = file.absolutePath,
                    streamUrl = Uri.fromFile(file).toString()
                )
            }
        }

        val isYouTubeId = track.id.length == 11 && !track.id.startsWith("saavn_")

        // 2. Check cached streamUrl & purge contaminated Saavn URLs on YouTube IDs
        if (!forceFresh && !track.streamUrl.isNullOrBlank()) {
            val isCorruptedSaavnOnYouTube = isYouTubeId &&
                (track.streamUrl.contains("saavncdn.com") || track.streamUrl.contains("jiosaavn"))

            if (isCorruptedSaavnOnYouTube) {
                Log.d(TAG, "Track ${track.id} (${track.title}) has corrupted Saavn streamUrl. Purging cache.")
                trackDao.updateStreamUrl(track.id, null)
            } else if (!StreamUrlValidator.isExpired(track.streamUrl)) {
                return@withContext track
            } else {
                Log.d(TAG, "Track ${track.id} has expired streamUrl. Invalidating cached URL.")
                trackDao.updateStreamUrl(track.id, null)
            }
        }

        // 3. PRIMARY RESOLUTION: If track is a YouTube ID, resolve via decentralized YouTube stream engine
        if (isYouTubeId) {
            val streamResult = try {
                withTimeoutOrNull(5000L) {
                    streamRepository.resolveAudioStream(track.id, forceFresh = forceFresh).firstOrNull()?.getOrNull()
                }
            } catch (e: Exception) {
                Log.w(TAG, "YouTube stream resolution failed for ${track.id}: ${e.message}")
                null
            }

            if (streamResult != null && !streamResult.streamUrl.isNullOrBlank()) {
                val updated = track.copy(streamUrl = streamResult.streamUrl)
                trackDao.insertTrack(updated)
                return@withContext updated
            }
        }

        // 4. JioSaavn Engine:
        // Used for native Saavn IDs (starts with "saavn_") OR strict fallback when YouTube resolution failed
        if (track.title.isNotBlank()) {
            try {
                val cleanTitle = track.cleanSongTitle().ifBlank { track.title }
                val cleanArt = track.cleanArtistName().ifBlank { track.artist }
                val saavnQuery = if (cleanArt.isNotBlank()) "$cleanTitle $cleanArt" else cleanTitle
                val saavnResults = saavnMediaEngine.searchTracks(saavnQuery)

                // Strict matching: requires same song check, artist overlap, and duration consistency
                val saavnMatch = saavnResults.firstOrNull { match ->
                    val sameSong = match.isSameSong(track)
                    val artistMatch = cleanArt.isBlank() || hasArtistOverlap(match.artist, track.artist) ||
                        (cleanArt.length >= 3 && match.artist.contains(cleanArt, ignoreCase = true))
                    val durationMatch = track.durationSeconds <= 0 || match.durationSeconds <= 0 ||
                        Math.abs(match.durationSeconds - track.durationSeconds) <= 30
                    sameSong && artistMatch && durationMatch
                }

                if (saavnMatch != null && !saavnMatch.streamUrl.isNullOrBlank()) {
                    val updated = track.copy(
                        streamUrl = saavnMatch.streamUrl,
                        thumbnailUrl = if (track.thumbnailUrl.isBlank()) saavnMatch.thumbnailUrl else track.thumbnailUrl
                    )
                    trackDao.insertTrack(updated)
                    return@withContext updated
                }
            } catch (e: Exception) {
                Log.w(TAG, "Saavn fallback resolution non-fatal: ${e.message}")
            }
        }

        // 5. YouTube Search resolution for non-YouTube tracks (e.g. Deezer tracks or Saavn fallback)
        if (!isYouTubeId && track.title.isNotBlank()) {
            try {
                val query = "${track.title} ${track.artist} official audio"
                val searchResults = streamRepository.searchTracks(query).firstOrNull()?.getOrNull().orEmpty()
                val matched = searchResults.firstOrNull { it.isSongOnly() } ?: searchResults.firstOrNull()
                if (matched != null) {
                    val streamResult = withTimeoutOrNull(6000L) {
                        streamRepository.resolveAudioStream(matched.id, forceFresh = forceFresh).firstOrNull()?.getOrNull()
                    }
                    if (streamResult != null && !streamResult.streamUrl.isNullOrBlank()) {
                        val updated = track.copy(
                            streamUrl = streamResult.streamUrl,
                            thumbnailUrl = if (track.thumbnailUrl.isBlank()) matched.thumbnailUrl else track.thumbnailUrl
                        )
                        trackDao.insertTrack(updated)
                        return@withContext updated
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Search stream fallback failed for ${track.title}: ${e.message}")
            }
        }

        null
    }

    /**
     * Smart Preloading & Caching:
     * Pre-resolves valid audio stream URLs for the next 3–5 predicted tracks in the queue,
     * replacing placeholder URIs in ExoPlayer ahead of time. This enables true gapless playback
     * with 0ms silence, eliminating playback stalls and buffering interruptions.
     */
    /**
     * Pipelined asynchronous stream resolution for upcoming tracks.
     * Resolves stream URLs for upcoming tracks sequentially in the background without blocking the UI,
     * immediately updating ExoPlayer and _playbackState as each individual track resolves like a streaming pipeline.
     */
    private fun prefetchAdjacentTracks(currentIndex: Int) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            // Pipeline resolution: resolve up to 4 upcoming tracks ahead
            for (offset in 1..4) {
                val nextIndex = currentIndex + offset
                if (nextIndex in currentPlaylist.indices) {
                    val nextTrack = currentPlaylist[nextIndex]
                    val isExpired = StreamUrlValidator.isExpired(nextTrack.streamUrl)
                    if ((nextTrack.streamUrl.isNullOrBlank() || isExpired) && !nextTrack.isDownloaded) {
                        val resolved = resolveTrackForPlayback(nextTrack, forceFresh = isExpired)
                        if (resolved != null && !resolved.streamUrl.isNullOrBlank()) {
                            currentPlaylist[nextIndex] = resolved
                            try {
                                trackDao.insertTrack(resolved)
                            } catch (_: Exception) {}
                            withContext(Dispatchers.Main) {
                                val controller = getOrAwaitController()
                                if (controller != null && nextIndex < controller.mediaItemCount) {
                                    controller.replaceMediaItem(nextIndex, buildMediaItem(resolved))
                                }
                                _playbackState.update { it.copy(queue = currentPlaylist.toList()) }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun buildMediaItem(track: TrackEntity): MediaItem {
        val uri = when {
            !track.localPath.isNullOrBlank() -> {
                if (track.localPath.startsWith("file://")) {
                    Uri.parse(track.localPath)
                } else {
                    Uri.fromFile(java.io.File(track.localPath))
                }
            }
            !track.streamUrl.isNullOrBlank() -> Uri.parse(track.streamUrl)
            else -> Uri.EMPTY
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle("SatvikX")
            .setArtworkUri(if (track.thumbnailUrl.isNotBlank()) Uri.parse(track.thumbnailUrl) else null)
            .setExtras(android.os.Bundle().apply {
                putLong("duration_seconds", track.durationSeconds)
            })
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun syncPlaybackState() {
        val controller = mediaController ?: return
        val currentMediaItem = controller.currentMediaItem

        if (currentMediaItem == null) {
            // Player is idle / empty. Maintain restored session in UI if present.
            if (_playbackState.value.currentTrack != null) {
                _playbackState.update {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false
                    )
                }
                return
            }
        }

        val currentTrack = currentPlaylist.find { it.id == currentMediaItem?.mediaId }
            ?: currentMediaItem?.let {
                TrackEntity(
                    id = it.mediaId,
                    title = it.mediaMetadata.title?.toString() ?: "Unknown",
                    artist = it.mediaMetadata.artist?.toString() ?: "Unknown",
                    durationSeconds = controller.duration.coerceAtLeast(0L) / 1000L,
                    thumbnailUrl = it.mediaMetadata.artworkUri?.toString().orEmpty()
                )
            }

        _playbackState.update {
            it.copy(
                currentTrack = currentTrack,
                isPlaying = controller.isPlaying,
                isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                playbackPositionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = controller.duration.coerceAtLeast(0L),
                bufferedPositionMs = controller.bufferedPosition.coerceAtLeast(0L),
                shuffleModeEnabled = controller.shuffleModeEnabled,
                repeatMode = controller.repeatMode,
                currentQueueIndex = controller.currentMediaItemIndex,
                queue = currentPlaylist.toList(),
                isPlaylistPlayback = isPlaylistPlayback
            )
        }

        if (controller.isPlaying) {
            startProgressUpdates()
        } else {
            progressJob?.cancel()
            savePlaybackSession()
        }
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        val currentPos = controller.currentPosition.coerceAtLeast(0L)
                        val duration = controller.duration.coerceAtLeast(0L)
                        lastObservedPositionMs = currentPos

                        _playbackState.update {
                            it.copy(
                                playbackPositionMs = currentPos,
                                durationMs = duration,
                                bufferedPositionMs = controller.bufferedPosition.coerceAtLeast(0L)
                            )
                        }

                        // Crossfade Volume Modulation:
                        // As song nears its end (remaining <= crossfadeDurationSeconds * 1000L),
                        // smoothly ramp volume down from 1.0f -> 0.15f so the incoming track blends naturally.
                        val crossfadeMs = _playbackState.value.crossfadeDurationSeconds * 1000L
                        val isCrossfadeOn = _playbackState.value.isCrossfadeEnabled
                        val remainingMs = duration - currentPos
                        if (isCrossfadeOn && duration > 15000L && remainingMs in 0L..crossfadeMs) {
                            val fadeFactor = (remainingMs.toFloat() / crossfadeMs.toFloat()).coerceIn(0.15f, 1.0f)
                            isCrossfadingOut = true
                            controller.volume = fadeFactor
                        } else if (!isCrossfadingIn && isCrossfadingOut) {
                            // Reset back to nominal if user scrubbed back
                            isCrossfadingOut = false
                            controller.volume = 1.0f
                        }

                        // Background Candidate Harvesting & Sliding Pre-Resolution at 60% playback progress:
                        // Pre-warms candidate pool and pre-resolves the next 4 upcoming tracks.
                        // If upcoming queue is low (< 4 tracks), proactively extends the queue so playback never halts.
                        val currentTrack = _playbackState.value.currentTrack
                        if (currentTrack != null && duration > 15000L) {
                            val progressRatio = currentPos.toFloat() / duration.toFloat()
                            if (progressRatio >= 0.60f && lastPrewarmedTrackId != currentTrack.id) {
                                lastPrewarmedTrackId = currentTrack.id
                                triggerBackgroundPrewarming(currentTrack, controller.currentMediaItemIndex)
                            }
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastSaveTimeMs > 2500L) {
                            lastSaveTimeMs = now
                            playbackSessionManager.savePosition(currentPos)
                        }
                    }
                }
                delay(250) // 250ms cadence for silky-smooth progress and crossfade volume transitions
            }
        }
    }

    /**
     * Proactive Background Pre-warming & Sliding Buffer Engine:
     * Triggered at 60% playback completion to eliminate cold-start latency.
     * 1. Pre-resolves sliding 4-track audio URLs for upcoming items.
     * 2. Asynchronously harvests the 300+ candidate pool for the active seed in background cache.
     * 3. Proactively extends the queue if fewer than 4 tracks remain so playback never stalls.
     */
    private fun triggerBackgroundPrewarming(currentTrack: TrackEntity, currentIndex: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                // 1. Sliding 4-track audio URL pre-resolution for upcoming items
                prefetchAdjacentTracks(currentIndex)

                // 2. Proactive candidate harvest & queue replenishment if not playing custom user playlist
                if (!isPlaylistPlayback) {
                    recommendationRepositoryProvider.get().prewarmCandidates(currentTrack)

                    val upcomingCount = (currentPlaylist.size - (currentIndex + 1)).coerceAtLeast(0)
                    if (upcomingCount < 4) {
                        Log.d(TAG, "Upcoming queue low ($upcomingCount tracks) at 60% playback; proactively extending queue")
                        extendAutoplayQueue(currentTrack)
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.w(TAG, "Background pre-warming non-fatal: ${e.message}")
                }
            }
        }
    }

    private fun savePlaybackSession() {
        val track = _playbackState.value.currentTrack ?: return
        playbackSessionManager.saveSession(
            track = track,
            queue = currentPlaylist,
            queueIndex = _playbackState.value.currentQueueIndex,
            positionMs = _playbackState.value.playbackPositionMs,
            isPlaylist = isPlaylistPlayback
        )
    }

    /**
     * Active buffering timeout watchdog. If playback remains stuck in STATE_BUFFERING
     * for more than 8 seconds, automatically triggers auto-recovery to resolve a fresh URL.
     */
    private fun handleBufferingWatchdog(isBuffering: Boolean) {
        bufferingWatchdogJob?.cancel()
        if (isBuffering) {
            bufferingWatchdogJob = scope.launch {
                delay(8000) // 8-second buffering threshold
                if (isActive) {
                    val controller = mediaController
                    if (controller != null && controller.playbackState == Player.STATE_BUFFERING && !controller.isPlaying) {
                        Log.w(TAG, "Playback stuck buffering for >8s! Initiating active auto-recovery...")
                        recoverCurrentTrackPlayback(reason = "Buffering watchdog timeout")
                    }
                }
            }
        }
    }

    /**
     * Intelligently recovers playback by clearing stale stream URLs from Room database,
     * re-resolving a fresh stream URL via network engine, and replacing the MediaItem seamlessly.
     */
    private fun recoverCurrentTrackPlayback(reason: String) {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex in currentPlaylist.indices) {
            val currentTrack = currentPlaylist[currentIndex]
            val currentPos = controller.currentPosition.coerceAtLeast(0L)
            Log.i(TAG, "Attempting playback recovery for '${currentTrack.title}' (${currentTrack.id}) due to: $reason")

            scope.launch {
                _playbackState.update { it.copy(isBuffering = true) }
                trackDao.updateStreamUrl(currentTrack.id, null)
                val refreshed = resolveTrackForPlayback(currentTrack, forceFresh = true)
                if (refreshed != null && !refreshed.streamUrl.isNullOrBlank()) {
                    currentPlaylist[currentIndex] = refreshed
                    trackDao.insertTrack(refreshed)
                    controller.replaceMediaItem(currentIndex, buildMediaItem(refreshed))
                    controller.seekTo(currentIndex, currentPos)
                    controller.prepare()
                    controller.play()
                    _playbackState.update {
                        it.copy(
                            currentTrack = refreshed,
                            isBuffering = false,
                            isPlaying = true
                        )
                    }
                    savePlaybackSession()
                    prefetchAdjacentTracks(currentIndex)
                    Log.i(TAG, "Successfully auto-recovered '${currentTrack.title}' with fresh stream URL!")
                } else {
                    Log.w(TAG, "Auto-recovery failed to obtain fresh stream for '${currentTrack.title}'. Advancing.")
                    if (currentIndex < currentPlaylist.size - 1) {
                        playTrackAtIndex(currentIndex + 1)
                    } else {
                        _playbackState.update { it.copy(isBuffering = false, isPlaying = false) }
                    }
                }
            }
        }
    }

    private inner class PlayerListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncPlaybackState()
            if (!isPlaying) {
                savePlaybackSession()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncPlaybackState()
            handleBufferingWatchdog(playbackState == Player.STATE_BUFFERING)

            if (playbackState == Player.STATE_ENDED && !isPlaylistPlayback) {
                Log.i(TAG, "ExoPlayer reached STATE_ENDED in autoplay mode. Reviving continuous playback...")
                scope.launch {
                    val controller = getOrAwaitController() ?: return@launch
                    val currentIdx = controller.currentMediaItemIndex
                    if (currentIdx + 1 < currentPlaylist.size) {
                        playTrackAtIndex(currentIdx + 1)
                    } else {
                        val currentTrack = _playbackState.value.currentTrack
                        if (currentTrack != null) {
                            extendAutoplayQueue(currentTrack, forceFresh = true)
                        }
                    }
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val controller = mediaController ?: return
            val currentIndex = controller.currentMediaItemIndex

            // Reset pre-warming anchor for the new track
            lastPrewarmedTrackId = null

            // Complete telemetry for previous song if transitioning
            finalizeActiveTrackTelemetry(isSkipped = reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)

            if (currentIndex in currentPlaylist.indices) {
                val track = currentPlaylist[currentIndex]
                scope.launch(Dispatchers.IO) {
                    trackDao.insertTrack(track)
                    recentPlaybackDao.recordOrIncrementPlayback(track.id)
                    try {
                        recommendationRepositoryProvider.get().notifyPlaybackRecorded()
                    } catch (_: Exception) {}
                }

                // Telemetry: record start of newly active track
                recordTrackStartTelemetry(track)

                // Smooth Crossfade Fade-In:
                // If crossfade enabled, ramp volume up smoothly from 0.2f -> 1.0f over 1500ms
                if (_playbackState.value.isCrossfadeEnabled) {
                    performSmoothFadeIn(controller)
                } else {
                    controller.volume = 1.0f
                }

                val isExpired = StreamUrlValidator.isExpired(track.streamUrl)
                if ((track.streamUrl.isNullOrBlank() || isExpired) && !track.isDownloaded) {
                    scope.launch {
                        _playbackState.update { it.copy(isBuffering = true) }
                        val resolved = resolveTrackForPlayback(track, forceFresh = isExpired)
                        if (resolved != null && !resolved.streamUrl.isNullOrBlank()) {
                            currentPlaylist[currentIndex] = resolved
                            controller.replaceMediaItem(currentIndex, buildMediaItem(resolved))
                            controller.prepare()
                            controller.play()
                            _playbackState.update { it.copy(currentTrack = resolved, isBuffering = false) }
                        }
                    }
                }
                prefetchAdjacentTracks(currentIndex)
                savePlaybackSession()

                // YouTube-like Infinite Autoplay: regenerate every 5 tracks or when nearing queue end (only if not playing a playlist)
                val shouldRegenerateCadence = (lastAutoplayGenerationIndex != -1 && currentIndex - lastAutoplayGenerationIndex >= 5)
                if (!isPlaylistPlayback && (currentIndex >= currentPlaylist.size - 2 || shouldRegenerateCadence)) {
                    extendAutoplayQueue(track)
                }
            }
            syncPlaybackState()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            syncPlaybackState()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _playbackState.update { it.copy(shuffleModeEnabled = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _playbackState.update { it.copy(repeatMode = repeatMode) }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(TAG, "ExoPlayer error: ${error.errorCodeName} (${error.errorCode}) - ${error.message}", error)
            recoverCurrentTrackPlayback(reason = "Player error ${error.errorCodeName}")
        }
    }

    private var lastAutoplayGenerationIndex: Int = -1
    private var autoplayQueueJob: kotlinx.coroutines.Job? = null

    /**
     * Synthesizes and appends fresh high-affinity recommendations to the active ExoPlayer queue,
     * delivering seamless infinite continuous playback just like YouTube Autoplay.
     * Skipped entirely when playing a playlist.
     */
    fun extendAutoplayQueue(seedTrack: TrackEntity, forceFresh: Boolean = false) {
        if (isPlaylistPlayback) {
            Log.d(TAG, "extendAutoplayQueue ignored: playlist playback is active")
            return
        }
        if (forceFresh) {
            autoplayQueueJob?.cancel()
        } else if (autoplayQueueJob?.isActive == true) {
            return
        }
        val controller = mediaController
        lastAutoplayGenerationIndex = controller?.currentMediaItemIndex ?: 0
        autoplayQueueJob = scope.launch(Dispatchers.IO) {
            try {
                val recommendationRepo = recommendationRepositoryProvider.get()
                val nextBatch = recommendationRepo.generateRecommendationQueue(seedTrack, 15)
                val currentIdx = controller?.currentMediaItemIndex ?: 0
                val upcomingAndRecent = if (currentPlaylist.isNotEmpty()) {
                    val startIndex = (currentIdx - 5).coerceAtLeast(0)
                    currentPlaylist.subList(startIndex, currentPlaylist.size)
                } else emptyList()

                val uniqueNew = nextBatch
                    .filter { candidate ->
                        candidate.isSongOnly() &&
                        !candidate.isSameSong(seedTrack) &&
                        upcomingAndRecent.none { existing -> candidate.isSameSong(existing) }
                    }
                    .distinctSongs()

                val candidatePool = if (uniqueNew.isNotEmpty()) {
                    uniqueNew
                } else {
                    try {
                        val fallbackSearch = streamRepository.searchTracks("${seedTrack.artist} top hits").firstOrNull()?.getOrNull().orEmpty()
                        val filteredFallback = fallbackSearch
                            .filter { it.isSongOnly() && !it.isSameSong(seedTrack) && upcomingAndRecent.none { c -> it.isSameSong(c) } }
                            .distinctSongs()
                        filteredFallback.ifEmpty {
                            nextBatch.filter { it.isSongOnly() && !it.isSameSong(seedTrack) }.distinctSongs()
                        }
                    } catch (_: Exception) {
                        nextBatch.filter { it.isSongOnly() && !it.isSameSong(seedTrack) }.distinctSongs()
                    }
                }

                if (candidatePool.isNotEmpty()) {
                    val finalNew = candidatePool.distinctSongs()

                    withContext(Dispatchers.Main) {
                        val controller = getOrAwaitController()
                        currentPlaylist.addAll(finalNew)
                        val mediaItems = finalNew.map { buildMediaItem(it) }
                        controller?.addMediaItems(mediaItems)
                        _playbackState.update { it.copy(queue = currentPlaylist.toList()) }
                        savePlaybackSession()
                        prefetchAdjacentTracks(controller?.currentMediaItemIndex ?: 0)
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.w(TAG, "Autoplay extension non-fatal: ${e.message}")
                }
            }
        }
    }

    private var fadeInJob: Job? = null

    private fun performSmoothFadeIn(controller: MediaController) {
        fadeInJob?.cancel()
        fadeInJob = scope.launch {
            isCrossfadingIn = true
            val totalSteps = 10
            val delayPerStep = 150L // 1.5 seconds total
            controller.volume = 0.2f
            for (step in 1..totalSteps) {
                delay(delayPerStep)
                val targetVol = 0.2f + (0.8f * (step.toFloat() / totalSteps))
                controller.volume = targetVol.coerceIn(0.2f, 1.0f)
            }
            controller.volume = 1.0f
            isCrossfadingIn = false
            isCrossfadingOut = false
        }
    }

    private fun recordTrackStartTelemetry(track: TrackEntity) {
        activeTrackId = track.id
        activeTrackStartTimeMs = System.currentTimeMillis()
        activeTrackTotalListenedMs = 0L
        lastObservedPositionMs = 0L

        scope.launch(Dispatchers.IO) {
            try {
                playbackTelemetryDao.recordPlayStart(
                    trackId = track.id,
                    title = track.title,
                    artist = track.artist
                )
                val trigger = recommendationRepositoryProvider.get().onTrackStarted(track)
                if (!isPlaylistPlayback) {
                    if (trigger == SessionAdaptationTrigger.MAJOR_GENRE_SWITCH) {
                        recalculateUpcomingQueue(track, reason = "Major genre switch")
                    } else if (trigger == SessionAdaptationTrigger.TWO_REPLAYS) {
                        recalculateUpcomingQueue(track, reason = "2 consecutive replays")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Telemetry start non-fatal: ${e.message}")
            }

            try {
                supabaseAnalyticsRepositoryProvider.get().recordTrackStarted(track)
            } catch (e: Exception) {
                Log.w(TAG, "Real-time track start Supabase analytics non-fatal: ${e.message}")
            }
        }
    }

    private fun finalizeActiveTrackTelemetry(isSkipped: Boolean) {
        val trackId = activeTrackId ?: return
        val currentTrack = currentPlaylist.find { it.id == trackId } ?: return
        val durationMs = currentTrack.durationSeconds * 1000L
        val listenedMs = lastObservedPositionMs.coerceAtLeast(0L)
        val completionRate = if (durationMs > 0) (listenedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

        val isCompleted = completionRate >= 0.70f || (durationMs > 0 && listenedMs >= (durationMs - 5000L))
        val isQuickSkip = isSkipped && listenedMs < 20000L && completionRate < 0.25f

        scope.launch(Dispatchers.IO) {
            try {
                playbackTelemetryDao.updateTrackEngagement(
                    trackId = trackId,
                    listenTimeMs = listenedMs,
                    completionRate = completionRate,
                    completed = if (isCompleted) 1 else 0,
                    skipped = if (isQuickSkip) 1 else 0
                )

                if (!isPlaylistPlayback && (isQuickSkip || isSkipped)) {
                    val trigger = recommendationRepositoryProvider.get().onTrackSkipped(currentTrack, listenedMs)
                    if (trigger == SessionAdaptationTrigger.THREE_SKIPS) {
                        recalculateUpcomingQueue(currentTrack, reason = "3 consecutive skips")
                    }
                } else if (isCompleted) {
                    recommendationRepositoryProvider.get().onTrackCompleted(currentTrack)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Telemetry finalize non-fatal: ${e.message}")
            }

            val listenedSec = listenedMs / 1000L
            if (listenedSec > 0L) {
                try {
                    authRepositoryProvider.get().recordListeningSeconds(listenedSec)
                } catch (_: Exception) {}

                try {
                    supabaseAnalyticsRepositoryProvider.get().recordTrackPlayback(
                        track = currentTrack,
                        listenedSeconds = listenedSec,
                        isCompleted = isCompleted,
                        isSkipped = isSkipped || isQuickSkip
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Supabase analytics telemetry non-fatal: ${e.message}")
                }
            }
        }

        activeTrackId = null
    }

    fun setCrossfadeSettings(enabled: Boolean, durationSeconds: Int = 4) {
        _playbackState.update {
            it.copy(
                isCrossfadeEnabled = enabled,
                crossfadeDurationSeconds = durationSeconds.coerceIn(2, 10)
            )
        }
    }

    fun onTrackLiked(track: TrackEntity) {
        updateTrackLikeStatusInTelemetry(track.id, true)
        scope.launch(Dispatchers.IO) {
            recommendationRepositoryProvider.get().onTrackLiked(track)
            if (!isPlaylistPlayback) {
                recalculateUpcomingQueue(track, reason = "Track liked")
            }
        }
    }

    fun recalculateUpcomingQueue(seedTrack: TrackEntity, reason: String) {
        if (isPlaylistPlayback) {
            Log.d(TAG, "recalculateUpcomingQueue ignored ($reason): playlist playback is active")
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "Dynamic session adaptation triggered ($reason) for: ${seedTrack.title}")
                val freshRecs = recommendationRepositoryProvider.get().generateRecommendationQueue(seedTrack, 15)
                if (freshRecs.isNotEmpty()) {
                    updateUpcomingQueue(freshRecs)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.w(TAG, "Recalculate upcoming queue error: ${e.message}")
                }
            }
        }
    }

    fun updateTrackLikeStatusInTelemetry(trackId: String, isLiked: Boolean) {
        scope.launch(Dispatchers.IO) {
            try {
                playbackTelemetryDao.updateLikedStatus(trackId, isLiked)
            } catch (_: Exception) {}
        }
    }
}
