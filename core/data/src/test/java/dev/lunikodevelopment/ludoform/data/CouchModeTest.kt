package dev.lunikodevelopment.ludoform.data

import dev.lunikodevelopment.ludoform.input.FocusSpot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CouchModeTest {
    @Test
    fun couchModeIsInScreensAndQuickSettings() {
        val store = AppStore()
        store.dispatch(AppIntent.Navigate(Destination.Settings))
        store.dispatch(AppIntent.SelectFocus(FocusSpot(Zones.SETTINGS_SECTIONS, SettingsSection.SCREENS.ordinal)))
        val spot = Screens.settingsSpot(store.state.value, SettingsItem.COUCH_MODE)!!
        assertEquals(UiAction.ToggleCouch, Screens.actionFor(store.state.value, spot))
        store.dispatch(AppIntent.Navigate(Destination.QuickSettings))
        assertEquals(UiAction.ToggleCouch, Screens.actionFor(store.state.value, FocusSpot(Zones.QS_COUCH, 0)))
        store.dispatch(AppIntent.CouchChanged(enabled = true, active = true))
        assertTrue(store.state.value.couchActive)
    }
}
