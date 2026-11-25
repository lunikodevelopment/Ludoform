package dev.lunikodevelopment.ludoform.transfer

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadPlanTest {
    private fun file(name: String) = UploadFile("content://$name", name, 10)

    @Test
    fun oneFileGoesIntoThePlatformFolderSeveralIntoAGameFolder() {
        assertEquals("nds", UploadPlan.remoteDir("nds", "Game", listOf(file("Game.nds"))))
        assertEquals(
            "psx/Game_ Part 1",
            UploadPlan.remoteDir("psx", "Game: Part 1", listOf(file("Game.cue"), file("Game.bin"))),
        )
        assertEquals("psx/roms", UploadPlan.platformDir("psx", hasRomsSubfolder = true))
        assertEquals("psx", UploadPlan.platformDir("psx", hasRomsSubfolder = false))
    }

    @Test
    fun platformsWithoutARommFolderGetRommsDefaultNames() {
        assertEquals("ngc", UploadPlan.defaultFsSlug("gc"))
        assertEquals("dc", UploadPlan.defaultFsSlug("dreamcast"))
        assertEquals("genesis", UploadPlan.defaultFsSlug("megadrive"))
        assertEquals("3ds", UploadPlan.defaultFsSlug("3ds"))
    }

    @Test
    fun filesRoundTripThroughTheirStoredForm() {
        val files =
            listOf(UploadFile("content://a/b%20c", "Game (USA).cue", 120), UploadFile("content://x", "Game (USA).bin", 7_000_000_000))
        assertEquals(files, UploadPlan.decode(UploadPlan.encode(files)))
    }

    @Test
    fun aCompleteDiscSetHasNothingMissing() {
        val files = listOf(file("Game.m3u"), file("Game (Disc 1).cue"), file("Game (Disc 1).bin"))
        val texts =
            mapOf(
                "Game.m3u" to "#EXTM3U\nGame (Disc 1).cue\n",
                "Game (Disc 1).cue" to "FILE \"Game (Disc 1).bin\" BINARY\n  TRACK 01 MODE2/2352\n",
            )
        assertEquals(emptyList<String>(), UploadPlan.missing(files, { true }, { texts[it.name] }))
    }

    @Test
    fun aDiscTheSetNamesOrAFileGoneFromTheDeviceIsMissing() {
        val files = listOf(file("Game.m3u"), file("Game (Disc 1).cue"), file("Game (Disc 1).bin"))
        val texts =
            mapOf("Game.m3u" to "Game (Disc 1).cue\nGame (Disc 2).cue\n", "Game (Disc 1).cue" to "FILE \"Game (Disc 1).bin\" BINARY\n")
        assertEquals(listOf("Game (Disc 2).cue"), UploadPlan.missing(files, { true }, { texts[it.name] }))
        val complete =
            mapOf(
                "Game.m3u" to "Game (Disc 1).cue\n",
                "Game (Disc 1).cue" to "FILE \"Game (Disc 1).bin\" BINARY\n",
            )
        assertEquals(listOf("Game (Disc 1).bin"), UploadPlan.missing(files, { it.name != "Game (Disc 1).bin" }, { complete[it.name] }))
    }

    @Test
    fun partFilesAreHidden() {
        assertEquals(".Game.nds.ludoform-part", UploadPlan.partName("Game.nds"))
    }
}
