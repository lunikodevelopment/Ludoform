package dev.lunikodevelopment.ludoform.menu

import android.content.Context
import android.view.KeyEvent

/**
 * The buttons whose held pair opens the in-game menu (D39). The Thor's two round hinge buttons report START and SELECT
 * (D15), so those are the default; Quickstart records another pair.
 */
class MenuButtons(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("menu", Context.MODE_PRIVATE)

    var pair: Pair<Int, Int>
        get() = prefs.getInt("first", DEFAULT.first) to prefs.getInt("second", DEFAULT.second)
        set(value) = prefs.edit().putInt("first", value.first).putInt("second", value.second).apply()

    companion object {
        val DEFAULT = KeyEvent.KEYCODE_BUTTON_START to KeyEvent.KEYCODE_BUTTON_SELECT

        fun describe(pair: Pair<Int, Int>): String =
            if (pair == DEFAULT || pair == DEFAULT.second to DEFAULT.first) {
                "both hinge buttons"
            } else {
                listOf(pair.first, pair.second).joinToString(" + ") { name(it) }
            }

        fun name(code: Int): String =
            KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").removePrefix("BUTTON_").replace('_', ' ')
                .lowercase().replaceFirstChar { it.uppercase() }
    }
}

/**
 * Detects the menu pair held together for [holdMillis] (D39). Pure, so it is unit-tested: feed it key downs and ups
 * with their times and ask [due] whether the hold completed.
 */
class HoldDetector(
    private val pair: Pair<Int, Int>,
    private val holdMillis: Long = 1_000,
) {
    private val down = mutableSetOf<Int>()
    private var since: Long? = null
    private var fired = false

    fun isMenuKey(code: Int) = code == pair.first || code == pair.second

    /** Records a key; returns the time the hold completes when both are now down, else null. */
    fun key(
        code: Int,
        pressed: Boolean,
        at: Long,
    ): Long? {
        if (!isMenuKey(code)) return null
        if (pressed) down.add(code) else down.remove(code)
        return if (down.size == 2) {
            if (since == null) {
                since = at
                fired = false
            }
            since!! + holdMillis
        } else {
            since = null
            fired = false
            null
        }
    }

    /** True once per hold, when both keys have been down for the hold time at [now]. */
    fun due(now: Long): Boolean {
        val start = since ?: return false
        if (fired || now - start < holdMillis) return false
        fired = true
        return true
    }

    /** Both keys are down (the pair's own events are then kept from the game). */
    val holding: Boolean get() = down.size == 2

    fun reset() {
        down.clear()
        since = null
        fired = false
    }
}
