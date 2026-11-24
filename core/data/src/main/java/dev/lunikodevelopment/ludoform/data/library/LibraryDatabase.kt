package dev.lunikodevelopment.ludoform.data.library

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [PlatformEntity::class, LibraryFolderEntity::class, GameEntity::class, GameFileEntity::class, PlaySessionEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
}

@Module
@InstallIn(SingletonComponent::class)
object LibraryDatabaseModule {
    @Provides
    @Singleton
    fun database(
        @ApplicationContext context: Context,
    ): LibraryDatabase = Room.databaseBuilder(context, LibraryDatabase::class.java, "ludoform-library.db").build()

    @Provides
    fun libraryDao(database: LibraryDatabase): LibraryDao = database.libraryDao()
}
