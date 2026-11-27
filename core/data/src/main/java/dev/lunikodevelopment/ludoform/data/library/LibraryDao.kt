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
            (SELECT r.sizeBytes FROM romm_roms r WHERE r.gameId = g.id ORDER BY r.id LIMIT 1) AS rommSizeBytes,
            (SELECT r.name FROM romm_roms r WHERE r.gameId = g.id ORDER BY r.id LIMIT 1) AS metadataTitle,
            a.localUri AS artLocal, a.file AS artFile,
            EXISTS (SELECT 1 FROM favorites v WHERE v.gameId = g.id) AS favorite
        FROM games g LEFT JOIN game_art a ON a.gameId = g.id
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

    /** Every game with what the art job needs to choose and fetch its cover (D34). */
    @Query(
        """
        SELECT g.id AS gameId, g.platformId, g.title,
            (SELECT f.displayName FROM game_files f WHERE f.gameId = g.id ORDER BY f.displayName COLLATE NOCASE LIMIT 1) AS primaryFile,
            (SELECT r.fsNameNoExt FROM romm_roms r WHERE r.gameId = g.id ORDER BY r.id LIMIT 1) AS rommFileName,
            (SELECT r.cover FROM romm_roms r WHERE r.gameId = g.id ORDER BY r.id LIMIT 1) AS rommCover,
            (SELECT MAX(s.startedAt) FROM play_sessions s WHERE s.gameId = g.id) AS lastPlayedAt,
            (SELECT COUNT(*) FROM game_files f WHERE f.gameId = g.id) AS fileCount,
            a.localUri, a.file, a.sourceKey, COALESCE(a.checkedAt, 0) AS checkedAt
        FROM games g LEFT JOIN game_art a ON a.gameId = g.id
        """,
    )
    abstract suspend fun getArtCandidates(): List<ArtCandidate>

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertArtIfMissing(art: GameArtEntity)

    @Query("UPDATE game_art SET localUri = :uri WHERE gameId = :gameId")
    protected abstract suspend fun updateLocalArt(
        gameId: String,
        uri: String?,
    )

    /** Records the image next to the ROM, or that there is none ([uri] null). */
    @Transaction
    open suspend fun setLocalArt(
        gameId: String,
        uri: String?,
    ) {
        insertArtIfMissing(GameArtEntity(gameId))
        updateLocalArt(gameId, uri)
    }

    @Query("UPDATE game_art SET file = :file, source = :source, sourceKey = :sourceKey, checkedAt = :checkedAt WHERE gameId = :gameId")
    protected abstract suspend fun updateDownloadedArt(
        gameId: String,
        file: String?,
        source: String?,
        sourceKey: String?,
        checkedAt: Long,
    )

    /** Records a download attempt: the cached [file] and where it came from, or all null when nothing was found. */
    @Transaction
    open suspend fun setDownloadedArt(
        gameId: String,
        file: String?,
        source: String?,
        sourceKey: String?,
        checkedAt: Long,
    ) {
        insertArtIfMissing(GameArtEntity(gameId))
        updateDownloadedArt(gameId, file, source, sourceKey, checkedAt)
    }

    @Query("UPDATE game_art SET sourceKey = :sourceKey, checkedAt = :checkedAt WHERE gameId = :gameId")
    protected abstract suspend fun updateArtChecked(
        gameId: String,
        sourceKey: String?,
        checkedAt: Long,
    )

    /** Records an attempt that found nothing; a cover already cached stays. */
    @Transaction
    open suspend fun markArtChecked(
        gameId: String,
        sourceKey: String?,
        checkedAt: Long,
    ) {
        insertArtIfMissing(GameArtEntity(gameId))
        updateArtChecked(gameId, sourceKey, checkedAt)
    }

    /** Saves a batch of art job results in one transaction, so the screens rebuild once for the whole batch. */
    @Transaction
    open suspend fun applyArt(results: List<ArtResult>) {
        for (result in results) {
            if (result.file != null) {
                setDownloadedArt(result.gameId, result.file, result.source, result.sourceKey, result.checkedAt)
            } else {
                markArtChecked(result.gameId, result.sourceKey, result.checkedAt)
            }
        }
    }

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    abstract suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("SELECT gameId FROM favorites")
    abstract fun observeFavoriteIds(): Flow<List<String>>

    @Query("DELETE FROM favorites WHERE gameId = :gameId")
    abstract suspend fun removeFavorite(gameId: String)

    @Query("SELECT EXISTS (SELECT 1 FROM favorites WHERE gameId = :gameId)")
    abstract suspend fun isFavorite(gameId: String): Boolean

    /** Marks or unmarks [gameId] as a favourite; returns whether it is one now. */
    @Transaction
    open suspend fun toggleFavorite(
        gameId: String,
        now: Long,
    ): Boolean =
        if (isFavorite(gameId)) {
            removeFavorite(gameId)
            false
        } else {
            addFavorite(FavoriteEntity(gameId, now))
            true
        }

    @Query("SELECT file FROM game_art WHERE file IS NOT NULL AND gameId NOT IN (SELECT id FROM games)")
    abstract suspend fun getOrphanArtFiles(): List<String>

    @Query("DELETE FROM game_art WHERE gameId NOT IN (SELECT id FROM games)")
    abstract suspend fun deleteOrphanArt()
}

/** One game's art job outcome: a cached [file] from [source], or none found ([file] null). */
data class ArtResult(
    val gameId: String,
    val file: String?,
    val source: String?,
    val sourceKey: String?,
    val checkedAt: Long,
)

data class ArtCandidate(
    val gameId: String,
    val platformId: String,
    val title: String,
    val primaryFile: String?,
    val rommFileName: String?,
    val rommCover: String?,
    val lastPlayedAt: Long?,
    val fileCount: Int,
    val localUri: String?,
    val file: String?,
    val sourceKey: String?,
    val checkedAt: Long,
)

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
    val metadataTitle: String? = null,
    val artLocal: String? = null,
    val artFile: String? = null,
    val favorite: Boolean = false,
)
