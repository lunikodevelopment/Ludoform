package dev.lunikodevelopment.ludoform.transfer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.EntryPointAccessors
import dev.lunikodevelopment.ludoform.data.ImportFileRef
import dev.lunikodevelopment.ludoform.data.ImportPlan
import dev.lunikodevelopment.ludoform.library.PlatformRegistry
import dev.lunikodevelopment.ludoform.library.ScanFile
import dev.lunikodevelopment.ludoform.library.groupGames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** Finds games to import: in a folder the player picks, or library games RomM doesn't have. */
object ImportScanner {
    private const val MAX_DEPTH = 4
    private const val MAX_FILES = 5_000

    /** A library game to offer for upload. */
    data class LibraryGame(val id: String, val title: String, val platformId: String)

    private fun entry(context: Context) =
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            UploadWorkerEntryPoint::class.java,
        )

    /** What the server already has, for marking games on RomM and folders it lacks. */
    suspend fun server(context: Context): ImportPlan.Server =
        withContext(Dispatchers.IO) {
            val romm = entry(context).rommDao()
            val platforms = romm.getPlatforms().filter { it.platformId != null }
            val byServerId = platforms.associateBy { it.id }
            val names =
                romm.getRoms().mapNotNull { rom -> byServerId[rom.rommPlatformId]?.platformId?.let { it to rom } }
                    .groupBy({ it.first }) { it.second }
                    .mapValues { (_, roms) -> roms.flatMap { listOf(it.fsName, it.fsNameNoExt) }.map { it.lowercase(Locale.ROOT) }.toSet() }
            ImportPlan.Server(platforms.associate { it.platformId!! to it.fsSlug }, names)
        }

    suspend fun libraryGames(
        context: Context,
        games: List<LibraryGame>,
    ): List<ImportPlan.Found> =
        withContext(Dispatchers.IO) {
            val dao = entry(context).libraryDao()
            games.mapNotNull { game ->
                val files =
                    dao.getGameFiles(game.id).map { file ->
                        val size = DocumentFile.fromSingleUri(context, Uri.parse(file.uri))?.length() ?: 0
                        ImportFileRef(file.uri, file.displayName, size)
                    }
                if (files.isEmpty()) return@mapNotNull null
                // The descriptor (.m3u, .cue, .gdi) first, so the review shows it and decides the system by it.
                val ordered = files.sortedBy { if (it.name.substringAfterLast('.').lowercase(Locale.ROOT) in DESCRIPTORS) 0 else 1 }
                ImportPlan.Found(game.title, ordered, game.platformId, folderPlatform = game.platformId, gameId = game.id)
            }
        }

    /** Every game under [treeUri], disc sets grouped, with its folder's system and a GameCube or Wii disc header. */
    suspend fun folder(
        context: Context,
        treeUri: Uri,
    ): List<ImportPlan.Found> =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
            val registry = PlatformRegistry(context)
            val allowed = registry.platforms.flatMap { it.extensions }.toSet() + DESCRIPTORS
            val files = mutableListOf<ScanFile>()
            val sizes = mutableMapOf<String, Long>()

            fun visit(
                folder: DocumentFile,
                prefix: String,
                depth: Int,
            ) {
                if (depth > MAX_DEPTH) return
                for (item in folder.listFiles()) {
                    if (files.size >= MAX_FILES) return
                    val name = item.name ?: continue
                    if (name.startsWith('.')) continue
                    val path = if (prefix.isEmpty()) name else "$prefix/$name"
                    if (item.isDirectory) {
                        visit(item, path, depth + 1)
                    } else if (name.substringAfterLast('.', "").lowercase(Locale.ROOT) in allowed) {
                        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        val descriptor = if (extension in DESCRIPTORS) readText(context, item.uri) else null
                        files.add(ScanFile(path, item.uri.toString(), descriptor))
                        sizes[item.uri.toString()] = item.length()
                    }
                }
            }
            visit(root, "", 0)
            val rootName = root.name ?: "Folder"
            groupGames(files, allowed).games.map { game ->
                val primary = game.files.first()
                val dir = primary.path.substringBeforeLast('/', "")
                val folderPlatform =
                    dir.split('/').filter { it.isNotEmpty() }.reversed().firstNotNullOfOrNull { registry.findFolder(it)?.id }
                ImportPlan.Found(
                    title = game.title,
                    files = game.files.map { ImportFileRef(it.uri, it.path.substringAfterLast('/'), sizes[it.uri] ?: 0) },
                    foundIn = if (dir.isEmpty()) rootName else "$rootName/$dir",
                    folderPlatform = folderPlatform,
                    header = discHeader(context, Uri.parse(primary.uri), primary.path),
                )
            }
        }

    private fun readText(
        context: Context,
        uri: Uri,
    ): String? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readNBytes(65_536).toString(Charsets.UTF_8).removePrefix("") }
        }.getOrNull()

    /** A GameCube or Wii disc says so in its first 32 bytes; other shared extensions need the player. */
    private fun discHeader(
        context: Context,
        uri: Uri,
        path: String,
    ): Pair<String, String>? {
        val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension !in setOf("iso", "gcm")) return null
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readNBytes(32) } }.getOrNull() ?: return null
        return DiscHeaders.identify(bytes)
    }

    private val DESCRIPTORS = setOf("cue", "m3u", "gdi")
}

/** Disc headers Ludoform can read without the emulator. */
object DiscHeaders {
    private val GAMECUBE = byteArrayOf(0xC2.toByte(), 0x33, 0x9F.toByte(), 0x3D)
    private val WII = byteArrayOf(0x5D, 0x1C, 0x9E.toByte(), 0xA3.toByte())

    /** The system and how it was read, from a disc image's first 32 bytes. */
    fun identify(bytes: ByteArray): Pair<String, String>? {
        if (bytes.size < 32) return null
        val id = String(bytes, 0, 4, Charsets.US_ASCII).takeIf { text -> text.all { it.isLetterOrDigit() } }
        return when {
            bytes.copyOfRange(0x1C, 0x20).contentEquals(GAMECUBE) -> "gc" to "GameCube disc" + (id?.let { " ($it)" } ?: "")
            bytes.copyOfRange(0x18, 0x1C).contentEquals(WII) -> "wii" to "Wii disc" + (id?.let { " ($it)" } ?: "")
            else -> null
        }
    }
}
