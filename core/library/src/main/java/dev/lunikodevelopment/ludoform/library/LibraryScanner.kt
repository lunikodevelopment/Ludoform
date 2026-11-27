package dev.lunikodevelopment.ludoform.library

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.lunikodevelopment.ludoform.data.library.GameEntity
import dev.lunikodevelopment.ludoform.data.library.GameFileEntity
import dev.lunikodevelopment.ludoform.data.library.LibraryDao
import dev.lunikodevelopment.ludoform.data.library.PlatformEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest
import java.util.Locale

data class ScanSummary(val games: Int, val skipped: List<String>)

class LibraryScanner(private val context: Context, private val dao: LibraryDao) {
    private val registry = PlatformRegistry(context)

    suspend fun scan(folderUri: String): ScanSummary {
        val job = currentCoroutineContext()
        val uri = Uri.parse(folderUri)
        val root = DocumentFile.fromTreeUri(context, uri) ?: throw IllegalArgumentException("Cannot open selected folder")
        if (!root.canRead()) throw SecurityException("Folder permission is unavailable")
        val skipped = mutableListOf<String>()
        val roots = mutableListOf<Pair<PlatformDefinition, DocumentFile>>()
        val selectedPlatform = registry.findFolder(root.name.orEmpty())
        if (selectedPlatform != null) {
            roots.add(selectedPlatform to root)
        } else {
            root.listFiles().forEach { item ->
                val platform = if (item.isDirectory) registry.findFolder(item.name.orEmpty()) else null
                if (platform != null) roots.add(platform to item) else skipped.add("Unknown platform or item: ${item.name ?: "unnamed"}")
            }
        }
        var total = 0
        for ((platform, platformRoot) in roots) {
            val files = mutableListOf<ScanFile>()
            val images = mutableMapOf<String, String>()
            val visited = mutableSetOf<String>()

            fun visit(
                directory: DocumentFile,
                prefix: String,
                depth: Int,
            ) {
                job.ensureActive()
                if (depth > 32 || !visited.add(directory.uri.toString())) {
                    skipped.add("Skipped nested or repeated folder: $prefix")
                    return
                }
                for (item in directory.listFiles()) {
                    job.ensureActive()
                    val name = item.name
                    if (name == null) {
                        skipped.add("Unnamed item in $prefix")
                        continue
                    }
                    val path = if (prefix.isEmpty()) name else "$prefix/$name"
                    if (item.isDirectory) {
                        visit(item, path, depth + 1)
                    } else if (item.isFile) {
                        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        if (extension in LocalArt.EXTENSIONS) {
                            images.putIfAbsent(LocalArt.key(path), item.uri.toString())
                            continue
                        }
                        val descriptor =
                            if (extension in setOf("m3u", "cue", "gdi")) {
                                try {
                                    context.contentResolver.openInputStream(item.uri)?.use { stream ->
                                        val bytes = stream.readNBytes(65537)
                                        if (bytes.size > 65536) null else bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                                    }
                                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    null
                                }
                            } else {
                                null
                            }
                        files.add(ScanFile(path, item.uri.toString(), descriptor))
                    }
                }
            }
            visit(platformRoot, "", 0)
            val result = groupGames(files, platform.extensions)
            skipped.addAll(result.skipped.map { "${platform.name}: $it" })
            dao.upsertPlatform(PlatformEntity(platform.id, platform.name))
            for (game in result.games) {
                val primary = game.files.first()
                val digest = MessageDigest.getInstance("SHA-256").digest(primary.uri.toByteArray(Charsets.UTF_8))
                val id = digest.joinToString("") { "%02x".format(it) }
                val addedAt = dao.getGame(id)?.addedAt?.takeIf { it > 0 } ?: System.currentTimeMillis()
                dao.saveGameWithFiles(
                    GameEntity(id, platform.id, game.title, addedAt),
                    game.files.map { file -> GameFileEntity(file.uri, id, folderUri, file.path.substringAfterLast('/')) },
                )
                dao.setLocalArt(id, LocalArt.find(images, game.files.map { it.path }))
                total++
            }
        }
        return ScanSummary(total, skipped)
    }
}

/** Art next to a ROM: an image with the ROM's name in the same folder, for example `Game (USA).png`. */
object LocalArt {
    val EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

    /** The folder path and file name without extension, lower-cased: `nds/Game (USA)` for `nds/Game (USA).png`. */
    fun key(path: String): String = path.substringBeforeLast('.').lowercase(Locale.ROOT)

    /** The image for a game made of [paths], trying each file in order; null when there is none. */
    fun find(
        images: Map<String, String>,
        paths: List<String>,
    ): String? = paths.firstNotNullOfOrNull { images[key(it)] }
}
