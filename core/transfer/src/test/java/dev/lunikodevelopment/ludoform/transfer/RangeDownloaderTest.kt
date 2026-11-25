package dev.lunikodevelopment.ludoform.transfer

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

class RangeDownloaderTest {
    private val server = MockWebServer()
    private val content = ByteArray(300_000) { (it % 251).toByte() }
    private var file = ByteArrayOutputStream()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    private fun open(append: Boolean): ByteArrayOutputStream {
        if (!append) file = ByteArrayOutputStream()
        return file
    }

    private fun run(
        offset: Long = 0,
        downloader: RangeDownloader = RangeDownloader(),
        limit: Long = 0,
        stop: () -> Boolean = { false },
    ) = downloader.download(server.url("/api/roms/1/content/a.nds"), "Basic x", offset, ::open, { limit }, stop) { _, _, _ -> }

    @Test
    fun aWholeFileDownloads() {
        server.enqueue(MockResponse().setBody(Buffer().write(content)))
        assertEquals(DownloadOutcome.Complete(content.size.toLong()), run())
        assertArrayEquals(content, file.toByteArray())
        val request = server.takeRequest()
        assertNull(request.getHeader("Range"))
        assertEquals("Basic x", request.getHeader("Authorization"))
    }

    @Test
    fun aPartialFileResumesWithRange() {
        file.write(content, 0, 100_000)
        server.enqueue(
            MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 100000-299999/300000")
                .setBody(Buffer().write(content, 100_000, 200_000)),
        )
        assertEquals(DownloadOutcome.Complete(300_000), run(offset = 100_000))
        assertEquals("bytes=100000-", server.takeRequest().getHeader("Range"))
        assertArrayEquals(content, file.toByteArray())
    }

    @Test
    fun aServerIgnoringRangeStartsOver() {
        file.write(content, 0, 100_000)
        server.enqueue(MockResponse().setBody(Buffer().write(content)))
        assertEquals(DownloadOutcome.Complete(300_000), run(offset = 100_000))
        assertArrayEquals("The partial file is replaced, not appended to", content, file.toByteArray())
    }

    @Test
    fun aFinishedPartialIsComplete() {
        server.enqueue(MockResponse().setResponseCode(416))
        assertEquals(DownloadOutcome.Complete(300_000), run(offset = 300_000))
    }

    @Test
    fun busyServersAreRetriedAndOtherErrorsAreNot() {
        for (code in listOf(408, 425, 429, 500, 503)) {
            // OkHttp itself repeats a request once after a 408, so that one is answered twice.
            repeat(if (code == 408) 2 else 1) { server.enqueue(MockResponse().setResponseCode(code)) }
            assertTrue("$code", run() is DownloadOutcome.Retry)
        }
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(DownloadOutcome.SignIn, run())
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(DownloadOutcome.NotFound, run())
        server.enqueue(MockResponse().setResponseCode(400))
        assertEquals(DownloadOutcome.Refused(400), run())
    }

    @Test
    fun aDroppedConnectionIsRetriedAndKeepsWhatArrived() {
        server.enqueue(MockResponse().setBody(Buffer().write(content)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        assertTrue(run() is DownloadOutcome.Retry)
        assertTrue(file.size() < content.size)
    }

    @Test
    fun pausingStopsTheTransfer() {
        server.enqueue(MockResponse().setBody(Buffer().write(content)))
        assertEquals(DownloadOutcome.Stopped, run(stop = { true }))
    }

    @Test
    fun theSpeedLimitSleepsBetweenChunks() {
        var now = 0L
        var slept = 0L
        val downloader =
            RangeDownloader(clock = { now }, sleep = {
                slept += it
                now += it * 1_000_000
            })
        server.enqueue(MockResponse().setBody(Buffer().write(content)))
        assertEquals(DownloadOutcome.Complete(300_000), run(downloader = downloader, limit = 100_000))
        // 300,000 bytes at 100,000 bytes/s take about three seconds of sleep.
        assertTrue("slept $slept ms", slept in 2_300..3_100)
    }
}
