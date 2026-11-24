package dev.lunikodevelopment.ludoform.launch

import java.util.Locale

/** A document already grouped with the game by the library scanner. */
data class LaunchFile(val uri: String, val name: String)

sealed interface GameFilesResult {
    data class Ready(val discs: List<LaunchFile>, val grants: List<LaunchFile>) : GameFilesResult

    data class Unavailable(val reason: String) : GameFilesResult
}

/** Selects launchable documents without confusing a descriptor's tracks with extra discs. */
fun selectGameFiles(
    files: List<LaunchFile>,
    playlist: String? = null,
): GameFilesResult {
    if (files.isEmpty()) return GameFilesResult.Unavailable("No game files are available. Try scanning your library again.")

    fun extension(file: LaunchFile) = file.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    val playlists = files.filter { extension(it) == "m3u" }
    if (playlists.size > 1) return GameFilesResult.Unavailable("This game has more than one playlist. Check its files and scan again.")
    if (playlists.isNotEmpty()) {
        if (playlist == null) return GameFilesResult.Unavailable("The game playlist could not be read. Check folder access and scan again.")
        val entries =
            playlist.lineSequence().map { it.trim().trimStart('\uFEFF') }
                .filter { it.isNotEmpty() && !it.startsWith('#') }.toList()
        if (entries.isEmpty()) return GameFilesResult.Unavailable("The game playlist has no discs.")
        val discs = mutableListOf<LaunchFile>()
        for (entry in entries) {
            val name = entry.replace('\\', '/').substringAfterLast('/')
            val matches = files.filter { it.name.equals(name, ignoreCase = true) }
            if (matches.size != 1 || extension(matches.single()) == "m3u") {
                return GameFilesResult.Unavailable("A disc in the playlist is missing or ambiguous: $entry")
            }
            discs.add(matches.single())
        }
        return GameFilesResult.Ready(discs.distinctBy { it.uri }, files.distinctBy { it.uri })
    }
    val descriptors = files.filter { extension(it) in setOf("cue", "gdi", "ccd", "mds") }
    if (descriptors.size > 1) return GameFilesResult.Unavailable("More than one disc descriptor was found. Add a playlist and scan again.")
    val launchFile =
        descriptors.singleOrNull() ?: files.singleOrNull()
            ?: return GameFilesResult.Unavailable("The game files are ambiguous. Check them and scan again.")
    return GameFilesResult.Ready(listOf(launchFile), files.distinctBy { it.uri })
}
