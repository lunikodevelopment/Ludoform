package dev.lunikodevelopment.ludoform.romm

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/** A save stored on the RomM server for one ROM. */
data class RemoteSave(
    val id: Int,
    val romId: Int,
    val fileName: String,
    val sizeBytes: Long,
    /** The server's own timestamp string, compared verbatim with the baseline. */
    val updatedAt: String,
    val updatedAtMillis: Long,
    val emulator: String?,
    /** The server's content hash when it reports one; compared only when it looks like an MD5. */
    val contentHash: String? = null,
    /** Where the server serves the file, when it says (`download_path`). */
    val downloadPath: String? = null,
    /** The server knows the save but its file is gone from its disk, so it cannot be downloaded. */
    val missing: Boolean = false,
)

/** RomM's save endpoints (`/api/saves`), signed in with HTTP Basic like the rest. Nothing here deletes. */
class RommSavesApi(
    private val base: HttpUrl,
    username: String,
    password: String,
    private val http: OkHttpClient = RommClient.defaultHttpClient(),
) {
    private val authorization = Credentials.basic(username, password, Charsets.UTF_8)

    fun list(romId: Int): List<RemoteSave> {
        val url = base.resolve("api/saves")!!.newBuilder().addQueryParameter("rom_id", romId.toString()).build()
        val body = call(Request.Builder().url(url).get())
        return try {
            val array = JSONArray(String(body, Charsets.UTF_8))
            (0 until array.length()).map { save(array.getJSONObject(it)) }
        } catch (error: JSONException) {
            throw RommRequestException(RommRequestException.Kind.FORMAT, "Saves are not a list")
        }
    }

    /** The save's bytes, from the server's own `download_path` when it gives one. */
    fun download(save: RemoteSave): ByteArray {
        val url =
            save.downloadPath?.takeIf { it.startsWith("/") }?.let { base.resolve(it.removePrefix("/")) }
                ?: base.resolve("api/saves/${save.id}/content")!!
        return call(Request.Builder().url(url).get())
    }

    /** A new save on the server for [romId]. */
    fun upload(
        romId: Int,
        fileName: String,
        content: ByteArray,
        emulator: String,
        contentHash: String,
    ): RemoteSave {
        val url =
            base.resolve("api/saves")!!.newBuilder()
                .addQueryParameter("rom_id", romId.toString())
                .addQueryParameter("emulator", emulator.take(50))
                .addQueryParameter("content_hash", contentHash)
                .build()
        return save(json(call(Request.Builder().url(url).post(form(fileName, content)))))
    }

    /** Replaces the content of an existing server save, which the baseline says is ours to update. */
    fun update(
        saveId: Int,
        fileName: String,
        content: ByteArray,
        contentHash: String,
    ): RemoteSave {
        val url = base.resolve("api/saves/$saveId")!!.newBuilder().addQueryParameter("content_hash", contentHash).build()
        return save(json(call(Request.Builder().url(url).put(form(fileName, content)))))
    }

    private fun form(
        fileName: String,
        content: ByteArray,
    ): RequestBody =
        MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("saveFile", fileName, content.toRequestBody("application/octet-stream".toMediaType()))
            .build()

    private fun call(builder: Request.Builder): ByteArray {
        val request = builder.header("Authorization", authorization).build()
        try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.bytes() ?: ByteArray(0)
                return when (response.code) {
                    in 200..299 -> body
                    401, 403 -> throw RommRequestException(RommRequestException.Kind.SIGN_IN, "Sign-in refused")
                    404 -> throw RommRequestException(RommRequestException.Kind.NOT_FOUND, "Not found: ${request.url.encodedPath}")
                    else -> throw RommRequestException(
                        RommRequestException.Kind.SERVER,
                        "HTTP ${response.code} on ${request.url.encodedPath}",
                    )
                }
            }
        } catch (error: RommRequestException) {
            throw error
        } catch (error: IOException) {
            throw RommRequestException(RommRequestException.Kind.UNREACHABLE, "${error.javaClass.simpleName} on ${request.url.encodedPath}")
        }
    }

    private fun json(body: ByteArray): JSONObject =
        try {
            JSONObject(String(body, Charsets.UTF_8))
        } catch (error: JSONException) {
            throw RommRequestException(RommRequestException.Kind.FORMAT, "Not a save")
        }

    private fun save(json: JSONObject): RemoteSave {
        val updated = json.optString("updated_at")
        return RemoteSave(
            id = json.getInt("id"),
            romId = json.optInt("rom_id"),
            fileName = json.optString("file_name"),
            sizeBytes = json.optLong("file_size_bytes"),
            updatedAt = updated,
            updatedAtMillis = RommLibraryApi.parseTime(updated),
            emulator = json.optString("emulator").takeIf { it.isNotBlank() && it != "null" },
            contentHash = json.optString("content_hash").takeIf { it.matches(Regex("[0-9a-fA-F]{32}")) },
            downloadPath = json.optString("download_path").takeIf { it.isNotBlank() && it != "null" },
            missing = json.optBoolean("missing_from_fs"),
        )
    }
}

/**
 * The save sync rules, pure so they are unit-tested. A side "changed" when it differs from the baseline: the
 * last content both sides agreed on. Newest wins only when exactly one side changed; when both changed, or there is
 * no baseline and the two differ, the player decides.
 */
object SaveRules {
    sealed interface Action {
        data object Nothing : Action

        data object Upload : Action

        data class Download(val save: RemoteSave) : Action

        data class Conflict(val save: RemoteSave) : Action
    }

    /** The server save a local save corresponds to: same file type, newest first. */
    fun pick(
        saves: List<RemoteSave>,
        extension: String,
    ): RemoteSave? =
        saves.filter { !it.missing && it.fileName.substringAfterLast('.', "").equals(extension, ignoreCase = true) }
            .maxWithOrNull(compareBy<RemoteSave> { it.updatedAtMillis }.thenBy { it.id })

    fun decide(
        localHash: String?,
        server: RemoteSave?,
        serverHash: String?,
        baselineHash: String?,
        baselineServerId: Int?,
        baselineServerUpdatedAt: String?,
    ): Action {
        val serverChanged = server != null && (server.id != baselineServerId || server.updatedAt != baselineServerUpdatedAt)
        val localChanged = localHash != null && localHash != baselineHash
        return when {
            localHash == null && server == null -> Action.Nothing
            localHash == null -> Action.Download(server!!)
            server == null -> Action.Upload
            // Same bytes on both sides (when the server's hash is known and comparable): nothing to move.
            serverHash != null && serverHash.equals(localHash, ignoreCase = true) -> Action.Nothing
            baselineHash == null -> Action.Conflict(server)
            localChanged && serverChanged -> Action.Conflict(server)
            localChanged -> Action.Upload
            serverChanged -> Action.Download(server)
            else -> Action.Nothing
        }
    }
}
