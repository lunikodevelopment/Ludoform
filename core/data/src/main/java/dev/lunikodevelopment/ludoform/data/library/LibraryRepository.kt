package dev.lunikodevelopment.ludoform.data.library

import dev.lunikodevelopment.ludoform.model.GameSummary
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.model.PlatformSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Read-only view of the local library for the screens. It never writes or deletes. */
@Singleton
class LibraryRepository
    @Inject
    constructor(private val dao: LibraryDao) {
        val snapshots: Flow<LibrarySnapshot> =
            combine(dao.observeFolders(), dao.observePlatforms(), dao.observeGameRows()) { folders, platforms, rows ->
                toSnapshot(folders.map { it.uri }, platforms, rows)
            }

        companion object {
            fun toSnapshot(
                folders: List<String>,
                platforms: List<PlatformEntity>,
                rows: List<GameRow>,
            ): LibrarySnapshot {
                val counts = rows.groupingBy { it.platformId }.eachCount()
                return LibrarySnapshot(
                    folders = folders,
                    platforms =
                        platforms.filter { (counts[it.id] ?: 0) > 0 }
                            .map { PlatformSummary(it.id, it.name, counts.getValue(it.id)) },
                    games =
                        rows.map {
                            GameSummary(
                                id = it.id,
                                platformId = it.platformId,
                                rawTitle = it.title,
                                addedAt = it.addedAt,
                                fileCount = it.fileCount,
                                primaryFile = it.primaryFile ?: it.title,
                            )
                        },
                )
            }
        }
    }
