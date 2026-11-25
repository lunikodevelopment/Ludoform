package dev.lunikodevelopment.ludoform.data.library

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every released schema must open as the current one; a missing step crashes Ludoform on update. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LibraryDatabase::class.java)

    @Test
    fun everyVersionMigratesToTheCurrentOneKeepingData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO platforms (id, name) VALUES ('nds', 'Nintendo DS')")
            execSQL("INSERT INTO games (id, platformId, title) VALUES ('g1', 'nds', 'Kept')")
            close()
        }
        val current = Room.databaseBuilder(context, LibraryDatabase::class.java, name).build()
        try {
            assertEquals("Kept", runBlocking { current.libraryDao().getGame("g1")?.title })
            assertEquals(null, runBlocking { current.saveSyncDao().get("g1") })
        } finally {
            current.close()
            context.deleteDatabase(name)
        }
    }
}
