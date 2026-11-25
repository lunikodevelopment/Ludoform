package dev.lunikodevelopment.ludoform.romm

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Bodies follow the RomM 5.4.1 OpenAPI schemas (SimpleRomSchema, PlatformSchema), trimmed to the fields Ludoform reads. */
class RommLibraryApiTest {
    private val server = MockWebServer()
    private lateinit var api: RommLibraryApi

    @Before
    fun start() {
        server.start()
        api = RommLibraryApi(server.url("/"), "player", "secret")
    }

    @After
    fun stop() = server.shutdown()

    private fun romJson(id: Int) =
        JSONObject()
            .put("id", id).put("platform_id", 7).put("fs_name", "Game $id (USA).gba").put("fs_name_no_ext", "Game $id (USA)")
            .put("fs_size_bytes", 4_194_304).put("created_at", "2026-01-02T03:04:05+00:00")
            .put("updated_at", "2026-11-20T10:00:00.123456+00:00").put("missing_from_fs", false)

    private fun page(ids: IntRange) =
        MockResponse().setBody(
            JSONObject().put(
                "items",
                JSONArray(ids.map { romJson(it) }),
            ).put("total", JSONObject.NULL).put("limit", 500).put("offset", 0).toString(),
        )

    @Test
    fun romsArePagedUntilAShortPage() {
        server.enqueue(page(1..500))
        server.enqueue(page(501..503))
        val seen = mutableListOf<RemoteRom>()
        api.roms("2026-11-01T00:00:00+00:00") { seen.addAll(it) }
        assertEquals(503, seen.size)
        val first = server.takeRequest().requestUrl!!
        assertEquals("/api/roms", first.encodedPath)
        assertEquals("0", first.queryParameter("offset"))
        assertEquals("id", first.queryParameter("order_by"))
        assertEquals("2026-11-01T00:00:00+00:00", first.queryParameter("updated_after"))
        assertEquals(
            "Basic cGxheWVyOnNlY3JldA==",
            server.takeRequest().also {
                assertEquals("500", it.requestUrl!!.queryParameter("offset"))
            }.getHeader("Authorization"),
        )
        val rom = seen.first()
        assertEquals("Game 1 (USA)", rom.fsNameNoExt)
        assertEquals(4_194_304, rom.sizeBytes)
        assertEquals(7, rom.platformId)
    }

    @Test
    fun platformsAndIdentifiersAreRead() {
        server.enqueue(
            MockResponse().setBody(
                JSONArray().put(
                    JSONObject().put("id", 7).put("slug", "gba").put("fs_slug", "gba").put("name", "Game Boy Advance")
                        .put("display_name", "Game Boy Advance").put("rom_count", 512),
                ).toString(),
            ),
        )
        server.enqueue(MockResponse().setBody("[1, 2, 3]"))
        assertEquals(listOf(RemotePlatform(7, "gba", "gba", "Game Boy Advance", 512)), api.platforms())
        assertEquals(setOf(1, 2, 3), api.identifiers())
    }

    @Test
    fun refusedSignInIsReportedNotSwallowed() {
        server.enqueue(MockResponse().setResponseCode(401))
        val error = runCatching { api.identifiers() }.exceptionOrNull()
        assertTrue(error is RommRequestException && error.kind == RommRequestException.Kind.SIGN_IN)
    }
}
