package dev.lunikodevelopment.ludoform.transfer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.lunikodevelopment.ludoform.data.library.ArtCandidate
import dev.lunikodevelopment.ludoform.data.library.LibraryDao
import dev.lunikodevelopment.ludoform.media.ArtRules
import dev.lunikodevelopment.ludoform.romm.RommAddress
import dev.lunikodevelopment.ludoform.romm.RommClient
import dev.lunikodevelopment.ludoform.romm.RommSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ArtEntryPoint {
    fun libraryDao(): LibraryDao
}

/**
 * Fetches covers into `files/art` (D34): RomM's first, then libretro's. It runs only on an unmetered network, works
 * through every game that needs art (played games first) and stops when none is left. An image next to the ROM is
 * found by the library scan and never downloaded over.
 */
class ArtWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val http: OkHttpClient by lazy { RommClient.defaultHttpClient() }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val dao = EntryPointAccessors.fromApplication(applicationContext, ArtEntryPoint::class.java).libraryDao()
            val folder = applicationContext.filesDir.resolve("art").apply { mkdirs() }
            dao.getOrphanArtFiles().forEach { File(it).delete() }
            dao.deleteOrphanArt()

            val settings = RommSettings(applicationContext)
            val server = settings.server()
            val base = server?.let { RommAddress.parse(it.address) }
            val authorization = settings.password()?.let { Credentials.basic(server!!.username, it, Charsets.UTF_8) }
            val tried = mutableSetOf<String>()
            while (!isStopped) {
                val now = System.currentTimeMillis()
                val batch =
                    dao.getArtCandidates().map { it to it.rules() }
                        .filter { (candidate, game) -> attempt(candidate, game) !in tried && ArtRules.needsWork(game, now) }
                        .sortedBy { ArtRules.priority(it.second) }
                        .take(BATCH)
                if (batch.isEmpty()) break
                for ((candidate, game) in batch) {
                    if (isStopped) break
                    tried.add(attempt(candidate, game))
                    try {
                        fetch(dao, folder, candidate, game, base?.toString(), authorization, now)
                    } catch (_: IOException) {
                        // The network went away: try again later from where this stopped.
                        return@withContext Result.retry()
                    }
                }
            }
            Result.success()
        }

    private suspend fun fetch(
        dao: LibraryDao,
        folder: File,
        candidate: ArtCandidate,
        game: ArtRules.Game,
        base: String?,
        authorization: String?,
        now: Long,
    ) {
        val romm = ArtRules.rommCoverFor(game)
        val sources =
            buildList {
                if (romm != null && base != null) ArtRules.rommUrl(base, romm)?.let { add(Triple("romm", romm, it)) }
                ArtRules.libretroUrls(game).forEach { add(Triple("libretro", it, it)) }
            }
        for ((source, key, url) in sources) {
            val bytes = download(url, authorization.takeIf { base != null && sameHost(url, base) }) ?: continue
            val file = save(folder, candidate.gameId, key, bytes) ?: continue
            candidate.file?.takeIf { it != file.path }?.let { File(it).delete() }
            dao.setDownloadedArt(candidate.gameId, file.path, source, key, now)
            return
        }
        // Nothing found: keep what there was and remember the attempt, keyed to the RomM cover asked for.
        dao.markArtChecked(candidate.gameId, romm ?: candidate.sourceKey, now)
    }

    /** The image's bytes, or null when the server has none (any answer but 200). Throws when the network fails. */
    private fun download(
        url: String,
        authorization: String?,
    ): ByteArray? {
        val request =
            Request.Builder().url(url).apply { if (authorization != null) header("Authorization", authorization) }.build()
        http.newCall(request).execute().use { response ->
            if (response.code != 200) return null
            val body = response.body ?: return null
            if (body.contentLength() > MAX_BYTES) return null
            return body.bytes().takeIf { it.size <= MAX_BYTES }
        }
    }

    /** Shrinks the image to [ArtRules.MAX_SIDE] and stores it as WebP; null when it is not an image. */
    private fun save(
        folder: File,
        gameId: String,
        key: String,
        bytes: ByteArray,
    ): File? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= ArtRules.MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val scale = ArtRules.MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap =
            if (scale < 1f) {
                Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
            } else {
                decoded
            }
        // A new name per source, so screens holding the old picture see a new one.
        val file = folder.resolve(hash(gameId).take(24) + "-" + hash(key).take(8) + ".webp")
        val part = File(file.path + ".part")
        part.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, QUALITY, it) }
        if (!part.renameTo(file)) {
            part.delete()
            return null
        }
        return file
    }

    /** One try per game and RomM cover: a cover that arrives with a sync during this run is still fetched. */
    private fun attempt(
        candidate: ArtCandidate,
        game: ArtRules.Game,
    ) = candidate.gameId + "|" + ArtRules.rommCoverFor(game)

    private fun sameHost(
        url: String,
        base: String,
    ) = url.toHttpUrlOrNull()?.host == base.toHttpUrlOrNull()?.host

    private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun ArtCandidate.rules() =
        ArtRules.Game(
            platformId = platformId,
            primaryFile = primaryFile,
            rommFileName = rommFileName,
            title = title,
            rommCover = rommCover,
            onDevice = fileCount > 0,
            lastPlayedAt = lastPlayedAt,
            localUri = localUri,
            file = file,
            sourceKey = sourceKey,
            checkedAt = checkedAt,
        )

    companion object {
        private const val NAME = "art"
        private const val BATCH = 50
        private const val QUALITY = 85
        private const val MAX_BYTES = 8L * 1024 * 1024

        /** Starts the art job unless it is already running; it re-reads the library until nothing needs art. */
        fun enqueue(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<ArtWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
