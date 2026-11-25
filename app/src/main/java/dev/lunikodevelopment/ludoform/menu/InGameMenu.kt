package dev.lunikodevelopment.ludoform.menu

/** The in-game menu's tiles in board 12's order. */
enum class MenuItem(val label: String) {
    RESUME("Resume"),
    SCREENSHOT("Screenshot"),
    RECORD("Record"),
    HOME("Home"),
    SWAP_SCREENS("Swap screens"),
    SYNC_SAVES("Sync saves"),
    ACHIEVEMENTS("Achievements"),
    MANUAL("Manual"),
    VOLUME_DOWN("Volume −"),
    VOLUME_UP("Volume +"),
    BRIGHTNESS_DOWN("Brightness −"),
    BRIGHTNESS_UP("Brightness +"),
}

/** What the menu can do right now; items that can't are shown disabled, not hidden (#26). */
data class MenuAbilities(
    val screenshot: Boolean,
    val syncSaves: Boolean,
    val brightness: Boolean,
) {
    fun enabled(item: MenuItem): Boolean =
        when (item) {
            MenuItem.RESUME, MenuItem.HOME, MenuItem.VOLUME_DOWN, MenuItem.VOLUME_UP -> true
            MenuItem.SCREENSHOT -> screenshot
            MenuItem.SYNC_SAVES -> syncSaves
            MenuItem.BRIGHTNESS_DOWN, MenuItem.BRIGHTNESS_UP -> brightness
            MenuItem.RECORD, MenuItem.SWAP_SCREENS, MenuItem.ACHIEVEMENTS, MenuItem.MANUAL -> false
        }
}

/**
 * The menu's focus (D39): four tiles per row, D-pad moves between enabled items only, Resume first. Pure, so it is
 * unit-tested.
 */
object InGameMenu {
    const val COLUMNS = 4
    val items = MenuItem.entries

    fun move(
        current: MenuItem,
        dx: Int,
        dy: Int,
        abilities: MenuAbilities,
    ): MenuItem {
        var index = items.indexOf(current)
        repeat(items.size) {
            val row = index / COLUMNS
            val column = index % COLUMNS
            val rows = (items.size + COLUMNS - 1) / COLUMNS
            val nextRow = (row + dy).coerceIn(0, rows - 1)
            val nextColumn = (column + dx).coerceIn(0, COLUMNS - 1)
            val next = (nextRow * COLUMNS + nextColumn).coerceAtMost(items.lastIndex)
            if (next == index) return current
            index = next
            if (abilities.enabled(items[index])) return items[index]
        }
        return current
    }
}
