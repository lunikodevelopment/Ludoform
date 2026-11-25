package dev.lunikodevelopment.ludoform.data.library

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SaveSyncDao {
    @Query("SELECT * FROM save_sync WHERE gameId = :gameId")
    abstract suspend fun get(gameId: String): SaveSyncEntity?

    @Query("SELECT * FROM save_sync WHERE state = 'PENDING'")
    abstract suspend fun pending(): List<SaveSyncEntity>

    @Query("SELECT * FROM save_sync")
    abstract fun observeAll(): Flow<List<SaveSyncEntity>>

    @Upsert
    abstract suspend fun put(sync: SaveSyncEntity)
}
