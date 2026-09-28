package com.satvik.satvikx.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.satvik.satvikx.BuildConfig
import com.satvik.satvikx.updater.model.GitHubReleaseResponse
import com.satvik.satvikx.updater.model.UpdateState
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enterprise In-App Native Self-Updater for SatvikX.
 * Periodically queries GitHub Releases API for tag updates, streams the signed APK
 * into cache storage, and triggers the native Android PackageInstaller without requiring
 * rebuilding in Android Studio.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) {

    companion object {
        private const val TAG = "UpdateManager"
        private const val DEFAULT_OWNER = "satvikjoshi17"
        private const val DEFAULT_REPO = "Satvikx"
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val updatesDir: File
        get() {
            val dir = File(context.cacheDir, "updates")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    /**
     * Queries the latest release metadata from GitHub Releases API.
     */
    suspend fun checkForUpdates(
        owner: String = DEFAULT_OWNER,
        repo: String = DEFAULT_REPO,
        isManual: Boolean = false
    ) = withContext(Dispatchers.IO) {
        _updateState.value = UpdateState.Checking
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"

        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", "SatvikX-Updater/1.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                _updateState.value = UpdateState.Idle
                if (isManual) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "SatvikX is up to date (v${BuildConfig.VERSION_NAME})",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                return@withContext
            }

            val json = response.body!!.string()
            val adapter = moshi.adapter(GitHubReleaseResponse::class.java)
            val release = adapter.fromJson(json)

            if (release != null && !release.tagName.isNullOrBlank()) {
                val latestTag = release.tagName.removePrefix("v").trim()
                val currentVersion = BuildConfig.VERSION_NAME.removePrefix("v").trim()

                if (isNewerVersion(latestTag, currentVersion)) {
                    val apkAsset = release.assets?.firstOrNull { it.name?.endsWith(".apk") == true }
                    if (apkAsset?.browserDownloadUrl != null) {
                        _updateState.value = UpdateState.Available(
                            newVersion = latestTag,
                            releaseNotes = release.body ?: "Bug fixes and performance improvements.",
                            downloadUrl = apkAsset.browserDownloadUrl,
                            apkSize = apkAsset.size ?: 0L
                        )
                        return@withContext
                    }
                }
            }

            _updateState.value = UpdateState.Idle
            if (isManual) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "SatvikX is up to date (v${BuildConfig.VERSION_NAME})",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed: ${e.message}", e)
            _updateState.value = UpdateState.Idle
            if (isManual) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "Check for updates: SatvikX v${BuildConfig.VERSION_NAME}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * Downloads the APK file with real-time progress updates and triggers PackageInstaller.
     */
    suspend fun downloadAndInstallUpdate(downloadUrl: String) = withContext(Dispatchers.IO) {
        val apkFile = File(updatesDir, "SatvikX_update.apk")
        if (apkFile.exists()) {
            apkFile.delete()
        }

        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .addHeader("User-Agent", "SatvikX-Updater/1.0")
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful || response.body == null) {
                _updateState.value = UpdateState.Error("Failed to download APK from server.")
                return@withContext
            }

            val body = response.body!!
            val totalBytes = body.contentLength()
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(apkFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var downloadedBytes = 0L

            inputStream.use { input ->
                outputStream.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalBytes > 0) {
                            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        _updateState.value = UpdateState.Downloading(
                            progress = progress,
                            bytesDownloaded = downloadedBytes,
                            totalBytes = totalBytes
                        )
                    }
                    output.flush()
                }
            }

            _updateState.value = UpdateState.ReadyToInstall(apkFile.absolutePath)
            triggerPackageInstaller(apkFile)

        } catch (e: Exception) {
            Log.e(TAG, "APK download failed: ${e.message}", e)
            _updateState.value = UpdateState.Error(e.message ?: "Download interrupted.")
        }
    }

    /**
     * Launches Android PackageInstaller Intent via FileProvider.
     */
    fun triggerPackageInstaller(apkFile: File) {
        if (!apkFile.exists()) return

        // On Android 8.0+ verify package install permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        context.startActivity(installIntent)
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    /**
     * SemVer string comparison logic (e.g. 1.1.0 > 1.0.0).
     */
    private fun isNewerVersion(remote: String, local: String): Boolean {
        return try {
            val remoteParts = remote.split(".").map { it.filter { char -> char.isDigit() }.toIntOrNull() ?: 0 }
            val localParts = local.split(".").map { it.filter { char -> char.isDigit() }.toIntOrNull() ?: 0 }

            val length = maxOf(remoteParts.size, localParts.size)
            for (i in 0 until length) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}
