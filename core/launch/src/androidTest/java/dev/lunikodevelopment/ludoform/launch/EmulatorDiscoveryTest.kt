package dev.lunikodevelopment.ludoform.launch

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmulatorDiscoveryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun loadsRegistryAndMatchesVisibleInstalledEmulators() {
        val registry = EmulatorRegistry.load(context)
        val installed = InstalledEmulators(context).packages()
        val shellOutput =
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("pm list packages -3").use { descriptor ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().readText()
                }
        val knownOnDevice =
            listOf(
                "org.ppsspp.ppsspp", "com.github.stenzek.duckstation", "com.retroarch.aarch64", "org.azahar_emu.azahar",
                "org.dolphinemu.dolphinemu", "xyz.aethersx2.tturnip", "me.magnum.melonds", "me.magnum.melonds.nightly",
                "me.magnum.melondualds", "org.citra.emu", "io.github.lime3ds.android",
            )
                .filter { shellOutput.lines().contains("package:$it") }
        knownOnDevice.forEach { packageName ->
            assertTrue("Installed emulator is not visible: $packageName", packageName in installed)
            assertTrue("Missing registry match: $packageName", registry.emulators.any { it.matchesPackage(packageName) })
        }
        assertTrue("No emulator packages available for device verification", knownOnDevice.isNotEmpty())
    }

    @Test
    fun observedEmulatorComponentsAreExportedOnThor() {
        val installed = InstalledEmulators(context).packages()
        val components =
            mapOf(
                "org.dolphinemu.dolphinemu" to "org.dolphinemu.dolphinemu.activities.AppLinkActivity",
                "xyz.aethersx2.tturnip" to "xyz.aethersx2.android.EmulationActivity",
                "com.github.stenzek.duckstation" to "com.github.stenzek.duckstation.EmulationActivity",
                "org.ppsspp.ppsspp" to "org.ppsspp.ppsspp.PpssppActivity",
                "me.magnum.melonds" to "me.magnum.melonds.ui.emulator.EmulatorActivity",
                "me.magnum.melonds.nightly" to "me.magnum.melonds.ui.emulator.EmulatorActivity",
                "me.magnum.melondualds" to "me.magnum.melonds.ui.emulator.EmulatorActivity",
                "org.azahar_emu.azahar" to "org.citra.citra_emu.activities.EmulationActivity",
                "io.github.lime3ds.android" to "org.citra.citra_emu.activities.EmulationActivity",
                "org.citra.emu" to "org.citra.emu.ui.EmulationActivity",
                "com.retroarch.aarch64" to "com.retroarch.browser.retroactivity.RetroActivityFuture",
            )
        val present = components.filterKeys { it in installed }
        assertTrue("No emulator components available for device verification", present.isNotEmpty())
        present.forEach { (packageName, activity) ->
            val info =
                context.packageManager.getActivityInfo(
                    ComponentName(packageName, activity),
                    PackageManager.ComponentInfoFlags.of(0),
                )
            assertTrue("Activity not exported: $packageName/$activity", info.exported)
        }
    }

    @Test
    fun preferencesPersistAndCanBeClearedIndependently() {
        val prefs = context.getSharedPreferences("emulator-preferences-test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val first = EmulatorPreferences(prefs)
        first.setGame("game-1", EmulatorPreference("com.retroarch.aarch64", "mgba"))
        first.setPlatform("gba", EmulatorPreference("org.example.emulator"))
        val reopened = EmulatorPreferences(prefs)
        assertEquals(EmulatorPreference("com.retroarch.aarch64", "mgba"), reopened.game("game-1"))
        first.setDefault(EmulatorPreference("org.dolphinemu.dolphinemu"))
        val resolver = EmulatorResolver(EmulatorRegistry.load(context))
        val selected =
            reopened.resolveForGame(
                resolver,
                "game-1",
                "gba",
                listOf("com.retroarch.aarch64"),
                listOf(CoreInstallation("com.retroarch.aarch64", "/cores/mgba_libretro_android.so")),
            ) as EmulatorResolution.Selected
        assertEquals(PreferenceSource.GAME, selected.source)
        first.setGame("game-1", null)
        assertEquals(null, reopened.game("game-1"))
        assertEquals(EmulatorPreference("org.example.emulator"), reopened.platform("gba"))
        prefs.edit().clear().commit()
    }
}
