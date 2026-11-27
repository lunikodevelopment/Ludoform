package dev.lunikodevelopment.ludoform.ui

import dev.lunikodevelopment.ludoform.R
import dev.lunikodevelopment.ludoform.model.GameSummary
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.model.PlatformSummary

/**
 * The showcase build's placeholder library (D50). Every game is invented; only the file names follow the real dump
 * naming schemes (No-Intro for cartridges, Redump for discs: region, languages, revision, disc number), so the title
 * cleanup, regions and disc sets show as they would with a real library. Nothing here is a ROM.
 */
object DesignFixtures {
    const val AVAILABLE = true

    /** Shown without the `fixtures` extra: only in the showcase build. */
    const val ALWAYS = true

    private const val HOUR = 3_600_000L
    private const val DAY = 24 * HOUR

    private val now = System.currentTimeMillis()

    private val platformNames =
        linkedMapOf(
            "gc" to "GameCube",
            "psx" to "PlayStation",
            "gba" to "Game Boy Advance",
            "snes" to "Super Nintendo",
            "dreamcast" to "Dreamcast",
            "nds" to "Nintendo DS",
            "ps2" to "PlayStation 2",
            "n64" to "Nintendo 64",
            "3ds" to "Nintendo 3DS",
            "wii" to "Wii",
            "psp" to "PlayStation Portable",
            "megadrive" to "Mega Drive",
            "gbc" to "Game Boy Color",
            "gb" to "Game Boy",
            "nes" to "Nintendo Entertainment System",
        )

    /** [file] is the name on disk; the title shown is cleaned from it like a scanned game's. */
    private fun game(
        platform: String,
        file: String,
        addedDaysAgo: Int,
        art: String? = null,
        files: Int = 1,
        playedHoursAgo: Long? = null,
        playMinutes: Long = 0,
    ) = GameSummary(
        id = "fixture-" + file.substringBeforeLast('.').lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'),
        platformId = platform,
        rawTitle = file.substringBeforeLast('.'),
        addedAt = now - addedDaysAgo * DAY,
        fileCount = files,
        primaryFile = file,
        artKey = art,
        playTimeMillis = playMinutes * 60_000,
        lastPlayedAt = playedHoursAgo?.let { now - it * HOUR },
    )

    private val games: List<GameSummary> =
        listOf(
            // GameCube and Wii: Redump dumps kept as RVZ, Dolphin's compressed format.
            game("gc", "Orbital Garden (USA).rvz", 2, "game_orbital2", playedHoursAgo = 2, playMinutes = 1_470),
            game("gc", "Ember Vale (Europe) (En,Fr,De,Es,It).rvz", 30, "cover_ember", playedHoursAgo = 50, playMinutes = 610),
            game("gc", "Tidefield (USA).rvz", 12, "cover_tidefield"),
            game("gc", "Skygarden (Japan).rvz", 41, "game_orbital"),
            game("gc", "Iron Grove (USA) (Rev 1).iso", 55, "cover_softcircuit"),
            game("gc", "Lumen Rally (USA).rvz", 19),
            game("gc", "Frostline (USA).rvz", 6, "game_frostline", playedHoursAgo = 26, playMinutes = 205),
            // A two-disc set grouped by its playlist: the .m3u and both discs.
            game("gc", "Starquarry (USA).m3u", 8, "hero_recent", files = 3),
            game("wii", "Paper Lanterns (Europe) (En,Fr,De,Es,It).rvz", 14),
            game("wii", "Tidefield Tennis (USA).wbfs", 60),
            // PlayStation: Redump discs as CHD, a cue sheet with its track, and a multi-disc playlist.
            game("psx", "Ridge of Glass (USA).m3u", 3, "game_frostline", files = 3, playedHoursAgo = 5, playMinutes = 1_960),
            game("psx", "Saltwind (Europe) (En,Fr,De).chd", 22),
            game("psx", "Neon Ferry (Japan).cue", 35, files = 2),
            game("psx", "Kettle Knights (USA) (Rev 1).chd", 9),
            game("ps2", "Cinder Choir (USA).chd", 11),
            game("ps2", "Glass Orchard (Europe) (En,Fr,De,Es,It).iso", 48),
            game("psp", "Frostbound Tactics (USA).cso", 17),
            game("psp", "Pocket Atlas (Europe) (En,Fr,De,Es,It).iso", 70),
            // Dreamcast: a GDI with its tracks, and a CHD.
            game("dreamcast", "Soft Circuit (USA).gdi", 4, "cover_softcircuit", files = 4, playedHoursAgo = 120, playMinutes = 95),
            game("dreamcast", "Sunspine (Europe) (En,Fr,De,Es).chd", 27, "hero_favorites"),
            game("dreamcast", "Mossreach (Japan).chd", 40, "game_frostline"),
            // Cartridges: No-Intro names, with regions, languages, revisions and compatibility flags.
            game("gba", "Pocket Comet (USA).gba", 1, "cover_tidefield", playedHoursAgo = 1, playMinutes = 340),
            game("gba", "Lantern Days (Europe) (En,Fr,De,Es,It).gba", 25, "cover_ember"),
            game("gba", "Quillfeather Academy (USA) (Rev 2).gba", 33),
            game("nds", "Twin Petals (USA) (En,Fr,Es).nds", 7, "game_orbital2"),
            game("nds", "Clockwork Picnic (Europe) (En,Fr,De,Es,It).nds", 52),
            game("3ds", "Harbor Lights 3D (USA).3ds", 15),
            game("3ds", "Petalfall (Europe) (En,Fr,De,Es,It).3ds", 44),
            game("n64", "Ridgeback Racers (USA).z64", 13, playedHoursAgo = 200, playMinutes = 48),
            game("n64", "Hollow Bell (Japan).z64", 66),
            game("snes", "Moss & Mortar (Japan) (Rev 1).sfc", 20),
            game("snes", "Skyhook Derby (USA).sfc", 37),
            game("snes", "Velvet Comet (Europe) (En,Fr,De).sfc", 58),
            game("megadrive", "Turbo Tortoise (USA, Europe).md", 29),
            game("megadrive", "Pixel Paladin (Japan).md", 63),
            game("gbc", "Coral Courier (USA, Europe) (SGB Enhanced) (GB Compatible).gbc", 18),
            game("gb", "Pebble Patrol (World).gb", 46),
            game("nes", "Tiny Tactician (USA).nes", 39),
            game("nes", "Lantern Keep (Europe).nes", 75),
        )

    val snapshot: LibrarySnapshot =
        LibrarySnapshot(
            folders = listOf("fixture://roms"),
            platforms =
                platformNames.mapNotNull { (id, name) ->
                    val count = games.count { it.platformId == id }
                    if (count == 0) null else PlatformSummary(id, name, count)
                },
            games = games,
        )

    fun art(key: String): Int? =
        when (key) {
            "cover_ember" -> R.drawable.fixture_cover_ember
            "cover_softcircuit" -> R.drawable.fixture_cover_softcircuit
            "cover_tidefield" -> R.drawable.fixture_cover_tidefield
            "game_frostline" -> R.drawable.fixture_game_frostline
            "game_orbital" -> R.drawable.fixture_game_orbital
            "game_orbital2" -> R.drawable.fixture_game_orbital2
            "hero_favorites" -> R.drawable.fixture_hero_favorites
            "hero_recent" -> R.drawable.fixture_hero_recent
            else -> null
        }
}
