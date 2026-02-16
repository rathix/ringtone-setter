package com.kennyandries.ringtonesetter.download

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.io.IOException

class RingtoneDownloaderTest {

    private val mockClient: OkHttpClient = mock()
    private val mockCall: Call = mock()

    @Test
    fun `download writes content to output stream`() {
        val expectedContent = "test audio content".toByteArray()
        val responseBody = ResponseBody.create("audio/mpeg".toMediaType(), expectedContent)
        val response = Response.Builder()
            .request(Request.Builder().url("http://example.com/ringtone.mp3").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()

        whenever(mockClient.newCall(any())).thenReturn(mockCall)
        whenever(mockCall.execute()).thenReturn(response)

        val downloader = RingtoneDownloader(mockClient)
        val outputStream = ByteArrayOutputStream()

        val result = downloader.download("http://example.com/ringtone.mp3", outputStream)

        assertEquals("audio/mpeg", result.mimeType)
        assertEquals(expectedContent.size.toLong(), result.bytesWritten)
        assertEquals("test audio content", outputStream.toString())
    }

    @Test(expected = IOException::class)
    fun `download throws exception on failure`() {
        val response = Response.Builder()
            .request(Request.Builder().url("http://example.com/ringtone.mp3").build())
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .build()

        whenever(mockClient.newCall(any())).thenReturn(mockCall)
        whenever(mockCall.execute()).thenReturn(response)

        val downloader = RingtoneDownloader(mockClient)
        downloader.download("http://example.com/ringtone.mp3", ByteArrayOutputStream())
    }

    @Test
    fun `download rejects unsupported content type`() {
        val responseBody = ResponseBody.create("text/html".toMediaType(), "<html></html>")
        val response = Response.Builder()
            .request(Request.Builder().url("http://example.com/ringtone.mp3").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()

        whenever(mockClient.newCall(any())).thenReturn(mockCall)
        whenever(mockCall.execute()).thenReturn(response)

        val downloader = RingtoneDownloader(mockClient)
        try {
            downloader.download("http://example.com/ringtone.mp3", ByteArrayOutputStream())
            fail("Expected IOException for unsupported content type")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("Unsupported content type"))
        }
    }

    @Test
    fun `download rejects oversized content-length`() {
        val responseBody = mock<ResponseBody>()
        whenever(responseBody.contentType()).thenReturn("audio/mpeg".toMediaType())
        whenever(responseBody.contentLength()).thenReturn(RingtoneDownloader.MAX_RINGTONE_BYTES + 1)

        val response = Response.Builder()
            .request(Request.Builder().url("http://example.com/ringtone.mp3").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()

        whenever(mockClient.newCall(any())).thenReturn(mockCall)
        whenever(mockCall.execute()).thenReturn(response)

        val downloader = RingtoneDownloader(mockClient)
        try {
            downloader.download("http://example.com/ringtone.mp3", ByteArrayOutputStream())
            fail("Expected IOException for oversized content")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("too large"))
        }
    }

    @Test
    fun `download respects cancellation callback`() {
        val largeContent = ByteArray(16384) // Larger than one buffer read
        val responseBody = ResponseBody.create("audio/mpeg".toMediaType(), largeContent)
        val response = Response.Builder()
            .request(Request.Builder().url("http://example.com/ringtone.mp3").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build()

        whenever(mockClient.newCall(any())).thenReturn(mockCall)
        whenever(mockCall.execute()).thenReturn(response)

        val downloader = RingtoneDownloader(mockClient)
        try {
            downloader.download(
                "http://example.com/ringtone.mp3",
                ByteArrayOutputStream(),
                isCancelled = { true },
            )
            fail("Expected IOException for cancelled download")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("cancelled"))
        }
    }
}
