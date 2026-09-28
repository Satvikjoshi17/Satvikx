package com.satvik.satvikx.data.local.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.text.DecimalFormat
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.pow

/**
 * Enterprise Scoped Storage Manager for SatvikX.
 * Persists downloaded audio files into Context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
 * to ensure files are never evicted by Android system cache sweepers while adhering to scoped storage.
 */
@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val musicDir: File
        get() {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: File(context.filesDir, "Music")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    private val tempDir: File
        get() {
            val dir = File(context.cacheDir, "audio_temp")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    /**
     * Sanitizes file names to prevent directory traversal and invalid filesystem characters.
     */
    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    }

    /**
     * Returns the permanent storage destination File for an audio track.
     */
    fun getTrackAudioFile(trackId: String, extension: String = "m4a"): File {
        val safeId = sanitizeFileName(trackId)
        val ext = extension.removePrefix(".")
        return File(musicDir, "$safeId.$ext")
    }

    /**
     * Returns a temporary file for chunked downloading before atomic commit.
     */
    fun getTemporaryDownloadFile(trackId: String): File {
        val safeId = sanitizeFileName(trackId)
        return File(tempDir, "${safeId}_part.tmp")
    }

    /**
     * Atomically moves a completed temporary download file to the permanent music destination.
     */
    @Throws(IOException::class)
    fun commitDownloadedFile(tempFile: File, targetFile: File): Boolean {
        if (!tempFile.exists() || tempFile.length() == 0L) {
            return false
        }
        if (targetFile.exists()) {
            targetFile.delete()
        }
        val renamed = tempFile.renameTo(targetFile)
        if (!renamed) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
        return targetFile.exists() && targetFile.length() > 0L
    }

    /**
     * Verifies if a given track ID exists and has non-zero size in the music storage.
     */
    fun isAudioDownloaded(trackId: String): Boolean {
        val file = getTrackAudioFile(trackId)
        return file.exists() && file.length() > 0L
    }

    /**
     * Deletes the audio file associated with a track ID.
     */
    fun deleteTrackAudio(trackId: String): Boolean {
        val file = getTrackAudioFile(trackId)
        var deleted = true
        if (file.exists()) {
            deleted = file.delete()
        }
        val temp = getTemporaryDownloadFile(trackId)
        if (temp.exists()) {
            temp.delete()
        }
        return deleted
    }

    /**
     * Calculates the total bytes consumed by downloaded audio tracks.
     */
    fun getUsedStorageBytes(): Long {
        var totalSize = 0L
        val files = musicDir.listFiles() ?: return 0L
        for (file in files) {
            if (file.isFile) {
                totalSize += file.length()
            }
        }
        return totalSize
    }

    /**
     * Returns the available disk space in bytes on the storage partition.
     */
    fun getAvailableDiskSpaceBytes(): Long {
        return try {
            val stat = StatFs(musicDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Utility method to format raw byte count into human-readable representation.
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        val index = digitGroups.coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(index.toDouble())
        return DecimalFormat("#,##0.#").format(value) + " " + units[index]
    }
}
