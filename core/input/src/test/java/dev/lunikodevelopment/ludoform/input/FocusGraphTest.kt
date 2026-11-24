package dev.lunikodevelopment.ludoform.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusGraphTest {
    private val layout =
        FocusLayout(
            stage = listOf(FocusZone("tabs", Screen.STAGE, 3), FocusZone("play", Screen.STAGE, 1)),
            deck =
                listOf(
                    FocusZone("row", Screen.DECK, 3),
                    FocusZone("grid", Screen.DECK, 10, columns = 4),
                    FocusZone("empty", Screen.DECK, 0),
                    FocusZone("bar", Screen.DECK, 2),
                ),
        )

    private fun at(
        zone: String,
        index: Int = 0,
    ) = FocusState().select(FocusSpot(zone, index), layout)

    @Test
    fun upFromDeckTopRowCrossesToStageAndDownRestoresTheTileItLeft() {
        val stage = at("row", 2).move(FocusDirection.UP, layout)
        assertEquals(FocusSpot("play", 0), stage.current)
        val tabs = stage.move(FocusDirection.UP, layout)
        assertEquals(FocusSpot("tabs", 0), tabs.current)
        val back = tabs.move(FocusDirection.DOWN, layout).move(FocusDirection.DOWN, layout)
        assertEquals(FocusSpot("row", 2), back.current)
    }

    @Test
    fun gridMovesByRowsAndClampsToTheShortLastRow() {
        val start = at("grid", 3)
        assertEquals(FocusSpot("grid", 7), start.move(FocusDirection.DOWN, layout).current)
        assertEquals(FocusSpot("grid", 9), at("grid", 7).move(FocusDirection.DOWN, layout).current)
        assertEquals(FocusSpot("grid", 3), at("grid", 3).move(FocusDirection.RIGHT, layout).current)
    }

    @Test
    fun emptyZonesAreSkippedAndZonesRememberTheirItem() {
        val bar = at("grid", 9).move(FocusDirection.DOWN, layout)
        assertEquals(FocusSpot("bar", 0), bar.current)
        assertEquals(FocusSpot("grid", 9), bar.move(FocusDirection.UP, layout).current)
    }

    @Test
    fun enteringAGridFromBelowStartsOnItsLastRowWhenNothingIsRemembered() {
        val bar = FocusState().select(FocusSpot("bar", 1), layout)
        assertEquals(FocusSpot("grid", 8), bar.move(FocusDirection.UP, layout).current)
    }

    @Test
    fun carouselWrapsWhileRowsStopAtTheirEdge() {
        val carousel = layout.copy(deck = listOf(FocusZone("consoles", Screen.DECK, 5, wrap = true)))
        val first = FocusState().select(FocusSpot("consoles", 0), carousel)
        assertEquals(FocusSpot("consoles", 4), first.move(FocusDirection.LEFT, carousel).current)
        assertEquals(FocusSpot("row", 0), at("row", 0).move(FocusDirection.LEFT, layout).current)
    }

    @Test
    fun touchMovesFocusBetweenScreensAndRestoresTheDeck() {
        val stage = at("row", 1).touchScreen(Screen.STAGE, layout)
        assertEquals(FocusSpot("play", 0), stage.current)
        assertEquals(FocusSpot("row", 1), stage.touchScreen(Screen.DECK, layout).current)
    }

    @Test
    fun normalizeRecoversFromShrinkingOrMissingZones() {
        val shrunk = layout.copy(deck = listOf(FocusZone("grid", Screen.DECK, 2, columns = 4)))
        assertEquals(FocusSpot("grid", 1), at("grid", 9).normalize(shrunk).current)
        val other = FocusLayout(stage = emptyList(), deck = listOf(FocusZone("list", Screen.DECK, 3)))
        assertEquals(FocusSpot("list", 0), at("grid", 9).normalize(other).current)
        assertNull(at("grid", 1).normalize(FocusLayout(emptyList(), emptyList())).current)
    }

    @Test
    fun initialSpotIsPreferredOnAFreshLayout() {
        val withInitial = layout.copy(initial = FocusSpot("grid", 5))
        assertEquals(FocusSpot("grid", 5), FocusState().normalize(withInitial).current)
    }
}
