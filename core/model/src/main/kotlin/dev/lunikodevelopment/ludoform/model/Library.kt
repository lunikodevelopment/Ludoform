package dev.lunikodevelopment.ludoform.model

/** A platform that holds at least one game in the local library. */
data class PlatformSummary(
    val id: String,
    val name: String,
    val gameCount: Int,
)

/**
 * One game as the screens show it. [artKey] is only set by design fixtures; real games show [art] when they have a
 * cover and their console's artwork otherwise.
 */
data class GameSummary(
    val id: String,
    val platformId: String,
    val rawTitle: String,
    val addedAt: Long,
    val fileCount: Int,
    val primaryFile: String,
    val artKey: String? = null,
    /** Total of finished play sessions, in milliseconds. */
    val playTimeMillis: Long = 0,
    /** Start of the latest play session, in epoch milliseconds; null when the game was never started from Ludoform. */
    val lastPlayedAt: Long? = null,
    /** The RomM ROM this game is, when the server has it. */
    val rommId: Int? = null,
    val rommSizeBytes: Long? = null,
    /** RomM's metadata name; shown instead of the cleaned-up file name when set (D34). */
    val metadataTitle: String? = null,
    /** The cover to show, as a `content://` or `file://` URI; null shows the console's art. */
    val art: String? = null,
) {
    /** Only on the RomM server: shown dimmed until it is downloaded. */
    val remoteOnly: Boolean get() = fileCount == 0 && rommId != null

    private val info: TitleInfo by lazy { TitleInfo.parse(rawTitle) }
    val title: String get() = metadataTitle ?: info.title
    val region: String? get() = info.region
}

data class LibrarySnapshot(
    val folders: List<String>,
    val platforms: List<PlatformSummary>,
    val games: List<GameSummary>,
) {
    val isEmpty: Boolean get() = games.isEmpty()

    fun platform(id: String): PlatformSummary? = platforms.firstOrNull { it.id == id }

    fun game(id: String): GameSummary? = games.firstOrNull { it.id == id }

    fun gamesFor(platformId: String?): List<GameSummary> = if (platformId == null) games else games.filter { it.platformId == platformId }

    companion object {
        val EMPTY = LibrarySnapshot(emptyList(), emptyList(), emptyList())
    }
}

/** Display title and region parsed from a ROM file name, without touching the stored name. */
data class TitleInfo(val title: String, val region: String?) {
    companion object {
        private val titleId = Regex("^[0-9A-Fa-f]{16}\\s+")
        private val bracketed = Regex("\\s*[\\[(][^\\])]*[\\])]")
        private val dumpSuffix = Regex("(\\.(piratelegit|legit|standard|decrypted|trimmed))+$", RegexOption.IGNORE_CASE)
        private val regions =
            mapOf(
                "usa" to "USA", "u" to "USA", "us" to "USA",
                "europe" to "Europe", "e" to "Europe", "eu" to "Europe",
                "japan" to "Japan", "j" to "Japan", "jp" to "Japan",
                "world" to "World", "w" to "World",
                "germany" to "Germany", "france" to "France", "spain" to "Spain", "italy" to "Italy",
                "australia" to "Australia", "korea" to "Korea", "canada" to "Canada", "netherlands" to "Netherlands",
            )

        fun parse(raw: String): TitleInfo {
            val withoutSuffix = raw.trim().replace(dumpSuffix, "")
            val region =
                Regex("[\\[(]([^\\])]*)[\\])]").findAll(withoutSuffix)
                    .flatMap { match -> match.groupValues[1].split(',', '/').map { it.trim().lowercase() } }
                    .firstNotNullOfOrNull { regions[it] }
            val cleaned =
                withoutSuffix.replace(titleId, "").replace(bracketed, "").trimEnd('.', ' ').replace(dumpSuffix, "").replace('_', ' ')
                    .replace(Regex("\\s+"), " ").trim().trimEnd('-', '.', ' ')
            return TitleInfo(cleaned.ifEmpty { raw.trim() }, region)
        }
    }
}
