package dev.lunikodevelopment.ludoform.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStoreTest {
    @Test
    fun navigationAndBackMaintainStackWithoutDuplicateCurrentDestination() {
        val store = AppStore()
        store.dispatch(AppIntent.Navigate(Destination.LIBRARY))
        store.dispatch(AppIntent.Navigate(Destination.LIBRARY))
        store.dispatch(AppIntent.Navigate(Destination.SETTINGS))
        assertEquals(listOf(Destination.HOME, Destination.LIBRARY, Destination.SETTINGS), store.state.value.navigation)
        store.dispatch(AppIntent.Back)
        assertEquals(Destination.LIBRARY, store.state.value.destination)
        store.dispatch(AppIntent.Back)
        store.dispatch(AppIntent.Back)
        assertEquals(listOf(Destination.HOME), store.state.value.navigation)
        assertFalse(store.state.value.canGoBack)
    }

    @Test
    fun connectionUpdatesDoNotResetNavigation() {
        val store = AppStore()
        store.dispatch(AppIntent.Navigate(Destination.SETTINGS))
        store.dispatch(AppIntent.DeckConnectionChanged(true))
        assertTrue(store.state.value.deckAttached)
        store.dispatch(AppIntent.DeckConnectionChanged(false))
        assertFalse(store.state.value.deckAttached)
        assertEquals(Destination.SETTINGS, store.state.value.destination)
    }

    @Test
    fun testerStateKeepsOnlyRecentEvents() {
        val store = AppStore()
        store.dispatch(AppIntent.SetButtonTester(true))
        repeat(10) { store.dispatch(AppIntent.RecordButtonEvent("event $it")) }
        assertTrue(store.state.value.buttonTesterActive)
        assertEquals((2..9).map { "event $it" }, store.state.value.recentButtonEvents)
        store.dispatch(AppIntent.SetButtonTester(false))
        assertFalse(store.state.value.buttonTesterActive)
    }
}
