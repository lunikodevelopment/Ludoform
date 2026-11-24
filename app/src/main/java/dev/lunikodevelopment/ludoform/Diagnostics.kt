package dev.lunikodevelopment.ludoform

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Diagnostics
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val executor = Executors.newSingleThreadExecutor()
        private val log = RollingLog(context.filesDir.resolve("diagnostics.log"))

        fun record(message: String) {
            executor.execute { log.append("${Instant.now()} $message") }
        }

        fun export(
            uri: Uri,
            onComplete: (Boolean) -> Unit,
        ) {
            executor.execute {
                val success =
                    runCatching {
                        val output = checkNotNull(context.contentResolver.openOutputStream(uri, "w"))
                        output.use { it.write(log.snapshot().toByteArray(Charsets.UTF_8)) }
                    }.isSuccess
                onComplete(success)
            }
        }
    }
