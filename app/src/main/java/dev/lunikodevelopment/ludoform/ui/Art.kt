package dev.lunikodevelopment.ludoform.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import coil.compose.rememberAsyncImagePainter
import dev.lunikodevelopment.ludoform.R
import dev.lunikodevelopment.ludoform.model.GameSummary
import dev.lunikodevelopment.ludoform.model.PlatformCatalog
import dev.lunikodevelopment.ludoform.model.PlatformInfo
import kotlin.math.absoluteValue

/** Console icons and heroes from Ludoform's design pack, keyed by [PlatformInfo.artKey]. */
object PlatformArt {
    @DrawableRes
    fun icon(key: String): Int =
        when (key) {
            "nes" -> R.drawable.platform_icon_nes
            "snes" -> R.drawable.platform_icon_snes
            "gbc" -> R.drawable.platform_icon_gbc
            "gba" -> R.drawable.platform_icon_gba
            "ds" -> R.drawable.platform_icon_ds
            "3ds" -> R.drawable.platform_icon_3ds
            "n64" -> R.drawable.platform_icon_n64
            "gamecube" -> R.drawable.platform_icon_gamecube
            "wii" -> R.drawable.platform_icon_wii
            "playstation" -> R.drawable.platform_icon_playstation
            "ps2" -> R.drawable.platform_icon_ps2
            "psp" -> R.drawable.platform_icon_psp
            "dreamcast" -> R.drawable.platform_icon_dreamcast
            "genesis" -> R.drawable.platform_icon_genesis
            else -> R.drawable.platform_icon_collections
        }

    @DrawableRes
    fun hero(key: String): Int =
        when (key) {
            "nes" -> R.drawable.platform_hero_nes
            "snes" -> R.drawable.platform_hero_snes
            "gbc" -> R.drawable.platform_hero_gbc
            "gba" -> R.drawable.platform_hero_gba
            "ds" -> R.drawable.platform_hero_ds
            "3ds" -> R.drawable.platform_hero_3ds
            "n64" -> R.drawable.platform_hero_n64
            "gamecube" -> R.drawable.platform_hero_gamecube
            "wii" -> R.drawable.platform_hero_wii
            "playstation" -> R.drawable.platform_hero_playstation
            "ps2" -> R.drawable.platform_hero_ps2
            "psp" -> R.drawable.platform_hero_psp
            "dreamcast" -> R.drawable.platform_hero_dreamcast
            "genesis" -> R.drawable.platform_hero_genesis
            else -> R.drawable.platform_hero_collections
        }
}

fun platformInfo(
    id: String,
    name: String = id,
): PlatformInfo = PlatformCatalog.info(id, name)

/**
 * Game artwork: the game's cover when it has one, loaded from the device only. Without one, and while it loads,
 * the console's hero shows, framed differently per game so a shelf of one console does not repeat the same crop.
 */
@Composable
fun gameArt(game: GameSummary): Painter {
    val fixture = game.artKey?.let { DesignFixtures.art(it) }
    val fallback = painterResource(fixture ?: PlatformArt.hero(platformInfo(game.platformId).artKey))
    val art = game.art ?: return fallback
    return rememberAsyncImagePainter(model = art, placeholder = fallback, error = fallback)
}

fun gameArtAlignment(game: GameSummary): Alignment {
    if (game.artKey != null || game.art != null) return Alignment.Center
    val hash = game.id.hashCode().absoluteValue
    val horizontal = ((hash % 7) - 3) / 3f
    val vertical = (((hash / 7) % 5) - 2) / 2f
    return androidx.compose.ui.BiasAlignment(horizontal, vertical)
}
