package dev.lunikodevelopment.ludoform.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtRulesTest {
    private val big = "/assets/romm/resources/roms/241/79/cover/big.png?ts=1"
    private val game =
        ArtRules.Game(
            platformId = "nds",
            primaryFile = "Animal Crossing - Wild World (USA) (Rev 1) (Wii U Virtual Console).nds",
            rommFileName = null,
            title = "Animal Crossing - Wild World (USA)",
            rommCover = big,
            onDevice = true,
            lastPlayedAt = null,
            localUri = null,
            file = null,
            sourceKey = null,
            checkedAt = 0,
        )
    private val now = 10 * ArtRules.RECHECK_MILLIS

    @Test
    fun libretroNamesDropTrailingTags() {
        assertEquals(
            listOf(
                "Animal Crossing - Wild World (USA) (Rev 1) (Wii U Virtual Console)",
                "Animal Crossing - Wild World (USA) (Rev 1)",
                "Animal Crossing - Wild World (USA)",
            ),
            ArtRules.libretroNames("Animal Crossing - Wild World (USA) (Rev 1) (Wii U Virtual Console)"),
        )
        assertEquals(listOf("Tetris"), ArtRules.libretroNames("Tetris"))
        assertEquals(listOf("Q_bert (USA)"), ArtRules.libretroNames("Q*bert (USA)"))
    }

    @Test
    fun libretroUrlsPointAtTheThumbnailRepositories() {
        assertEquals(
            "https://raw.githubusercontent.com/libretro-thumbnails/Nintendo_-_Nintendo_DS/master/Named_Boxarts/" +
                "Animal%20Crossing%20-%20Wild%20World%20%28USA%29%20%28Rev%201%29.png",
            ArtRules.libretroUrls(game)[1],
        )
        assertTrue(ArtRules.libretroUrls(game.copy(platformId = "switch")).isEmpty())
    }

    @Test
    fun rommUrlsResolveAgainstTheServer() {
        assertEquals(
            "https://romm.example/assets/romm/resources/roms/1/cover/big.png",
            ArtRules.rommUrl("https://romm.example/", "/assets/romm/resources/roms/1/cover/big.png"),
        )
        assertEquals(
            "https://romm.example/assets/romm/resources/roms/1/cover/big.png",
            ArtRules.rommUrl("https://romm.example", "roms/1/cover/big.png"),
        )
        assertEquals("https://images.igdb.com/x.jpg", ArtRules.rommUrl("https://romm.example", "https://images.igdb.com/x.jpg"))
    }

    @Test
    fun localArtIsNeverReplaced() {
        assertFalse(ArtRules.needsWork(game.copy(localUri = "content://x"), now))
    }

    @Test
    fun aNewOrChangedRommCoverIsFetched() {
        assertTrue(ArtRules.needsWork(game, now))
        assertFalse(ArtRules.needsWork(game.copy(file = "/a.webp", sourceKey = big, checkedAt = now), now))
        assertTrue(ArtRules.needsWork(game.copy(file = "/a.webp", sourceKey = "libretro-url", checkedAt = now), now))
    }

    @Test
    fun serverOnlyGamesGetTheSmallCoverUntilDownloaded() {
        val remote = game.copy(onDevice = false, primaryFile = null)
        assertEquals("/assets/romm/resources/roms/241/79/cover/small.png?ts=1", ArtRules.rommCoverFor(remote))
        val small = ArtRules.rommCoverFor(remote)
        assertFalse(ArtRules.needsWork(remote.copy(file = "/a.webp", sourceKey = small, checkedAt = now), now))
        // Once on the device, the big one replaces it.
        assertTrue(ArtRules.needsWork(game.copy(file = "/a.webp", sourceKey = small, checkedAt = now), now))
    }

    @Test
    fun gamesWithoutArtAreAskedAgainAfterThirtyDays() {
        val none = game.copy(rommCover = null, checkedAt = now)
        assertFalse(ArtRules.needsWork(none, now + 1000))
        assertTrue(ArtRules.needsWork(none, now + ArtRules.RECHECK_MILLIS + 1))
        assertNull(ArtRules.rommCoverFor(none))
    }

    @Test
    fun playedGamesComeFirst() {
        val order =
            listOf(
                game.copy(onDevice = false, title = "remote"),
                game.copy(title = "local"),
                game.copy(title = "old", lastPlayedAt = 10),
                game.copy(title = "new", lastPlayedAt = 20),
            ).sortedBy(ArtRules::priority).map { it.title }
        assertEquals(listOf("new", "old", "local", "remote"), order)
    }
}
