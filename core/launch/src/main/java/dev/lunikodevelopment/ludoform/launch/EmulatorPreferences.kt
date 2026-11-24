package dev.lunikodevelopment.ludoform.launch

import android.content.Context
import android.content.SharedPreferences

/** Saved independently per game, per platform, and for the global default. */
class EmulatorPreferences(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("emulator-preferences", Context.MODE_PRIVATE))

    fun game(gameId: String): EmulatorPreference? = read("game:$gameId")

    fun platform(platformId: String): EmulatorPreference? = read("platform:$platformId")

    fun default(): EmulatorPreference? = read("default")

    fun setGame(
        gameId: String,
        value: EmulatorPreference?,
    ) = write("game:$gameId", value)

    fun setPlatform(
        platformId: String,
        value: EmulatorPreference?,
    ) = write("platform:$platformId", value)

    fun setDefault(value: EmulatorPreference?) = write("default", value)

    fun resolveForGame(
        resolver: EmulatorResolver,
        gameId: String,
        platformId: String,
        installedPackages: Collection<String>,
        installedCores: Collection<CoreInstallation> = emptyList(),
    ): EmulatorResolution =
        resolver.resolve(
            platformId = platformId,
            installedPackages = installedPackages,
            gamePreference = game(gameId),
            platformPreference = platform(platformId),
            defaultPreference = default(),
            installedCores = installedCores,
        )

    private fun read(key: String): EmulatorPreference? {
        val packageName = preferences.getString("$key:package", null) ?: return null
        return EmulatorPreference(packageName, preferences.getString("$key:core", null))
    }

    private fun write(
        key: String,
        value: EmulatorPreference?,
    ) {
        require(value == null || value.packageName.isNotBlank())
        preferences.edit().apply {
            if (value == null) {
                remove("$key:package")
                remove("$key:core")
            } else {
                putString("$key:package", value.packageName)
                if (value.coreId == null) remove("$key:core") else putString("$key:core", value.coreId)
            }
        }.apply()
    }
}
