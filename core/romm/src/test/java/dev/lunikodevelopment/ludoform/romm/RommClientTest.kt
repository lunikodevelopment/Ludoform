package dev.lunikodevelopment.ludoform.romm

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RommClientTest {
    private val server = MockWebServer()
    private val client = RommClient()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.shutdown()

    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/romm/$name")).readText()

    private fun heartbeat(version: String = "5.4.1") =
        MockResponse().setBody(fixture("heartbeat-5.4.1.json").replace("\"5.4.1\"", "\"$version\""))

    private fun check() = client.check(server.url("/"), "player", "secret")

    @Test
    fun connectedReportsVersionAndUser() {
        server.enqueue(heartbeat())
        server.enqueue(MockResponse().setBody(fixture("users-me.json")))
        assertEquals(RommHealth.Connected("5.4.1", "player"), check())
        assertEquals("/api/heartbeat", server.takeRequest().path)
        val me = server.takeRequest()
        assertEquals("/api/users/me", me.path)
        assertEquals("Basic cGxheWVyOnNlY3JldA==", me.getHeader("Authorization"))
    }

    @Test
    fun heartbeatIsSentWithoutCredentials() {
        server.enqueue(heartbeat())
        server.enqueue(MockResponse().setBody(fixture("users-me.json")))
        check()
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun wrongPasswordIsASignInFailure() {
        server.enqueue(heartbeat())
        server.enqueue(MockResponse().setResponseCode(401).setBody("{\"detail\":\"Invalid credentials\"}"))
        assertEquals(RommHealth.SignInFailed("5.4.1"), check())
    }

    @Test
    fun oldServerIsRefusedBeforeSigningIn() {
        server.enqueue(heartbeat("4.8.0"))
        assertEquals(RommHealth.TooOld("4.8.0"), check())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun aWebPageIsNotRomm() {
        server.enqueue(MockResponse().setBody("<html>Welcome</html>"))
        assertEquals(RommHealth.NotRomm, check())
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(RommHealth.NotRomm, check())
    }

    @Test
    fun serverErrorAfterHeartbeatKeepsTheVersion() {
        server.enqueue(heartbeat())
        server.enqueue(MockResponse().setResponseCode(502))
        assertEquals(RommHealth.ServerError("5.4.1", 502), check())
    }

    @Test
    fun droppedConnectionIsUnreachable() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertTrue(check() is RommHealth.Unreachable)
    }

    @Test
    fun addressesAreNormalised() {
        assertEquals("https://romm.example.org/", RommAddress.parse("romm.example.org")?.toString())
        assertEquals("https://romm.example.org/", RommAddress.parse(" https://romm.example.org/api/ ")?.toString())
        assertEquals("https://example.org:8443/romm/", RommAddress.parse("example.org:8443/romm")?.toString())
        assertEquals("https://example.org/", RommAddress.parse("https://user:pw@example.org/?x=1")?.toString())
        assertEquals("romm.example.org", RommAddress.display(RommAddress.parse("romm.example.org/")!!))
    }

    @Test
    fun plainHttpAndJunkAreRefused() {
        assertNull(RommAddress.parse("http://192.168.1.10:8080"))
        assertNull(RommAddress.parse(""))
        assertNull(RommAddress.parse("not a url"))
        assertEquals(RommHealth.BadAddress, client.check("http://192.168.1.10", "a", "b"))
    }

    @Test
    fun versionsBelowFiveAreUnsupported() {
        assertTrue(RommVersion.isSupported("5.0.0"))
        assertTrue(RommVersion.isSupported("5.4.1"))
        assertTrue(RommVersion.isSupported("v6.0.0-beta.1"))
        assertTrue(RommVersion.isSupported("development"))
        assertFalse(RommVersion.isSupported("4.8.1"))
        assertTrue(RommVersion.isSupported("5.0.0-alpha.1"))
    }
}
