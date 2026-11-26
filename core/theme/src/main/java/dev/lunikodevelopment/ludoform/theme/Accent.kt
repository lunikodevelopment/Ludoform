package dev.lunikodevelopment.ludoform.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Accent colours from a seed (board 11's colour source, D47). The seed's hue drives the lavender roles and, turned
 * 50° around the wheel, the orange ones; lightness stays where the boards put each role, so text keeps its contrast
 * whatever the art looks like. Pure, so it is unit-tested.
 */
object Accent {
    /** The palette [base] with its accents rebuilt around [seed]. Surfaces and text stay as they are. */
    fun apply(
        base: LudoPalette,
        seed: Color,
    ): LudoPalette {
        val (hue, saturation) = hsl(seed)
        val s = saturation.coerceIn(0.35f, 0.75f)
        val tertiary = (hue + 50f) % 360f
        return if (base.dark) {
            base.copy(
                lav = fromHsl(hue, s, 0.80f),
                lavD = fromHsl(hue, s * 0.9f, 0.20f),
                lavC = fromHsl(hue, s * 0.6f, 0.34f),
                pur = fromHsl(hue, s, 0.62f),
                org = fromHsl(tertiary, s, 0.72f),
                orgD = fromHsl(tertiary, s * 0.9f, 0.17f),
                decoPurple = fromHsl(hue, s * 0.8f, 0.55f),
                decoOrange = fromHsl(tertiary, s, 0.72f),
                blobBack = fromHsl(hue, s * 0.5f, if (base.bg == Color.Black) 0.14f else 0.24f),
            )
        } else {
            base.copy(
                lav = fromHsl(hue, s, 0.40f),
                lavC = fromHsl(hue, s, 0.90f),
                pur = fromHsl(hue, s, 0.55f),
                org = fromHsl(tertiary, s, 0.70f),
                orgD = fromHsl(tertiary, s * 0.9f, 0.17f),
                decoPurple = fromHsl(hue, s * 0.8f, 0.60f),
                decoOrange = fromHsl(tertiary, s, 0.72f),
                blobBack = fromHsl(hue, s, 0.88f),
            )
        }
    }

    /**
     * The colour that best stands for an image, from its ARGB [pixels]: the most common hue among the colourful
     * pixels, weighted by how colourful they are. Null when the image is (nearly) grey, black or white.
     */
    fun seedFromPixels(pixels: IntArray): Color? {
        val buckets = DoubleArray(BUCKETS)
        val red = DoubleArray(BUCKETS)
        val green = DoubleArray(BUCKETS)
        val blue = DoubleArray(BUCKETS)
        var colourful = 0
        for (pixel in pixels) {
            if ((pixel ushr 24) < 128) continue
            val color = Color(pixel)
            val (hue, saturation, lightness) = hsl(color)
            if (saturation < 0.25f || lightness < 0.15f || lightness > 0.9f) continue
            val weight = saturation * (1f - abs(lightness - 0.5f) * 2f).coerceAtLeast(0.1f)
            val bucket = (hue / (360f / BUCKETS)).toInt().coerceIn(0, BUCKETS - 1)
            buckets[bucket] += weight.toDouble()
            red[bucket] += color.red * weight.toDouble()
            green[bucket] += color.green * weight.toDouble()
            blue[bucket] += color.blue * weight.toDouble()
            colourful++
        }
        if (colourful < pixels.size / 50 || colourful == 0) return null
        val best = buckets.indices.maxBy { buckets[it] }
        val total = buckets[best]
        return Color((red[best] / total).toFloat(), (green[best] / total).toFloat(), (blue[best] / total).toFloat())
    }

    private const val BUCKETS = 24

    /** Hue (0–360), saturation and lightness (0–1). */
    fun hsl(color: Color): Triple<Float, Float, Float> {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val lightness = (max + min) / 2f
        if (max == min) return Triple(0f, 0f, lightness)
        val delta = max - min
        val saturation = if (lightness > 0.5f) delta / (2f - max - min) else delta / (max + min)
        val hue =
            when (max) {
                r -> ((g - b) / delta + (if (g < b) 6f else 0f))
                g -> (b - r) / delta + 2f
                else -> (r - g) / delta + 4f
            } * 60f
        return Triple(hue, saturation, lightness)
    }

    fun fromHsl(
        hue: Float,
        saturation: Float,
        lightness: Float,
    ): Color {
        val s = saturation.coerceIn(0f, 1f)
        val l = lightness.coerceIn(0f, 1f)
        val c = (1f - abs(2f * l - 1f)) * s
        val h = ((hue % 360f) + 360f) % 360f / 60f
        val x = c * (1f - abs(h % 2f - 1f))
        val (r, g, b) =
            when (h.toInt()) {
                0 -> Triple(c, x, 0f)
                1 -> Triple(x, c, 0f)
                2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c)
                4 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
        val m = l - c / 2f
        return Color((r + m).coerceIn(0f, 1f), (g + m).coerceIn(0f, 1f), (b + m).coerceIn(0f, 1f))
    }
}
