package dev.lunikodevelopment.ludoform.data.library

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** What one RomM sync changes in the library, applied in a single transaction. */
data class RommLibraryChanges(
    val platforms: List<RommPlatformEntity>,
    val removedPlatformIds: List<Int>,
    val ludoformPlatforms: List<PlatformEntity>,
    val roms: List<RommRomEntity>,
    val removedRomIds: List<Int>,
    val remoteGames: List<GameEntity>,
    val removedRemoteGameIds: List<String>,
)

@Dao
abstract class RommDao {
    @Query("SELECT * FROM romm_platforms ORDER BY name COLLATE NOCASE")
    abstract suspend fun getPlatforms(): List<RommPlatformEntity>

    @Query("SELECT * FROM romm_platforms ORDER BY name COLLATE NOCASE")
    abstract fun observePlatforms(): Flow<List<RommPlatformEntity>>

    @Query("UPDATE romm_platforms SET sync = :sync WHERE id = :id")
    abstract suspend fun setSync(
        id: Int,
        sync: Boolean,
    )

    @Query("SELECT id FROM games WHERE id LIKE 'romm:%'")
    abstract suspend fun getRemoteGameIds(): List<String>

    @Query("SELECT * FROM romm_roms")
    abstract suspend fun getRoms(): List<RommRomEntity>

    @Query(
        """
        SELECT g.id AS gameId, g.platformId, g.title, f.displayName
        FROM games g JOIN game_files f ON f.gameId = g.id
        """,
    )
    abstract suspend fun getLocalFiles(): List<LocalGameFile>

    /** Ids among [ids] that are remote-only games safe to delete: no files and no play history. */
    @Query(
        """
        SELECT g.id FROM games g WHERE g.id IN (:ids)
            AND NOT EXISTS (SELECT 1 FROM game_files f WHERE f.gameId = g.id)
            AND NOT EXISTS (SELECT 1 FROM play_sessions s WHERE s.gameId = g.id)
        """,
    )
    abstract suspend fun deletableRemoteGames(ids: List<String>): List<String>

    @Upsert
    protected abstract suspend fun upsertPlatforms(platforms: List<RommPlatformEntity>)

    @Query("DELETE FROM romm_platforms WHERE id IN (:ids)")
    protected abstract suspend fun deletePlatforms(ids: List<Int>)

    @Upsert
    protected abstract suspend fun upsertLudoformPlatforms(platforms: List<PlatformEntity>)

    @Upsert
    protected abstract suspend fun upsertRoms(roms: List<RommRomEntity>)

    @Query("DELETE FROM romm_roms WHERE id IN (:ids)")
    protected abstract suspend fun deleteRoms(ids: List<Int>)

    @Upsert
    protected abstract suspend fun upsertGames(games: List<GameEntity>)

    @Query("DELETE FROM games WHERE id IN (:ids)")
    protected abstract suspend fun deleteGames(ids: List<String>)

    @Transaction
    open suspend fun apply(changes: RommLibraryChanges) {
        upsertLudoformPlatforms(changes.ludoformPlatforms)
        changes.platforms.chunked(CHUNK).forEach { upsertPlatforms(it) }
        changes.remoteGames.chunked(CHUNK).forEach { upsertGames(it) }
        changes.roms.chunked(CHUNK).forEach { upsertRoms(it) }
        changes.removedRomIds.chunked(CHUNK).forEach { deleteRoms(it) }
        // Only games without files or play history go; anything the player touched stays.
        changes.removedRemoteGameIds.chunked(CHUNK).forEach { ids -> deleteGames(deletableRemoteGames(ids)) }
        changes.removedPlatformIds.chunked(CHUNK).forEach { deletePlatforms(it) }
    }

    private companion object {
        /** SQLite's limit on bound variables is 999 on older versions. */
        const val CHUNK = 500
    }
}
