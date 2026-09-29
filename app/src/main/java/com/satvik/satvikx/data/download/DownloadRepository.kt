package com.satvik.satvikx.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.satvik.satvikx.data.download.model.DownloadState
import com.satvik.satvikx.data.download.model.DownloadStatus
import com.satvik.satvikx.data.local.dao.TrackDao
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import com.satvik.satvikx.data.download.model.DownloadQuality
import javax.inject.Singleton

interface DownloadRepository {
    fun enqueueDownload(track: TrackEntity, quality: DownloadQuality? = null)
    fun enqueuePlaylistDownload(tracks: List<TrackEntity>, quality: DownloadQuality? = null)
    fun cancelDownload(trackId: String)
    suspend fun deleteDownload(trackId: String)
    fun observeTrackDownload(trackId: String): Flow<DownloadState>
    fun observeAllDownloads(): Flow<List<DownloadState>>
}

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao,
    private val storageManager: StorageManager,
    private val downloadQualityManager: DownloadQualityManager
) : DownloadRepository {

    companion object {
        const val TAG_DOWNLOAD = "satvikx_download_tag"
    }

    private val workManager by lazy { WorkManager.getInstance(context) }

    override fun enqueueDownload(track: TrackEntity, quality: DownloadQuality?) {
        try {
            val targetQuality = quality ?: downloadQualityManager.getQuality()
            val inputData = workDataOf(
                DownloadWorker.KEY_TRACK_ID to track.id,
                DownloadWorker.KEY_TITLE to track.title,
                DownloadWorker.KEY_ARTIST to track.artist,
                DownloadWorker.KEY_THUMBNAIL_URL to track.thumbnailUrl,
                DownloadWorker.KEY_STREAM_URL to (track.streamUrl ?: ""),
                DownloadWorker.KEY_QUALITY to targetQuality.key
            )

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(TAG_DOWNLOAD)
                .addTag("track_${track.id}")
                .build()

            workManager.enqueueUniqueWork(
                "download_${track.id}",
                ExistingWorkPolicy.KEEP,
                downloadRequest
            )
        } catch (e: Exception) {
            android.util.Log.e("DownloadRepository", "Failed to enqueue download for ${track.id}: ${e.message}", e)
        }
    }

    override fun enqueuePlaylistDownload(tracks: List<TrackEntity>, quality: DownloadQuality?) {
        val targetQuality = quality ?: downloadQualityManager.getQuality()
        tracks.forEach { track ->
            if (!storageManager.isAudioDownloaded(track.id)) {
                enqueueDownload(track, targetQuality)
            }
        }
    }

    override fun cancelDownload(trackId: String) {
        workManager.cancelUniqueWork("download_$trackId")
    }

    override suspend fun deleteDownload(trackId: String) {
        cancelDownload(trackId)
        storageManager.deleteTrackAudio(trackId)
        trackDao.updateDownloadStatus(trackId, isDownloaded = false, localPath = null)
    }

    override fun observeTrackDownload(trackId: String): Flow<DownloadState> {
        return workManager.getWorkInfosForUniqueWorkFlow("download_$trackId")
            .map { workInfos ->
                val workInfo = workInfos.firstOrNull()
                mapWorkInfoToState(trackId, workInfo)
            }
    }

    override fun observeAllDownloads(): Flow<List<DownloadState>> {
        return workManager.getWorkInfosByTagFlow(TAG_DOWNLOAD)
            .map { list ->
                list.map { workInfo ->
                    val trackId = workInfo.tags.firstOrNull { it.startsWith("track_") }?.removePrefix("track_") ?: ""
                    mapWorkInfoToState(trackId, workInfo)
                }
            }
    }

    private fun mapWorkInfoToState(trackId: String, workInfo: WorkInfo?): DownloadState {
        if (workInfo == null) {
            val isDownloaded = storageManager.isAudioDownloaded(trackId)
            return DownloadState(
                trackId = trackId,
                status = if (isDownloaded) DownloadStatus.COMPLETED else DownloadStatus.IDLE,
                progress = if (isDownloaded) 1f else 0f
            )
        }

        val progress = workInfo.progress.getFloat(DownloadWorker.KEY_PROGRESS, 0f)
        val bytes = workInfo.progress.getLong(DownloadWorker.KEY_BYTES_DOWNLOADED, 0L)
        val total = workInfo.progress.getLong(DownloadWorker.KEY_TOTAL_BYTES, 0L)

        val status = when (workInfo.state) {
            WorkInfo.State.ENQUEUED -> DownloadStatus.QUEUED
            WorkInfo.State.RUNNING -> DownloadStatus.DOWNLOADING
            WorkInfo.State.SUCCEEDED -> DownloadStatus.COMPLETED
            WorkInfo.State.FAILED -> DownloadStatus.FAILED
            WorkInfo.State.CANCELLED -> DownloadStatus.CANCELLED
            WorkInfo.State.BLOCKED -> DownloadStatus.QUEUED
        }

        return DownloadState(
            trackId = trackId,
            progress = if (status == DownloadStatus.COMPLETED) 1f else progress,
            bytesDownloaded = bytes,
            totalBytes = total,
            status = status
        )
    }
}
