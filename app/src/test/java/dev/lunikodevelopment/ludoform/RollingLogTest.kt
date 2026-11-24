package dev.lunikodevelopment.ludoform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class RollingLogTest {
    @Test
    fun retainsNewestWholeLinesWithinByteLimit() {
        val file = Files.createTempDirectory("ludoform-log").resolve("log.txt").toFile()
        val log = RollingLog(file, 14)
        log.append("first")
        log.append("second")
        log.append("third")
        assertEquals("second\nthird\n", log.snapshot())
        assertTrue(file.length() <= 14)
    }
}
