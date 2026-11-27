package dev.lunikodevelopment.ludoform.romm

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** What the health check found, from "the address is wrong" to "signed in". */
sealed interface RommHealth {
    /** The address cannot be used as a server address. */
    data object BadAddress : RommHealth

    /** Nothing answered: no network, a wrong name, a closed port or a timeout. */
    data class Unreachable(val reason: String) : RommHealth

    /** Something answered, but it is not a RomM server (or not its API). */
    data object NotRomm : RommHealth

    data class TooOld(val version: String) : RommHealth

    data class SignInFailed(val version: String) : RommHealth

    /** The server answered an authenticated request with an unexpected status. */
    data class ServerError(val version: String, val code: Int) : RommHealth

    data class Connected(val version: String, val username: String) : RommHealth
}

/**
 * RomM's REST API. Every call blocks, so callers run it from a WorkManager job (only `core:romm` and
 * `core:transfer` touch the network, and only from background work).
 */
class RommClient(
    private val http: OkHttpClient = defaultHttpClient(),
) {
    /** Checks reachability and version with the open heartbeat, then sign-in with `/api/users/me`. */
    fun check(
        address: String,
        username: String,
        password: String,
    ): RommHealth {
        val base = RommAddress.parse(address) ?: return RommHealth.BadAddress
        return check(base, username, password)
    }

    /** [check] for an already parsed base URL; tests use it with a plain-http mock server. */
    internal fun check(
        base: HttpUrl,
        username: String,
        password: String,
    ): RommHealth {
        val version =
            when (val heartbeat = heartbeat(base)) {
                is Heartbeat.Answered -> heartbeat.version ?: return RommHealth.NotRomm
                is Heartbeat.Failed -> return heartbeat.health
            }
        if (!RommVersion.isSupported(version)) return RommHealth.TooOld(version)
        val request =
            Request.Builder().url(base.resolve("api/users/me")!!)
                .header("Authorization", Credentials.basic(username, password, Charsets.UTF_8))
                .header("Accept", "application/json")
                .build()
        return try {
            http.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val name =
                            response.body?.string()?.let { body ->
                                runCatching { JSONObject(body).optString("username") }.getOrNull()
                            }
                        if (name.isNullOrEmpty()) RommHealth.SignInFailed(version) else RommHealth.Connected(version, name)
                    }
                    401, 403 -> RommHealth.SignInFailed(version)
                    else -> RommHealth.ServerError(version, response.code)
                }
            }
        } catch (error: IOException) {
            RommHealth.Unreachable(describe(error))
        }
    }

    private sealed interface Heartbeat {
        data class Answered(val version: String?) : Heartbeat

        data class Failed(val health: RommHealth) : Heartbeat
    }

    private fun heartbeat(base: HttpUrl): Heartbeat {
        val request = Request.Builder().url(base.resolve("api/heartbeat")!!).header("Accept", "application/json").build()
        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Heartbeat.Failed(RommHealth.NotRomm)
                val body = response.body?.string().orEmpty()
                val version =
                    try {
                        JSONObject(body).optJSONObject("SYSTEM")?.optString("VERSION")?.takeIf { it.isNotBlank() }
                    } catch (_: JSONException) {
                        null
                    }
                Heartbeat.Answered(version)
            }
        } catch (error: IOException) {
            Heartbeat.Failed(RommHealth.Unreachable(describe(error)))
        }
    }

    private fun describe(error: IOException): String =
        when (error) {
            is UnknownHostException -> "The server name was not found."
            is SSLException -> "The secure connection failed."
            is java.net.SocketTimeoutException -> "The server did not answer in time."
            is java.net.ConnectException -> "The server refused the connection."
            else -> "The connection failed."
        }

    companion object {
        fun defaultHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}

object RommAddress {
    /**
     * Turns what the user typed into the server's base URL, ending in a slash: `romm.example.org` becomes
     * `https://romm.example.org/`. A pasted `/api` suffix is dropped. Plain http is refused: the password travels
     * with every request.
     */
    fun parse(input: String): HttpUrl? {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val withScheme = if ("://" in trimmed) trimmed else "https://$trimmed"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (!url.isHttps) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }.let { if (it.lastOrNull() == "api") it.dropLast(1) else it }
        val builder = url.newBuilder().username("").password("").query(null).fragment(null).encodedPath("/")
        segments.forEach { builder.addPathSegment(it) }
        if (segments.isNotEmpty()) builder.addPathSegment("")
        return builder.build()
    }

    /** The address as shown and saved: no scheme for https, no trailing slash. */
    fun display(url: HttpUrl): String = url.toString().removePrefix("https://").trimEnd('/')
}

/** RomM release numbers (`5.4.1`, `4.0.0-beta.2`). Unknown builds such as `development` count as current. */
object RommVersion {
    /** Save sync needs 5.0.0, so Ludoform does not connect to older servers. */
    const val MINIMUM = "5.0.0"

    fun isSupported(version: String): Boolean {
        val parts = numbers(version) ?: return true
        val minimum = numbers(MINIMUM)!!
        for (i in 0 until 3) {
            if (parts[i] != minimum[i]) return parts[i] > minimum[i]
        }
        return true
    }

    private fun numbers(version: String): List<Int>? {
        val match = Regex("""^v?(\d+)(?:\.(\d+))?(?:\.(\d+))?""").find(version.trim()) ?: return null
        return match.groupValues.drop(1).map { it.toIntOrNull() ?: 0 }
    }
}
