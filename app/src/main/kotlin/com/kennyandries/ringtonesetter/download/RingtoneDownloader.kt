package com.kennyandries.ringtonesetter.download

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.OutputStream

class RingtoneDownloader(private val client: OkHttpClient) {

    data class DownloadResult(val mimeType: String, val bytesWritten: Long)

    companion object {
        private const val TAG = "RingtoneDownloader"
        const val MAX_RINGTONE_BYTES = 10L * 1024 * 1024 // 10 MB
        private val ALLOWED_MIME_TYPES = setOf(
            "audio/mpeg", "audio/mp3", "audio/ogg", "audio/wav",
            "audio/aac", "audio/flac", "audio/x-wav", "audio/mp4",
            "application/ogg", "application/octet-stream",
        )
    }

    /**
     * Downloads the ringtone from [url] and streams it directly into [outputStream].
     * Enforces a maximum size of [MAX_RINGTONE_BYTES] and validates Content-Type is audio.
     * [isCancelled] is polled during the download loop to support cooperative cancellation.
     */
    fun download(
        url: String,
        outputStream: OutputStream,
        isCancelled: () -> Boolean = { false },
    ): DownloadResult {
        val request = Request.Builder().url(url).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed: HTTP ${response.code}")
            }

            val body = response.body ?: throw IOException("Download failed: empty response body")
            val mimeType = body.contentType()?.let { "${it.type}/${it.subtype}" }
                ?: "audio/mpeg"

            if (mimeType !in ALLOWED_MIME_TYPES) {
                throw IOException("Unsupported content type: $mimeType")
            }

            val contentLength = body.contentLength()
            if (contentLength > MAX_RINGTONE_BYTES) {
                throw IOException(
                    "Ringtone too large: ${contentLength / 1024}KB exceeds ${MAX_RINGTONE_BYTES / 1024}KB limit"
                )
            }

            Log.d(TAG, "Downloading ringtone: contentType=$mimeType, contentLength=$contentLength")

            val bytesWritten = body.byteStream().use { inputStream ->
                var total = 0L
                val buffer = ByteArray(8192)
                while (true) {
                    if (isCancelled()) {
                        throw IOException("Download cancelled")
                    }
                    val read = inputStream.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > MAX_RINGTONE_BYTES) {
                        throw IOException(
                            "Ringtone too large: exceeded ${MAX_RINGTONE_BYTES / 1024}KB limit during download"
                        )
                    }
                    outputStream.write(buffer, 0, read)
                }
                total
            }

            outputStream.flush()
            Log.d(TAG, "Download complete: $bytesWritten bytes written")
            DownloadResult(mimeType, bytesWritten)
        }
    }
}
