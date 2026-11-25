package dev.lunikodevelopment.ludoform.romm

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

data class RemotePlatform(val id: Int, val slug: String, val fsSlug: String, val name: String, val romCount: Int)

data class RemoteRom(
    val id: Int,
    val platformId: Int,
    val fsName: String,
    val fsNameNoExt: String,
    val sizeBytes: Long,
    /** Epoch milliseconds; 0 when the server's timestamp cannot be read. */
    val createdAt: Long,
    /** The server's own timestamp string, passed back verbatim as `updated_after`. */
    val updatedAt: String,
    val missingFromDisk: Boolean,
)

/** Why a library request failed: the sync reports it and keeps the library as it was. */
class RommRequestException(val kind: Kind, message: String) : IOException(message) {
    enum class Kind { UNREACHABLE, SIGN_IN, SERVER, FORMAT, NOT_FOUND }
}

/** The library half of RomM's API, signed in with HTTP Basic like the health check (D29). */
class RommLibraryApi(
    private val base: HttpUrl,
    username: String,
    password: String,
    private val http: OkHttpClient = RommClient.defaultHttpClient(),
) {
    private val authorization = Credentials.basic(username, password, Charsets.UTF_8)

    fun platforms(): List<RemotePlatform> =
        array(get(base.resolve("api/platforms")!!)).objects().map {
            RemotePlatform(
                id = it.getInt("id"),
                slug = it.optString("slug"),
                fsSlug = it.optString("fs_slug"),
                name = it.optString("display_name").ifEmpty { it.optString("name") },
                romCount = it.optInt("rom_count"),
            )
        }

    /** Every ROM id on the server, for deletion reconciliation. */
    fun identifiers(): Set<Int> =
        array(get(base.resolve("api/roms/identifiers")!!)).let {
                ids ->
            (0 until ids.length()).map { ids.getInt(it) }.toSet()
        }

    /**
     * ROMs ordered by id, [PAGE] at a time, optionally only those changed after [updatedAfter]. Calls [onPage] per
     * page so a large library never sits in memory twice.
     */
    fun roms(
        updatedAfter: String?,
        onPage: (List<RemoteRom>) -> Unit,
    ) {
        var offset = 0
        while (true) {
            val url =
                base.resolve("api/roms")!!.newBuilder()
                    .addQueryParameter("order_by", "id")
                    .addQueryParameter("order_dir", "asc")
                    .addQueryParameter("limit", PAGE.toString())
                    .addQueryParameter("offset", offset.toString())
                    .addQueryParameter("with_char_index", "false")
                    .addQueryParameter("with_filter_values", "false")
                    .addQueryParameter("with_rom_id_index", "false")
                    .addQueryParameter("with_total", "false")
                    .apply { if (updatedAfter != null) addQueryParameter("updated_after", updatedAfter) }
                    .build()
            val items = obj(get(url)).optJSONArray("items") ?: throw RommRequestException(RommRequestException.Kind.FORMAT, "No items")
            val page = items.objects().map(::rom)
            onPage(page)
            if (page.size < PAGE) return
            offset += PAGE
        }
    }

    private fun rom(json: JSONObject): RemoteRom {
        val fsName = json.optString("fs_name")
        return RemoteRom(
            id = json.getInt("id"),
            platformId = json.getInt("platform_id"),
            fsName = fsName,
            fsNameNoExt = json.optString("fs_name_no_ext").ifEmpty { fsName.substringBeforeLast('.') },
            sizeBytes = json.optLong("fs_size_bytes"),
            createdAt = parseTime(json.optString("created_at")),
            updatedAt = json.optString("updated_at"),
            missingFromDisk = json.optBoolean("missing_from_fs"),
        )
    }

    private fun get(url: HttpUrl): String {
        val request = Request.Builder().url(url).header("Authorization", authorization).header("Accept", "application/json").build()
        try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                return when (response.code) {
                    200 -> body
                    401, 403 -> throw RommRequestException(RommRequestException.Kind.SIGN_IN, "Sign-in refused")
                    else -> throw RommRequestException(RommRequestException.Kind.SERVER, "HTTP ${response.code}")
                }
            }
        } catch (error: RommRequestException) {
            throw error
        } catch (error: IOException) {
            throw RommRequestException(RommRequestException.Kind.UNREACHABLE, error.javaClass.simpleName)
        }
    }

    private fun array(body: String): JSONArray =
        try {
            JSONArray(body)
        } catch (error: JSONException) {
            throw RommRequestException(RommRequestException.Kind.FORMAT, "Not a list")
        }

    private fun obj(body: String): JSONObject =
        try {
            JSONObject(body)
        } catch (error: JSONException) {
            throw RommRequestException(RommRequestException.Kind.FORMAT, "Not an object")
        }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    companion object {
        const val PAGE = 500

        /** RomM writes ISO 8601 with an offset; a timestamp without one is read as UTC. */
        fun parseTime(text: String): Long {
            if (text.isBlank()) return 0
            return try {
                OffsetDateTime.parse(text).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
                try {
                    java.time.LocalDateTime.parse(text).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
                } catch (_: DateTimeParseException) {
                    0
                }
            }
        }
    }
}
