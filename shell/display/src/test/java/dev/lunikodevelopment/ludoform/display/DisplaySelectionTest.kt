package dev.lunikodevelopment.ludoform.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplaySelectionTest {
    private fun candidate(
        id: Int,
        category: Boolean = false,
        flag: Boolean = false,
    ) = DisplayCandidate(id, false, false, true, category, flag)

    @Test
    fun prefersCategoryThenFlagThenPublicDisplay() {
        val plain = candidate(1)
        val flagged = candidate(2, flag = true)
        val category = candidate(3, category = true)
        assertEquals(category, selectDeck(listOf(plain, flagged, category)))
        assertEquals(flagged, selectDeck(listOf(plain, flagged)))
        assertEquals(plain, selectDeck(listOf(plain)))
    }

    @Test
    fun excludesDefaultPrivateAndInvalidDisplays() {
        val ordinary = candidate(1)
        assertNull(selectDeck(listOf(ordinary.copy(isDefault = true), ordinary.copy(isPrivate = true), ordinary.copy(isValid = false))))
    }
}
