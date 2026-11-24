package dev.lunikodevelopment.ludoform.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleInfoTest {
    @Test
    fun stripsTitleIdsTagsAndDumpSuffixes() {
        val info = TitleInfo.parse("0004000000030700 Mario Kart 7 (CTR-P-AMKP) (v2.4.0) (E).legit.[hID-478]")
        assertEquals("Mario Kart 7", info.title)
        assertEquals("Europe", info.region)
    }

    @Test
    fun readsMultiLanguageRegionTags() {
        val info = TitleInfo.parse("007 - Quantum of Solace (Europe) (En,Fr)")
        assertEquals("007 - Quantum of Solace", info.title)
        assertEquals("Europe", info.region)
    }

    @Test
    fun keepsPlainNamesAndNeverReturnsBlank() {
        assertEquals("Pokemon Pink v2.1", TitleInfo.parse("Pokemon Pink v2.1").title)
        assertNull(TitleInfo.parse("Pokemon Pink v2.1").region)
        assertEquals("(Proto)", TitleInfo.parse("(Proto)").title)
    }

    @Test
    fun unknownPlatformsUseCollectionsArt() {
        assertEquals("collections", PlatformCatalog.info("vectrex", "Vectrex").artKey)
        assertEquals("Vectrex", PlatformCatalog.info("vectrex", "Vectrex").shortName)
        assertEquals("gamecube", PlatformCatalog.info("gc").artKey)
    }
}
