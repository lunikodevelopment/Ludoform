package dev.lunikodevelopment.ludoform.ui

import dev.lunikodevelopment.ludoform.R
import dev.lunikodevelopment.ludoform.model.GameSummary
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.model.PlatformSummary

/**
 * Debug-only design fixtures: the boards' placeholder games, used to compare screens with the boards. They are only
 * shown when the debug build is started with `--ez fixtures true` and are never written to the library database.
 */
object DesignFixtures {
    const val AVAILABLE = true

    private fun game(
        id: String,
        platform: String,
        title: String,
        added: Long,
        art: String,
    ) = GameSummary("fixture-$id", platform, title, added, 1, "$title.iso", art)

    val snapshot: LibrarySnapshot =
        LibrarySnapshot(
            folders = listOf("fixture://roms"),
            platforms =
                listOf(
                    PlatformSummary("gc", "GameCube", 8),
                    PlatformSummary("dreamcast", "Dreamcast", 3),
                    PlatformSummary("gba", "Game Boy Advance", 2),
                    PlatformSummary("nds", "Nintendo DS", 1),
                    PlatformSummary("psx", "PlayStation", 1),
                ),
            games =
                listOf(
                    game("orbital", "gc", "Orbital Garden", 9, "game_orbital2"),
                    game("ember", "gc", "Ember Vale", 3, "cover_ember"),
                    game("tidefield", "gc", "Tidefield", 8, "cover_tidefield"),
                    game("skygarden", "gc", "Skygarden", 2, "game_orbital"),
                    game("iron", "gc", "Iron Grove", 1, "cover_softcircuit"),
                    game("lumen", "gc", "Lumen Rally", 4, "game_orbital"),
                    game("frostline", "gc", "Frostline", 7, "game_frostline"),
                    game("starquarry", "gc", "Starquarry", 5, "hero_recent"),
                    game("soft", "dreamcast", "Soft Circuit", 6, "cover_softcircuit"),
                    game("sunspine", "dreamcast", "Sunspine", 2, "hero_favorites"),
                    game("mossreach", "dreamcast", "Mossreach", 1, "game_frostline"),
                    game("pocket", "gba", "Pocket Comet", 2, "cover_tidefield"),
                    game("lantern", "gba", "Lantern Days", 1, "cover_ember"),
                    game("twin", "nds", "Twin Petals", 1, "game_orbital2"),
                    game("ridge", "psx", "Ridge of Glass", 1, "game_frostline"),
                ),
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
