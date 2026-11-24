package dev.lunikodevelopment.ludoform

import dev.lunikodevelopment.ludoform.data.library.LibraryDao
import dev.lunikodevelopment.ludoform.data.library.PlaySessionEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records play sessions. A session opens when an emulator is started and closes the next time Ludoform is shown,
 * which is the only end signal available without usage-access permission. Open rows live in Room, so a session
 * survives the launcher being killed while the game runs.
 */
@Singleton
class PlaySessions
    @Inject
    constructor(private val dao: LibraryDao) {
        suspend fun start(
            gameId: String,
            emulatorPackage: String,
            now: Long = System.currentTimeMillis(),
        ) {
            // A session still open belongs to a game that was left without returning here; close it first.
            finishOpen(now)
            dao.insertSession(PlaySessionEntity(gameId = gameId, emulatorPackage = emulatorPackage, startedAt = now))
        }

        /** Closes every open session. Returns how many sessions were kept. */
        suspend fun finishOpen(now: Long = System.currentTimeMillis()): Int {
            var kept = 0
            for (session in dao.getOpenSessions()) {
                when (val end = endFor(session.startedAt, now)) {
                    null -> dao.deleteSession(session.id)
                    else -> {
                        dao.endSession(session.id, end)
                        kept++
                    }
                }
            }
            return kept
        }

        companion object {
            /** Shorter than this, the emulator most likely failed to open the game: the session is not counted. */
            const val MIN_SESSION_MILLIS = 15_000L

            /** A session is never counted longer than this; the launcher cannot see when a forgotten game was left. */
            const val MAX_SESSION_MILLIS = 6 * 60 * 60 * 1000L

            /** The end time to record, or null when the session should be dropped. */
            fun endFor(
                startedAt: Long,
                now: Long,
            ): Long? {
                val length = now - startedAt
                return when {
                    length < MIN_SESSION_MILLIS -> null
                    length > MAX_SESSION_MILLIS -> startedAt + MAX_SESSION_MILLIS
                    else -> now
                }
            }
        }
    }
