package dev.lunikodevelopment.ludoform.transfer

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/** How one attempt at a download ended. */
sealed interface DownloadOutcome {
    data class Complete(val bytes: Long) : DownloadOutcome

    /** Paused or cancelled by the player; the partial file stays for a resume. */
    data object Stopped : DownloadOutcome

    /** 408, 425, 429, 5xx or a dropped connection: try again later from where it stopped. */
    data class Retry(val reason: String) : DownloadOutcome

    data object SignIn : DownloadOutcome

    data object NotFound : DownloadOutcome

    data object NoSpace : DownloadOutcome

    data class Refused(val code: Int) : DownloadOutcome
}

/**
 * Streams one file with HTTP Range resume (D31). [open] gives the output stream: appending when the server honours
 * the range (206), truncating when it sends the whole file again (200). [limitBytesPerSecond] of 0 means no limit.
 */
class RangeDownloader(
    private val http: OkHttpClient = defaultClient(),
    private val clock: () -> Long = System::nanoTime,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    fun download(
        url: HttpUrl,
        authorization: String,
        offset: Long,
        open: (append: Boolean) -> OutputStream,
        limitBytesPerSecond: () -> Long,
        shouldStop: () -> Boolean,
        onProgress: (done: Long, total: Long, bytesPerSecond: Long) -> Unit,
    ): DownloadOutcome {
        val request =
            Request.Builder().url(url).header("Authorization", authorization)
                .apply { if (offset > 0) header("Range", "bytes=$offset-") }
                .build()
        try {
            http.newCall(request).execute().use { response ->
                val code = response.code
                when {
                    code == 416 && offset > 0 -> return DownloadOutcome.Complete(offset)
                    code == 401 || code == 403 -> return DownloadOutcome.SignIn
                    code == 404 -> return DownloadOutcome.NotFound
                    code == 202 || code == 408 || code == 425 || code == 429 || code in 500..599 -> return DownloadOutcome.Retry(
                        "HTTP $code",
                    )
                    code != 200 && code != 206 -> return DownloadOutcome.Refused(code)
                }
                val body = response.body ?: return DownloadOutcome.Retry("Empty body")
                val resumed = code == 206
                val start = if (resumed) offset else 0L
                val total =
                    if (resumed) {
                        response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1
                    } else {
                        body.contentLength()
                    }
                var done = start
                val buffer = ByteArray(BUFFER)
                var windowStart = clock()
                var windowBytes = 0L
                var lastReport = windowStart
                var speed = 0L
                try {
                    open(resumed).use { out ->
                        body.byteStream().use { input ->
                            while (true) {
                                if (shouldStop()) return DownloadOutcome.Stopped
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                                done += read
                                windowBytes += read
                                val now = clock()
                                val limit = limitBytesPerSecond()
                                if (limit > 0) {
                                    // Sleep until this window's bytes fit the limit.
                                    val earliest = windowStart + windowBytes * NANOS / limit
                                    if (earliest > now) sleep((earliest - now) / 1_000_000)
                                }
                                val after = clock()
                                if (after - lastReport >= NANOS / 2) {
                                    speed = windowBytes * NANOS / maxOf(after - windowStart, 1)
                                    onProgress(done, total, speed)
                                    lastReport = after
                                    if (after - windowStart > 2 * NANOS) {
                                        windowStart = after
                                        windowBytes = 0
                                    }
                                }
                            }
                        }
                    }
                } catch (error: IOException) {
                    if (isNoSpace(error)) return DownloadOutcome.NoSpace
                    throw error
                }
                onProgress(done, total, speed)
                if (total > 0 && done < total) return DownloadOutcome.Retry("Connection ended early")
                return DownloadOutcome.Complete(done)
            }
        } catch (error: IOException) {
            return DownloadOutcome.Retry(error.javaClass.simpleName)
        }
    }

    private fun isNoSpace(error: IOException): Boolean =
        generateSequence<Throwable>(
            error,
        ) { it.cause }.any { "ENOSPC" in (it.message ?: "") }

    companion object {
        private const val BUFFER = 64 * 1024
        private const val NANOS = 1_000_000_000L

        fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}
