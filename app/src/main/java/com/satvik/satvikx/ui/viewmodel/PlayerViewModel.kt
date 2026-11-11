package com.satvik.satvikx.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satvik.satvikx.data.download.DownloadRepository
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.remote.model.LyricsData
import com.satvik.satvikx.data.remote.network.NetworkMonitor
import com.satvik.satvikx.data.repository.LyricsRepository
import com.satvik.satvikx.playback.PlaybackConnectionManager
import com.satvik.satvikx.playback.model.PlaybackState
import com.satvik.satvikx.updater.UpdateManager
import com.satvik.satvikx.updater.model.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LyricsUiState(
    val isLoading: Boolean = false,
    val lyrics: LyricsData? = null,
    val error: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackConnectionManager: PlaybackConnectionManager,
    private val downloadRepository: DownloadRepository,
    private val updateManager: UpdateManager,
    private val networkMonitor: NetworkMonitor,
    private val lyricsRepository: LyricsRepository
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playbackConnectionManager.playbackState
    val updateState: StateFlow<UpdateState> = updateManager.updateState
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    private val _lyricsState = MutableStateFlow(LyricsUiState())
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()

    private var lyricsFetchJob: Job? = null

    init {
        // Automatically check for GitHub releases in the background on app start
        viewModelScope.launch {
            updateManager.checkForUpdates()
        }

        // Observe track changes to proactively resolve time-synced / plain lyrics
        viewModelScope.launch {
            playbackState
                .map { it.currentTrack }
                .distinctUntilChanged { old, new -> old?.id == new?.id }
                .collect { currentTrack ->
                    onTrackChanged(currentTrack)
                }
        }
    }

    private fun onTrackChanged(track: TrackEntity?) {
        lyricsFetchJob?.cancel()
        if (track == null) {
            _lyricsState.value = LyricsUiState()
            return
        }

        lyricsFetchJob = viewModelScope.launch {
            // Step 1: Immediate 0ms local Room lookup
            val cachedLyrics = lyricsRepository.getCachedLyrics(track.id)
            if (cachedLyrics != null) {
                _lyricsState.value = LyricsUiState(isLoading = false, lyrics = cachedLyrics)
                // If cached lyrics are synced or instrumental, we have full fidelity; no remote query needed
                if (cachedLyrics.isSynced || (cachedLyrics.plainLyrics == null && cachedLyrics.lines.isEmpty())) {
                    return@launch
                }
            } else {
                _lyricsState.value = LyricsUiState(isLoading = true, lyrics = null)
            }

            // Step 2: Query multi-tier cascade (LRCLIB -> JioSaavn -> YouTube Captions)
            val result = lyricsRepository.getLyrics(
                trackId = track.id,
                title = track.title,
                artist = track.artist,
                album = null,
                durationMs = track.durationSeconds * 1000L,
                isNetworkAvailable = isOnline.value
            )

            result.fold(
                onSuccess = { lyricsData ->
                    _lyricsState.value = LyricsUiState(
                        isLoading = false,
                        lyrics = lyricsData ?: cachedLyrics,
                        error = if (lyricsData == null && cachedLyrics == null) "NO_LYRICS_FOUND" else null
                    )
                },
                onFailure = { error ->
                    _lyricsState.value = LyricsUiState(
                        isLoading = false,
                        lyrics = cachedLyrics,
                        error = if (cachedLyrics == null) (error.message ?: "TELEMETRY_SCAN_FAILED") else null
                    )
                }
            )
        }
    }

    fun retryFetchLyrics() {
        val currentTrack = playbackState.value.currentTrack ?: return
        lyricsFetchJob?.cancel()
        lyricsFetchJob = viewModelScope.launch {
            _lyricsState.value = LyricsUiState(isLoading = true, lyrics = null)
            val result = lyricsRepository.getLyrics(
                trackId = currentTrack.id,
                title = currentTrack.title,
                artist = currentTrack.artist,
                album = null,
                durationMs = currentTrack.durationSeconds * 1000L,
                isNetworkAvailable = isOnline.value
            )
            result.fold(
                onSuccess = { lyricsData ->
                    _lyricsState.value = LyricsUiState(
                        isLoading = false,
                        lyrics = lyricsData,
                        error = if (lyricsData == null) "NO_LYRICS_FOUND" else null
                    )
                },
                onFailure = { error ->
                    _lyricsState.value = LyricsUiState(
                        isLoading = false,
                        lyrics = null,
                        error = error.message ?: "TELEMETRY_SCAN_FAILED"
                    )
                }
            )
        }
    }

    fun playPause() = playbackConnectionManager.playPause()

    fun seekTo(positionMs: Long) = playbackConnectionManager.seekTo(positionMs)

    fun skipToNext() = playbackConnectionManager.skipToNext()

    fun skipToPrevious() = playbackConnectionManager.skipToPrevious()

    fun toggleShuffle() = playbackConnectionManager.toggleShuffle()

    fun toggleRepeat() = playbackConnectionManager.toggleRepeat()

    fun playNext(track: TrackEntity) = playbackConnectionManager.playNext(track)

    fun addToQueue(track: TrackEntity) = playbackConnectionManager.addToQueue(track)

    fun playTrackAtIndex(index: Int) = playbackConnectionManager.playTrackAtIndex(index)

    fun removeFromQueue(index: Int) = playbackConnectionManager.removeFromQueue(index)
    fun moveQueueItem(fromIndex: Int, toIndex: Int) = playbackConnectionManager.moveQueueItem(fromIndex, toIndex)
    fun refreshQueue() = playbackConnectionManager.refreshQueueRecommendations()

    fun startSleepTimer(minutes: Int) = playbackConnectionManager.startSleepTimer(minutes)

    fun cancelSleepTimer() = playbackConnectionManager.cancelSleepTimer()

    fun setCrossfadeSettings(enabled: Boolean, durationSeconds: Int = 4) =
        playbackConnectionManager.setCrossfadeSettings(enabled, durationSeconds)

    fun downloadCurrentTrack() {
        val currentTrack = playbackState.value.currentTrack ?: return
        downloadRepository.enqueueDownload(currentTrack)
    }

    fun checkForUpdates(isManual: Boolean = false) {
        viewModelScope.launch {
            updateManager.checkForUpdates(isManual = isManual)
        }
    }

    fun downloadAndInstallUpdate(downloadUrl: String) {
        viewModelScope.launch {
            updateManager.downloadAndInstallUpdate(downloadUrl)
        }
    }

    fun installDownloadedUpdate(filePath: String) {
        updateManager.triggerPackageInstaller(java.io.File(filePath))
    }

    fun dismissUpdate() {
        updateManager.dismissUpdate()
    }
}
