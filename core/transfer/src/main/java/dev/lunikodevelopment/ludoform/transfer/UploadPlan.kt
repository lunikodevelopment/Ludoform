package dev.lunikodevelopment.ludoform.transfer

import java.util.Locale

/** One file of a game in the upload queue. */
data class UploadFile(val uri: String, val name: String, val size: Long)

/** Where a game goes on the upload server and whether it is complete (D37). Pure, so it is unit-tested. */
object UploadPlan {
    /** RomM's folder name for a platform the server does not have yet (RomM's own default names). */
    fun defaultFsSlug(platformId: String): String = dev.lunikodevelopment.ludoform.data.ImportPlan.defaultFsSlug(platformId)

    /**
     * Where the game's files go, relative to the RomM folder: straight into the platform folder for one file, into a
     * folder named after the game for several (RomM reads a folder as one game with several files).
     */
    fun remoteDir(
        platformDir: String,
        title: String,
        files: List<UploadFile>,
    ): String = if (files.size <= 1) platformDir else platformDir + "/" + safeName(title)

    /**
     * The platform folder inside the RomM folder. RomM accepts `<platform>/` directly in its `roms` folder, or
     * `<platform>/roms/` in its library folder; the second is used when the server already has it.
     */
    fun platformDir(
        fsSlug: String,
        hasRomsSubfolder: Boolean,
    ): String = if (hasRomsSubfolder) "$fsSlug/roms" else fsSlug

    /** A name every server accepts as a folder name. */
    fun safeName(name: String): String = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().trim('.').ifEmpty { "Game" }

    /** The name a file has on the server while it is being sent; renamed when complete. Hidden, so RomM skips it. */
    fun partName(name: String): String = ".$name.ludoform-part"

    fun encode(files: List<UploadFile>): String =
        files.joinToString(
            "\n",
        ) { listOf(it.uri, it.name, it.size.toString()).joinToString("\t") }

    fun decode(text: String): List<UploadFile> =
        text.lines().filter { it.isNotEmpty() }.mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) null else UploadFile(parts[0], parts[1], parts[2].toLongOrNull() ?: 0)
        }

    /**
     * Names the game needs but lacks: a file gone from the device ([exists] false), or a file a `.cue`, `.m3u` or
     * `.gdi` names that is not among [files]. [readText] gives a descriptor's text, null when unreadable.
     */
    fun missing(
        files: List<UploadFile>,
        exists: (UploadFile) -> Boolean,
        readText: (UploadFile) -> String?,
    ): List<String> {
        val names = files.map { it.name.lowercase(Locale.ROOT) }.toSet()
        val missing = linkedSetOf<String>()
        for (file in files) {
            if (!exists(file)) {
                missing.add(file.name)
                continue
            }
            val extension = file.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (extension !in DESCRIPTORS) continue
            val text = readText(file)
            if (text == null) {
                missing.add(file.name)
                continue
            }
            for (reference in references(extension, text)) {
                val name = reference.replace('\\', '/').substringAfterLast('/')
                if (name.lowercase(Locale.ROOT) !in names) missing.add(name)
            }
        }
        return missing.toList()
    }

    private val DESCRIPTORS = setOf("cue", "m3u", "gdi")

    private fun references(
        extension: String,
        text: String,
    ): List<String> =
        when (extension) {
            "m3u" -> text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith('#') }.toList()
            "cue" ->
                text.lineSequence().mapNotNull { line ->
                    Regex(
                        """(?i)^\s*FILE\s+(?:"([^"]+)"|(\S+))\s+\S+""",
                    ).find(line)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }
                }.toList()
            "gdi" ->
                text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.drop(1).mapNotNull { line ->
                    Regex("\"[^\"]*\"|\\S+").findAll(line).map { it.value.trim('"') }.toList().getOrNull(4)
                }.toList()
            else -> emptyList()
        }
}
