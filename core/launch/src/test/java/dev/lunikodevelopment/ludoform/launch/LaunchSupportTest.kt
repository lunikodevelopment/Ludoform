package dev.lunikodevelopment.ludoform.launch

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class LaunchSupportTest {
    private val registry = EmulatorRegistry.parse(File("src/main/assets/emulators.json").readText())

    private fun emulator(id: String) = registry.emulators.single { it.id == id }

    @Test
    fun onlySourceVerifiedUriContractsAreReady() {
        assertEquals(LaunchReadiness.READY, emulator("ppsspp").readiness())
        assertEquals(LaunchReadiness.READY, emulator("melonds").readiness())
        assertEquals(LaunchReadiness.READY, emulator("duckstation").readiness())
        assertEquals(LaunchReadiness.NEEDS_CORE, emulator("retroarch").readiness())
        assertEquals(LaunchReadiness.NOT_VERIFIED, emulator("dolphin").readiness())
        assertEquals(LaunchReadiness.NOT_VERIFIED, emulator("aethersx2").readiness())
    }

    @Test
    fun launchMethodDescribesTheContract() {
        assertEquals("Opens the game file", emulator("ppsspp").launchMethod())
        assertEquals("Launch intent · game path", emulator("duckstation").launchMethod())
        assertEquals("Needs Dolphin's game ID", emulator("dolphin").launchMethod())
        assertEquals("Not verified yet", emulator("citra").launchMethod())
    }
}
