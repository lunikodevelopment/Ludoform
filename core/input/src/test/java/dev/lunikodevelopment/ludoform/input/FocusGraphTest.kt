package dev.lunikodevelopment.ludoform.input

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusGraphTest {
    @Test
    fun upCrossesToStageAndDownRestoresLastDeckItem() {
        val deck = FocusState().select(FocusTarget.DECK_SETTINGS)
        val stage = deck.move(FocusDirection.UP)
        assertEquals(FocusTarget.STAGE_SETTINGS, stage.current)
        assertEquals(FocusTarget.DECK_SETTINGS, stage.move(FocusDirection.DOWN).current)
    }

    @Test
    fun touchMovesFocusBetweenScreensAndRemembersDeck() {
        val state = FocusState().select(FocusTarget.DECK_LIBRARY).touchScreen(Screen.STAGE)
        assertEquals(FocusTarget.STAGE_HOME, state.current)
        assertEquals(FocusTarget.DECK_LIBRARY, state.touchScreen(Screen.DECK).current)
    }

    @Test
    fun directionStaysInBoundsAndBackRowMovesUp() {
        assertEquals(FocusTarget.DECK_HOME, FocusState().move(FocusDirection.LEFT).current)
        assertEquals(FocusTarget.DECK_BACK, FocusState().move(FocusDirection.DOWN).current)
        assertEquals(FocusTarget.DECK_HOME, FocusState().move(FocusDirection.DOWN).move(FocusDirection.UP).current)
    }

    @Test
    fun returningFromBackRestoresMostRecentTopRowItem() {
        val state = FocusState().select(FocusTarget.DECK_SETTINGS).move(FocusDirection.DOWN)
        assertEquals(FocusTarget.DECK_BACK, state.current)
        assertEquals(FocusTarget.DECK_SETTINGS, state.move(FocusDirection.UP).current)
    }

    @Test
    fun diagnosticsControlsAreReachableOnlyWhenAvailable() {
        val settings = FocusState().select(FocusTarget.DECK_SETTINGS)
        assertEquals(FocusTarget.DECK_BACK, settings.move(FocusDirection.DOWN).current)
        val tester = settings.move(FocusDirection.DOWN, diagnosticsAvailable = true)
        assertEquals(FocusTarget.DECK_TESTER, tester.current)
        val export = tester.move(FocusDirection.RIGHT, diagnosticsAvailable = true)
        assertEquals(FocusTarget.DECK_EXPORT, export.current)
        assertEquals(FocusTarget.DECK_SETTINGS, export.move(FocusDirection.UP, diagnosticsAvailable = true).current)
    }

    @Test
    fun libraryFolderControlsAreReachableAndReturnToTopRow() {
        val library = FocusState().select(FocusTarget.DECK_LIBRARY)
        val add = library.move(FocusDirection.DOWN, libraryAvailable = true)
        assertEquals(FocusTarget.DECK_ADD_FOLDER, add.current)
        val rescan = add.move(FocusDirection.RIGHT, libraryAvailable = true)
        assertEquals(FocusTarget.DECK_RESCAN, rescan.current)
        assertEquals(FocusTarget.DECK_LIBRARY, rescan.move(FocusDirection.UP, libraryAvailable = true).current)
    }
}
