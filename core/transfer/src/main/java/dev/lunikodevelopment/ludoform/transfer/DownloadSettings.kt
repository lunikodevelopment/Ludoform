package dev.lunikodevelopment.ludoform.transfer

import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** The Transfers holds: Wi-Fi only (on by default, games are large), while charging only, and a speed limit. */
class DownloadSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("downloads", Context.MODE_PRIVATE)

    var wifiOnly: Boolean
        get() = prefs.getBoolean("wifi_only", true)
        set(value) = prefs.edit().putBoolean("wifi_only", value).apply()

    var chargingOnly: Boolean
        get() = prefs.getBoolean("charging_only", false)
        set(value) = prefs.edit().putBoolean("charging_only", value).apply()

    /** Bytes per second; 0 is no limit. */
    var speedLimit: Long
        get() = prefs.getLong("speed_limit", 0)
        set(value) = prefs.edit().putLong("speed_limit", value).apply()

    val changes: Flow<Unit> =
        callbackFlow {
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            trySend(Unit)
            awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
        }

    companion object {
        private const val MB = 1024L * 1024

        /** The speed limit steps the Transfers screen cycles through. */
        val SPEED_STEPS = listOf(0L, 2 * MB, 4 * MB, 8 * MB, 16 * MB)
    }
}
