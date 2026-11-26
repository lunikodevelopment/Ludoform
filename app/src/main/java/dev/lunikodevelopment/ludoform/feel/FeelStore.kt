package dev.lunikodevelopment.ludoform.feel

import android.content.Context
import dev.lunikodevelopment.ludoform.data.ColorSource
import dev.lunikodevelopment.ludoform.data.FeelState

/** Board 11's saved look and feel (D47), next to the theme in the "appearance" preferences. */
object FeelStore {
    private const val PREFS = "appearance"

    fun load(context: Context): FeelState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return FeelState(
            colorSource =
                prefs.getString("color_source", null)
                    ?.let { name -> ColorSource.entries.firstOrNull { it.name == name } } ?: ColorSource.LUDOFORM,
            haptics = prefs.getBoolean("haptics", false),
            sounds = prefs.getBoolean("ui_sounds", false),
            music = prefs.getBoolean("music", false),
            musicTrack = prefs.getString("music_track", null),
            musicName = prefs.getString("music_name", null),
        )
    }

    fun save(
        context: Context,
        feel: FeelState,
    ) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString("color_source", feel.colorSource.name)
        .putBoolean("haptics", feel.haptics)
        .putBoolean("ui_sounds", feel.sounds)
        .putBoolean("music", feel.music)
        .putString("music_track", feel.musicTrack)
        .putString("music_name", feel.musicName)
        .apply()
}
