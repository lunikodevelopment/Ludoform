package dev.lunikodevelopment.ludoform

import dev.lunikodevelopment.ludoform.data.RommState
import dev.lunikodevelopment.ludoform.data.RommStatus
import dev.lunikodevelopment.ludoform.romm.RommCheck
import dev.lunikodevelopment.ludoform.romm.RommHealth
import dev.lunikodevelopment.ludoform.romm.RommRequestException
import dev.lunikodevelopment.ludoform.romm.RommServer
import dev.lunikodevelopment.ludoform.romm.RommSyncResult
import dev.lunikodevelopment.ludoform.romm.RommVersion

/** Turns the saved server and its last check into what Settings says, in the player's words. */
object RommStatusText {
    fun state(
        server: RommServer?,
        check: RommCheck?,
        checking: Boolean,
    ): RommState {
        if (server == null) return RommState()
        val base = RommState(server.address, server.username)
        if (checking) return base.copy(status = RommStatus.CHECKING, title = "Checking…", detail = "Reaching the server and signing in.")
        val health =
            check?.health
                ?: return base.copy(status = RommStatus.NOT_CHECKED, title = "Not checked yet", detail = "Save and check to connect.")
        val (title, detail) =
            when (health) {
                is RommHealth.Connected -> "Connected" to "RomM ${health.version} · signed in as ${health.username}"
                RommHealth.BadAddress -> "Check the address" to "Use the https address of your RomM server."
                is RommHealth.Unreachable -> "Can't reach the server" to health.reason
                RommHealth.NotRomm -> "This isn't a RomM server" to "Check the address."
                is RommHealth.TooOld ->
                    "RomM ${health.version} is too old" to "Ludoform needs RomM ${RommVersion.MINIMUM} or newer."
                is RommHealth.SignInFailed ->
                    if (health.version.isEmpty()) {
                        "Sign in again" to "Type your password and save."
                    } else {
                        "Sign-in failed" to "Check the user name and password."
                    }
                is RommHealth.ServerError -> "The server had a problem" to "Try again later."
            }
        val status = if (health is RommHealth.Connected) RommStatus.CONNECTED else RommStatus.PROBLEM
        return base.copy(status = status, title = title, detail = detail)
    }

    /** "1,204 games · synced 10:42", "Syncing…", or why the last sync failed. */
    fun syncLine(
        result: RommSyncResult?,
        syncing: Boolean,
        time: (Long) -> String,
    ): String =
        when {
            syncing -> "Syncing your library…"
            result == null -> ""
            result.problem == null -> "%,d games · synced %s".format(java.util.Locale.ROOT, result.games, time(result.at))
            else ->
                when (result.problem) {
                    RommRequestException.Kind.UNREACHABLE -> "Sync failed: can't reach the server"
                    RommRequestException.Kind.SIGN_IN -> "Sync failed: sign-in was refused"
                    else -> "Sync failed: the server had a problem"
                }
        }
}
