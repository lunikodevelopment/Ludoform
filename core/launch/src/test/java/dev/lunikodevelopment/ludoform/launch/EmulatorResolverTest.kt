package dev.lunikodevelopment.ludoform.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmulatorResolverTest {
    private val registry = EmulatorRegistry.parse(File("src/main/assets/emulators.json").readText())
    private val resolver = EmulatorResolver(registry)
    private val thorPackages =
        listOf(
            "com.github.stenzek.duckstation", "org.ppsspp.ppsspp", "me.magnum.melondualds", "org.citra.emu",
            "com.retroarch.aarch64", "me.magnum.melonds", "xyz.aethersx2.tturnip", "me.magnum.melonds.nightly",
            "org.azahar_emu.azahar", "org.dolphinemu.dolphinemu",
        )

    @Test
    fun detectsThorEmulatorFamiliesWithoutPrefixCollisions() {
        val all = registry.emulators.flatMap { emulator -> thorPackages.filter(emulator::matchesPackage) }
        assertEquals(thorPackages.toSet(), all.toSet())
        assertFalse(registry.emulators.first { it.id == "dolphin" }.matchesPackage("org.dolphinemu.dolphinemu_fake"))
        assertFalse(registry.emulators.first { it.id == "retroarch" }.matchesPackage("com.retroarchmalware"))
    }

    @Test
    fun preferencesOverrideAutomaticStandaloneChoiceInOrder() {
        val game = EmulatorPreference("org.citra.emu")
        val platform = EmulatorPreference("org.azahar_emu.azahar")
        val default = EmulatorPreference("com.retroarch.aarch64")
        val chosen = resolver.resolve("3ds", thorPackages, game, platform, default) as EmulatorResolution.Selected
        assertEquals("org.citra.emu", chosen.candidate.packageName)
        assertEquals(PreferenceSource.GAME, chosen.source)
        val platformChoice =
            resolver.resolve(
                "3ds",
                thorPackages,
                platformPreference = platform,
                defaultPreference = default,
            ) as EmulatorResolution.Selected
        assertEquals(PreferenceSource.PLATFORM, platformChoice.source)
        val automatic = resolver.resolve("gc", thorPackages) as EmulatorResolution.Selected
        assertEquals("org.dolphinemu.dolphinemu", automatic.candidate.packageName)
        assertEquals(PreferenceSource.AUTOMATIC, automatic.source)
    }

    @Test
    fun missingOrIncompatiblePreferenceIsNotSilentlyOverridden() {
        assertTrue(
            resolver.resolve("gc", thorPackages, gamePreference = EmulatorPreference("missing.pkg")) is EmulatorResolution.MissingEmulator,
        )
        assertTrue(
            resolver.resolve(
                "gc",
                thorPackages,
                gamePreference = EmulatorPreference("org.ppsspp.ppsspp"),
            ) is EmulatorResolution.IncompatiblePreference,
        )
    }

    @Test
    fun retroarchNeedsRealCorePathAndUsesSelectedCompatibleCore() {
        val retro = EmulatorPreference("com.retroarch.aarch64")
        val path = "/data/user/0/com.retroarch.aarch64/cores/mgba_libretro_android.so"
        assertTrue(resolver.resolve("gba", thorPackages, platformPreference = retro) is EmulatorResolution.NeedsCore)
        val choice =
            resolver.resolve(
                "gba",
                thorPackages,
                platformPreference = retro,
                installedCores = listOf(CoreInstallation(retro.packageName, path)),
            ) as EmulatorResolution.Selected
        assertEquals("mgba", choice.core?.id)
        assertEquals(path, choice.corePath)
        assertTrue(
            resolver.resolve(
                "gba",
                thorPackages,
                gamePreference = retro.copy(coreId = "snes9x"),
                installedCores = listOf(CoreInstallation(retro.packageName, path)),
            ) is EmulatorResolution.MissingCore,
        )
        assertTrue(
            resolver.resolve(
                "gba",
                thorPackages,
                platformPreference = retro,
                installedCores = listOf(CoreInstallation(retro.packageName, "mgba_libretro_android.so")),
            ) is EmulatorResolution.NeedsCore,
        )
    }

    @Test
    fun globalDefaultAndCorePackageIsolationAreRespected() {
        val retro = EmulatorPreference("com.retroarch.aarch64", "mgba")
        val wrongPackage = CoreInstallation("com.retroarch", "/cores/mgba_libretro_android.so")
        assertTrue(
            resolver.resolve(
                "gba",
                thorPackages,
                defaultPreference = retro,
                installedCores = listOf(wrongPackage),
            ) is EmulatorResolution.MissingCore,
        )
        val correct = CoreInstallation(retro.packageName, "/cores/mgba_libretro_android.so")
        val result =
            resolver.resolve(
                "gba",
                thorPackages,
                defaultPreference = retro,
                installedCores = listOf(correct),
            ) as EmulatorResolution.Selected
        assertEquals(PreferenceSource.DEFAULT, result.source)
        assertEquals("mgba", result.core?.id)
        val unsafe = CoreInstallation(retro.packageName, "/cores/../mgba_libretro_android.so")
        assertTrue(
            resolver.resolve(
                "gba",
                thorPackages,
                defaultPreference = retro,
                installedCores = listOf(unsafe),
            ) is EmulatorResolution.MissingCore,
        )
    }

    @Test
    fun unverifiedContractsRemainExplicit() {
        assertEquals(TemplateStatus.NEEDS_VERIFICATION, registry.emulators.first { it.id == "aethersx2" }.template.status)
        assertEquals(TemplateStatus.SOURCE_VERIFIED, registry.emulators.first { it.id == "ppsspp" }.template.status)
    }
}
