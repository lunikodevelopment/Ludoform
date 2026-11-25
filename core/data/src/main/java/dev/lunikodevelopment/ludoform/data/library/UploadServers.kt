package dev.lunikodevelopment.ludoform.data.library

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** How Ludoform reaches the folder RomM reads its games from (D36). */
enum class UploadProtocol(val defaultPort: Int) { SFTP(22), SMB(445) }

/** The last connection test of a server, in a form the screens turn into words. */
enum class ServerCheck {
    NOT_CHECKED,
    CHECKING,
    OK,
    HOST_KEY_UNKNOWN,
    HOST_KEY_CHANGED,
    UNREACHABLE,
    SIGN_IN,
    NO_FOLDER,
    NOT_WRITABLE,
    FAILED,
}

/**
 * An upload server (D36). [folder] is the RomM library folder: an absolute path over SFTP, `share/path` over SMB.
 * The password is not here: it is kept encrypted under [id] in the upload secrets. [trustedKey] is the SFTP host key
 * the player trusted (`type SHA256:base64`); [offeredKey] is the one the server showed at the last test.
 */
@Entity(tableName = "upload_servers")
data class UploadServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: UploadProtocol,
    val host: String,
    val port: Int,
    val username: String,
    val folder: String,
    val trustedKey: String? = null,
    val offeredKey: String? = null,
    @ColumnInfo(defaultValue = "NOT_CHECKED") val check: ServerCheck = ServerCheck.NOT_CHECKED,
    val checkDetail: String? = null,
    @ColumnInfo(defaultValue = "0") val checkedAt: Long = 0,
    val lastUploadAt: Long? = null,
)

@Dao
abstract class UploadServerDao {
    @Query("SELECT * FROM upload_servers ORDER BY name COLLATE NOCASE, id")
    abstract fun observeAll(): Flow<List<UploadServerEntity>>

    @Query("SELECT * FROM upload_servers ORDER BY name COLLATE NOCASE, id")
    abstract suspend fun all(): List<UploadServerEntity>

    @Query("SELECT * FROM upload_servers WHERE id = :id")
    abstract suspend fun get(id: Long): UploadServerEntity?

    @Insert
    abstract suspend fun insert(server: UploadServerEntity): Long

    @Update
    abstract suspend fun update(server: UploadServerEntity)

    @Query("DELETE FROM upload_servers WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("UPDATE upload_servers SET `check` = :check, checkDetail = :detail, offeredKey = :offeredKey, checkedAt = :at WHERE id = :id")
    abstract suspend fun setCheck(
        id: Long,
        check: ServerCheck,
        detail: String?,
        offeredKey: String?,
        at: Long,
    )

    @Query("UPDATE upload_servers SET trustedKey = :key WHERE id = :id")
    abstract suspend fun trust(
        id: Long,
        key: String?,
    )
}
