package dev.lunikodevelopment.ludoform

import dev.lunikodevelopment.ludoform.menu.HoldDetector
import dev.lunikodevelopment.ludoform.menu.InGameMenu
import dev.lunikodevelopment.ludoform.menu.MenuAbilities
import dev.lunikodevelopment.ludoform.menu.MenuItem
import dev.lunikodevelopment.ludoform.menu.clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InGameMenuTest {
    private val start = 108
    private val select = 109

    @Test
    fun bothButtonsHeldForASecondOpenTheMenuOnce() {
        val hold = HoldDetector(start to select)
        assertNull(hold.key(start, true, 0))
        assertEquals(1_100L, hold.key(select, true, 100))
        assertFalse(hold.due(900))
        assertTrue(hold.due(1_100))
        assertFalse("Only once per hold", hold.due(1_500))
        hold.key(start, false, 1_600)
        hold.key(select, false, 1_610)
        hold.key(start, true, 2_000)
        hold.key(select, true, 2_000)
        assertTrue(hold.due(3_000))
    }

    @Test
    fun releasingEarlyOrOtherButtonsDoNothing() {
        val hold = HoldDetector(start to select)
        hold.key(start, true, 0)
        hold.key(select, true, 0)
        hold.key(select, false, 500)
        assertFalse(hold.due(1_200))
        assertNull(hold.key(96, true, 0))
        assertFalse(hold.isMenuKey(96))
    }

    @Test
    fun focusSkipsItemsThatCannotWorkForThisGame() {
        val abilities = MenuAbilities(syncSaves = false, achievements = false, manual = true)
        assertEquals(MenuItem.SCREENSHOT, InGameMenu.move(MenuItem.RESUME, 1, 0, abilities))
        assertEquals(MenuItem.RECORD, InGameMenu.move(MenuItem.SCREENSHOT, 1, 0, abilities))
        // Down from Resume: Sync saves can't work, so focus goes on to the volume row.
        assertEquals(MenuItem.VOLUME_DOWN, InGameMenu.move(MenuItem.RESUME, 0, 1, abilities))
        // Down from Home lands on the short row's last tile, Manual.
        assertEquals(MenuItem.MANUAL, InGameMenu.move(MenuItem.HOME, 0, 1, abilities))
        // Achievements is skipped going right along the short row.
        assertEquals(MenuItem.RESUME, InGameMenu.move(MenuItem.RESUME, -1, 0, abilities))
        assertEquals(MenuItem.BRIGHTNESS_UP, InGameMenu.move(MenuItem.BRIGHTNESS_DOWN, 1, 0, abilities))
    }

    @Test
    fun theReasonForDisabledTilesIsSaid() {
        assertEquals(
            "Sync saves needs the game on RomM · Achievements and Manual come from RomM",
            InGameMenu.why(MenuAbilities()),
        )
        assertNull(InGameMenu.why(MenuAbilities(syncSaves = true, achievements = true, manual = true)))
    }

    @Test
    fun recordingsAreAtMost1920WideAndEven() {
        assertEquals(1920 to 1080, dev.lunikodevelopment.ludoform.menu.RecordSize.fit(1920, 1080))
        assertEquals(1920 to 1080, dev.lunikodevelopment.ludoform.menu.RecordSize.fit(2560, 1440))
        assertEquals(1240 to 1080, dev.lunikodevelopment.ludoform.menu.RecordSize.fit(1240, 1080))
        assertEquals(1000 to 600, dev.lunikodevelopment.ludoform.menu.RecordSize.fit(1001, 601))
    }

    @Test
    fun sessionTimeReadsAsAClock() {
        assertEquals("00:42:18", clock((42 * 60 + 18) * 1000L))
    }
}
