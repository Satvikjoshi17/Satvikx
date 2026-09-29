package com.satvik.satvikx.data.download.model

enum class DownloadQuality(
    val key: String,
    val label: String,
    val bitrateKbps: Int,
    val description: String,
    val badge: String,
    val approxMbPerSong: Double
) {
    SAVER(
        key = "saver",
        label = "Space Saver (96 kbps)",
        bitrateKbps = 96,
        description = "Uses ~70% less storage (~2 MB / song)",
        badge = "ECO",
        approxMbPerSong = 2.0
    ),
    STANDARD(
        key = "standard",
        label = "Standard (160 kbps)",
        bitrateKbps = 160,
        description = "Optimal balance of quality & size (~4 MB / song)",
        badge = "BALANCED",
        approxMbPerSong = 4.0
    ),
    HIGH(
        key = "high",
        label = "High Fidelity (320 kbps)",
        bitrateKbps = 320,
        description = "Studio master sound quality (~8 MB / song)",
        badge = "HQ",
        approxMbPerSong = 8.0
    );

    companion object {
        fun fromKey(key: String?): DownloadQuality {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: STANDARD
        }
    }
}
