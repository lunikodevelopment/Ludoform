package dev.lunikodevelopment.ludoform.data

import dev.lunikodevelopment.ludoform.input.FocusSpot
import org.junit.Assert.assertEquals
import org.junit.Test

/** Quickstart and Choose the menu button, gamepad only. */
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
        store.dispatch(AppIntent.SelectFocus(FocusSpot(Zones.SETTINGS_SECTIONS, SettingsSection.MENU.ordinal)))
        val spot = Screens.settingsSpot(store.state.value, SettingsItem.QUICKSTART)!!
        assertEquals(UiAction.Navigate(Destination.Quickstart), Screens.actionFor(store.state.value, spot))
    }
}

class SettingsSectionsTest {
    @Test
    fun sectionsOnTheDeckShowTheirSettingsOnTheStage() {
        val store = AppStore()
        store.dispatch(AppIntent.Navigate(Destination.Settings))
        assertEquals(FocusSpot(Zones.SETTINGS_SECTIONS, 0), store.state.value.focus.current)
        assertEquals(listOf(SettingsItem.ROMM), Screens.settingsItems(store.state.value))
        store.dispatch(AppIntent.SelectFocus(FocusSpot(Zones.SETTINGS_SECTIONS, SettingsSection.THEME.ordinal)))
        val state = store.state.value
        assertEquals(SettingsSection.THEME, Screens.settingsSection(state))
        assertEquals(UiAction.FocusOn(FocusSpot(Zones.SETTINGS_LIST, 0)), Screens.actionFor(state, state.focus.current!!))
        store.dispatch(AppIntent.SelectFocus(FocusSpot(Zones.SETTINGS_LIST, 1)))
        // The Stage keeps showing the section focus came from.
        assertEquals(SettingsSection.THEME, Screens.settingsSection(store.state.value))
        assertEquals(UiAction.SetTheme(ThemeChoice.LIGHT), Screens.actionFor(store.state.value, FocusSpot(Zones.SETTINGS_LIST, 1)))
    }

    @Test
    fun uploadShortcutsWaitForATestedServer() {
        val store = AppStore()
        assertEquals(listOf(SettingsItem.UPLOAD_SERVERS), Screens.settingsItems(store.state.value, SettingsSection.UPLOADS))
        store.dispatch(
            AppIntent.UploadServersChanged(listOf(UploadServerRow(1, "nas", "SMB", "nas", "roms", "Ready", false, false, ready = true))),
        )
        assertEquals(3, Screens.settingsItems(store.state.value, SettingsSection.UPLOADS).size)
    }
}
