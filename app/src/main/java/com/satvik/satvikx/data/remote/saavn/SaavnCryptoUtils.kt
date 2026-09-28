package com.satvik.satvikx.data.remote.saavn

import android.util.Base64
import android.util.Log
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Enterprise client-side cryptographic deciphering utility for JioSaavn CDN audio streams.
 * Performs hardware-accelerated DES/ECB/PKCS5Padding decryption of encrypted media keys
 * to produce direct unthrottled high-bitrate (320kbps / 160kbps) Akamai / Azure Blob CDN URLs.
 */
object SaavnCryptoUtils {

    private const val TAG = "SaavnCryptoUtils"
    private const val DES_SECRET_KEY = "38346591"

    /**
     * Decrypts an encrypted media token into a direct CDN audio stream URL.
     * Optionally adjusts stream quality to 320 kbps or 160 kbps.
     */
    fun decryptMediaUrl(encryptedUrl: String?, targetBitrate: Int = 320): String? {
        if (encryptedUrl.isNullOrBlank()) return null
        return try {
            val keyBytes = DES_SECRET_KEY.toByteArray(StandardCharsets.UTF_8)
            val secretKey = SecretKeySpec(keyBytes, "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)

            val decodedBytes = Base64.decode(encryptedUrl.trim(), Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            val baseDecryptedUrl = String(decryptedBytes, StandardCharsets.UTF_8).trim()

            // Promote quality from default preview (_96.mp4) to high-fidelity stream
            val enhancedUrl = when (targetBitrate) {
                320 -> baseDecryptedUrl.replace("_96.mp4", "_320.mp4")
                160 -> baseDecryptedUrl.replace("_96.mp4", "_160.mp4")
                else -> baseDecryptedUrl
            }

            enhancedUrl
        } catch (e: Exception) {
            Log.w(TAG, "Decryption error for token: ${e.message}")
            null
        }
    }

    /**
     * Replaces low-resolution thumbnail URLs with high-definition 500x500 album art.
     */
    fun cleanAlbumArt(imageUrl: String?): String {
        if (imageUrl.isNullOrBlank()) return ""
        return imageUrl
            .replace("150x150", "500x500")
            .replace("50x50", "500x500")
            .replace("http://", "https://")
    }

    /**
     * Unescapes HTML entities commonly returned in track titles and artist names.
     */
    fun unescapeHtml(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&copy;", "")
            .trim()
    }
}
