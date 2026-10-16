package com.satvik.satvikx.playback

import android.content.ComponentName
import android.content.Context
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
import com.satvik.satvikx.data.local.entity.isSongOnly
import com.satvik.satvikx.data.local.storage.StorageManager
import com.satvik.satvikx.data.repository.RecommendationRepository
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
    private val storageManager: StorageManager,
    private val playbackSessionManager: PlaybackSessionManager,
    private val recommendationRepositoryProvider: Provider<RecommendationRepository>
) {

    companion object {
        private const val TAG = "PlaybackConnManager"
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
            _playbackState.update {
                it.copy(
                    currentTrack = session.track,
                    queue = session.queue,
                    currentQueueIndex = session.queueIndex,
                    playbackPositionMs = session.positionMs,
                    durationMs = session.track.durationSeconds * 1000L,
                    isPlaying = false,
                    isBuffering = false
                )
            }
            scope.launch {
                val fullQueue = playbackSessionManager.restoreFullQueue()
                if (fullQueue.isNotEmpty()) {
                    currentPlaylist = fullQueue.toMutableList()
                    val safeIdx = session.queueIndex.coerceIn(0, fullQueue.size - 1)
                    _playbackState.update {
                        it.copy(
                            queue = fullQueue,
                            currentQueueIndex = safeIdx
                        )
                    }
                }
            }
        }
    }

    private fun initializeController() {
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
    fun playTrack(track: TrackEntity, playlist: List<TrackEntity> = emptyList()) {
        val filteredPlaylist = if (playlist.isNotEmpty()) {
            playlist.filter { it.id == track.id || it.isSongOnly() }
        } else {
            emptyList()
        }
        val initialQueue = if (filteredPlaylist.isNotEmpty()) filteredPlaylist else listOf(track)
        val initialIndex = initialQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        // 1. Immediately update UI state so Spotify-style bottom mini-player pops up instantly!
        _playbackState.update {
            it.copy(
                currentTrack = track,
                queue = initialQueue,
                currentQueueIndex = initialIndex,
                isPlaying = true,
                isBuffering = true
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
            val mediaItems = currentPlaylist.map { buildMediaItem(it) }

            controller?.let { c ->
                c.setMediaItems(mediaItems, targetIndex, 0L)
                c.prepare()
                c.play()
            }

            // Guarantee track persistence in local database and register in playback telemetry history
            trackDao.insertTrack(playableTrack)
            recentPlaybackDao.recordOrIncrementPlayback(playableTrack.id)

            // If queue is single track, asynchronously load YouTube-like up next recommendations
            if (currentPlaylist.size <= 1) {
                extendAutoplayQueue(playableTrack, forceFresh = true)
            }

            _playbackState.update {
                it.copy(
                    currentTrack = playableTrack,
                    queue = currentPlaylist.toList(),
                    currentQueueIndex = targetIndex,
                    isPlaying = true,
                    isBuffering = false
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
    fun resumePlayback(track: TrackEntity, playlist: List<TrackEntity> = emptyList(), startPositionMs: Long = 0L) {
        val filteredPlaylist = if (playlist.isNotEmpty()) {
            playlist.filter { it.id == track.id || it.isSongOnly() }
        } else {
            emptyList()
        }
        val initialQueue = if (filteredPlaylist.isNotEmpty()) filteredPlaylist else listOf(track)
        val initialIndex = initialQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        _playbackState.update {
            it.copy(
                currentTrack = track,
                queue = initialQueue,
                currentQueueIndex = initialIndex,
                isPlaying = true,
                isBuffering = true,
                playbackPositionMs = startPositionMs
            )
        }

        scope.launch {
            val controller = getOrAwaitController()
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
            val mediaItems = currentPlaylist.map { buildMediaItem(it) }

            controller?.let { c ->
                c.setMediaItems(mediaItems, targetIndex, startPositionMs)
                c.prepare()
                c.play()
            }

            trackDao.insertTrack(playableTrack)
            recentPlaybackDao.recordOrIncrementPlayback(playableTrack.id)

            if (currentPlaylist.size <= 1) {
                extendAutoplayQueue(playableTrack, forceFresh = true)
            }

            _playbackState.update {
                it.copy(
                    currentTrack = playableTrack,
                    queue = currentPlaylist.toList(),
                    currentQueueIndex = targetIndex,
                    isPlaying = true,
                    isBuffering = false
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
                future.get(5, java.util.concurrent.TimeUnit.SECONDS)
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
     */
    fun updateUpcomingQueue(newQueue: List<TrackEntity>) {
        scope.launch {
            val controller = mediaController ?: return@launch
            val currentIdx = controller.currentMediaItemIndex
            val currentTrack = if (currentIdx in currentPlaylist.indices) currentPlaylist[currentIdx] else _playbackState.value.currentTrack
            if (currentTrack == null) return@launch

            val filteredUpcoming = newQueue.filter { it.id != currentTrack.id && it.isSongOnly() }
            currentPlaylist = (listOf(currentTrack) + filteredUpcoming).toMutableList()

            val mediaItems = currentPlaylist.map { buildMediaItem(it) }
            controller.setMediaItems(mediaItems, 0, controller.currentPosition)

            _playbackState.update {
                it.copy(
                    queue = currentPlaylist.toList(),
                    currentQueueIndex = 0
                )
            }
            savePlaybackSession()
        }
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
            mediaController?.let { controller ->
                controller.seekToDefaultPosition(index)
                controller.play()
            }
        }
    }

    fun removeFromQueue(index: Int) {
        if (index in currentPlaylist.indices) {
            val controller = mediaController
            if (controller != null && index != controller.currentMediaItemIndex) {
                currentPlaylist.removeAt(index)
                controller.removeMediaItem(index)
                _playbackState.update {
                    it.copy(
                        queue = currentPlaylist.toList(),
                        currentQueueIndex = controller.currentMediaItemIndex
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
    private suspend fun resolveTrackForPlayback(track: TrackEntity, forceFresh: Boolean = false): TrackEntity? {
        // 1. Check if downloaded locally
        if (storageManager.isAudioDownloaded(track.id)) {
            val file = storageManager.getTrackAudioFile(track.id)
            if (file.exists() && file.length() > 0L) {
                return track.copy(
                    isDownloaded = true,
                    localPath = file.absolutePath,
                    streamUrl = Uri.fromFile(file).toString()
                )
            }
        }

        // 2. Check if cached streamUrl exists, is non-empty, and is NOT expired
        if (!forceFresh && !track.streamUrl.isNullOrBlank()) {
            if (!StreamUrlValidator.isExpired(track.streamUrl)) {
                return track
            } else {
                Log.d(TAG, "Track ${track.id} has expired streamUrl. Invalidating cached URL.")
                trackDao.updateStreamUrl(track.id, null)
            }
        }

        // 3. Resolve stream via decentralized network engine (forcing fresh resolution if requested or expired)
        val streamResult = streamRepository.resolveAudioStream(track.id, forceFresh = forceFresh).firstOrNull()?.getOrNull()
        return if (streamResult != null && !streamResult.streamUrl.isNullOrBlank()) {
            val updated = track.copy(streamUrl = streamResult.streamUrl)
            trackDao.insertTrack(updated)
            updated
        } else {
            null
        }
    }

    private fun prefetchAdjacentTracks(currentIndex: Int) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            for (offset in 1..2) {
                val nextIndex = currentIndex + offset
                if (nextIndex in currentPlaylist.indices) {
                    val nextTrack = currentPlaylist[nextIndex]
                    val isExpired = StreamUrlValidator.isExpired(nextTrack.streamUrl)
                    if ((nextTrack.streamUrl.isNullOrBlank() || isExpired) && !nextTrack.isDownloaded) {
                        val resolved = resolveTrackForPlayback(nextTrack, forceFresh = isExpired)
                        if (resolved != null) {
                            currentPlaylist[nextIndex] = resolved
                            launch(Dispatchers.Main) {
                                mediaController?.replaceMediaItem(nextIndex, buildMediaItem(resolved))
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
                val file = if (track.localPath.startsWith("file://")) {
                    java.io.File(Uri.parse(track.localPath).path.orEmpty())
                } else {
                    java.io.File(track.localPath)
                }
                if (file.exists() && file.length() > 0L) {
                    Uri.fromFile(file)
                } else if (!track.streamUrl.isNullOrBlank()) {
                    Uri.parse(track.streamUrl)
                } else {
                    Uri.EMPTY
                }
            }
            storageManager.isAudioDownloaded(track.id) -> {
                val file = storageManager.getTrackAudioFile(track.id)
                if (file.exists() && file.length() > 0L) {
                    Uri.fromFile(file)
                } else if (!track.streamUrl.isNullOrBlank()) {
                    Uri.parse(track.streamUrl)
                } else {
                    Uri.EMPTY
                }
            }
            !track.streamUrl.isNullOrBlank() -> Uri.parse(track.streamUrl)
            else -> Uri.EMPTY
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setArtworkUri(if (track.thumbnailUrl.isNotBlank()) Uri.parse(track.thumbnailUrl) else null)
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
                queue = currentPlaylist.toList()
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
                        _playbackState.update {
                            it.copy(
                                playbackPositionMs = currentPos,
                                durationMs = controller.duration.coerceAtLeast(0L),
                                bufferedPositionMs = controller.bufferedPosition.coerceAtLeast(0L)
                            )
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastSaveTimeMs > 2500L) {
                            lastSaveTimeMs = now
                            playbackSessionManager.savePosition(currentPos)
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun savePlaybackSession() {
        val track = _playbackState.value.currentTrack ?: return
        playbackSessionManager.saveSession(
            track = track,
            queue = currentPlaylist,
            queueIndex = _playbackState.value.currentQueueIndex,
            positionMs = _playbackState.value.playbackPositionMs
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
                val refreshed = streamRepository.resolveAudioStream(currentTrack.id, forceFresh = true).firstOrNull()?.getOrNull()
                if (refreshed != null && !refreshed.streamUrl.isNullOrBlank()) {
                    val updatedTrack = currentTrack.copy(streamUrl = refreshed.streamUrl)
                    currentPlaylist[currentIndex] = updatedTrack
                    trackDao.insertTrack(updatedTrack)
                    controller.replaceMediaItem(currentIndex, buildMediaItem(updatedTrack))
                    controller.seekTo(currentIndex, currentPos)
                    controller.prepare()
                    controller.play()
                    _playbackState.update {
                        it.copy(
                            currentTrack = updatedTrack,
                            isBuffering = false,
                            isPlaying = true
                        )
                    }
                    savePlaybackSession()
                    Log.i(TAG, "Successfully auto-recovered '${currentTrack.title}' with fresh stream URL!")
                } else {
                    Log.w(TAG, "Auto-recovery failed to obtain fresh stream for '${currentTrack.title}'. Skipping.")
                    if (currentIndex < currentPlaylist.size - 1) {
                        skipToNext()
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
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val controller = mediaController ?: return
            val currentIndex = controller.currentMediaItemIndex
            if (currentIndex in currentPlaylist.indices) {
                val track = currentPlaylist[currentIndex]
                scope.launch(Dispatchers.IO) {
                    trackDao.insertTrack(track)
                    recentPlaybackDao.recordOrIncrementPlayback(track.id)
                }
                val isExpired = StreamUrlValidator.isExpired(track.streamUrl)
                if ((track.streamUrl.isNullOrBlank() || isExpired) && !track.isDownloaded) {
                    scope.launch {
                        val resolved = resolveTrackForPlayback(track, forceFresh = isExpired)
                        if (resolved != null) {
                            currentPlaylist[currentIndex] = resolved
                            controller.replaceMediaItem(currentIndex, buildMediaItem(resolved))
                            controller.play()
                        }
                    }
                }
                prefetchAdjacentTracks(currentIndex)
                savePlaybackSession()

                // YouTube-like Infinite Autoplay: automatically append more songs when nearing queue end
                if (currentIndex >= currentPlaylist.size - 2) {
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

    private var autoplayQueueJob: kotlinx.coroutines.Job? = null

    /**
     * Synthesizes and appends fresh high-affinity recommendations to the active ExoPlayer queue,
     * delivering seamless infinite continuous playback just like YouTube Autoplay.
     */
    fun extendAutoplayQueue(seedTrack: TrackEntity, forceFresh: Boolean = false) {
        if (forceFresh) {
            autoplayQueueJob?.cancel()
        } else if (autoplayQueueJob?.isActive == true) {
            return
        }
        autoplayQueueJob = scope.launch {
            try {
                val recommendationRepo = recommendationRepositoryProvider.get()
                val nextBatch = recommendationRepo.generateRecommendationQueue(seedTrack, 15)
                val existingIds = currentPlaylist.map { it.id }.toSet()
                val uniqueNew = nextBatch.filter { it.id !in existingIds && it.isSongOnly() }

                if (uniqueNew.isNotEmpty()) {
                    // Pre-resolve the first autoplay track so it never lands in ExoPlayer with Uri.EMPTY
                    val firstTrack = uniqueNew.first()
                    val resolvedFirst = resolveTrackForPlayback(firstTrack) ?: firstTrack
                    val finalNew = listOf(resolvedFirst) + uniqueNew.drop(1)

                    currentPlaylist.addAll(finalNew)
                    val mediaItems = finalNew.map { buildMediaItem(it) }
                    mediaController?.addMediaItems(mediaItems)
                    _playbackState.update { it.copy(queue = currentPlaylist.toList()) }
                    savePlaybackSession()
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.w(TAG, "Autoplay extension non-fatal: ${e.message}")
                }
            }
        }
    }
}
