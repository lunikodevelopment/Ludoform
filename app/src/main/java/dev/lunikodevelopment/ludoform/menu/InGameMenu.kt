package dev.lunikodevelopment.ludoform.menu

/** The in-game menu's tiles. Swap screens is left out: Android lets no app move another app's game. */
enum class MenuItem(val label: String) {
    RESUME("Resume"),
    SCREENSHOT("Screenshot"),
    RECORD("Record"),
    HOME("Home"),
    SYNC_SAVES("Sync saves"),
    ACHIEVEMENTS("Achievements"),
    MANUAL("Manual"),
    VOLUME_DOWN("Volume −"),
    VOLUME_UP("Volume +"),
    BRIGHTNESS_DOWN("Brightness −"),
    BRIGHTNESS_UP("Brightness +"),
}

/** What the menu can do for this game right now; tiles that can't are shown disabled, with the reason below. */
data class MenuAbilities(
    val screenshot: Boolean = true,
    val syncSaves: Boolean = false,
    val achievements: Boolean = false,
    val manual: Boolean = false,
) {
    fun enabled(item: MenuItem): Boolean =
        when (item) {
            MenuItem.SYNC_SAVES -> syncSaves
            MenuItem.ACHIEVEMENTS -> achievements
            MenuItem.MANUAL -> manual
            MenuItem.SCREENSHOT -> screenshot
            else -> true
        }
}

/**
 * The menu's focus: rows of tiles, D-pad moves between enabled tiles only, Resume first. Moving
 * to a shorter row keeps the column as far as the row goes. Pure, so it is unit-tested.
 */
object InGameMenu {
    val rows: List<List<MenuItem>> =
        listOf(
            listOf(MenuItem.RESUME, MenuItem.SCREENSHOT, MenuItem.RECORD, MenuItem.HOME),
            listOf(MenuItem.SYNC_SAVES, MenuItem.ACHIEVEMENTS, MenuItem.MANUAL),
            listOf(MenuItem.VOLUME_DOWN, MenuItem.VOLUME_UP, MenuItem.BRIGHTNESS_DOWN, MenuItem.BRIGHTNESS_UP),
        )

    fun move(
        current: MenuItem,
        dx: Int,
        dy: Int,
        abilities: MenuAbilities,
    ): MenuItem {
        var row = rows.indexOfFirst { current in it }
        var column = rows[row].indexOf(current)
        repeat(rows.sumOf { it.size }) {
            val nextRow = (row + dy).coerceIn(0, rows.lastIndex)
            val nextColumn = (column + dx).coerceIn(0, rows[nextRow].lastIndex)
            if (nextRow == row && nextColumn == column) return current
            row = nextRow
            column = nextColumn
            val item = rows[row][column]
            if (abilities.enabled(item)) return item
        }
        return current
    }

    /** One line under the tiles saying why the disabled ones are disabled. */
    fun why(abilities: MenuAbilities): String? {
        val missing =
            listOfNotNull(
                "Sync saves needs the game on RomM".takeIf { !abilities.syncSaves },
                "Achievements and Manual come from RomM".takeIf { !abilities.achievements || !abilities.manual },
            )
        return missing.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
