package com.satvik.satvikx.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satvik.satvikx.data.download.DownloadRepository
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.remote.network.NetworkMonitor
import com.satvik.satvikx.playback.PlaybackConnectionManager
import com.satvik.satvikx.playback.model.PlaybackState
import com.satvik.satvikx.updater.UpdateManager
import com.satvik.satvikx.updater.model.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackConnectionManager: PlaybackConnectionManager,
    private val downloadRepository: DownloadRepository,
    private val updateManager: UpdateManager,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playbackConnectionManager.playbackState
    val updateState: StateFlow<UpdateState> = updateManager.updateState
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    init {
        // Automatically check for GitHub releases in the background on app start
        viewModelScope.launch {
            updateManager.checkForUpdates()
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

    fun startSleepTimer(minutes: Int) = playbackConnectionManager.startSleepTimer(minutes)

    fun cancelSleepTimer() = playbackConnectionManager.cancelSleepTimer()

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
