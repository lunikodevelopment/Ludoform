package dev.lunikodevelopment.ludoform.menu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import dev.lunikodevelopment.ludoform.data.ColorSource
import dev.lunikodevelopment.ludoform.feel.FeelStore
import dev.lunikodevelopment.ludoform.theme.Accent
import dev.lunikodevelopment.ludoform.theme.LudoPalette
import java.io.File

/** What the in-game menu card shows. */
enum class MenuPanel { TILES, ACHIEVEMENTS, MANUAL }

/** A game manual PDF, one page at a time (D48). The last page read is remembered per game. */
class ManualReader private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) {
    val pages: Int get() = renderer.pageCount

    /** [page] drawn on white at [height] pixels tall. */
    fun render(
        page: Int,
        height: Int,
    ): Bitmap? =
        runCatching {
            renderer.openPage(page).use { open ->
                val width = (open.width.toFloat() / open.height * height).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(Color.WHITE)
                    open.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }.getOrNull()

    fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        fun open(file: File): ManualReader? =
            runCatching {
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer =
                    try {
                        PdfRenderer(descriptor)
                    } catch (error: Exception) {
                        descriptor.close()
                        throw error
                    }
                if (renderer.pageCount == 0) {
                    renderer.close()
                    descriptor.close()
                    return null
                }
                ManualReader(descriptor, renderer)
            }.getOrNull()

        fun lastPage(
            context: Context,
            gameId: String,
        ): Int = context.getSharedPreferences("manuals", Context.MODE_PRIVATE).getInt(gameId, 0)

        fun savePage(
            context: Context,
            gameId: String,
            page: Int,
        ) = context.getSharedPreferences("manuals", Context.MODE_PRIVATE).edit().putInt(gameId, page).apply()
    }
}

/**
 * The menu's palette: the theme, with the wallpaper's accent when that is the colour source (D47). Art and Console
 * follow focus in the launcher; over a game there is none, so they keep Ludoform's own accents.
 */
fun menuPalette(
    context: Context,
    base: LudoPalette,
): LudoPalette {
    if (FeelStore.load(context).colorSource != ColorSource.WALLPAPER) return base
    val color =
        runCatching {
            android.app.WallpaperManager.getInstance(context)
                .getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
        }.getOrNull() ?: return base
    return Accent.apply(base, androidx.compose.ui.graphics.Color(color))
}
