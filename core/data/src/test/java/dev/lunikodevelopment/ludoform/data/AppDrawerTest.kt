package dev.lunikodevelopment.ludoform.data

import dev.lunikodevelopment.ludoform.input.FocusSpot
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDrawerTest {
    private val apps =
        listOf(
            InstalledApp("org.example.browser", "browser", AppGroup.OTHER),
            InstalledApp("org.dolphinemu.dolphinemu", "Dolphin", AppGroup.EMULATORS),
            InstalledApp("com.example.puzzle", "Alpha Puzzle", AppGroup.GAMES),
            InstalledApp("com.retroarch", "RetroArch", AppGroup.EMULATORS),
        )

    @Test
    fun listsAppsByNameIgnoringCaseAndFiltersByGroup() {
        assertEquals(listOf("Alpha Puzzle", "browser", "Dolphin", "RetroArch"), AppDrawer.visible(apps, AppGroup.ALL).map { it.label })
        assertEquals(listOf("Dolphin", "RetroArch"), AppDrawer.visible(apps, AppGroup.EMULATORS).map { it.label })
    }

    @Test
    fun showsOnlyChipsWithAppsBehindThem() {
        val noGames = apps.filter { it.group != AppGroup.GAMES }
        assertEquals(listOf(AppGroup.ALL, AppGroup.EMULATORS, AppGroup.OTHER), AppDrawer.groups(noGames))
    }

    @Test
    fun emulatorsWinOverGames() {
        assertEquals(AppGroup.EMULATORS, AppDrawer.group(isEmulator = true, isGame = true))
        assertEquals(AppGroup.GAMES, AppDrawer.group(isEmulator = false, isGame = true))
        assertEquals(AppGroup.OTHER, AppDrawer.group(isEmulator = false, isGame = false))
    }

    @Test
    fun homeAppsLabelOpensTheDrawerAndTilesOpenApps() {
        val home = AppState()
        assertEquals(UiAction.Navigate(Destination.Apps()), Screens.actionFor(home, FocusSpot(Zones.HOME_APPS, 0)))
        val drawer = AppState(navigation = listOf(NavEntry(Destination.Home), NavEntry(Destination.Apps(AppGroup.EMULATORS))), apps = apps)
        assertEquals(UiAction.OpenApp("com.retroarch"), Screens.actionFor(drawer, FocusSpot(Zones.APPS_GRID, 1)))
        assertEquals(UiAction.Replace(Destination.Apps(AppGroup.OTHER)), Screens.actionFor(drawer, FocusSpot(Zones.APPS_FILTER, 3)))
    }
}
