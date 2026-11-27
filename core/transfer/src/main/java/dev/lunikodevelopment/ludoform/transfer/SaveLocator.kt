package dev.lunikodevelopment.ludoform.transfer

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import dev.lunikodevelopment.ludoform.data.library.GameFileEntity
import java.util.Locale

/** A save file next to its ROM, or where one would go. */
data class SaveSlot(
    /** The folder (a document in the library tree) that holds the ROM. */
    val parent: Uri,
    /** The save's file name, existing or the one a download would create. */
    val fileName: String,
    val extension: String,
    val existing: Uri?,
    val lastModified: Long,
    val size: Long,
)

/**
 * Finds a game's save next to its ROM: same name, a save extension of the platform. Saves named by game ID
 * (Dolphin memory cards, PPSSPP folders, 3DS title IDs) and saves inside another app's private folder are out of
 * reach and not synced.
 */
object SaveLocator {
    private val extensions =
        mapOf(
            "nds" to listOf("sav", "dsv"),
            "gba" to listOf("sav", "srm"),
            "gb" to listOf("sav", "srm"),
            "gbc" to listOf("sav", "srm"),
            "nes" to listOf("sav", "srm"),
            "snes" to listOf("srm", "sav"),
            "megadrive" to listOf("srm", "sav"),
            "n64" to listOf("srm", "eep", "sra", "fla"),
            "psx" to listOf("srm", "mcd"),
        )

    fun supports(platformId: String): Boolean = platformId in extensions

    /** The save types of [platformId], preferred first. */
    fun extensionsFor(platformId: String): List<String> = extensions[platformId].orEmpty()

    fun locate(
        resolver: ContentResolver,
        platformId: String,
        files: List<GameFileEntity>,
    ): SaveSlot? {
        val wanted = extensions[platformId] ?: return null
        // The ROM a save is named after: a disc descriptor for disc games, else the only file.
        val descriptor = files.firstOrNull { it.displayName.substringAfterLast('.').lowercase(Locale.ROOT) in setOf("m3u", "cue") }
        val rom = descriptor ?: files.singleOrNull() ?: return null
        val romUri = Uri.parse(rom.uri)
        val tree = Uri.parse(rom.folderUri)
        val documentId = runCatching { DocumentsContract.getDocumentId(romUri) }.getOrNull() ?: return null
        if ('/' !in documentId) return null
        val parentId = documentId.substringBeforeLast('/')
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
        val base = rom.displayName.substringBeforeLast('.')
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val siblings = mutableMapOf<String, Triple<Uri, Long, Long>>()
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1) ?: continue
                if (!name.substringBeforeLast('.').equals(base, ignoreCase = true)) continue
                siblings[name.substringAfterLast('.', "").lowercase(Locale.ROOT)] =
                    Triple(DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(0)), cursor.getLong(2), cursor.getLong(3))
            }
        }
        val found = wanted.firstOrNull { it in siblings }
        val extension = found ?: wanted.first()
        val existing = found?.let { siblings.getValue(it) }
        return SaveSlot(parent, "$base.$extension", extension, existing?.first, existing?.second ?: 0, existing?.third ?: 0)
    }
}
