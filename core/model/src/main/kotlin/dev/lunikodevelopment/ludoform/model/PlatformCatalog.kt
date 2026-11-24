package dev.lunikodevelopment.ludoform.model

/**
 * Presentation facts for platforms in the registry: a short label, maker and launch year, and the key of the
 * console icon and hero artwork from Ludoform's design pack. Unknown platforms use the collections artwork.
 */
data class PlatformInfo(
    val shortName: String,
    val maker: String?,
    val year: Int?,
    val artKey: String,
)

object PlatformCatalog {
    private val known =
        mapOf(
            "nes" to PlatformInfo("NES", "Nintendo", 1983, "nes"),
            "snes" to PlatformInfo("SNES", "Nintendo", 1990, "snes"),
            "gb" to PlatformInfo("Game Boy", "Nintendo", 1989, "gbc"),
            "gbc" to PlatformInfo("GBC", "Nintendo", 1998, "gbc"),
            "gba" to PlatformInfo("GBA", "Nintendo", 2001, "gba"),
            "nds" to PlatformInfo("DS", "Nintendo", 2004, "ds"),
            "3ds" to PlatformInfo("3DS", "Nintendo", 2011, "3ds"),
            "n64" to PlatformInfo("N64", "Nintendo", 1996, "n64"),
            "gc" to PlatformInfo("GameCube", "Nintendo", 2001, "gamecube"),
            "wii" to PlatformInfo("Wii", "Nintendo", 2006, "wii"),
            "psx" to PlatformInfo("PlayStation", "Sony", 1994, "playstation"),
            "ps2" to PlatformInfo("PS2", "Sony", 2000, "ps2"),
            "psp" to PlatformInfo("PSP", "Sony", 2004, "psp"),
            "dreamcast" to PlatformInfo("Dreamcast", "Sega", 1998, "dreamcast"),
            "megadrive" to PlatformInfo("Mega Drive", "Sega", 1988, "genesis"),
        )

    fun info(
        id: String,
        name: String = id,
    ): PlatformInfo = known[id] ?: PlatformInfo(name, null, null, "collections")
}
