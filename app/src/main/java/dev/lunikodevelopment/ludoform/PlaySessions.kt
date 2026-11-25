package dev.lunikodevelopment.ludoform

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lunikodevelopment.ludoform.data.library.LibraryDao
import dev.lunikodevelopment.ludoform.data.library.PlaySessionEntity
import javax.inject.Inject
import javax.inject.Singleton

/** What the device's clocks say right now. [uptime] stops while the device sleeps; [bootCount] is null when unknown. */
data class DeviceTime(val wall: Long, val uptime: Long, val bootCount: Int?)

/** Reads [DeviceTime]; tests pass their own. */
fun interface DeviceClock {
    fun now(): DeviceTime
}

class SystemDeviceClock
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DeviceClock {
        override fun now(): DeviceTime =
            DeviceTime(
                wall = System.currentTimeMillis(),
                uptime = SystemClock.uptimeMillis(),
                bootCount = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrNull(),
            )
    }

/**
 * Records play sessions. A session opens when an emulator is started and closes the next time Ludoform is shown,
 * which is the only end signal available without usage-access permission. Open rows live in Room, so a session
 * survives the launcher being killed while the game runs. Only time the device was awake with the screen on counts
 * (D33): [screenOff] and [screenOn] come from [LudoformApplication] while Ludoform's process is alive.
 */
@Singleton
class PlaySessions(
    private val dao: LibraryDao,
    private val clock: DeviceClock,
) {
    @Inject
    constructor(dao: LibraryDao, clock: SystemDeviceClock) : this(dao, clock as DeviceClock)

    suspend fun start(
        gameId: String,
        emulatorPackage: String,
    ) {
        val now = clock.now()
        // A session still open belongs to a game that was left without returning here; close it first.
        finishOpen(now)
        dao.insertSession(
            PlaySessionEntity(
                gameId = gameId,
                emulatorPackage = emulatorPackage,
                startedAt = now.wall,
                bootCount = now.bootCount,
                startedUptime = now.uptime,
            ),
        )
    }

    /** When the screen went off, by the uptime clock; null while it is on or when Ludoform did not see it go off. */
    @Volatile private var screenOffAt: Long? = null

    fun screenOff() {
        screenOffAt = clock.now().uptime
    }

    /** Adds the time the screen was off to every open session that was running then. */
    suspend fun screenOn() {
        val offAt = screenOffAt ?: return
        screenOffAt = null
        val now = clock.now()
        val off = now.uptime - offAt
        if (off <= 0) return
        for (session in dao.getOpenSessions()) {
            // A session started while the screen was off (not possible from Ludoform, but cheap to guard) loses less.
            val sinceStart = now.wall - session.startedAt
            dao.addScreenOff(session.id, minOf(off, sinceStart).coerceAtLeast(0))
        }
    }

    /** Closes every open session. Returns the games whose sessions were kept, so their saves can be synced. */
    suspend fun finishOpen(): List<String> = finishOpen(clock.now())

    private suspend fun finishOpen(now: DeviceTime): List<String> {
        val kept = mutableListOf<String>()
        for (session in dao.getOpenSessions()) {
            when (val end = endFor(session, now)) {
                null -> dao.deleteSession(session.id)
                else -> {
                    dao.endSession(session.id, end)
                    kept.add(session.gameId)
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

        /**
         * The end time to record, or null when the session should be dropped. The length is the time the device was
         * awake with the screen on, as far as Ludoform saw. When the device restarted during the session, when the game ended is unknown: the session is kept
         * (it was played, and its save may need syncing) but counts no time.
         */
        fun endFor(
            session: PlaySessionEntity,
            now: DeviceTime,
        ): Long? {
            val wall = now.wall - session.startedAt
            val restarted = session.bootCount != null && now.bootCount != null && session.bootCount != now.bootCount
            if (restarted) return session.startedAt
            val awake =
                session.startedUptime
                    ?.takeIf { session.bootCount != null && now.bootCount != null }
                    ?.let { now.uptime - it }
                    ?.takeIf { it >= 0 }
            val length = minOf(wall - session.screenOffMillis, awake ?: wall)
            return when {
                length < MIN_SESSION_MILLIS -> null
                length > MAX_SESSION_MILLIS -> session.startedAt + MAX_SESSION_MILLIS
                else -> session.startedAt + length
            }
        }
    }
}
