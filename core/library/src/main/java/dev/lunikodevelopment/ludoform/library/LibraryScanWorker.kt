package dev.lunikodevelopment.ludoform.library

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.lunikodevelopment.ludoform.data.library.LibraryDao
import dev.lunikodevelopment.ludoform.data.library.LibraryFolderEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LibraryWorkerEntryPoint {
    fun libraryDao(): LibraryDao
}

class LibraryScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val dao = EntryPointAccessors.fromApplication(applicationContext, LibraryWorkerEntryPoint::class.java).libraryDao()
            val addedFolder = inputData.getString("folder")
            if (addedFolder != null && dao.getFolders().none { it.uri == addedFolder }) {
                dao.upsertFolder(LibraryFolderEntity(addedFolder, null))
            }
            val skipped = mutableListOf<String>()
            var count = 0
            for (folder in dao.getFolders()) {
                try {
                    val result = LibraryScanner(applicationContext, dao).scan(folder.uri)
                    count += result.games
                    skipped.addAll(result.skipped)
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    skipped.add("Folder unavailable: ${folder.uri} (${error.javaClass.simpleName})")
                }
            }
            val summary = "Scanned $count games; ${skipped.size} skipped."
            applicationContext.filesDir.resolve("library-scan-report.txt").writeText(
                (listOf(summary) + skipped).joinToString("\n", postfix = "\n"),
            )
            val details = skipped.take(30).joinToString("\n").take(3500)
            val more = if (skipped.size > 30 || details.length == 3500) " (report truncated)" else ""
            Result.success(
                Data.Builder().putString(
                    "summary",
                    "$summary$more",
                ).putString("skipped", details).build(),
            )
        }
}

object LibraryScans {
    private const val NAME = "library-scan"

    fun enqueue(
        context: Context,
        folder: String? = null,
    ) {
        val data = Data.Builder().apply { if (folder != null) putString("folder", folder) }.build()
        val request = OneTimeWorkRequestBuilder<LibraryScanWorker>().setInputData(data).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun workName(): String = NAME
}

class MediaMountedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_MEDIA_MOUNTED) LibraryScans.enqueue(context)
    }
}
