package dev.lunikodevelopment.ludoform.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.lunikodevelopment.ludoform.data.AccentSubject
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.ColorSource
import dev.lunikodevelopment.ludoform.data.Screens
import dev.lunikodevelopment.ludoform.theme.Accent
import dev.lunikodevelopment.ludoform.theme.LudoPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The palette every Ludoform window uses: the theme with its accents from the colour source.
 * Art and Console follow focus; the colour eases over 400 ms so moving along a shelf doesn't flicker.
 */
@Composable
fun ludoPalette(state: AppState): LudoPalette {
    val base = paletteFor(state.theme)
    val context = LocalContext.current
    val target: Color? =
        when (state.feel.colorSource) {
            ColorSource.LUDOFORM -> null
            ColorSource.WALLPAPER -> state.feel.wallpaperColor?.let { Color(it) }
            ColorSource.ART, ColorSource.CONSOLE -> artSeed(context, Screens.accentSubject(state))
        }
    val animated by animateColorAsState(target ?: base.lav, tween(400), label = "accent")
    return if (target == null) base else Accent.apply(base, animated)
}

@Composable
private fun artSeed(
    context: Context,
    subject: AccentSubject?,
): Color? {
    val key = subject?.let { it.art ?: "console:" + platformInfo(it.platformId).artKey }
    var seed by remember { mutableStateOf(key?.let { SeedCache.get(it) }) }
    LaunchedEffect(key) {
        if (key == null) {
            seed = null
            return@LaunchedEffect
        }
        SeedCache.get(key)?.let {
            seed = it
            return@LaunchedEffect
        }
        // Wait for focus to settle before decoding: scrolling a shelf passes many covers.
        delay(150)
        val found = withContext(Dispatchers.IO) { SeedCache.load(context, key, subject.platformId) }
        seed = found
    }
    return seed
}

/** Seeds per image, so returning to a game is instant. A grey image caches as [NONE]. */
private object SeedCache {
    private val NONE = Color.Unspecified
    private val cache = LruCache<String, Color>(200)

    fun get(key: String): Color? = cache.get(key)?.takeIf { it != NONE }

    fun load(
        context: Context,
        key: String,
        platformId: String,
    ): Color? {
        cache.get(key)?.let { return it.takeIf { color -> color != NONE } }
        val bitmap =
            runCatching {
                if (key.startsWith("console:")) {
                    decodeResource(context, PlatformArt.hero(platformInfo(platformId).artKey))
                } else {
                    decodeUri(context, Uri.parse(key))
                }
            }.getOrNull()
        val seed =
            bitmap?.let {
                val small = Bitmap.createScaledBitmap(it, SIZE, SIZE, true)
                val pixels = IntArray(SIZE * SIZE)
                small.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
                Accent.seedFromPixels(pixels)
            }
        cache.put(key, seed ?: NONE)
        return seed
    }

    private fun decodeResource(
        context: Context,
        id: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(context.resources, id, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, bounds.outHeight) }
        return BitmapFactory.decodeResource(context.resources, id, options)
    }

    private fun decodeUri(
        context: Context,
        uri: Uri,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sample(bounds.outWidth, bounds.outHeight) }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun sample(
        width: Int,
        height: Int,
    ): Int {
        var sample = 1
        while (width / (sample * 2) >= 128 && height / (sample * 2) >= 128) sample *= 2
        return sample
    }

    private const val SIZE = 48
}
