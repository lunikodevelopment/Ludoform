package dev.lunikodevelopment.ludoform.launch

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchScreensTest {
    @Test
    fun gameBeatsEmulatorBeatsDefaultAndTopIsTheFallback() {
        assertEquals(ScreenPreference.TOP, resolveScreen(null, null, null))
        assertEquals(ScreenPreference.ASK, resolveScreen(null, null, ScreenPreference.ASK))
        assertEquals(ScreenPreference.BOTTOM, resolveScreen(null, ScreenPreference.BOTTOM, ScreenPreference.ASK))
        assertEquals(ScreenPreference.TOP, resolveScreen(ScreenPreference.TOP, ScreenPreference.BOTTOM, ScreenPreference.ASK))
    }
}
