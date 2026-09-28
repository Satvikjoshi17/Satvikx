package com.satvik.satvikx.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.satvik.satvikx.data.local.dao.TrackDao
import com.satvik.satvikx.data.local.entity.TrackEntity
import com.satvik.satvikx.data.local.storage.StorageManager
import com.satvik.satvikx.data.repository.StreamRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.io.IOException

/**
 * Enterprise Jetpack WorkManager CoroutineWorker.
 * Performs direct streaming download from CDN chunks to Scoped Storage,
 * emitting progress to both notification and UI StateFlow, surviving app termination.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted private val params: WorkerParameters,
    private val okHttpClient: OkHttpClient,
    private val storageManager: StorageManager,
    private val trackDao: TrackDao,
    private val streamRepository: StreamRepository
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_TRACK_ID = "key_track_id"
        const val KEY_TITLE = "key_title"
        const val KEY_ARTIST = "key_artist"
        const val KEY_THUMBNAIL_URL = "key_thumbnail_url"
        const val KEY_STREAM_URL = "key_stream_url"
        const val KEY_PROGRESS = "key_progress"
        const val KEY_BYTES_DOWNLOADED = "key_bytes_downloaded"
        const val KEY_TOTAL_BYTES = "key_total_bytes"
        const val KEY_LOCAL_PATH = "key_local_path"

        private const val CHANNEL_ID = "satvikx_download_channel"
        private const val CHANNEL_NAME = "SatvikX Downloads"
    }

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val trackId = inputData.getString(KEY_TRACK_ID) ?: return@withContext Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Audio Track"
        val artist = inputData.getString(KEY_ARTIST) ?: "Unknown Artist"
        val thumbnailUrl = inputData.getString(KEY_THUMBNAIL_URL).orEmpty()
        var streamUrl = inputData.getString(KEY_STREAM_URL)

        createNotificationChannel()

        val notificationId = trackId.hashCode()
        try {
            notificationManager.notify(notificationId, buildNotification(title, artist, 0f))
        } catch (e: Exception) {
            // Notifications may be restricted by runtime permission, safely ignore
        }

        // 1. Resolve direct audio stream if not supplied
        if (streamUrl.isNullOrBlank()) {
            val streamResult = streamRepository.resolveAudioStream(trackId).firstOrNull()?.getOrNull()
            if (streamResult == null || streamResult.streamUrl.isBlank()) {
                return@withContext Result.failure()
            }
            streamUrl = streamResult.streamUrl
        }

        val tempFile = storageManager.getTemporaryDownloadFile(trackId)
        val targetFile = storageManager.getTrackAudioFile(trackId)

        try {
            val request = Request.Builder()
                .url(streamUrl)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                return@withContext Result.retry()
            }

            val body = response.body!!
            val totalBytes = body.contentLength()
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var downloadedBytes = 0L
            var lastUpdateTimestamp = 0L

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isStopped) {
                            tempFile.delete()
                            return@withContext Result.failure()
                        }

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdateTimestamp >= 400 || downloadedBytes == totalBytes) {
                            lastUpdateTimestamp = currentTime
                            val progress = if (totalBytes > 0) {
                                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }

                            setProgress(
                                workDataOf(
                                    KEY_PROGRESS to progress,
                                    KEY_BYTES_DOWNLOADED to downloadedBytes,
                                    KEY_TOTAL_BYTES to totalBytes
                                )
                            )

                            try {
                                notificationManager.notify(
                                    notificationId,
                                    buildNotification(title, artist, progress)
                                )
                            } catch (e: Exception) {
                                // Ignore notification permission issues
                            }
                        }
                    }
                    output.flush()
                }
            }

            // 2. Commit downloaded file to scoped storage
            val success = storageManager.commitDownloadedFile(tempFile, targetFile)
            if (!success) {
                tempFile.delete()
                return@withContext Result.failure()
            }

            // 3. Update Room database entity
            val existing = trackDao.getTrackByIdSync(trackId)
            val updated = (existing ?: TrackEntity(
                id = trackId,
                title = title,
                artist = artist,
                durationSeconds = 0L,
                thumbnailUrl = thumbnailUrl
            )).copy(
                isDownloaded = true,
                localPath = targetFile.absolutePath
            )
            trackDao.insertTrack(updated)

            // 4. Show completed notification briefly
            showCompletedNotification(notificationId, title, artist)

            return@withContext Result.success(
                workDataOf(
                    KEY_TRACK_ID to trackId,
                    KEY_LOCAL_PATH to targetFile.absolutePath
                )
            )

        } catch (e: Exception) {
            tempFile.delete()
            return@withContext if (e is IOException) Result.retry() else Result.failure()
        }
    }

    private fun buildNotification(title: String, artist: String, progress: Float) =
        NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("Downloading: $title")
            .setContentText(artist)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, (progress * 100).toInt(), progress <= 0f)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun showCompletedNotification(notificationId: Int, title: String, artist: String) {
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("Downloaded: $title")
            .setContentText("$artist • Saved to Music")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
        } catch (e: Exception) {
            // Notifications may be restricted by runtime permission, safely ignore
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for offline music downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
