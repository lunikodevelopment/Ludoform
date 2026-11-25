package dev.lunikodevelopment.ludoform.romm

import dev.lunikodevelopment.ludoform.romm.SaveRules.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveRulesTest {
    private val server = RemoteSave(5, 1, "Game.sav", 8192, "2026-11-25T10:00:00+00:00", 1, "melonDS")
    private val later = server.copy(updatedAt = "2026-11-25T12:00:00+00:00", updatedAtMillis = 2)

    private fun decide(
        local: String?,
        remote: RemoteSave?,
        baseline: String? = "base",
        remoteHash: String? = null,
    ) = SaveRules.decide(local, remote, remoteHash, baseline, server.id, server.updatedAt)

    @Test
    fun onlyOneSideChangedMovesTheNewest() {
        assertEquals(Action.Upload, decide("new", server))
        assertEquals(Action.Download(later), decide("base", later))
        assertEquals(Action.Nothing, decide("base", server))
    }

    @Test
    fun bothChangedIsAConflictNeverAnOverwrite() {
        assertEquals(Action.Conflict(later), decide("new", later))
        assertEquals(Action.Conflict(server), decide("new", server, baseline = null))
    }

    @Test
    fun identicalContentNeedsNothingEvenWithoutABaseline() {
        assertEquals(Action.Nothing, decide("abc", later, baseline = null, remoteHash = "ABC"))
    }

    @Test
    fun aSaveOnOneSideOnlyIsCopied() {
        assertEquals(Action.Upload, decide("new", null, baseline = null))
        assertEquals(Action.Download(server), decide(null, server, baseline = null))
        assertEquals(Action.Nothing, decide(null, null, baseline = null))
    }

    @Test
    fun aDifferentServerSaveCountsAsAChange() {
        val other = server.copy(id = 9)
        assertEquals(Action.Download(other), decide("base", other))
    }

    @Test
    fun theMatchingTypeAndNewestServerSaveIsPicked() {
        val dsv = later.copy(id = 7, fileName = "Game.dsv", updatedAtMillis = 9)
        assertEquals(later, SaveRules.pick(listOf(server, later, dsv), "sav"))
        assertEquals(dsv, SaveRules.pick(listOf(server, dsv), "DSV"))
        assertEquals(null, SaveRules.pick(listOf(dsv), "srm"))
        assertEquals(
            "A save whose file is gone cannot be picked",
            server,
            SaveRules.pick(listOf(server, later.copy(missing = true)), "sav"),
        )
    }
}
