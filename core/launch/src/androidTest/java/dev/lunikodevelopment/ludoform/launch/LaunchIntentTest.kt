package dev.lunikodevelopment.ludoform.launch

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchIntentTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val registry = EmulatorRegistry.load(context)

    @Test
    fun ppssppIntentGrantsReadAccessToEveryGameFile() {
        val packages = InstalledEmulators(context).packages()
        assumeTrue("PPSSPP is not installed", "org.ppsspp.ppsspp" in packages)
        val selected = EmulatorResolver(registry).resolve("psp", packages) as EmulatorResolution.Selected
        val disc = LaunchFile("content://test.example/game.iso", "game.iso")
        val sibling = LaunchFile("content://test.example/track.bin", "track.bin")
        val result = createLaunchIntent(context, selected, disc, listOf(disc, sibling)) as LaunchIntentResult.Ready
        assertEquals("org.ppsspp.ppsspp", result.intent.component?.packageName)
        assertEquals(disc.uri, result.intent.data.toString())
        assertTrue(result.intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(2, result.intent.clipData?.itemCount)
    }

    @Test
    fun unverifiedContractIsNotLaunched() {
        val candidate = EmulatorCandidate(registry.emulators.first { it.id == "aethersx2" }, "xyz.aethersx2.tturnip")
        val selected = EmulatorResolution.Selected(candidate, PreferenceSource.AUTOMATIC)
        val disc = LaunchFile("content://test.example/game.iso", "game.iso")
        assertTrue(createLaunchIntent(context, selected, disc, listOf(disc)) is LaunchIntentResult.Unavailable)
    }
}
