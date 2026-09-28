package com.satvik.satvikx.data.remote.network

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Enterprise Resilience & Failover Interceptor.
 * Automatically recovers from dropped cellular/Wi-Fi transitions, socket timeouts,
 * and rate-limiting by applying exponential backoff before throwing exceptions.
 */
class NetworkRecoveryInterceptor(
    private val maxRetries: Int = 3
) : Interceptor {

    companion object {
        private const val TAG = "NetworkRecovery"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        var lastException: IOException? = null

        while (attempt < maxRetries) {
            try {
                val response = chain.proceed(request)

                // If remote server returns 429 (Too Many Requests) or 503 (Unavailable), retry briefly
                if (response.code == 429 || response.code == 503) {
                    response.close()
                    attempt++
                    val backoffMs = (300L * (1 shl attempt)).coerceAtMost(3000L)
                    Log.w(TAG, "Server returned ${response.code}. Retry attempt $attempt in ${backoffMs}ms")
                    Thread.sleep(backoffMs)
                    continue
                }

                return response

            } catch (e: IOException) {
                lastException = e
                attempt++
                if (attempt >= maxRetries) {
                    break
                }

                val backoffMs = (300L * (1 shl attempt)).coerceAtMost(3000L)
                Log.w(TAG, "Network glitch (${e::class.java.simpleName}). Retry attempt $attempt in ${backoffMs}ms: ${e.message}")
                try {
                    Thread.sleep(backoffMs)
                } catch (ignored: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
        }

        throw lastException ?: IOException("Request failed after $maxRetries attempts")
    }
}
