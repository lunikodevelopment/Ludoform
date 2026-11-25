package dev.lunikodevelopment.ludoform.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPlanTest {
    private val extensions =
        mapOf(
            "gba" to setOf("gba", "zip"),
            "nds" to setOf("nds", "zip"),
            "gc" to setOf("iso", "rvz"),
            "wii" to setOf("iso", "wbfs"),
            "ps2" to setOf("iso", "chd"),
        )
    private val server =
        ImportPlan.Server(
            fsSlugs = mapOf("gba" to "gba", "gc" to "ngc", "wii" to "wii", "ps2" to "ps2"),
            names = mapOf("gba" to setOf("metroid fusion (usa).gba")),
        )

    private fun found(
        name: String,
        folderPlatform: String? = null,
        header: Pair<String, String>? = null,
    ) = ImportPlan.Found(
        name.substringBeforeLast('.'),
        listOf(ImportFileRef("content://$name", name, 100)),
        "Download/roms",
        folderPlatform,
        header,
    )

    private fun item(
        name: String,
        folderPlatform: String? = null,
        header: Pair<String, String>? = null,
    ) = ImportPlan.build(listOf(found(name, folderPlatform, header)), extensions, server).single()

    @Test
    fun aSingleSystemExtensionIsReady() {
        val gba = item("Kirby (USA).gba")
        assertEquals(ImportStatus.READY, gba.status)
        assertEquals("gba", gba.platformId)
        assertTrue(gba.include)
    }

    @Test
    fun aSharedExtensionNeedsAChoiceUnlessItsFolderOrHeaderDecides() {
        val iso = item("Soft Circuit (USA).iso")
        assertEquals(ImportStatus.NEEDS_REVIEW, iso.status)
        assertNull(iso.platformId)
        assertFalse(iso.include)
        assertEquals(listOf("gc", "wii", "ps2"), iso.candidates)
        assertEquals("ps2", item("Game.iso", folderPlatform = "ps2").platformId)
        val disc = item("Game.iso", header = "gc" to "GameCube disc (GM4E)")
        assertEquals("gc", disc.platformId)
        assertEquals("GameCube disc (GM4E)", disc.decidedBy)
        // A .zip in a gba folder is a GBA game.
        assertEquals("gba", item("Game.zip", folderPlatform = "gba").platformId)
    }

    @Test
    fun choosingCyclesThroughTheSystemsAndIncludesTheGame() {
        val iso = item("Soft Circuit (USA).iso")
        val first = ImportPlan.act(iso, server)
        assertEquals("gc", first.platformId)
        assertEquals("ngc", first.fsSlug)
        assertTrue(first.include)
        assertEquals("wii", ImportPlan.act(first, server).platformId)
        assertEquals("GC", ImportPlan.actionLabel(first) { it.uppercase() })
    }

    @Test
    fun gamesRommHasAreSkippedUnlessThePlayerInsists() {
        val metroid = item("Metroid Fusion (USA).gba")
        assertEquals(ImportStatus.ON_ROMM, metroid.status)
        assertFalse(metroid.include)
        assertTrue(ImportPlan.act(metroid, server).include)
    }

    @Test
    fun aSystemWithoutARommFolderWaitsForCreate() {
        val nds = item("Starquarry.nds")
        assertEquals(ImportStatus.NO_FOLDER, nds.status)
        assertEquals("nds", nds.fsSlug)
        assertFalse(nds.include)
        val created = ImportPlan.act(nds, server)
        assertTrue(created.include)
        assertEquals("Will create", ImportPlan.actionLabel(created) { it })
    }

    @Test
    fun unknownFilesAreLeftOutAndTheReviewSortsProblemsFirst() {
        val items =
            ImportPlan.build(
                listOf(found("Kirby.gba"), found("readme.txt"), found("Soft.iso"), found("Star.nds")),
                extensions,
                server,
            )
        assertEquals(listOf(ImportStatus.NEEDS_REVIEW, ImportStatus.NO_FOLDER, ImportStatus.READY), items.map { it.status })
        val review = ImportReview("Download", items = items)
        assertEquals(1, review.uploadCount)
        assertEquals(1, review.visible(ImportFilter.NO_FOLDER).size)
    }
}
