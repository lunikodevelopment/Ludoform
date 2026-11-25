package dev.lunikodevelopment.ludoform.media

import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Where a game's cover comes from (D34). An image next to the ROM always wins and is never downloaded over. Otherwise
 * RomM's cover, then a libretro thumbnail; a game with none of these keeps its console's art.
 */
object ArtRules {
    /** A game without art found is asked for again after this long. */
    val RECHECK_MILLIS: Long = TimeUnit.DAYS.toMillis(30)

    /** Covers are cached no larger than this on their longer side; the Stage's largest cover is about this size. */
    const val MAX_SIDE = 512

    /** What the art job knows about one game. */
    data class Game(
        val platformId: String,
        /** File name of the game's first file on the device, with extension; null for a game only on RomM. */
        val primaryFile: String?,
        val rommFileName: String?,
        val title: String,
        val rommCover: String?,
        val onDevice: Boolean,
        val lastPlayedAt: Long?,
        val localUri: String?,
        val file: String?,
        val sourceKey: String?,
        val checkedAt: Long,
    )

    /** The RomM cover to ask for: the small one for a game only on the server, which may never be downloaded. */
    fun rommCoverFor(game: Game): String? {
        val cover = game.rommCover ?: return null
        return if (game.onDevice) cover else cover.replace("/cover/big.", "/cover/small.")
    }

    /** Whether the art job should look for this game's cover now. */
    fun needsWork(
        game: Game,
        now: Long,
    ): Boolean {
        if (game.localUri != null) return false
        val romm = rommCoverFor(game)
        // A RomM cover that is new or changed (or replaces a libretro one, or the small one once downloaded) is fetched.
        if (romm != null && game.sourceKey != romm) return true
        if (game.file != null) return false
        return game.checkedAt == 0L || now - game.checkedAt > RECHECK_MILLIS
    }

    /** Played games first (latest first), then games on the device, then the rest. */
    fun priority(game: Game): Long =
        when {
            game.lastPlayedAt != null -> -game.lastPlayedAt
            game.onDevice -> 0
            else -> 1
        }

    /** RomM's cover as a full URL: [cover] is a server path or already a URL. Null when it is neither. */
    fun rommUrl(
        base: String,
        cover: String,
    ): String? =
        when {
            cover.startsWith("https://") || cover.startsWith("http://") -> cover
            cover.startsWith("//") -> "https:$cover"
            cover.startsWith("/") -> base.trimEnd('/') + cover
            else -> base.trimEnd('/') + "/assets/romm/resources/" + cover
        }

    /** libretro-thumbnails repository per Ludoform platform; null for a platform it does not cover. */
    fun libretroSystem(platformId: String): String? =
        when (platformId) {
            "nes" -> "Nintendo - Nintendo Entertainment System"
            "snes" -> "Nintendo - Super Nintendo Entertainment System"
            "gb" -> "Nintendo - Game Boy"
            "gbc" -> "Nintendo - Game Boy Color"
            "gba" -> "Nintendo - Game Boy Advance"
            "nds" -> "Nintendo - Nintendo DS"
            "3ds" -> "Nintendo - Nintendo 3DS"
            "n64" -> "Nintendo - Nintendo 64"
            "gc" -> "Nintendo - GameCube"
            "wii" -> "Nintendo - Wii"
            "psx" -> "Sony - PlayStation"
            "ps2" -> "Sony - PlayStation 2"
            "psp" -> "Sony - PlayStation Portable"
            "dreamcast" -> "Sega - Dreamcast"
            "megadrive" -> "Sega - Mega Drive - Genesis"
            else -> null
        }

    /**
     * Names to try, best first: the file's own name, then with trailing tags dropped one at a time down to the first
     * one (`Game (USA) (Rev 1) (Virtual Console)` → `Game (USA) (Rev 1)` → `Game (USA)`). libretro names its
     * thumbnails after No-Intro and Redump names, which a dumped file often has with extra tags.
     */
    fun libretroNames(fileName: String): List<String> {
        val names = mutableListOf(fileName.trim())
        var name = fileName.trim()
        while (true) {
            val match = Regex("""^(.*\S)\s*[(\[][^()\[\]]*[)\]]$""").find(name) ?: break
            val shorter = match.groupValues[1]
            if (!Regex("""[(\[]""").containsMatchIn(shorter)) break
            names.add(shorter)
            name = shorter
        }
        return names.map(::libretroSafe).distinct()
    }

    /** libretro replaces these characters with `_` in thumbnail names. */
    fun libretroSafe(name: String): String = name.replace(Regex("""[&*/:`<>?\\|"]"""), "_")

    fun libretroUrl(
        system: String,
        name: String,
    ): String {
        val repository = system.replace(' ', '_')
        return "https://raw.githubusercontent.com/libretro-thumbnails/" + encode(repository) + "/master/Named_Boxarts/" +
            encode(name) + ".png"
    }

    /** All libretro URLs to try for [game], best first. */
    fun libretroUrls(game: Game): List<String> {
        val system = libretroSystem(game.platformId) ?: return emptyList()
        val base = game.primaryFile?.substringBeforeLast('.') ?: game.rommFileName ?: game.title
        return libretroNames(base).map { libretroUrl(system, it) }
    }

    private fun encode(segment: String): String = URLEncoder.encode(segment, Charsets.UTF_8).replace("+", "%20")
}
