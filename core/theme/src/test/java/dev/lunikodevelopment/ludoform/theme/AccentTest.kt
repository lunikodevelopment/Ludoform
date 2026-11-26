package dev.lunikodevelopment.ludoform.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Colour source (D47): a seed from art, and accents rebuilt around it. */
class AccentTest {
    @Test
    fun theMostCommonColourfulHueWinsAndGreyGivesNone() {
        val red = Color(0xFFD03030).toArgb()
        val blue = Color(0xFF3050D0).toArgb()
        val grey = Color(0xFF808080).toArgb()
        val pixels =
            IntArray(100) {
                if (it < 20) {
                    red
                } else if (it < 60) {
                    blue
                } else {
                    grey
                }
            }
        val seed = Accent.seedFromPixels(pixels)!!
        val hue = Accent.hsl(seed).first
        assertTrue("hue $hue", hue in 220f..240f)
        assertNull(Accent.seedFromPixels(IntArray(100) { grey }))
    }

    @Test
    fun accentsFollowTheSeedHueButKeepTheBoardsLightness() {
        val green = Accent.apply(LudoPalette.Dark, Color(0xFF20A040))
        val (hue, _, lightness) = Accent.hsl(green.lav)
        assertTrue("hue $hue", hue in 130f..145f)
        assertEquals(0.80f, lightness, 0.02f)
        assertEquals(LudoPalette.Dark.bg, green.bg)
        assertEquals(LudoPalette.Dark.on, green.on)
        val light = Accent.apply(LudoPalette.Light, Color(0xFF20A040))
        assertEquals(0.40f, Accent.hsl(light.lav).third, 0.02f)
        assertEquals(LudoPalette.Light.lavD, light.lavD)
    }

    @Test
    fun hslRoundTrips() {
        val color = Color(0xFF6B4FC0)
        val (h, s, l) = Accent.hsl(color)
        val back = Accent.fromHsl(h, s, l)
        assertEquals(color.red, back.red, 0.01f)
        assertEquals(color.green, back.green, 0.01f)
        assertEquals(color.blue, back.blue, 0.01f)
    }
}
