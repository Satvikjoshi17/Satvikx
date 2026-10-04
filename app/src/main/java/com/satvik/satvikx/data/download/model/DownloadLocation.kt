package com.satvik.satvikx.data.download.model

/**
 * Supported storage destination locations for downloaded audio tracks in SatvikX.
 */
enum class DownloadLocation(
    val key: String,
    val title: String,
    val subtitle: String,
    val pathDisplay: String,
    val badge: String
) {
    PUBLIC_MUSIC(
        key = "public_music",
        title = "Public Music Folder",
        subtitle = "Directly accessible in your phone's Music directory and recognized by all music players & file managers.",
        pathDisplay = "Internal Storage/Music/SatvikX",
        badge = "ACCESSIBLE"
    ),
    PUBLIC_DOWNLOADS(
        key = "public_downloads",
        title = "Public Downloads Folder",
        subtitle = "Saved directly into your main Downloads folder for instant browsing and USB transfer.",
        pathDisplay = "Internal Storage/Download/SatvikX",
        badge = "DIRECT"
    ),
    APP_INTERNAL(
        key = "internal",
        title = "App Vault (Private)",
        subtitle = "Protected scoped storage inside Android/data. Hidden from other apps and cleaned up on uninstall.",
        pathDisplay = "Android/data/com.satvik.satvikx/files/Music",
        badge = "VAULT"
    );

    companion object {
        fun fromKey(key: String?): DownloadLocation {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: PUBLIC_MUSIC
        }
    }
}
