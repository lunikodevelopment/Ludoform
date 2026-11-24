package dev.lunikodevelopment.ludoform.launch

/** A preference names the exact app package, so variants can be selected independently. */
data class EmulatorPreference(val packageName: String, val coreId: String? = null)

/** Core paths are supplied by the caller; this app cannot inspect another app's private files. */
data class CoreInstallation(val packageName: String, val libraryPath: String)

data class EmulatorCandidate(val definition: EmulatorDefinition, val packageName: String)

enum class PreferenceSource { GAME, PLATFORM, DEFAULT, AUTOMATIC }

sealed interface EmulatorResolution {
    data class Selected(
        val candidate: EmulatorCandidate,
        val source: PreferenceSource,
        val core: CoreDefinition? = null,
        val corePath: String? = null,
    ) : EmulatorResolution

    data class MissingEmulator(val preferred: EmulatorPreference?) : EmulatorResolution

    data class IncompatiblePreference(val preferred: EmulatorPreference) : EmulatorResolution

    data class NeedsCore(
        val candidate: EmulatorCandidate,
        val source: PreferenceSource,
        val suggested: List<CoreDefinition>,
    ) : EmulatorResolution

    data class MissingCore(val candidate: EmulatorCandidate, val coreId: String) : EmulatorResolution
}

class EmulatorResolver(private val registry: EmulatorRegistry) {
    fun candidates(
        platformId: String,
        installedPackages: Collection<String>,
    ): List<EmulatorCandidate> =
        registry.emulators.filter { platformId in it.platforms }.flatMap { definition ->
            installedPackages.distinct().filter(definition::matchesPackage)
                .sortedWith(
                    compareBy<String> { packageName ->
                        val index = definition.packageFamilies.indexOf(packageName)
                        if (index >= 0) index else definition.packageFamilies.size
                    }.thenBy { it },
                )
                .map { EmulatorCandidate(definition, it) }
        }.sortedBy { if (it.definition.kind == EmulatorKind.STANDALONE) 0 else 1 }

    fun resolve(
        platformId: String,
        installedPackages: Collection<String>,
        gamePreference: EmulatorPreference? = null,
        platformPreference: EmulatorPreference? = null,
        defaultPreference: EmulatorPreference? = null,
        installedCores: Collection<CoreInstallation> = emptyList(),
    ): EmulatorResolution {
        val configured = gamePreference ?: platformPreference ?: defaultPreference
        val source =
            when {
                gamePreference != null -> PreferenceSource.GAME
                platformPreference != null -> PreferenceSource.PLATFORM
                defaultPreference != null -> PreferenceSource.DEFAULT
                else -> PreferenceSource.AUTOMATIC
            }
        val options = candidates(platformId, installedPackages)
        val selected =
            if (configured != null) {
                if (configured.packageName !in installedPackages) return EmulatorResolution.MissingEmulator(configured)
                options.firstOrNull { it.packageName == configured.packageName }
                    ?: return EmulatorResolution.IncompatiblePreference(configured)
            } else {
                options.firstOrNull() ?: return EmulatorResolution.MissingEmulator(null)
            }
        if (selected.definition.kind != EmulatorKind.RETROARCH) return EmulatorResolution.Selected(selected, source)
        val supported = registry.cores.filter { platformId in it.platforms }
        val available =
            supported.mapNotNull { core ->
                installedCores.firstOrNull { install ->
                    install.packageName == selected.packageName && install.libraryPath.startsWith('/') &&
                        install.libraryPath.substringAfterLast('/') == core.library &&
                        install.libraryPath.split('/').none { it == ".." }
                }?.let { core to it.libraryPath }
            }
        val preferenceCore = configured?.coreId
        if (preferenceCore != null) {
            val match =
                available.firstOrNull { it.first.id == preferenceCore }
                    ?: return EmulatorResolution.MissingCore(selected, preferenceCore)
            return EmulatorResolution.Selected(selected, source, match.first, match.second)
        }
        val chosen = available.firstOrNull() ?: return EmulatorResolution.NeedsCore(selected, source, supported)
        return EmulatorResolution.Selected(selected, source, chosen.first, chosen.second)
    }
}
