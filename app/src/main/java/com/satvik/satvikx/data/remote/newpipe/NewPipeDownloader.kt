package com.satvik.satvikx.data.remote.newpipe

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException

/**
 * Enterprise client-side Downloader bridging NewPipeExtractor with OkHttpClient.
 * Handles HTTP requests, headers, and cipher responses for native YouTube scraping without cookies.
 */
class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val reqBuilder = okhttp3.Request.Builder().url(url)

        headers.forEach { (name, values) ->
            values.forEach { value ->
                reqBuilder.addHeader(name, value)
            }
        }

        if (httpMethod.equals("POST", ignoreCase = true)) {
            val body = dataToSend?.toRequestBody() ?: ByteArray(0).toRequestBody()
            reqBuilder.post(body)
        } else if (httpMethod.equals("HEAD", ignoreCase = true)) {
            reqBuilder.head()
        } else {
            reqBuilder.get()
        }

        val response = client.newCall(reqBuilder.build()).execute()
        val responseBody = response.body?.string().orEmpty()
        val responseHeaders = mutableMapOf<String, List<String>>()
        response.headers.names().forEach { name ->
            responseHeaders[name] = response.headers.values(name)
        }

        return Response(
            response.code,
            response.message,
            responseHeaders,
            responseBody,
            response.request.url.toString()
        )
    }
}
