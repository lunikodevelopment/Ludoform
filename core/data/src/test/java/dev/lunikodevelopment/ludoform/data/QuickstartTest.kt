package dev.lunikodevelopment.ludoform.data

import dev.lunikodevelopment.ludoform.input.FocusSpot
import org.junit.Assert.assertEquals
import org.junit.Test

/** Quickstart and Choose the menu button (D40), gamepad only. */
class QuickstartTest {
    @Test
    fun quickstartStepsOpenTheirSetup() {
        val store = AppStore()
        store.dispatch(AppIntent.Navigate(Destination.Quickstart))
        val state = store.state.value
        assertEquals(FocusSpot(Zones.QUICKSTART_STEPS, 0), state.focus.current)
        assertEquals(UiAction.OpenRomm, Screens.actionFor(state, FocusSpot(Zones.QUICKSTART_STEPS, 0)))
        assertEquals(UiAction.PickFolder, Screens.actionFor(state, FocusSpot(Zones.QUICKSTART_STEPS, 1)))
        assertEquals(UiAction.OpenAccessibility, Screens.actionFor(state, FocusSpot(Zones.QUICKSTART_STEPS, 2)))
        assertEquals(UiAction.Navigate(Destination.MenuButton), Screens.actionFor(state, FocusSpot(Zones.QUICKSTART_STEPS, 3)))
        assertEquals(UiAction.FinishQuickstart, Screens.actionFor(state, FocusSpot(Zones.QUICKSTART_ACTIONS, 0)))
    }

    @Test
    fun theMenuButtonCardKeepsOrCancels() {
        val store = AppStore()
        store.dispatch(AppIntent.Navigate(Destination.MenuButton))
        val state = store.state.value
        assertEquals(FocusSpot(Zones.MENU_BUTTON_ACTIONS, 0), state.focus.current)
        assertEquals(UiAction.UseMenuButtons, Screens.actionFor(state, FocusSpot(Zones.MENU_BUTTON_ACTIONS, 0)))
        assertEquals(UiAction.Back, Screens.actionFor(state, FocusSpot(Zones.MENU_BUTTON_ACTIONS, 1)))
    }

    @Test
    fun settingsReopensQuickstart() {
        val store = AppStore()
        store.dispatch(AppIntent.Navigate(Destination.Settings))
        val index = Screens.settingsItems.indexOf(SettingsItem.QUICKSTART)
        assertEquals(UiAction.Navigate(Destination.Quickstart), Screens.actionFor(store.state.value, FocusSpot(Zones.SETTINGS_LIST, index)))
    }
}
