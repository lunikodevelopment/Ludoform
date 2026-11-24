package dev.lunikodevelopment.ludoform.library

import java.util.Locale

data class ScanFile(val path: String, val uri: String, val descriptor: String? = null)

data class GroupedGame(val title: String, val files: List<ScanFile>)

data class GroupResult(val games: List<GroupedGame>, val skipped: List<String>)

/** Groups only complete descriptor sets; missing or unreadable members are reported, never deleted. */
fun groupGames(
    files: List<ScanFile>,
    allowedExtensions: Set<String>,
): GroupResult {
    fun key(path: String) = path.replace('\\', '/').lowercase(Locale.ROOT)

    fun extension(path: String) = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val skipped = mutableListOf<String>()
    val groupedByPath = files.groupBy { key(it.path) }
    val unique = groupedByPath.filterValues { it.size == 1 }.mapValues { it.value.single() }
    groupedByPath.filterValues { it.size > 1 }.values.flatten().forEach { skipped.add("Ambiguous path: ${it.path}") }
    val consumed = mutableSetOf<String>()
    val blocked = mutableSetOf<String>()
    val games = mutableListOf<GroupedGame>()
    val descriptors = setOf("m3u", "cue", "gdi", "ccd", "mds")

    fun resolvePath(
        owner: String,
        raw: String,
    ): String? {
        val reference = raw.trim().trim('"').replace('\\', '/')
        if (reference.startsWith('/') || reference.contains(':')) return null
        val components = owner.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }.toMutableList()
        for (part in reference.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (components.isEmpty()) return null else components.removeAt(components.lastIndex)
                else -> components.add(part)
            }
        }
        return key(components.joinToString("/"))
    }

    fun references(file: ScanFile): List<String>? {
        val ext = extension(file.path)
        val text = file.descriptor
        val names =
            when (ext) {
                "m3u" -> text?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith('#') }?.toList()
                "cue" ->
                    text?.lineSequence()?.mapNotNull {
                        Regex("(?i)^\\s*FILE\\s+(?:\"([^\"]+)\"|(\\S+))\\s+\\S+").find(it)?.let {
                                m ->
                            m.groupValues[1].ifEmpty { m.groupValues[2] }
                        }
                    }?.toList()
                "gdi" -> {
                    val lines = text?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() }?.toList() ?: return null
                    val expected = lines.firstOrNull()?.toIntOrNull() ?: return null
                    if (expected <= 0 || lines.size - 1 != expected) return null
                    lines.drop(1).map { line ->
                        val tokens = Regex("\"[^\"]*\"|\\S+").findAll(line).map { it.value.trim('"') }.toList()
                        if (tokens.size < 6 || tokens.take(4).any { it.toLongOrNull() == null }) return null
                        tokens[4]
                    }
                }
                "ccd" -> listOf(file.path.substringAfterLast('/').substringBeforeLast('.') + ".img")
                "mds" -> listOf(file.path.substringAfterLast('/').substringBeforeLast('.') + ".mdf")
                else -> return emptyList()
            } ?: return null
        if (names.isEmpty()) return null
        return names.map { resolvePath(file.path, it) ?: return null }
    }

    fun collect(start: ScanFile): List<ScanFile>? {
        val found = linkedMapOf<String, ScanFile>()
        val visiting = mutableSetOf<String>()

        fun walk(file: ScanFile): Boolean {
            val fileKey = key(file.path)
            if (fileKey in visiting) return false
            if (fileKey in found) return true
            visiting.add(fileKey)
            found[fileKey] = file
            val refs = references(file)
            if (refs == null) {
                blocked.addAll(found.keys)
                return false
            }
            var complete = true
            for (ref in refs) {
                val child = unique[ref]
                if (child == null) {
                    blocked.add(ref)
                    complete = false
                } else if (!walk(child)) {
                    complete = false
                }
            }
            if (!complete) {
                blocked.addAll(found.keys)
                visiting.remove(fileKey)
                return false
            }
            if (extension(file.path) == "ccd") {
                val optional = resolvePath(file.path, file.path.substringAfterLast('/').substringBeforeLast('.') + ".sub")
                if (optional != null) unique[optional]?.let { found[optional] = it }
            }
            visiting.remove(fileKey)
            return true
        }
        return if (walk(start)) found.values.toList() else null
    }

    val ordered = unique.values.sortedWith(compareBy<ScanFile> { if (extension(it.path) == "m3u") 0 else 1 }.thenBy { key(it.path) })
    for (file in ordered) {
        val pathKey = key(file.path)
        val ext = extension(file.path)
        if (pathKey in consumed || pathKey in blocked || ext !in allowedExtensions || ext !in descriptors) continue
        val group = collect(file)
        if (group == null || group.any { key(it.path) in consumed }) {
            skipped.add("Incomplete or overlapping set: ${file.path}")
            continue
        }
        consumed.addAll(group.map { key(it.path) })
        games.add(GroupedGame(file.path.substringAfterLast('/').substringBeforeLast('.'), group))
    }
    for (file in ordered) {
        val pathKey = key(file.path)
        if (pathKey in consumed || pathKey in blocked) continue
        if (extension(file.path) in descriptors) {
            if (extension(file.path) !in allowedExtensions) skipped.add("Unsupported file: ${file.path}")
            continue
        }
        if (extension(file.path) in allowedExtensions) {
            games.add(GroupedGame(file.path.substringAfterLast('/').substringBeforeLast('.'), listOf(file)))
        } else {
            skipped.add("Unsupported file: ${file.path}")
        }
    }
    return GroupResult(games, skipped)
}
