package dev.lunikodevelopment.ludoform.romm

import dev.lunikodevelopment.ludoform.data.library.GameEntity
import dev.lunikodevelopment.ludoform.data.library.LocalGameFile
import dev.lunikodevelopment.ludoform.data.library.PlatformEntity
import dev.lunikodevelopment.ludoform.data.library.RommLibraryChanges
import dev.lunikodevelopment.ludoform.data.library.RommPlatformEntity
import dev.lunikodevelopment.ludoform.data.library.RommRomEntity
import dev.lunikodevelopment.ludoform.library.PlatformDefinition
import dev.lunikodevelopment.ludoform.library.PlatformRegistry
import java.util.Locale

/**
 * Merges what the server reported into the library (D30). Pure, so the rules are unit-tested:
 * - Server platforms map to Ludoform platforms by folder name (`fs_slug`), then RomM's slug, then the display name.
 *   A file extension never decides.
 * - A ROM whose file is already on the device shows as that local game; otherwise it becomes a remote-only
 *   `romm:<id>` game, unless its platform is unmapped or set to "don't sync".
 * - A ROM is deleted only after two successful syncs in a row whose identifier list lacked it. Absence from an
 *   incremental page is never a deletion.
 */
object RommReconciler {
    const val REMOTE_PREFIX = "romm:"
    const val DELETE_AFTER_MISSING_SYNCS = 2

    /** IGDB-style slugs RomM uses that are not folder names in platforms.json. */
    private val slugs =
        mapOf(
            "genesis-slash-megadrive" to "megadrive",
            "sega-mega-drive" to "megadrive",
            "ps" to "psx",
            "playstation" to "psx",
            "nintendo-ds" to "nds",
            "nintendo-3ds" to "3ds",
            "new-nintendo-3ds" to "3ds",
            "gamecube" to "gc",
            "dc" to "dreamcast",
            "sfam" to "snes",
            "famicom" to "nes",
        )

    fun mapPlatform(
        platform: RemotePlatform,
        registry: PlatformRegistry,
    ): PlatformDefinition? =
        registry.findFolder(platform.fsSlug)
            ?: registry.findFolder(platform.slug)
            ?: slugs[platform.slug.lowercase(Locale.ROOT)]?.let { registry.byId(it) }
            ?: registry.findFolder(platform.name)

    data class Input(
        /** Null for a local-only pass (after a library scan or a sync switch), which keeps the saved platforms. */
        val serverPlatforms: List<RemotePlatform>?,
        val savedPlatforms: List<RommPlatformEntity>,
        val fetchedRoms: List<RemoteRom>,
        /** Null when the identifier list was not fetched: nothing is counted as missing then. */
        val identifiers: Set<Int>?,
        val savedRoms: List<RommRomEntity>,
        val localFiles: List<LocalGameFile>,
        val remoteGameIds: Set<String>,
        val now: Long,
    )

    fun reconcile(
        input: Input,
        registry: PlatformRegistry,
    ): RommLibraryChanges {
        val savedPlatforms = input.savedPlatforms.associateBy { it.id }
        val platforms =
            input.serverPlatforms?.map { remote ->
                RommPlatformEntity(
                    id = remote.id,
                    slug = remote.slug,
                    fsSlug = remote.fsSlug,
                    name = remote.name,
                    romCount = remote.romCount,
                    platformId = mapPlatform(remote, registry)?.id,
                    sync = savedPlatforms[remote.id]?.sync ?: true,
                )
            } ?: input.savedPlatforms
        val platformsById = platforms.associateBy { it.id }
        val removedPlatformIds = input.serverPlatforms?.let { server -> savedPlatforms.keys - server.map { it.id }.toSet() }.orEmpty()

        val saved = input.savedRoms.associateBy { it.id }
        val merged = saved.toMutableMap()
        for (rom in input.fetchedRoms) {
            merged[rom.id] =
                RommRomEntity(
                    id = rom.id,
                    rommPlatformId = rom.platformId,
                    fsName = rom.fsName,
                    fsNameNoExt = rom.fsNameNoExt,
                    sizeBytes = rom.sizeBytes,
                    createdAt = rom.createdAt,
                    updatedAt = rom.updatedAt,
                    missingFromServerDisk = rom.missingFromDisk,
                    gameId = saved[rom.id]?.gameId,
                    missingSyncs = 0,
                    name = rom.name,
                    cover = rom.cover,
                )
        }
        val removedRomIds = mutableListOf<Int>()
        val local = LocalIndex(input.localFiles)
        val kept = mutableListOf<RommRomEntity>()
        for (rom in merged.values) {
            val missingSyncs =
                when {
                    input.identifiers == null -> rom.missingSyncs
                    rom.id in input.identifiers -> 0
                    else -> rom.missingSyncs + 1
                }
            if (missingSyncs >= DELETE_AFTER_MISSING_SYNCS || rom.rommPlatformId in removedPlatformIds) {
                if (rom.id in saved) removedRomIds.add(rom.id)
                continue
            }
            val platform = platformsById[rom.rommPlatformId]
            val ludoformPlatform = platform?.platformId?.takeIf { platform.sync && !rom.missingFromServerDisk }
            val gameId = ludoformPlatform?.let { local.find(it, rom) ?: REMOTE_PREFIX + rom.id }
            kept.add(rom.copy(missingSyncs = missingSyncs, gameId = gameId))
        }

        val changedRoms = kept.filter { it != saved[it.id] }
        val changedIds = changedRoms.map { it.id }.toSet()
        val remote = kept.filter { it.gameId?.startsWith(REMOTE_PREFIX) == true }
        val remoteGames =
            remote.filter { it.id in changedIds || it.gameId !in input.remoteGameIds }.map { rom ->
                GameEntity(
                    id = rom.gameId!!,
                    platformId = platformsById.getValue(rom.rommPlatformId).platformId!!,
                    title = rom.fsNameNoExt,
                    addedAt = rom.createdAt.takeIf { it > 0 } ?: input.now,
                )
            }
        val ludoformPlatforms =
            remoteGames.map { it.platformId }.distinct().mapNotNull { id -> registry.byId(id)?.let { PlatformEntity(it.id, it.name) } }
        return RommLibraryChanges(
            platforms = if (input.serverPlatforms != null) platforms else emptyList(),
            removedPlatformIds = removedPlatformIds.toList(),
            ludoformPlatforms = ludoformPlatforms,
            roms = changedRoms,
            removedRomIds = removedRomIds,
            remoteGames = remoteGames,
            removedRemoteGameIds = (input.remoteGameIds - remote.mapNotNull { it.gameId }.toSet()).toList(),
        )
    }

    /** Local games by platform and lower-cased file name or title, for spotting ROMs already on the device. */
    private class LocalIndex(files: List<LocalGameFile>) {
        private val byFile = files.associate { key(it.platformId, it.displayName) to it.gameId }
        private val byTitle = files.associate { key(it.platformId, it.title) to it.gameId }

        fun find(
            platformId: String,
            rom: RommRomEntity,
        ): String? =
            byFile[key(platformId, rom.fsName)]
                ?: byTitle[key(platformId, rom.fsNameNoExt)]
                ?: byTitle[key(platformId, rom.fsName)]

        private fun key(
            platformId: String,
            name: String,
        ) = platformId + "/" + name.trim().lowercase(Locale.ROOT)
    }
}
