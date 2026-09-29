package com.satvik.satvikx.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satvik.satvikx.data.download.DownloadRepository
import com.satvik.satvikx.data.local.dao.PlaylistDao
import com.satvik.satvikx.data.local.dao.RecentPlaybackDao
import com.satvik.satvikx.data.local.dao.TrackDao
import com.satvik.satvikx.data.local.entity.PlaylistEntity
import com.satvik.satvikx.data.local.entity.PlaylistTrackCrossRef
import com.satvik.satvikx.data.local.entity.PlaylistWithTracks
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.storage.StorageManager
import com.satvik.satvikx.playback.PlaybackConnectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.satvik.satvikx.data.download.DownloadQualityManager
import com.satvik.satvikx.data.download.model.DownloadQuality
import javax.inject.Inject

data class StorageInfo(
    val usedStorageFormatted: String = "0 B",
    val availableStorageFormatted: String = "0 B",
    val usedBytes: Long = 0L,
    val availableBytes: Long = 0L,
    val usagePercentage: Float = 0f
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val recentPlaybackDao: RecentPlaybackDao,
    private val storageManager: StorageManager,
    private val downloadRepository: DownloadRepository,
    private val playbackConnectionManager: PlaybackConnectionManager,
    private val downloadQualityManager: DownloadQualityManager
) : ViewModel() {

    val downloadedTracks: StateFlow<List<TrackEntity>> = trackDao.getDownloadedTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTracks: StateFlow<List<TrackEntity>> = recentPlaybackDao.getRecentTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistWithTracks>> = playlistDao.getAllPlaylistsWithTracks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedTrackIds: StateFlow<Set<String>> = playlistDao.getLikedTrackIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val downloadQuality: StateFlow<DownloadQuality> = downloadQualityManager.currentQuality

    private val _storageInfo = MutableStateFlow(StorageInfo())
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()

    init {
        refreshStorageInfo()
    }

    fun refreshStorageInfo() {
        val used = storageManager.getUsedStorageBytes()
        val avail = storageManager.getAvailableDiskSpaceBytes()
        val total = used + avail
        val percentage = if (total > 0) (used.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
        _storageInfo.value = StorageInfo(
            usedStorageFormatted = storageManager.formatFileSize(used),
            availableStorageFormatted = storageManager.formatFileSize(avail),
            usedBytes = used,
            availableBytes = avail,
            usagePercentage = percentage
        )
    }

    fun setDownloadQuality(quality: DownloadQuality) {
        downloadQualityManager.setQuality(quality)
    }

    fun playTrack(track: TrackEntity, playlist: List<TrackEntity> = emptyList()) {
        playbackConnectionManager.playTrack(track, playlist)
    }

    fun downloadTrack(track: TrackEntity, quality: DownloadQuality? = null) {
        downloadRepository.enqueueDownload(track, quality)
    }

    fun downloadPlaylist(playlistWithTracks: PlaylistWithTracks, quality: DownloadQuality? = null) {
        downloadRepository.enqueuePlaylistDownload(playlistWithTracks.tracks, quality)
    }

    fun playPlaylist(playlistWithTracks: PlaylistWithTracks) {
        if (playlistWithTracks.tracks.isNotEmpty()) {
            playbackConnectionManager.playTrack(
                playlistWithTracks.tracks.first(),
                playlistWithTracks.tracks
            )
        }
    }

    fun playPlaylistShuffled(playlistWithTracks: PlaylistWithTracks) {
        if (playlistWithTracks.tracks.isNotEmpty()) {
            val shuffled = playlistWithTracks.tracks.shuffled()
            playbackConnectionManager.playTrack(
                shuffled.first(),
                shuffled
            )
        }
    }

    fun addTrackToQueue(track: TrackEntity) {
        playbackConnectionManager.addToQueue(track)
    }

    fun playNext(track: TrackEntity) {
        playbackConnectionManager.playNext(track)
    }

    fun deleteDownload(trackId: String) {
        viewModelScope.launch {
            downloadRepository.deleteDownload(trackId)
            refreshStorageInfo()
        }
    }

    fun playAllDownloads(shuffled: Boolean = false) {
        val tracks = downloadedTracks.value
        if (tracks.isNotEmpty()) {
            val list = if (shuffled) tracks.shuffled() else tracks
            playbackConnectionManager.playTrack(list.first(), list)
        }
    }

    fun playAllRecent(shuffled: Boolean = false) {
        val tracks = recentTracks.value
        if (tracks.isNotEmpty()) {
            val list = if (shuffled) tracks.shuffled() else tracks
            playbackConnectionManager.playTrack(list.first(), list)
        }
    }

    fun removeFromHistory(trackId: String) {
        viewModelScope.launch {
            recentPlaybackDao.deleteRecentById(trackId)
        }
    }

    fun clearRecentHistory() {
        viewModelScope.launch {
            recentPlaybackDao.clearHistory()
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            playlistDao.insertPlaylist(
                PlaylistEntity(name = name.trim())
            )
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistDao.deletePlaylistById(playlistId)
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            playlistDao.updatePlaylistName(playlistId, newName.trim())
        }
    }

    fun clearPlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistDao.clearPlaylistTracks(playlistId)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: TrackEntity) {
        viewModelScope.launch {
            trackDao.insertTrack(track)
            playlistDao.insertPlaylistTrackCrossRef(
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = track.id
                )
            )
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            playlistDao.deletePlaylistTrackCrossRef(playlistId, trackId)
        }
    }

    fun toggleFavorite(track: TrackEntity) {
        viewModelScope.launch {
            trackDao.insertTrack(track)
            var favPlaylist = playlistDao.getPlaylistByNameSync("Liked Songs")
            if (favPlaylist == null) {
                val newId = playlistDao.insertPlaylist(PlaylistEntity(name = "Liked Songs"))
                favPlaylist = PlaylistEntity(playlistId = newId, name = "Liked Songs")
            }
            val isAlreadyLiked = playlistDao.isTrackLikedSync(track.id)
            if (isAlreadyLiked) {
                playlistDao.deletePlaylistTrackCrossRef(favPlaylist.playlistId, track.id)
            } else {
                playlistDao.insertPlaylistTrackCrossRef(
                    PlaylistTrackCrossRef(
                        playlistId = favPlaylist.playlistId,
                        trackId = track.id
                    )
                )
            }
        }
    }
}
