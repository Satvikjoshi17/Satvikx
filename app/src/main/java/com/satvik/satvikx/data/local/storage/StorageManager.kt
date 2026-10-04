package com.satvik.satvikx.data.local.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.satvik.satvikx.data.download.DownloadLocationManager
import com.satvik.satvikx.data.download.model.DownloadLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.text.DecimalFormat
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.pow

/**
 * Enterprise Storage Manager for SatvikX.
 * Supports configurable storage destinations:
 * - App Vault: Context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
 * - Public Music Folder: /Music/SatvikX (via MediaStore / Scoped Storage)
 * - Public Downloads Folder: /Download/SatvikX (via MediaStore / Scoped Storage)
 */
@Singleton
class StorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadLocationManager: DownloadLocationManager
) {

    val musicDir: File
        get() {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                ?: File(context.filesDir, "Music")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    val publicMusicDir: File
        get() {
            val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val dir = File(base, "SatvikX")
            if (!dir.exists()) {
                try { dir.mkdirs() } catch (_: Exception) {}
            }
            return dir
        }

    val publicDownloadsDir: File
        get() {
            val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(base, "SatvikX")
            if (!dir.exists()) {
                try { dir.mkdirs() } catch (_: Exception) {}
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
        return name.replace(Regex("[^a-zA-Z0-9._\\-\\s]"), "_").trim()
    }

    /**
     * Finds an existing audio file for the track across all storage directories.
     */
    fun findAudioFile(trackId: String): File? {
        val safeId = sanitizeFileName(trackId)
        val filter = { file: File ->
            val name = file.name
            (name.startsWith(safeId) || name.contains("[$safeId]")) && !name.endsWith(".tmp") && file.length() > 0L
        }

        // 1. Check App Vault
        val inVault = musicDir.listFiles()?.firstOrNull(filter)
        if (inVault != null) return inVault

        // 2. Check Public Music
        if (publicMusicDir.exists()) {
            val inPublicMusic = publicMusicDir.listFiles()?.firstOrNull(filter)
            if (inPublicMusic != null) return inPublicMusic
        }

        // 3. Check Public Downloads
        if (publicDownloadsDir.exists()) {
            val inPublicDownloads = publicDownloadsDir.listFiles()?.firstOrNull(filter)
            if (inPublicDownloads != null) return inPublicDownloads
        }

        return null
    }

    /**
     * Returns the permanent storage destination File for an audio track.
     * If a file already exists for this track in any supported location, it is returned.
     */
    fun getTrackAudioFile(trackId: String, extension: String = "m4a"): File {
        val existing = findAudioFile(trackId)
        if (existing != null) return existing

        val safeId = sanitizeFileName(trackId)
        val ext = extension.removePrefix(".")
        val targetDir = when (downloadLocationManager.getLocation()) {
            DownloadLocation.APP_INTERNAL -> musicDir
            DownloadLocation.PUBLIC_MUSIC -> publicMusicDir
            DownloadLocation.PUBLIC_DOWNLOADS -> publicDownloadsDir
        }
        return File(targetDir, "$safeId.$ext")
    }

    /**
     * Returns a temporary file for chunked downloading before atomic commit.
     */
    fun getTemporaryDownloadFile(trackId: String): File {
        val safeId = sanitizeFileName(trackId)
        return File(tempDir, "${safeId}_part.tmp")
    }

    /**
     * Atomically moves a completed temporary download file to the target File.
     */
    @Throws(IOException::class)
    fun commitDownloadedFile(tempFile: File, targetFile: File): Boolean {
        if (!tempFile.exists() || tempFile.length() == 0L) {
            return false
        }
        if (targetFile.exists()) {
            targetFile.delete()
        }
        val targetParent = targetFile.parentFile
        if (targetParent != null && !targetParent.exists()) {
            targetParent.mkdirs()
        }
        val renamed = tempFile.renameTo(targetFile)
        if (!renamed) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }
        return targetFile.exists() && targetFile.length() > 0L
    }

    /**
     * Commits a completed downloaded track to the user-chosen location
     * (App Vault, Public Music, or Public Downloads).
     */
    fun commitDownloadedTrack(
        tempFile: File,
        trackId: String,
        title: String,
        artist: String,
        location: DownloadLocation = downloadLocationManager.getLocation()
    ): File? {
        if (!tempFile.exists() || tempFile.length() == 0L) return null

        val safeId = sanitizeFileName(trackId)
        val cleanTitle = sanitizeFileName(title.ifBlank { "Track" }).take(50)
        val cleanArtist = sanitizeFileName(artist.ifBlank { "Unknown" }).take(30)
        val userFriendlyFileName = "$cleanTitle - $cleanArtist [$safeId].m4a"

        return when (location) {
            DownloadLocation.APP_INTERNAL -> {
                val targetFile = File(musicDir, "$safeId.m4a")
                val success = commitDownloadedFile(tempFile, targetFile)
                if (success) targetFile else null
            }
            DownloadLocation.PUBLIC_MUSIC -> {
                commitToPublicStorage(
                    tempFile = tempFile,
                    fileName = userFriendlyFileName,
                    title = title,
                    artist = artist,
                    isDownloadsFolder = false,
                    trackId = safeId
                )
            }
            DownloadLocation.PUBLIC_DOWNLOADS -> {
                commitToPublicStorage(
                    tempFile = tempFile,
                    fileName = userFriendlyFileName,
                    title = title,
                    artist = artist,
                    isDownloadsFolder = true,
                    trackId = safeId
                )
            }
        }
    }

    private fun commitToPublicStorage(
        tempFile: File,
        fileName: String,
        title: String,
        artist: String,
        isDownloadsFolder: Boolean,
        trackId: String
    ): File? {
        val resolver = context.contentResolver
        val relativeSubDir = if (isDownloadsFolder) "Download/SatvikX" else "Music/SatvikX"
        val fallbackDir = if (isDownloadsFolder) publicDownloadsDir else publicMusicDir

        // Android 10+ (API 29+) Scoped Storage MediaStore insertion
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentUri = if (isDownloadsFolder) {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            // Remove any existing entry with the same display name to avoid duplicates
            try {
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(fileName)
                resolver.delete(contentUri, selection, selectionArgs)
            } catch (_: Exception) {}

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeSubDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                if (!isDownloadsFolder) {
                    put(MediaStore.Audio.Media.TITLE, title)
                    put(MediaStore.Audio.Media.ARTIST, artist)
                }
            }

            try {
                val itemUri = resolver.insert(contentUri, values)
                if (itemUri != null) {
                    resolver.openOutputStream(itemUri)?.use { out ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(itemUri, values, null, null)

                    val targetFile = File(fallbackDir, fileName)
                    tempFile.delete()

                    // Trigger media scan so file managers update immediately
                    MediaScannerConnection.scanFile(
                        context,
                        arrayOf(targetFile.absolutePath),
                        arrayOf("audio/mp4"),
                        null
                    )
                    return targetFile
                }
            } catch (e: Exception) {
                android.util.Log.e("StorageManager", "MediaStore insertion failed, falling back to direct write: ${e.message}")
            }
        }

        // Direct File write fallback (for Android 9 or if MediaStore fails)
        try {
            if (!fallbackDir.exists()) fallbackDir.mkdirs()
            val targetFile = File(fallbackDir, fileName)
            if (commitDownloadedFile(tempFile, targetFile)) {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("audio/mp4"),
                    null
                )
                return targetFile
            }
        } catch (e: Exception) {
            android.util.Log.e("StorageManager", "Direct write failed: ${e.message}")
        }

        // Ultimate fallback to App Vault so download is never lost
        val internalFile = File(musicDir, "$trackId.m4a")
        return if (commitDownloadedFile(tempFile, internalFile)) internalFile else null
    }

    /**
     * Verifies if a given track ID exists and has non-zero size in any storage location.
     */
    fun isAudioDownloaded(trackId: String): Boolean {
        return findAudioFile(trackId) != null
    }

    /**
     * Deletes the audio file associated with a track ID across all extension variants,
     * storage directories, and MediaStore.
     */
    fun deleteTrackAudio(trackId: String): Boolean {
        val safeId = sanitizeFileName(trackId)
        var anyDeleted = false

        val matcher = { name: String ->
            name.startsWith(safeId) || name.contains("[$safeId]")
        }

        val dirs = listOf(musicDir, publicMusicDir, publicDownloadsDir, tempDir)
        for (dir in dirs) {
            if (dir.exists()) {
                dir.listFiles { _, name -> matcher(name) }?.forEach { file ->
                    if (file.delete()) anyDeleted = true
                }
            }
        }

        // Remove from MediaStore if registered
        try {
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$safeId%")
            context.contentResolver.delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, selection, selectionArgs)
            }
        } catch (_: Exception) {}

        return anyDeleted
    }

    /**
     * Calculates the total bytes consumed by downloaded audio tracks across all storage locations.
     */
    fun getUsedStorageBytes(): Long {
        var totalSize = 0L
        val dirs = listOf(musicDir, publicMusicDir, publicDownloadsDir)
        for (dir in dirs) {
            if (dir.exists()) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && !file.name.endsWith(".tmp")) {
                        totalSize += file.length()
                    }
                }
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
