package dev.lunikodevelopment.ludoform.romm

import dev.lunikodevelopment.ludoform.data.library.LocalGameFile
import dev.lunikodevelopment.ludoform.data.library.RommLibraryChanges
import dev.lunikodevelopment.ludoform.data.library.RommPlatformEntity
import dev.lunikodevelopment.ludoform.data.library.RommRomEntity
import dev.lunikodevelopment.ludoform.library.PlatformDefinition
import dev.lunikodevelopment.ludoform.library.PlatformRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RommReconcilerTest {
    private val registry =
        PlatformRegistry(
            listOf(
                PlatformDefinition("gba", "Game Boy Advance", setOf("gba", "game boy advance"), setOf("gba", "zip")),
                PlatformDefinition("megadrive", "Mega Drive", setOf("megadrive", "genesis", "md"), setOf("md", "zip")),
                PlatformDefinition("psx", "PlayStation", setOf("psx", "ps1", "playstation"), setOf("cue", "chd")),
            ),
        )
    private val gba = RemotePlatform(1, "gba", "gba", "Game Boy Advance", 2)
    private val genesis = RemotePlatform(2, "genesis-slash-megadrive", "genesis_roms", "Sega Mega Drive/Genesis", 1)
    private val xbox = RemotePlatform(3, "series-x-s", "xbox", "Xbox Series X|S", 1)

    private fun rom(
        id: Int,
        platform: Int = 1,
        name: String = "Game $id.gba",
    ) = RemoteRom(id, platform, name, name.substringBeforeLast('.'), 1024, 1_000, "2026-11-25T10:00:0${id % 10}+00:00", false)

    private fun run(
        platforms: List<RemotePlatform>? = listOf(gba, genesis, xbox),
        fetched: List<RemoteRom> = emptyList(),
        identifiers: Set<Int>? = fetched.map { it.id }.toSet(),
        savedRoms: List<RommRomEntity> = emptyList(),
        savedPlatforms: List<RommPlatformEntity> = emptyList(),
        local: List<LocalGameFile> = emptyList(),
        remoteGames: Set<String> = savedRoms.mapNotNull { it.gameId }.filter { it.startsWith("romm:") }.toSet(),
    ): RommLibraryChanges =
        RommReconciler.reconcile(
            RommReconciler.Input(platforms, savedPlatforms, fetched, identifiers, savedRoms, local, remoteGames, 5_000),
            registry,
        )

    /** Applies [changes] to [saved] as the DAO would, for multi-step scenarios. */
    private fun after(
        saved: List<RommRomEntity>,
        changes: RommLibraryChanges,
    ): List<RommRomEntity> =
        (saved.associateBy { it.id } + changes.roms.associateBy { it.id }).filterKeys { it !in changes.removedRomIds }.values.toList()

    @Test
    fun platformsMapByFolderThenSlugAndNeverByExtension() {
        assertEquals("gba", RommReconciler.mapPlatform(gba, registry)?.id)
        assertEquals("megadrive", RommReconciler.mapPlatform(genesis, registry)?.id)
        assertNull(RommReconciler.mapPlatform(xbox, registry))
        assertEquals("psx", RommReconciler.mapPlatform(RemotePlatform(4, "ps", "PS1", "PlayStation", 0), registry)?.id)
    }

    @Test
    fun remoteOnlyRomsBecomeDimmedGamesAndLocalFilesAreMatched() {
        val changes =
            run(
                fetched = listOf(rom(10, name = "Pokemon Pink (USA).gba"), rom(11), rom(12, platform = 3, name = "Halo.xiso")),
                local = listOf(LocalGameFile("local-a", "gba", "Pokemon Pink (USA)", "Pokemon Pink (USA).gba")),
            )
        val byId = changes.roms.associateBy { it.id }
        assertEquals("local-a", byId.getValue(10).gameId)
        assertEquals("romm:11", byId.getValue(11).gameId)
        assertNull("Unmapped platform stays out of the library", byId.getValue(12).gameId)
        assertEquals(listOf("romm:11"), changes.remoteGames.map { it.id })
        assertEquals("gba", changes.remoteGames.single().platformId)
        assertEquals(listOf("gba"), changes.ludoformPlatforms.map { it.id })
    }

    @Test
    fun absenceFromAnIncrementalPageIsNotADeletion() {
        val first = run(fetched = listOf(rom(1), rom(2)))
        val saved = after(emptyList(), first)
        val second = run(fetched = listOf(rom(2)), identifiers = setOf(1, 2), savedRoms = saved)
        assertTrue(second.removedRomIds.isEmpty())
        assertTrue(second.removedRemoteGameIds.isEmpty())
    }

    @Test
    fun aRomIsDeletedOnlyAfterTwoSyncsWithoutIt() {
        var saved = after(emptyList(), run(fetched = listOf(rom(1), rom(2))))
        val once = run(identifiers = setOf(2), savedRoms = saved)
        assertTrue(once.removedRomIds.isEmpty())
        assertEquals(1, once.roms.single { it.id == 1 }.missingSyncs)
        saved = after(saved, once)
        val twice = run(identifiers = setOf(2), savedRoms = saved)
        assertEquals(listOf(1), twice.removedRomIds)
        assertEquals(listOf("romm:1"), twice.removedRemoteGameIds)
    }

    @Test
    fun aRomThatReturnsResetsItsMissingCount() {
        var saved = after(emptyList(), run(fetched = listOf(rom(1))))
        saved = after(saved, run(identifiers = emptySet(), savedRoms = saved))
        val back = run(identifiers = setOf(1), savedRoms = saved)
        assertEquals(0, back.roms.single().missingSyncs)
        assertTrue(back.removedRomIds.isEmpty())
    }

    @Test
    fun withoutIdentifiersNothingCountsAsMissing() {
        val saved = after(emptyList(), run(fetched = listOf(rom(1))))
        val changes = run(identifiers = null, savedRoms = saved)
        assertTrue(changes.roms.isEmpty())
        assertTrue(changes.removedRomIds.isEmpty())
    }

    @Test
    fun dontSyncRemovesRemoteGamesButKeepsRomsForLater() {
        val saved = after(emptyList(), run(fetched = listOf(rom(1))))
        val off = listOf(RommPlatformEntity(1, "gba", "gba", "Game Boy Advance", 1, "gba", sync = false))
        val changes = run(platforms = null, savedPlatforms = off, identifiers = null, savedRoms = saved)
        assertEquals(listOf("romm:1"), changes.removedRemoteGameIds)
        assertTrue(changes.removedRomIds.isEmpty())
        assertNull(changes.roms.single().gameId)
        val on =
            run(platforms = null, savedPlatforms = off.map { it.copy(sync = true) }, identifiers = null, savedRoms = after(saved, changes))
        assertEquals("romm:1", on.roms.single().gameId)
    }

    @Test
    fun aSyncSwitchSurvivesTheNextPlatformRefresh() {
        val saved = listOf(RommPlatformEntity(1, "gba", "gba", "Game Boy Advance", 1, "gba", sync = false))
        val changes = run(savedPlatforms = saved)
        assertEquals(false, changes.platforms.single { it.id == 1 }.sync)
    }

    @Test
    fun aDownloadedRomSwitchesFromRemoteToLocal() {
        val saved = after(emptyList(), run(fetched = listOf(rom(1))))
        val changes =
            run(identifiers = null, savedRoms = saved, local = listOf(LocalGameFile("local-1", "gba", "Game 1", "Game 1.gba")))
        assertEquals("local-1", changes.roms.single().gameId)
        assertEquals(listOf("romm:1"), changes.removedRemoteGameIds)
    }

    @Test
    fun clearingRemovesEverything() {
        val first = run(fetched = listOf(rom(1), rom(2)))
        val saved = after(emptyList(), first)
        val clear = run(platforms = emptyList(), savedPlatforms = first.platforms, identifiers = null, savedRoms = saved)
        assertEquals(setOf(1, 2, 3), clear.removedPlatformIds.toSet())
        assertEquals(setOf(1, 2), clear.removedRomIds.toSet())
        assertEquals(setOf("romm:1", "romm:2"), clear.removedRemoteGameIds.toSet())
    }

    @Test
    fun timestampsWithAndWithoutOffsetParse() {
        assertEquals(1_764_064_800_000, RommLibraryApi.parseTime("2025-11-25T10:00:00+00:00"))
        assertEquals(1_764_064_800_000, RommLibraryApi.parseTime("2025-11-25T10:00:00"))
        assertEquals(0, RommLibraryApi.parseTime("yesterday"))
    }
}
