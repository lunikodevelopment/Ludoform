package dev.lunikodevelopment.ludoform.data.library

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DownloadDao {
    @Query("SELECT * FROM downloads WHERE state != 'DONE' ORDER BY createdAt, id")
    abstract fun observeActive(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    abstract suspend fun get(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE rommId = :rommId")
    abstract suspend fun forRom(rommId: Int): DownloadEntity?

    /** The next download to run: one left RUNNING by a killed worker first, then the oldest queued. */
    @Query("SELECT * FROM downloads WHERE state IN ('RUNNING', 'QUEUED') ORDER BY state = 'RUNNING' DESC, createdAt, id LIMIT 1")
    abstract suspend fun next(): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(download: DownloadEntity): Long

    @Upsert
    abstract suspend fun update(download: DownloadEntity)

    @Query("UPDATE downloads SET state = :state, problem = NULL WHERE id = :id")
    abstract suspend fun setState(
        id: Long,
        state: DownloadState,
    )

    @Query("UPDATE downloads SET state = 'QUEUED', problem = NULL, attempts = 0 WHERE state IN ('PAUSED', 'FAILED')")
    abstract suspend fun resumeAll()

    @Query("SELECT * FROM downloads WHERE state != 'DONE'")
    abstract suspend fun active(): List<DownloadEntity>

    @Query("DELETE FROM downloads WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM downloads WHERE state = 'DONE'")
    abstract suspend fun clearDone()

    @Query("SELECT * FROM download_folders WHERE platformId = :platformId")
    abstract suspend fun folder(platformId: String): DownloadFolderEntity?

    @Upsert
    abstract suspend fun setFolder(folder: DownloadFolderEntity)
}
