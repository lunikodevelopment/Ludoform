package dev.lunikodevelopment.ludoform.data.library

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

enum class UploadState { QUEUED, RUNNING, PAUSED, FAILED, DONE }

/** Why an upload stopped, in a form the screens turn into words. */
enum class UploadProblem {
    /** The upload server was removed or has no password. */
    NO_SERVER,
    SIGN_IN,
    HOST_KEY,
    UNREACHABLE,
    NO_FOLDER,
    NOT_WRITABLE,

    /** A file of the game is gone from the device, or a disc a `.cue` or `.m3u` names is not with it. */
    MISSING_FILES,

    /** A different file with the same name is already in RomM's folder; nothing is overwritten. */
    EXISTS,
    NETWORK,
    FAILED,
}

/** The RomM scan after an upload. */
enum class UploadScan { NONE, WAITING, RUNNING, DONE, REFUSED, FAILED }

/**
 * One game in the upload queue. [files] lists the game's files as `uri\tname\tsize` lines, all sent to
 * [remoteDir] (relative to the server's RomM folder, `/`-separated: `nds`, or `psx/Game` for a game of several files).
 * [fileIndex] and [bytesDone] say how far it got; an interrupted file resumes from its part file on the server.
 */
@Entity(tableName = "uploads")
data class UploadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: String,
    val title: String,
    val platformId: String,
    val serverId: Long,
    /** The RomM platform folder name (`fs_slug`) the game goes into, which the scan is asked to look at. */
    val fsSlug: String,
    val remoteDir: String,
    val files: String,
    val state: UploadState = UploadState.QUEUED,
    val problem: UploadProblem? = null,
    val detail: String? = null,
    val fileIndex: Int = 0,
    val bytesDone: Long = 0,
    val totalBytes: Long = 0,
    val bytesPerSecond: Long = 0,
    val attempts: Int = 0,
    val createdAt: Long = 0,
    val finishedAt: Long? = null,
    @ColumnInfo(defaultValue = "NONE") val scan: UploadScan = UploadScan.NONE,
)

@Dao
abstract class UploadDao {
    @Query("SELECT * FROM uploads ORDER BY state = 'DONE', createdAt, id")
    abstract fun observeAll(): Flow<List<UploadEntity>>

    @Query("SELECT * FROM uploads WHERE id = :id")
    abstract suspend fun get(id: Long): UploadEntity?

    @Query("SELECT * FROM uploads WHERE gameId = :gameId AND state != 'DONE'")
    abstract suspend fun activeFor(gameId: String): UploadEntity?

    /** The next upload to run: one left RUNNING by a killed worker first, then the oldest queued. */
    @Query("SELECT * FROM uploads WHERE state IN ('RUNNING', 'QUEUED') ORDER BY state = 'RUNNING' DESC, createdAt, id LIMIT 1")
    abstract suspend fun next(): UploadEntity?

    @Insert
    abstract suspend fun insert(upload: UploadEntity): Long

    @Upsert
    abstract suspend fun update(upload: UploadEntity)

    @Query("UPDATE uploads SET state = :state, problem = NULL, detail = NULL WHERE id = :id")
    abstract suspend fun setState(
        id: Long,
        state: UploadState,
    )

    @Query("UPDATE uploads SET state = 'QUEUED', problem = NULL, detail = NULL, attempts = 0 WHERE id = :id")
    abstract suspend fun retry(id: Long)

    @Query("DELETE FROM uploads WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("SELECT * FROM uploads WHERE state != 'DONE'")
    abstract suspend fun active(): List<UploadEntity>

    @Query("DELETE FROM uploads WHERE state = 'DONE'")
    abstract suspend fun clearDone()

    /** Finished uploads whose platform has not been scanned since. */
    @Query("SELECT * FROM uploads WHERE state = 'DONE' AND scan = 'WAITING'")
    abstract suspend fun waitingForScan(): List<UploadEntity>

    @Query("UPDATE uploads SET scan = :scan WHERE id IN (:ids)")
    abstract suspend fun setScan(
        ids: List<Long>,
        scan: UploadScan,
    )
}
