package dev.lunikodevelopment.ludoform.ui

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import dev.lunikodevelopment.ludoform.data.ThemeChoice
import dev.lunikodevelopment.ludoform.theme.LudoPalette

/** The saved theme, shared by the launcher and the in-game menu. */
object ThemeStore {
    private const val PREFS = "appearance"
    private const val KEY = "theme"

    fun load(context: Context): ThemeChoice =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { name -> ThemeChoice.entries.firstOrNull { it.name == name } } ?: ThemeChoice.DARK

    fun save(
        context: Context,
        theme: ThemeChoice,
    ) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, theme.name).apply()
}

@Composable
fun paletteFor(theme: ThemeChoice): LudoPalette =
    when (theme) {
        ThemeChoice.DARK -> LudoPalette.Dark
        ThemeChoice.LIGHT -> LudoPalette.Light
        ThemeChoice.BLACK -> LudoPalette.Black
        ThemeChoice.AUTO -> if (isSystemInDarkTheme()) LudoPalette.Dark else LudoPalette.Light
    }

fun themeName(theme: ThemeChoice): String =
    when (theme) {
        ThemeChoice.DARK -> "Dark"
        ThemeChoice.LIGHT -> "Light"
        ThemeChoice.AUTO -> "Auto · follows Android"
        ThemeChoice.BLACK -> "Black · for OLED"
    }
