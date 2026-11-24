package dev.lunikodevelopment.ludoform.data.library

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: LibraryDatabase
    private val name = "library-test.db"

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        database = openDatabase()
    }

    private fun openDatabase(): LibraryDatabase = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()

    @After
    fun teardown() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun persistsAndOrdersGamesAndFilesAcrossReopen() =
        runBlocking {
            val dao = database.libraryDao()
            dao.upsertPlatform(PlatformEntity("gc", "GameCube"))
            dao.upsertFolder(LibraryFolderEntity("content://games", "gc"))
            dao.saveGameWithFiles(
                GameEntity("g1", "gc", "Zelda"),
                listOf(
                    GameFileEntity("content://games/disc2", "g1", "content://games", "Disc 2"),
                    GameFileEntity("content://games/disc1", "g1", "content://games", "Disc 1"),
                ),
            )
            dao.upsertGame(GameEntity("g2", "gc", "animal crossing"))
            dao.upsertGame(GameEntity("g1", "gc", "Zelda updated"))
            database.close()
            database = openDatabase()
            assertEquals(listOf("g2", "g1"), database.libraryDao().observeGames("gc").first().map { it.id })
            assertEquals(listOf("Disc 1", "Disc 2"), database.libraryDao().observeGameFiles("g1").first().map { it.displayName })
            assertEquals("Zelda updated", database.libraryDao().getGame("g1")?.title)
        }

    @Test
    fun invalidFileRollsBackGameWrite() =
        runBlocking {
            val dao = database.libraryDao()
            dao.upsertPlatform(PlatformEntity("gc", "GameCube"))
            assertThrows(SQLiteConstraintException::class.java) {
                runBlocking {
                    dao.saveGameWithFiles(
                        GameEntity("g1", "gc", "Game"),
                        listOf(GameFileEntity("content://missing/game", "g1", "content://missing", "Game")),
                    )
                }
            }
            assertNull(dao.getGame("g1"))
        }

    @Test
    fun rejectsFileAssignedToAnotherGameBeforeWriting() =
        runBlocking {
            val dao = database.libraryDao()
            dao.upsertPlatform(PlatformEntity("gc", "GameCube"))
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking {
                    dao.saveGameWithFiles(
                        GameEntity("g1", "gc", "Game"),
                        listOf(GameFileEntity("content://games/game", "g2", "content://games", "Game")),
                    )
                }
            }
            assertNull(dao.getGame("g1"))
        }

    @Test
    fun foreignKeysPreventRemovingReferencedPlatformOrFolder() =
        runBlocking {
            val dao = database.libraryDao()
            dao.upsertPlatform(PlatformEntity("gc", "GameCube"))
            dao.upsertFolder(LibraryFolderEntity("content://games", "gc"))
            dao.saveGameWithFiles(
                GameEntity("g1", "gc", "Game"),
                listOf(GameFileEntity("content://games/game", "g1", "content://games", "Game")),
            )
            assertThrows(SQLiteConstraintException::class.java) {
                database.openHelper.writableDatabase.execSQL("DELETE FROM library_folders WHERE uri = 'content://games'")
            }
            assertThrows(SQLiteConstraintException::class.java) {
                database.openHelper.writableDatabase.execSQL("DELETE FROM platforms WHERE id = 'gc'")
            }
            assertEquals("Game", dao.getGame("g1")?.title)
        }

    @Test
    fun gameRowsCarryFileCountsAndFeedTheSnapshot() =
        runBlocking {
            val dao = database.libraryDao()
            dao.upsertPlatform(PlatformEntity("psx", "PlayStation"))
            dao.upsertPlatform(PlatformEntity("gc", "GameCube"))
            dao.upsertFolder(LibraryFolderEntity("content://games", null))
            dao.saveGameWithFiles(
                GameEntity("g1", "psx", "Disc Game", addedAt = 42),
                listOf(
                    GameFileEntity("content://games/b.bin", "g1", "content://games", "b.bin"),
                    GameFileEntity("content://games/a.cue", "g1", "content://games", "a.cue"),
                ),
            )
            val rows = dao.observeGameRows().first()
            assertEquals(1, rows.size)
            assertEquals(2, rows.single().fileCount)
            assertEquals("a.cue", rows.single().primaryFile)
            assertEquals(42L, rows.single().addedAt)
            val snapshot = LibraryRepository(dao).snapshots.first()
            assertEquals(listOf("psx"), snapshot.platforms.map { it.id })
            assertEquals(1, snapshot.platforms.single().gameCount)
            assertEquals(listOf("content://games"), snapshot.folders)
        }

    @Test
    fun finishedSessionsAddUpToPlayTimeAndLastPlayed() =
        runBlocking {
            val dao = database.libraryDao()
            dao.upsertPlatform(PlatformEntity("psp", "PSP"))
            dao.upsertGame(GameEntity("g1", "psp", "Game"))
            val first = dao.insertSession(PlaySessionEntity(gameId = "g1", emulatorPackage = "org.ppsspp.ppsspp", startedAt = 1_000))
            dao.endSession(first, 61_000)
            val open = dao.insertSession(PlaySessionEntity(gameId = "g1", emulatorPackage = "org.ppsspp.ppsspp", startedAt = 100_000))
            database.close()
            database = openDatabase()
            val reopened = database.libraryDao()
            assertEquals(listOf(open), reopened.getOpenSessions().map { it.id })
            val row = reopened.observeGameRows().first().single()
            assertEquals(60_000L, row.playTimeMillis)
            assertEquals(100_000L, row.lastPlayedAt)
            reopened.endSession(open, 130_000)
            reopened.endSession(open, 999_000)
            assertEquals(90_000L, reopened.observeGameRows().first().single().playTimeMillis)
        }
}
