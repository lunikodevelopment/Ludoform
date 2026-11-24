package dev.lunikodevelopment.ludoform.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameGrouperTest {
    private fun f(
        path: String,
        content: String? = null,
    ) = ScanFile(path, "uri:$path", content)

    private val discExtensions = setOf("m3u", "cue", "gdi", "ccd", "mds", "chd", "zip", "7z")

    @Test
    fun playlistIncludesCueAndTracksAcrossDirectories() {
        val files =
            listOf(
                f("Set.m3u", "disc1/game.cue\ndisc2/game.chd\n"),
                f("disc1/game.cue", "FILE \"track.bin\" BINARY\n TRACK 01 MODE1/2352"),
                f("disc1/track.bin"),
                f("disc2/game.chd"),
            )
        val result = groupGames(files, discExtensions)
        assertEquals(1, result.games.size)
        assertEquals(files.map { it.path }.toSet(), result.games.single().files.map { it.path }.toSet())
        assertTrue(result.skipped.isEmpty())
    }

    @Test
    fun missingReferencedFilesDoNotCreateIncompleteGame() {
        val result = groupGames(listOf(f("game.cue", "FILE \"missing.bin\" BINARY")), discExtensions)
        assertTrue(result.games.isEmpty())
        assertTrue(result.skipped.single().contains("Incomplete"))
    }

    @Test
    fun groupsGdiCcdAndMdsWithTheirMembers() {
        val files =
            listOf(
                f("dc.gdi", "2\n1 0 4 2352 track01.bin 0\n2 450 0 2352 \"track 02.raw\" 0"),
                f("track01.bin"),
                f("track 02.raw"),
                f("ps.ccd"),
                f("ps.img"),
                f("ps.sub"),
                f("other.mds"),
                f("other.mdf"),
            )
        val games = groupGames(files, discExtensions).games.associateBy { it.title }
        assertEquals(setOf("dc", "ps", "other"), games.keys)
        assertEquals(3, games.getValue("dc").files.size)
        assertEquals(3, games.getValue("ps").files.size)
        assertEquals(2, games.getValue("other").files.size)
    }

    @Test
    fun reportsAmbiguousPathsAndTreatsArchivesAsSingleGames() {
        val result = groupGames(listOf(f("a.zip"), f("A.ZIP"), f("b.7z")), discExtensions)
        assertEquals(listOf("b"), result.games.map { it.title })
        assertEquals(2, result.skipped.size)
    }

    @Test
    fun malformedGdiAndIncompletePlaylistDoNotPublishPartialSets() {
        val result =
            groupGames(
                listOf(
                    f("bad.gdi", "2\n1 0 4 2352 track.bin 0"),
                    f("track.bin"),
                    f("set.m3u", "missing.chd\npart.cue"),
                    f("part.cue", "FILE \"part.bin\" BINARY"),
                    f("part.bin"),
                ),
                discExtensions,
            )
        assertTrue(result.games.isEmpty())
        assertTrue(result.skipped.any { it.contains("bad.gdi") })
        assertTrue(result.skipped.any { it.contains("set.m3u") })
    }
}
