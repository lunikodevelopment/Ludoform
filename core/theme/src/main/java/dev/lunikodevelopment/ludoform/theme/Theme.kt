package dev.lunikodevelopment.ludoform.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colour roles of the boards (`.lf` custom properties in ludoform.css). */
@Immutable
data class LudoPalette(
    val bg: Color,
    val sf: Color,
    val sf2: Color,
    val sf3: Color,
    val on: Color,
    val on2: Color,
    val lav: Color,
    val lavD: Color,
    val lavC: Color,
    val org: Color,
    val orgD: Color,
    val pur: Color,
    val ok: Color,
    val warn: Color,
    val err: Color,
    /** Tonal blob behind the Home title. */
    val blobBack: Color,
    /** Corner blob decorations on the Library and Details boards. */
    val decoPurple: Color,
    val decoOrange: Color,
    val scrim: Color,
    val dark: Boolean,
) {
    companion object {
        val Dark =
            LudoPalette(
                bg = Color(0xFF16131B), sf = Color(0xFF1F1B24), sf2 = Color(0xFF2A2631), sf3 = Color(0xFF35303D),
                on = Color(0xFFEEE7F4), on2 = Color(0xFFC9C1D3), lav = Color(0xFFCDB6FF), lavD = Color(0xFF2A1260),
                lavC = Color(0xFF4A3880), org = Color(0xFFF5A576), orgD = Color(0xFF4A1F06), pur = Color(0xFF8A6AD8),
                ok = Color(0xFFA6DDB0), warn = Color(0xFFF4C46B), err = Color(0xFFF2B8B5), blobBack = Color(0xFF3B2D5A),
                decoPurple = Color(0xFF7E5FCB), decoOrange = Color(0xFFF5A576), scrim = Color(0xFF16131B), dark = true,
            )

        // `.lf.light` from the boards; tertiary roles stay as on the dark boards.
        val Light =
            Dark.copy(
                bg = Color(0xFFFBF6FF), sf = Color(0xFFF2ECF8), sf2 = Color(0xFFE9E1F2), sf3 = Color(0xFFDED4EA),
                on = Color(0xFF1D1A22), on2 = Color(0xFF4B4553), lav = Color(0xFF6B4FC0), lavD = Color(0xFFFFFFFF),
                lavC = Color(0xFFE9DDFF), blobBack = Color(0xFFE6D9FF), scrim = Color(0xFFFBF6FF), dark = false,
            )
    }
}

val LocalLudoPalette = staticCompositionLocalOf { LudoPalette.Dark }

object Ludo {
    val colors: LudoPalette @Composable get() = LocalLudoPalette.current
}

private fun scheme(p: LudoPalette) =
    if (p.dark) {
        darkColorScheme(
            primary = p.lav, onPrimary = p.lavD, primaryContainer = p.lavC, onPrimaryContainer = p.on,
            tertiary = p.org, onTertiary = p.orgD, background = p.bg, onBackground = p.on, surface = p.sf,
            onSurface = p.on, surfaceVariant = p.sf3, onSurfaceVariant = p.on2, surfaceContainer = p.sf2, error = p.err,
        )
    } else {
        lightColorScheme(
            primary = p.lav, onPrimary = p.lavD, primaryContainer = p.lavC, onPrimaryContainer = p.on,
            tertiary = p.org, onTertiary = p.orgD, background = p.bg, onBackground = p.on, surface = p.sf,
            onSurface = p.on, surfaceVariant = p.sf3, onSurfaceVariant = p.on2, surfaceContainer = p.sf2,
        )
    }

/**
 * Ludoform renders the dark board palette by default (D18); the light boards' palette is available for the
 * light-theme milestone (#27) and is not tied to the system setting.
 */
@Composable
fun LudoformTheme(
    dark: Boolean = true,
    content: @Composable () -> Unit,
) {
    val palette = if (dark) LudoPalette.Dark else LudoPalette.Light
    CompositionLocalProvider(LocalLudoPalette provides palette) {
        MaterialTheme(colorScheme = scheme(palette), typography = Typography(), content = content)
    }
}
