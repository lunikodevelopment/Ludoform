package dev.lunikodevelopment.ludoform.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameFilesTest {
    private fun file(name: String) = LaunchFile("content://games/$name", name)

    @Test
    fun standaloneAndDescriptorSetsChooseThePlayableFile() {
        val rom = file("game.iso")
        assertEquals(listOf(rom), (selectGameFiles(listOf(rom)) as GameFilesResult.Ready).discs)
        val cue = file("game.cue")
        val bin = file("game.bin")
        val result = selectGameFiles(listOf(bin, cue)) as GameFilesResult.Ready
        assertEquals(listOf(cue), result.discs)
        assertEquals(listOf(bin, cue), result.grants)
    }

    @Test
    fun playlistSelectsDiscsInOrderAndRetainsTrackGrants() {
        val playlist = file("game.m3u")
        val first = file("disc1.cue")
        val second = file("disc2.chd")
        val track = file("track.bin")
        val result =
            selectGameFiles(listOf(playlist, first, second, track), "#EXTM3U\nsub/disc1.cue\nDISC2.CHD\n")
                as GameFilesResult.Ready
        assertEquals(listOf(first, second), result.discs)
        assertEquals(listOf(playlist, first, second, track), result.grants)
    }

    @Test
    fun missingAndAmbiguousEntriesAreNeverGuessed() {
        val playlist = file("game.m3u")
        val duplicateA = LaunchFile("content://a", "disc.cue")
        val duplicateB = LaunchFile("content://b", "disc.cue")
        assertTrue(selectGameFiles(listOf(playlist), null) is GameFilesResult.Unavailable)
        assertTrue(selectGameFiles(listOf(playlist), "missing.chd") is GameFilesResult.Unavailable)
        assertTrue(selectGameFiles(listOf(playlist, duplicateA, duplicateB), "disc.cue") is GameFilesResult.Unavailable)
        assertTrue(selectGameFiles(listOf(file("one.iso"), file("two.iso"))) is GameFilesResult.Unavailable)
    }
}
