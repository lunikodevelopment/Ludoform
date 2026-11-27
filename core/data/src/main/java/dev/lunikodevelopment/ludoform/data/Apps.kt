package dev.lunikodevelopment.ludoform.data

import java.util.Locale

/** The app drawer's filter chips (D51). An app is in one of the last three; All shows every app. */
enum class AppGroup(val label: String) { ALL("All"), EMULATORS("Emulators"), GAMES("Games"), OTHER("Other") }

/** An app Android can open, as the drawer lists it. [group] is never [AppGroup.ALL]. */
data class InstalledApp(val packageName: String, val label: String, val group: AppGroup)

/** The drawer's lists. Pure, so it is unit-tested. */
object AppDrawer {
    const val COLUMNS = 5

    fun visible(
        apps: List<InstalledApp>,
        group: AppGroup,
    ): List<InstalledApp> =
        apps.filter { group == AppGroup.ALL || it.group == group }
            .sortedWith(compareBy<InstalledApp> { it.label.lowercase(Locale.ROOT) }.thenBy { it.packageName })

    /** The chips worth showing: All, and each group that has an app (D21: no chip with nothing behind it). */
    fun groups(apps: List<InstalledApp>): List<AppGroup> =
        AppGroup.entries.filter { it == AppGroup.ALL || apps.any { app -> app.group == it } }

    /** Which group an app goes in: an emulator Ludoform knows, an app Android marks as a game, or anything else. */
    fun group(
        isEmulator: Boolean,
        isGame: Boolean,
    ): AppGroup =
        when {
            isEmulator -> AppGroup.EMULATORS
            isGame -> AppGroup.GAMES
            else -> AppGroup.OTHER
        }
}
