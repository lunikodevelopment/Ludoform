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
    fun focusSkipsItemsThatCannotWorkYet() {
        val abilities = MenuAbilities(screenshot = true, syncSaves = false, brightness = false)
        assertEquals(MenuItem.SCREENSHOT, InGameMenu.move(MenuItem.RESUME, 1, 0, abilities))
        // Record is disabled: right from Screenshot lands on Home.
        assertEquals(MenuItem.HOME, InGameMenu.move(MenuItem.SCREENSHOT, 1, 0, abilities))
        // Down from Resume: Swap screens is disabled, so focus goes on to the volume row.
        assertEquals(MenuItem.VOLUME_DOWN, InGameMenu.move(MenuItem.RESUME, 0, 1, abilities))
        assertEquals(MenuItem.RESUME, InGameMenu.move(MenuItem.RESUME, -1, 0, abilities))
    }

    @Test
    fun sessionTimeReadsAsAClock() {
        assertEquals("00:42:18", clock((42 * 60 + 18) * 1000L))
    }
}
