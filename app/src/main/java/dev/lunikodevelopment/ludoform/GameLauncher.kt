package dev.lunikodevelopment.ludoform

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lunikodevelopment.ludoform.data.Destination
import dev.lunikodevelopment.ludoform.data.EmulatorChoice
import dev.lunikodevelopment.ludoform.data.GameScreen
import dev.lunikodevelopment.ludoform.data.LaunchRequest
import dev.lunikodevelopment.ludoform.data.PreferenceScope
import dev.lunikodevelopment.ludoform.data.ScreenScope
import dev.lunikodevelopment.ludoform.data.ScreenSetting
import dev.lunikodevelopment.ludoform.data.library.LibraryDao
import dev.lunikodevelopment.ludoform.launch.EmulatorPreference
import dev.lunikodevelopment.ludoform.launch.EmulatorPreferences
import dev.lunikodevelopment.ludoform.launch.EmulatorRegistry
import dev.lunikodevelopment.ludoform.launch.EmulatorResolution
import dev.lunikodevelopment.ludoform.launch.EmulatorResolver
import dev.lunikodevelopment.ludoform.launch.GameFilesResult
import dev.lunikodevelopment.ludoform.launch.InstalledEmulators
import dev.lunikodevelopment.ludoform.launch.LaunchFile
import dev.lunikodevelopment.ludoform.launch.LaunchIntentResult
import dev.lunikodevelopment.ludoform.launch.LaunchReadiness
import dev.lunikodevelopment.ludoform.launch.LaunchScreenPreferences
import dev.lunikodevelopment.ludoform.launch.ScreenPreference
import dev.lunikodevelopment.ludoform.launch.createLaunchIntent
import dev.lunikodevelopment.ludoform.launch.launchMethod
import dev.lunikodevelopment.ludoform.launch.readiness
import dev.lunikodevelopment.ludoform.launch.selectGameFiles
import dev.lunikodevelopment.ludoform.model.GameSummary
import dev.lunikodevelopment.ludoform.model.PlatformCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a [LaunchRequest] into an emulator intent: resolves the emulator from the saved preferences, selects the
 * game's launchable documents and builds a source-verified intent. It reads the library and never changes game files.
 */
@Singleton
class GameLauncher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val dao: LibraryDao,
    ) {
        sealed interface Outcome {
            data class Start(
                val intent: Intent,
                val emulator: String,
                val packageName: String,
                val file: String,
                val screen: GameScreen,
            ) : Outcome

            data class ChooseDisc(val discs: List<String>) : Outcome

            /** The saved screen choice is "ask": the screen sheet opens for this emulator. */
            data class ChooseScreen(val emulator: String, val packageName: String) : Outcome

            /** No usable emulator: the Emulator sheet opens with [message]. */
            data class ChooseEmulator(val message: String) : Outcome

            data class Problem(val message: String) : Outcome
        }

        private val registry by lazy { EmulatorRegistry.load(context) }
        private val resolver by lazy { EmulatorResolver(registry) }
        private val preferences by lazy { EmulatorPreferences(context) }
        private val screens by lazy { LaunchScreenPreferences(context) }

        fun gameScreenDefault(): ScreenSetting = screens.default().toSetting()

        fun setGameScreenDefault(setting: ScreenSetting) = screens.setDefault(setting.toPreference())

        /** Remembers the screen picked in the screen sheet; a game choice wins over an emulator choice. */
        fun saveScreen(
            gameId: String,
            emulatorPackage: String,
            screen: GameScreen,
            scope: ScreenScope,
        ) {
            val value = if (screen == GameScreen.TOP) ScreenPreference.TOP else ScreenPreference.BOTTOM
            when (scope) {
                ScreenScope.ONCE -> Unit
                ScreenScope.GAME -> screens.setGame(gameId, value)
                ScreenScope.EMULATOR -> screens.setEmulator(emulatorPackage, value)
            }
        }

        private fun platformName(game: GameSummary) = PlatformCatalog.info(game.platformId, game.platformId).shortName

        /** [deckAvailable] is false in single-screen mode, where every game opens on the one screen there is. */
        suspend fun prepare(
            request: LaunchRequest,
            game: GameSummary,
            deckAvailable: Boolean,
        ): Outcome =
            withContext(Dispatchers.IO) {
                val installed = InstalledEmulators(context).packages()
                val requested = request.packageName
                val resolution =
                    if (requested != null) {
                        resolver.resolve(game.platformId, installed, gamePreference = EmulatorPreference(requested))
                    } else {
                        preferences.resolveForGame(resolver, game.id, game.platformId, installed)
                    }
                val selected =
                    when (resolution) {
                        is EmulatorResolution.Selected -> resolution
                        is EmulatorResolution.MissingEmulator ->
                            return@withContext Outcome.ChooseEmulator(
                                if (resolution.preferred == null) {
                                    "No installed emulator can run ${platformName(game)} games yet."
                                } else {
                                    "The emulator chosen for this game is no longer installed. Choose another."
                                },
                            )
                        is EmulatorResolution.IncompatiblePreference ->
                            return@withContext Outcome.ChooseEmulator(
                                "The saved emulator cannot run ${platformName(game)} games. Choose another.",
                            )
                        is EmulatorResolution.NeedsCore, is EmulatorResolution.MissingCore ->
                            return@withContext Outcome.ChooseEmulator(
                                "RetroArch needs a core, and choosing one is not available yet. Choose a standalone emulator.",
                            )
                    }
                val files = dao.getGameFiles(game.id).map { LaunchFile(it.uri, it.displayName) }
                val playlist = files.firstOrNull { it.name.endsWith(".m3u", ignoreCase = true) }?.let(::readPlaylist)
                val chosen =
                    when (val result = selectGameFiles(files, playlist)) {
                        is GameFilesResult.Unavailable -> return@withContext Outcome.Problem(result.reason)
                        is GameFilesResult.Ready -> result
                    }
                if (chosen.discs.size > 1 && request.disc == null) {
                    return@withContext Outcome.ChooseDisc(chosen.discs.map { it.name })
                }
                val disc =
                    chosen.discs.getOrNull(request.disc ?: 0)
                        ?: return@withContext Outcome.Problem("That disc is no longer available.")
                val intent =
                    when (val result = createLaunchIntent(context, selected, disc, chosen.grants)) {
                        is LaunchIntentResult.Unavailable -> return@withContext Outcome.Problem(result.reason)
                        is LaunchIntentResult.Ready -> result.intent
                    }
                val name = selected.candidate.definition.name
                val packageName = selected.candidate.packageName
                val requestedScreen = request.screen
                val screen =
                    when {
                        !deckAvailable -> GameScreen.TOP
                        requestedScreen != null -> requestedScreen
                        else ->
                            when (screens.resolve(game.id, packageName)) {
                                ScreenPreference.TOP -> GameScreen.TOP
                                ScreenPreference.BOTTOM -> GameScreen.BOTTOM
                                ScreenPreference.ASK -> return@withContext Outcome.ChooseScreen(name, packageName)
                            }
                    }
                Outcome.Start(intent, name, packageName, disc.name, screen)
            }

        /** Playlists are tiny text files; anything larger than 64 KiB is not treated as one. */
        private fun readPlaylist(file: LaunchFile): String? =
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(file.uri))?.use { input ->
                    val bytes = input.readNBytes(MAX_PLAYLIST_BYTES + 1)
                    if (bytes.size > MAX_PLAYLIST_BYTES) null else bytes.toString(Charsets.UTF_8)
                }
            }.getOrNull()

        /** The Emulator sheet for [game]: installed emulators first, then registry emulators that are not installed. */
        suspend fun emulatorSheet(
            game: GameSummary,
            message: String? = null,
        ): Destination.Emulator =
            withContext(Dispatchers.IO) {
                val installed = InstalledEmulators(context).packages()
                val candidates = resolver.candidates(game.platformId, installed)
                val current =
                    when (val resolution = preferences.resolveForGame(resolver, game.id, game.platformId, installed)) {
                        is EmulatorResolution.Selected -> resolution.candidate.packageName
                        is EmulatorResolution.NeedsCore -> resolution.candidate.packageName
                        is EmulatorResolution.MissingCore -> resolution.candidate.packageName
                        else -> null
                    }
                val installedChoices =
                    candidates.mapIndexed { index, candidate ->
                        val definition = candidate.definition
                        val readiness =
                            when (definition.readiness()) {
                                LaunchReadiness.READY -> null
                                LaunchReadiness.NEEDS_CORE -> "Needs a core"
                                LaunchReadiness.NOT_VERIFIED -> "Launch not verified yet"
                            }
                        val role =
                            when {
                                candidate.packageName == current && preferences.game(game.id) != null -> "Chosen for this game"
                                candidate.packageName == current && preferences.platform(game.platformId) != null ->
                                    "Chosen for all ${platformName(game)}"
                                index == 0 -> "Recommended"
                                else -> null
                            }
                        EmulatorChoice(
                            packageName = candidate.packageName,
                            name = definition.name,
                            detail = listOfNotNull(role, readiness).ifEmpty { listOf("Installed") }.joinToString(" · "),
                            method = definition.launchMethod(),
                            installed = true,
                        )
                    }
                val missing =
                    registry.emulators
                        .filter {
                                definition ->
                            game.platformId in definition.platforms && candidates.none { it.definition.id == definition.id }
                        }
                        .map { EmulatorChoice(it.packageFamilies.first(), it.name, "Not installed", it.launchMethod(), installed = false) }
                val platformWide = preferences.game(game.id) == null && preferences.platform(game.platformId) != null
                val scope = if (platformWide) PreferenceScope.PLATFORM else PreferenceScope.GAME
                Destination.Emulator(
                    gameId = game.id,
                    choices = installedChoices + missing,
                    current = installedChoices.indexOfFirst { it.packageName == current }.coerceAtLeast(0),
                    scope = scope,
                    message = message,
                )
            }

        /** A platform-wide choice also clears this game's own choice, so the game follows what the player just picked. */
        fun save(
            game: GameSummary,
            choice: EmulatorChoice,
            scope: PreferenceScope,
        ) {
            val preference = EmulatorPreference(choice.packageName)
            when (scope) {
                PreferenceScope.GAME -> preferences.setGame(game.id, preference)
                PreferenceScope.PLATFORM -> {
                    preferences.setPlatform(game.platformId, preference)
                    preferences.setGame(game.id, null)
                }
            }
        }

        private companion object {
            const val MAX_PLAYLIST_BYTES = 64 * 1024

            fun ScreenPreference?.toSetting(): ScreenSetting =
                when (this) {
                    ScreenPreference.BOTTOM -> ScreenSetting.BOTTOM
                    ScreenPreference.ASK -> ScreenSetting.ASK
                    ScreenPreference.TOP, null -> ScreenSetting.TOP
                }

            fun ScreenSetting.toPreference(): ScreenPreference =
                when (this) {
                    ScreenSetting.TOP -> ScreenPreference.TOP
                    ScreenSetting.BOTTOM -> ScreenPreference.BOTTOM
                    ScreenSetting.ASK -> ScreenPreference.ASK
                }
        }
    }
