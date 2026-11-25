package dev.lunikodevelopment.ludoform.data.library

import androidx.room.Dao
import androidx.room.Insert
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

    /** Every game with its file count, first file name and play history, for the library screens. */
    @Query(
        """
        SELECT g.id, g.platformId, g.title, g.addedAt,
            (SELECT COUNT(*) FROM game_files f WHERE f.gameId = g.id) AS fileCount,
            (SELECT f.displayName FROM game_files f WHERE f.gameId = g.id ORDER BY f.displayName COLLATE NOCASE LIMIT 1) AS primaryFile,
            (SELECT COALESCE(SUM(s.endedAt - s.startedAt), 0) FROM play_sessions s WHERE s.gameId = g.id AND s.endedAt IS NOT NULL)
                AS playTimeMillis,
            (SELECT MAX(s.startedAt) FROM play_sessions s WHERE s.gameId = g.id) AS lastPlayedAt,
            (SELECT r.id FROM romm_roms r WHERE r.gameId = g.id ORDER BY r.id LIMIT 1) AS rommId,
            (SELECT r.sizeBytes FROM romm_roms r WHERE r.gameId = g.id ORDER BY r.id LIMIT 1) AS rommSizeBytes
        FROM games g
        ORDER BY g.title COLLATE NOCASE, g.id
        """,
    )
    abstract fun observeGameRows(): Flow<List<GameRow>>

    @Query("SELECT * FROM game_files WHERE gameId = :gameId ORDER BY displayName COLLATE NOCASE, uri")
    abstract fun observeGameFiles(gameId: String): Flow<List<GameFileEntity>>

    @Query("SELECT * FROM game_files WHERE gameId = :gameId ORDER BY displayName COLLATE NOCASE, uri")
    abstract suspend fun getGameFiles(gameId: String): List<GameFileEntity>

    @Insert
    abstract suspend fun insertSession(session: PlaySessionEntity): Long

    @Query("SELECT * FROM play_sessions WHERE endedAt IS NULL ORDER BY startedAt")
    abstract suspend fun getOpenSessions(): List<PlaySessionEntity>

    @Query("UPDATE play_sessions SET endedAt = :endedAt WHERE id = :id AND endedAt IS NULL")
    abstract suspend fun endSession(
        id: Long,
        endedAt: Long,
    )

    @Query("UPDATE play_sessions SET screenOffMillis = screenOffMillis + :millis WHERE id = :id AND endedAt IS NULL")
    abstract suspend fun addScreenOff(
        id: Long,
        millis: Long,
    )

    @Query("DELETE FROM play_sessions WHERE id = :id")
    abstract suspend fun deleteSession(id: Long)
}

data class GameRow(
    val id: String,
    val platformId: String,
    val title: String,
    val addedAt: Long,
    val fileCount: Int,
    val primaryFile: String?,
    val playTimeMillis: Long = 0,
    val lastPlayedAt: Long? = null,
    val rommId: Int? = null,
    val rommSizeBytes: Long? = null,
)
