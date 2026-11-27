package dev.lunikodevelopment.ludoform.romm

import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * RomM's scan: `POST /api/tasks/scan` queues a quick scan of some platform folders, `GET /api/tasks/{id}` follows
 * it. Needs RomM 5's `tasks.run` scope, which RomM gives admins; anyone else gets [RommRequestException.Kind.SIGN_IN].
 */
class RommTasksApi(
    private val base: HttpUrl,
    username: String,
    password: String,
    private val http: OkHttpClient = RommClient.defaultHttpClient(),
) {
    private val authorization = Credentials.basic(username, password, Charsets.UTF_8)

    /** Queues a quick scan of the platform folders [fsSlugs]; returns the task id to follow. */
    fun scan(fsSlugs: Collection<String>): String {
        val body = JSONObject().put("type", "quick").put("platform_fs_slugs", JSONArray(fsSlugs.distinct().sorted()))
        val answer = call(Request.Builder().url(base.resolve("api/tasks/scan")!!).post(body.toString().toRequestBody(JSON)))
        return try {
            JSONObject(String(answer, Charsets.UTF_8)).getString("task_id")
        } catch (error: JSONException) {
            throw RommRequestException(RommRequestException.Kind.FORMAT, "No task id")
        }
    }

    /** The task's state as RomM names it: `queued`, `started`, `finished`, `failed`… */
    fun status(taskId: String): String {
        val answer = call(Request.Builder().url(base.resolve("api/tasks/$taskId")!!).get())
        return try {
            JSONObject(String(answer, Charsets.UTF_8)).getString("status")
        } catch (error: JSONException) {
            throw RommRequestException(RommRequestException.Kind.FORMAT, "No task status")
        }
    }

    private fun call(builder: Request.Builder): ByteArray {
        val request = builder.header("Authorization", authorization).build()
        try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.bytes() ?: ByteArray(0)
                return when (response.code) {
                    in 200..299 -> body
                    401, 403 -> throw RommRequestException(RommRequestException.Kind.SIGN_IN, "Not allowed: ${request.url.encodedPath}")
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

    companion object {
        private val JSON = "application/json".toMediaType()

        /** Task states after which RomM does no more. */
        val FINISHED = setOf("finished")
        val ENDED_BADLY = setOf("failed", "stopped", "canceled")
    }
}
