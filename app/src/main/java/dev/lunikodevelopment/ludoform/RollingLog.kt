package dev.lunikodevelopment.ludoform

import java.io.File

/** Bounded, line-oriented log in private app storage. Calls are serialized by the caller. */
class RollingLog(private val file: File, private val maxBytes: Int = 128 * 1024) {
    @Synchronized
    fun append(message: String) {
        val line = message.replace('\n', ' ').replace('\r', ' ').take(1000) + "\n"
        file.parentFile?.mkdirs()
        val old = if (file.exists()) file.readText(Charsets.UTF_8) else ""
        val lines = (old + line).lineSequence().filter { it.isNotEmpty() }.toList().asReversed()
        val kept = mutableListOf<String>()
        var size = 0
        for (entry in lines) {
            val bytes = (entry + "\n").toByteArray(Charsets.UTF_8).size
            if (size + bytes > maxBytes) break
            kept.add(entry)
            size += bytes
        }
        file.writeText(kept.asReversed().joinToString(separator = "\n", postfix = if (kept.isEmpty()) "" else "\n"))
    }

    @Synchronized
    fun snapshot(): String = if (file.exists()) file.readText(Charsets.UTF_8) else ""
}
