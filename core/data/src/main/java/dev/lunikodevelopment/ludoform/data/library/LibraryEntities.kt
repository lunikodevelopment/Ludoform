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
