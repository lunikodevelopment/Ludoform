package dev.lunikodevelopment.ludoform.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalArtTest {
    private val images =
        mapOf(
            LocalArt.key("Game (USA).PNG") to "content://game",
            LocalArt.key("Disc Game/Disc Game (Disc 2).jpg") to "content://disc2",
        )

    @Test
    fun anImageWithTheRomsNameInTheSameFolderIsFound() {
        assertEquals("content://game", LocalArt.find(images, listOf("Game (USA).nds")))
        assertNull(LocalArt.find(images, listOf("other/Game (USA).nds")))
        assertNull(LocalArt.find(images, listOf("Game (Europe).nds")))
    }

    @Test
    fun anyFileOfAGameCanCarryTheArt() {
        assertEquals(
            "content://disc2",
            LocalArt.find(images, listOf("Disc Game/Disc Game (Disc 1).cue", "Disc Game/Disc Game (Disc 2).cue")),
        )
    }
}
