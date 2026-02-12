package com.kennyandries.ringtonesetter.download

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.OutputStream

class RingtoneDownloader(private val client: OkHttpClient) {

    data class DownloadResult(val mimeType: String, val bytesWritten: Long)

    /**
     * Downloads the ringtone from [url] and streams it directly into [outputStream].
     * Returns the MIME type from the Content-Type header and the number of bytes written.
     */
    fun download(url: String, outputStream: OutputStream): DownloadResult {
        val request = Request.Builder().url(url).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed: HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("Download failed: empty response body")
            val mimeType = body.contentType()?.let { "${it.type}/${it.subtype}" }
                ?: "audio/mpeg"

            val bytesWritten = body.byteStream().use { inputStream ->
                var total = 0L
                val buffer = ByteArray(8192)
                while (true) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    outputStream.write(buffer, 0, read)
                    total += read
                }
                total
            }

            outputStream.flush()
            DownloadResult(mimeType, bytesWritten)
        }
    }
}
