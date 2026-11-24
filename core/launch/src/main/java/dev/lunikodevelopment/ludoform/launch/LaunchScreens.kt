package dev.lunikodevelopment.ludoform.launch

import android.content.Context
import android.content.SharedPreferences

/** Where a game opens: the top screen, the bottom screen, or ask each time. */
enum class ScreenPreference { TOP, BOTTOM, ASK }

/** Per game, then per emulator package, then the global default; the top screen when nothing is saved. */
fun resolveScreen(
    game: ScreenPreference?,
    emulator: ScreenPreference?,
    default: ScreenPreference?,
): ScreenPreference = game ?: emulator ?: default ?: ScreenPreference.TOP

class LaunchScreenPreferences(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("launch-screens", Context.MODE_PRIVATE))

    fun game(gameId: String): ScreenPreference? = read("game:$gameId")

    fun emulator(packageName: String): ScreenPreference? = read("emulator:$packageName")

    fun default(): ScreenPreference? = read("default")

    fun setGame(
        gameId: String,
        value: ScreenPreference?,
    ) = write("game:$gameId", value)

    fun setEmulator(
        packageName: String,
        value: ScreenPreference?,
    ) = write("emulator:$packageName", value)

    fun setDefault(value: ScreenPreference?) = write("default", value)

    fun resolve(
        gameId: String,
        packageName: String,
    ): ScreenPreference = resolveScreen(game(gameId), emulator(packageName), default())

    private fun read(key: String): ScreenPreference? =
        preferences.getString(key, null)?.let { stored -> ScreenPreference.entries.firstOrNull { it.name == stored } }

    private fun write(
        key: String,
        value: ScreenPreference?,
    ) {
        preferences.edit().apply { if (value == null) remove(key) else putString(key, value.name) }.apply()
    }
}
