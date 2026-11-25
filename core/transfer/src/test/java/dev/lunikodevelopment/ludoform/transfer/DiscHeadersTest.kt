package dev.lunikodevelopment.ludoform.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscHeadersTest {
    private fun header(
        id: String,
        magicAt: Int,
        magic: List<Int>,
    ): ByteArray {
        val bytes = ByteArray(32)
        id.toByteArray().copyInto(bytes)
        magic.forEachIndexed { index, value -> bytes[magicAt + index] = value.toByte() }
        return bytes
    }

    @Test
    fun gameCubeAndWiiDiscsNameTheirSystem() {
        assertEquals("gc" to "GameCube disc (GM4E)", DiscHeaders.identify(header("GM4E01", 0x1C, listOf(0xC2, 0x33, 0x9F, 0x3D))))
        assertEquals("wii" to "Wii disc (RMCE)", DiscHeaders.identify(header("RMCE01", 0x18, listOf(0x5D, 0x1C, 0x9E, 0xA3))))
        assertNull(DiscHeaders.identify(ByteArray(32)))
        assertNull(DiscHeaders.identify(ByteArray(8)))
    }
}
