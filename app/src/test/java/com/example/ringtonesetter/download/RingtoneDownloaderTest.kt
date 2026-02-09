package com.example.ringtonesetter.download

import okhttp3.*
import okio.Buffer
import org.junit.Assert.assertEquals
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
        val responseBody = ResponseBody.create(MediaType.parse("audio/mpeg"), expectedContent)
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
}
