package dev.lunikodevelopment.ludoform.data.library

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LibraryDao {
    @Upsert
    abstract suspend fun upsertPlatform(platform: PlatformEntity)

    @Upsert
    abstract suspend fun upsertFolder(folder: LibraryFolderEntity)

    @Upsert
    abstract suspend fun upsertGame(game: GameEntity)

    @Upsert
    abstract suspend fun upsertFiles(files: List<GameFileEntity>)

    @Transaction
    open suspend fun saveGameWithFiles(
        game: GameEntity,
        files: List<GameFileEntity>,
    ) {
        require(files.all { it.gameId == game.id }) { "Files must belong to the supplied game" }
        upsertGame(game)
        upsertFiles(files)
    }

    @Query("SELECT * FROM platforms ORDER BY name COLLATE NOCASE, id")
    abstract fun observePlatforms(): Flow<List<PlatformEntity>>

    @Query("SELECT * FROM library_folders ORDER BY uri")
    abstract suspend fun getFolders(): List<LibraryFolderEntity>

    @Query("SELECT * FROM library_folders ORDER BY uri")
    abstract fun observeFolders(): Flow<List<LibraryFolderEntity>>

    @Query("SELECT * FROM games WHERE platformId = :platformId ORDER BY title COLLATE NOCASE, id")
    abstract fun observeGames(platformId: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :gameId")
    abstract suspend fun getGame(gameId: String): GameEntity?

    /** Every game with its file count and first file name, for the library screens. */
    @Query(
        """
        SELECT g.id, g.platformId, g.title, g.addedAt,
            (SELECT COUNT(*) FROM game_files f WHERE f.gameId = g.id) AS fileCount,
            (SELECT f.displayName FROM game_files f WHERE f.gameId = g.id ORDER BY f.displayName COLLATE NOCASE LIMIT 1) AS primaryFile
        FROM games g
        ORDER BY g.title COLLATE NOCASE, g.id
        """,
    )
    abstract fun observeGameRows(): Flow<List<GameRow>>

    @Query("SELECT * FROM game_files WHERE gameId = :gameId ORDER BY displayName COLLATE NOCASE, uri")
    abstract fun observeGameFiles(gameId: String): Flow<List<GameFileEntity>>
}

data class GameRow(
    val id: String,
    val platformId: String,
    val title: String,
    val addedAt: Long,
    val fileCount: Int,
    val primaryFile: String?,
)
