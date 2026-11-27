package dev.lunikodevelopment.ludoform.romm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Achievements and manuals from RomM. */
class RommExtrasTest {
    private val rom =
        """
        {"id": 7, "ra_id": 1446, "has_manual": true, "path_manual": "roms/3/7/manual/7.pdf",
         "merged_ra_metadata": {"achievements": [
           {"ra_id": 2, "badge_id": "b2", "title": "Second", "description": "Later", "points": 10, "display_order": 2},
           {"ra_id": 1, "badge_id": "b1", "title": "First", "description": "Start", "points": 5, "display_order": 1}
         ]}}
        """.trimIndent()

    @Test
    fun romGivesItsAchievementsInOrderAndItsManual() {
        val extras = RommExtrasApi.parseRom(rom)
        assertEquals(1446, extras.raId)
        assertEquals(listOf("First", "Second"), extras.achievements.map { it.title })
        assertEquals("roms/3/7/manual/7.pdf", extras.manualPath)
    }

    @Test
    fun aRomWithoutExtrasHasNone() {
        val extras = RommExtrasApi.parseRom("""{"id": 7, "ra_id": null, "has_manual": false, "path_manual": null}""")
        assertNull(extras.raId)
        assertTrue(extras.achievements.isEmpty())
        assertNull(extras.manualPath)
    }

    @Test
    fun earnedAchievementsMatchByBadgeOrRetroAchievementsId() {
        val parsed = RommExtrasApi.parseRom(rom)
        val me =
            """
            {"username": "luna", "ra_progression": {"total": 1, "results": [
              {"rom_ra_id": 999, "earned_achievements": [{"id": "b2", "date": "x"}]},
              {"rom_ra_id": 1446, "earned_achievements": [{"id": "2", "date": "2025-01-01", "date_hardcore": "2025-01-01"}]}
            ]}}
            """.trimIndent()
        val marked = RommExtrasApi.applyProgress(parsed.achievements, parsed.raId, me, RommExtrasApi.rawIds(rom))
        assertFalse(marked[0].earned)
        assertTrue(marked[1].earned)
        assertTrue(marked[1].earnedHardcore)
        val extras = GameExtras(7, marked)
        assertEquals(1, extras.earned)
        assertEquals(15, extras.points)
        assertEquals(10, extras.earnedPoints)
    }

    @Test
    fun noProgressLeavesEverythingUnearned() {
        val parsed = RommExtrasApi.parseRom(rom)
        val marked = RommExtrasApi.applyProgress(parsed.achievements, parsed.raId, """{"username": "luna", "ra_progression": null}""")
        assertTrue(marked.none { it.earned })
    }
}
