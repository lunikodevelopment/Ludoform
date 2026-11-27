package dev.lunikodevelopment.ludoform.data

import java.util.Locale

/** One file of a game found for import. */
data class ImportFileRef(val uri: String, val name: String, val size: Long)

/** Where an import item stands, in the order the review sorts them. */
enum class ImportStatus { NEEDS_REVIEW, NO_FOLDER, READY, ON_ROMM }

/** The import review's filter chips. */
enum class ImportFilter { ALL, NEEDS_REVIEW, NO_FOLDER }

/**
 * A game the import review offers to upload. [candidates] are the systems its extension allows, [platformId]
 * the one decided (by a single candidate, its folder's name, a disc header or the player). [include] is whether
 * Upload sends it.
 */
data class ImportItem(
    val id: String,
    val title: String,
    val fileName: String,
    val files: List<ImportFileRef>,
    val foundIn: String,
    val candidates: List<String>,
    val platformId: String?,
    /** How [platformId] was decided without the player: "Folder gba", "GameCube disc (GM4E)"… */
    val decidedBy: String?,
    val fsSlug: String?,
    val serverHasFolder: Boolean,
    val onRomm: Boolean,
    val include: Boolean,
    /** The library game it is, when it is one. */
    val gameId: String? = null,
) {
    val sizeBytes: Long get() = files.sumOf { it.size }

    val status: ImportStatus
        get() =
            when {
                platformId == null -> ImportStatus.NEEDS_REVIEW
                onRomm -> ImportStatus.ON_ROMM
                !serverHasFolder -> ImportStatus.NO_FOLDER
                else -> ImportStatus.READY
            }

    /** Picked by the player among several systems; shown until confirmed. */
    val chosen: Boolean get() = candidates.size > 1 && decidedBy == null && platformId != null
}

/** The import review. [source] names where the files were found. */
data class ImportReview(
    val source: String,
    val scanning: Boolean = false,
    val items: List<ImportItem> = emptyList(),
) {
    val uploadCount: Int get() = items.count { it.include && it.platformId != null }

    fun count(status: ImportStatus): Int = items.count { it.status == status }

    fun visible(filter: ImportFilter): List<ImportItem> =
        when (filter) {
            ImportFilter.ALL -> items
            ImportFilter.NEEDS_REVIEW -> items.filter { it.status == ImportStatus.NEEDS_REVIEW || it.chosen }
            ImportFilter.NO_FOLDER -> items.filter { it.status == ImportStatus.NO_FOLDER }
        }
}

/** Builds and changes the import review. Pure, so it is unit-tested. */
object ImportPlan {
    /** A game found on the device: one file, or a disc set grouped by its `.cue`, `.m3u` or `.gdi`. */
    data class Found(
        val title: String,
        val files: List<ImportFileRef>,
        val foundIn: String,
        /** The system its folder is named after (`gba/`, `PlayStation/`), when one is. */
        val folderPlatform: String? = null,
        /** The system its disc header names, with how it was read. */
        val header: Pair<String, String>? = null,
        val gameId: String? = null,
    )

    /** What the server already has: RomM's folder per system and the file names in it (lower case). */
    data class Server(val fsSlugs: Map<String, String>, val names: Map<String, Set<String>>)

    /** RomM's folder name for a platform the server does not have yet (RomM's own default names). */
    fun defaultFsSlug(platformId: String): String =
        when (platformId) {
            "gc" -> "ngc"
            "dreamcast" -> "dc"
            "megadrive" -> "genesis"
            else -> platformId
        }

    fun build(
        found: List<Found>,
        extensions: Map<String, Set<String>>,
        server: Server,
    ): List<ImportItem> =
        found.mapNotNull { game ->
            val primary = game.files.firstOrNull() ?: return@mapNotNull null
            val extension = primary.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            val candidates = extensions.filterValues { extension in it }.keys.toList()
            if (candidates.isEmpty()) return@mapNotNull null
            val (platform, decidedBy) =
                when {
                    candidates.size == 1 -> candidates.single() to null
                    game.folderPlatform in candidates -> game.folderPlatform to "Folder ${game.foundIn.substringAfterLast('/')}"
                    game.header != null && game.header.first in candidates -> game.header.first to game.header.second
                    else -> null to null
                }
            val item =
                ImportItem(
                    id = primary.uri,
                    title = game.title,
                    fileName = primary.name,
                    files = game.files,
                    foundIn = game.foundIn,
                    candidates = candidates,
                    platformId = null,
                    decidedBy = decidedBy,
                    fsSlug = null,
                    serverHasFolder = false,
                    onRomm = false,
                    include = false,
                    gameId = game.gameId,
                )
            if (platform == null) item else assign(item, platform, server, decidedBy)
        }.sortedWith(compareBy<ImportItem> { it.status.ordinal }.thenBy { it.title.lowercase(Locale.ROOT) })

    /** Puts [item] on [platformId] and includes it unless RomM has it or lacks its folder (the player confirms those). */
    fun assign(
        item: ImportItem,
        platformId: String,
        server: Server,
        decidedBy: String? = null,
    ): ImportItem {
        val known = server.fsSlugs[platformId]
        val names = server.names[platformId].orEmpty()
        val onRomm = item.fileName.lowercase(Locale.ROOT) in names || item.title.lowercase(Locale.ROOT) in names
        return item.copy(
            platformId = platformId,
            decidedBy = decidedBy,
            fsSlug = known ?: defaultFsSlug(platformId),
            serverHasFolder = known != null,
            onRomm = onRomm,
            include = !onRomm && known != null,
        )
    }

    /**
     * The row button: the next system for a file several systems share, Create for a system RomM has no folder for,
     * Upload anyway for a game RomM has, and Upload or Skip otherwise.
     */
    fun act(
        item: ImportItem,
        server: Server,
    ): ImportItem =
        when {
            item.candidates.size > 1 && item.decidedBy == null -> {
                val next = item.candidates[(item.candidates.indexOf(item.platformId) + 1) % item.candidates.size]
                // A chosen system is the player's decision: it is included even without a folder on the server yet.
                assign(item, next, server).let { it.copy(include = !it.onRomm) }
            }
            else -> item.copy(include = !item.include)
        }

    /** The row button's words. */
    fun actionLabel(
        item: ImportItem,
        platformName: (String) -> String,
    ): String =
        when {
            item.candidates.size > 1 && item.decidedBy == null -> item.platformId?.let(platformName) ?: "Choose"
            item.status == ImportStatus.NO_FOLDER -> if (item.include) "Will create" else "Create"
            item.status == ImportStatus.ON_ROMM -> if (item.include) "Upload anyway" else "On RomM"
            else -> if (item.include) "Upload" else "Skip"
        }

    /** The line under the file name. */
    fun detail(
        item: ImportItem,
        platformName: (String) -> String,
    ): String {
        val extension = "." + item.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            item.platformId == null -> "$extension · pick " + item.candidates.map(platformName).joinToString(", ", limit = 3)
            item.status == ImportStatus.NO_FOLDER -> "${platformName(item.platformId)} · server has no ${item.fsSlug} folder"
            item.status == ImportStatus.ON_ROMM -> "${platformName(item.platformId)} · already on RomM"
            else -> platformName(item.platformId) + (item.decidedBy?.let { " · $it" } ?: "")
        }
    }
}
