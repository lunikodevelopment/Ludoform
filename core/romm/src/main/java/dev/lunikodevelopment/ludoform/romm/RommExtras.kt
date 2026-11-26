package dev.lunikodevelopment.ludoform.romm

import android.content.Context
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** One RetroAchievements achievement as RomM gives it, and whether the player has earned it. */
data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val points: Int,
    val earned: Boolean,
    val earnedHardcore: Boolean = false,
)

/**
 * What the in-game menu shows beyond the game itself (D48): RetroAchievements through RomM, and RomM's manual. Kept
 * per game in `files/extras` by [GameExtrasStore], so the menu reads it without the network.
 */
data class GameExtras(
    val rommId: Int,
    val achievements: List<Achievement> = emptyList(),
    /** RomM's manual, downloaded into `files/manuals`; null when RomM has none. */
    val manualFile: String? = null,
    val fetchedAt: Long = 0,
) {
    val earned: Int get() = achievements.count { it.earned }
    val points: Int get() = achievements.sumOf { it.points }
    val earnedPoints: Int get() = achievements.filter { it.earned }.sumOf { it.points }
}

/**
 * The parts of RomM 5's API the extras need: `GET /api/roms/{id}` for the game's RetroAchievements set and manual,
 * `GET /api/users/me` for what the player earned. Parsing is in the companion so it is unit-tested.
 */
class RommExtrasApi(
    private val base: HttpUrl,
    username: String,
    password: String,
    private val http: OkHttpClient = RommClient.defaultHttpClient(),
) {
    private val authorization = Credentials.basic(username, password, Charsets.UTF_8)

    fun rom(id: Int): String = String(get(base.resolve("api/roms/$id")!!), Charsets.UTF_8)

    fun me(): String = String(get(base.resolve("api/users/me")!!), Charsets.UTF_8)

    /** The file at a RomM resource path (or URL on the same server). */
    fun download(path: String): ByteArray {
        val url =
            when {
                path.startsWith("https://") || path.startsWith("http://") -> path
                path.startsWith("/") -> base.toString().trimEnd('/') + path
                else -> base.toString().trimEnd('/') + "/assets/romm/resources/" + path
            }
        val parsed =
            url.toHttpUrlOrNull()
                ?: throw RommRequestException(RommRequestException.Kind.FORMAT, "Bad manual address")
        if (parsed.host != base.host) throw RommRequestException(RommRequestException.Kind.FORMAT, "Manual on another host")
        return get(parsed)
    }

    private fun get(url: HttpUrl): ByteArray {
        val request = Request.Builder().url(url).header("Authorization", authorization).build()
        try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.bytes() ?: ByteArray(0)
                return when (response.code) {
                    200 -> body
                    401, 403 -> throw RommRequestException(RommRequestException.Kind.SIGN_IN, "Sign-in refused")
                    404 -> throw RommRequestException(RommRequestException.Kind.NOT_FOUND, "Not found")
                    else -> throw RommRequestException(RommRequestException.Kind.SERVER, "HTTP ${response.code}")
                }
            }
        } catch (error: RommRequestException) {
            throw error
        } catch (error: IOException) {
            throw RommRequestException(RommRequestException.Kind.UNREACHABLE, error.javaClass.simpleName)
        }
    }

    /** What `GET /api/roms/{id}` says about extras. */
    data class RomExtras(val raId: Int?, val achievements: List<Achievement>, val manualPath: String?)

    companion object {
        fun parseRom(body: String): RomExtras {
            val json =
                try {
                    JSONObject(body)
                } catch (error: JSONException) {
                    throw RommRequestException(RommRequestException.Kind.FORMAT, "Not an object")
                }
            val raId = json.optInt("ra_id", 0).takeIf { it > 0 }
            val metadata = json.optJSONObject("merged_ra_metadata") ?: json.optJSONObject("ra_metadata")
            val achievements =
                metadata?.optJSONArray("achievements")?.objects().orEmpty()
                    .sortedBy { it.optInt("display_order", Int.MAX_VALUE) }
                    .map {
                        Achievement(
                            id = it.text("badge_id") ?: it.text("ra_id") ?: it.text("id").orEmpty(),
                            title = it.text("title") ?: "Achievement",
                            description = it.text("description").orEmpty(),
                            points = it.optInt("points"),
                            earned = false,
                        )
                    }
            val manual =
                if (json.optBoolean("has_manual", false) || json.text("path_manual") != null) {
                    json.text("path_manual") ?: json.text("url_manual")
                } else {
                    null
                }
            return RomExtras(raId, achievements, manual)
        }

        /**
         * Marks what the player earned, from `ra_progression` in `GET /api/users/me`. RomM names an earned
         * achievement by its id, which matches either the achievement's RetroAchievements id or its badge.
         */
        fun applyProgress(
            achievements: List<Achievement>,
            raId: Int?,
            meBody: String,
            rawIds: Map<String, String> = emptyMap(),
        ): List<Achievement> {
            if (raId == null) return achievements
            val results =
                runCatching { JSONObject(meBody).optJSONObject("ra_progression")?.optJSONArray("results") }.getOrNull()
                    ?: return achievements
            val game = results.objects().firstOrNull { it.optInt("rom_ra_id") == raId } ?: return achievements
            val earned = game.optJSONArray("earned_achievements")?.objects().orEmpty()
            val soft = earned.mapNotNull { it.text("id") }.toSet()
            val hard = earned.filter { it.text("date_hardcore") != null }.mapNotNull { it.text("id") }.toSet()
            return achievements.map {
                val ids = setOfNotNull(it.id, rawIds[it.id])
                it.copy(earned = ids.any { id -> id in soft }, earnedHardcore = ids.any { id -> id in hard })
            }
        }

        /** Each achievement's id mapped to its RetroAchievements id, when RomM gives both. */
        fun rawIds(body: String): Map<String, String> {
            val metadata =
                runCatching { JSONObject(body).let { it.optJSONObject("merged_ra_metadata") ?: it.optJSONObject("ra_metadata") } }
                    .getOrNull() ?: return emptyMap()
            return metadata.optJSONArray("achievements")?.objects().orEmpty().mapNotNull {
                val id = it.text("badge_id") ?: return@mapNotNull null
                val ra = it.text("ra_id") ?: return@mapNotNull null
                id to ra
            }.toMap()
        }
    }
}

/** [GameExtras] per library game, as small JSON files (D48). */
class GameExtrasStore(context: Context) {
    private val folder = context.filesDir.resolve("extras").apply { mkdirs() }
    val manuals: File = context.filesDir.resolve("manuals").apply { mkdirs() }

    fun load(gameId: String): GameExtras? =
        runCatching {
            val json = JSONObject(file(gameId).takeIf { it.exists() }?.readText() ?: return null)
            GameExtras(
                rommId = json.getInt("rommId"),
                achievements =
                    json.optJSONArray("achievements")?.objects().orEmpty().map {
                        Achievement(
                            it.getString("id"),
                            it.getString("title"),
                            it.optString("description"),
                            it.optInt("points"),
                            it.optBoolean("earned"),
                            it.optBoolean("hardcore"),
                        )
                    },
                manualFile = json.text("manual")?.takeIf { File(it).exists() },
                fetchedAt = json.optLong("fetchedAt"),
            )
        }.getOrNull()

    fun save(
        gameId: String,
        extras: GameExtras,
    ) {
        val json =
            JSONObject()
                .put("rommId", extras.rommId)
                .put("manual", extras.manualFile ?: JSONObject.NULL)
                .put("fetchedAt", extras.fetchedAt)
                .put(
                    "achievements",
                    JSONArray(
                        extras.achievements.map {
                            JSONObject().put("id", it.id).put("title", it.title).put("description", it.description)
                                .put("points", it.points).put("earned", it.earned).put("hardcore", it.earnedHardcore)
                        },
                    ),
                )
        val target = file(gameId)
        val temp = File(target.path + ".tmp")
        temp.writeText(json.toString())
        temp.renameTo(target)
    }

    private fun file(gameId: String): File {
        val digest = java.security.MessageDigest.getInstance("SHA-1").digest(gameId.toByteArray(Charsets.UTF_8))
        return folder.resolve(digest.joinToString("") { "%02x".format(it) } + ".json")
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONObject.text(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }
