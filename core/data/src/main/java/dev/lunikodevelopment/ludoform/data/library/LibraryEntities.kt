package dev.lunikodevelopment.ludoform.data.library

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "platforms")
data class PlatformEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "library_folders",
    foreignKeys = [
        ForeignKey(
            entity = PlatformEntity::class,
            parentColumns = ["id"],
            childColumns = ["platformId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("platformId")],
)
data class LibraryFolderEntity(
    @PrimaryKey val uri: String,
    val platformId: String?,
)

@Entity(
    tableName = "games",
    foreignKeys = [
        ForeignKey(
            entity = PlatformEntity::class,
            parentColumns = ["id"],
            childColumns = ["platformId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("platformId"), Index(value = ["platformId", "title"])],
)
data class GameEntity(
    @PrimaryKey val id: String,
    val platformId: String,
    val title: String,
    /** First time a scan found the game, in epoch milliseconds; 0 for rows created before schema 2. */
    @ColumnInfo(defaultValue = "0") val addedAt: Long = 0,
)

@Entity(
    tableName = "game_files",
    foreignKeys = [
        ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(
            entity = LibraryFolderEntity::class,
            parentColumns = ["uri"],
            childColumns = ["folderUri"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("gameId"), Index("folderUri")],
)
data class GameFileEntity(
    @PrimaryKey val uri: String,
    val gameId: String,
    val folderUri: String,
    val displayName: String,
)

/**
 * One stretch of play started from Ludoform. [endedAt] is null while the game runs; an open row survives the launcher
 * being killed and is closed the next time Ludoform is shown. Times are epoch milliseconds.
 */
@Entity(
    tableName = "play_sessions",
    foreignKeys = [
        ForeignKey(entity = GameEntity::class, parentColumns = ["id"], childColumns = ["gameId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("gameId")],
)
data class PlaySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: String,
    val emulatorPackage: String,
    val startedAt: Long,
    val endedAt: Long? = null,
)

/**
 * A platform on the RomM server. [platformId] is the Ludoform platform it maps to, null when Ludoform has no such
 * platform; [sync] false keeps its games out of the library ("don't sync"). Ids are the server's.
 */
@Entity(tableName = "romm_platforms")
data class RommPlatformEntity(
    @PrimaryKey val id: Int,
    val slug: String,
    val fsSlug: String,
    val name: String,
    val romCount: Int,
    val platformId: String?,
    @ColumnInfo(defaultValue = "1") val sync: Boolean = true,
)

/**
 * A ROM on the RomM server, kept for every platform so switching sync on needs no download. [gameId] is the library
 * game it appears as: a local game with the same file, a remote-only `romm:<id>` game, or null when it is not shown.
 * [missingSyncs] counts successful syncs in a row whose identifier list lacked it; a ROM is only deleted at 2 (D30).
 */
@Entity(tableName = "romm_roms", indices = [Index("rommPlatformId"), Index("gameId")])
data class RommRomEntity(
    @PrimaryKey val id: Int,
    val rommPlatformId: Int,
    val fsName: String,
    val fsNameNoExt: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val updatedAt: String,
    val missingFromServerDisk: Boolean = false,
    val gameId: String? = null,
    val missingSyncs: Int = 0,
)

/** A local file of a game, for matching server ROMs to games already on the device. */
data class LocalGameFile(val gameId: String, val platformId: String, val title: String, val displayName: String)

/** Where a download stands. Only RUNNING moves bytes; the worker takes QUEUED rows oldest first (D31). */
enum class DownloadState { QUEUED, RUNNING, PAUSED, FAILED, DONE }

/** Why a download stopped, in a form the screens turn into words. */
enum class DownloadProblem { SIGN_IN, NOT_FOUND, SERVER, NO_SPACE, FOLDER, NETWORK }

/**
 * A RomM game being downloaded into [folderUri] (a SAF tree with write access). The bytes go to a `.part` document
 * that is appended to on resume and renamed to [fileName] when complete; [partUri] is null until it exists.
 */
@Entity(tableName = "downloads", indices = [Index(value = ["rommId"], unique = true)])
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rommId: Int,
    val gameId: String,
    val title: String,
    val platformId: String,
    val fileName: String,
    val folderUri: String,
    val state: DownloadState = DownloadState.QUEUED,
    val problem: DownloadProblem? = null,
    val partUri: String? = null,
    val bytesDone: Long = 0,
    val totalBytes: Long = 0,
    val attempts: Int = 0,
    val createdAt: Long = 0,
    /** Measured while RUNNING, for "62% · 6 MB/s"; 0 otherwise. */
    val bytesPerSecond: Long = 0,
)

/** The folder downloads of a platform go to, chosen once by the player (the local side of the platform mapping). */
@Entity(tableName = "download_folders")
data class DownloadFolderEntity(
    @PrimaryKey val platformId: String,
    val treeUri: String,
)
