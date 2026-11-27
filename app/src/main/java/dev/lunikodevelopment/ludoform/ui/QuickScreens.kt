package dev.lunikodevelopment.ludoform.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.ColorSource
import dev.lunikodevelopment.ludoform.data.Destination
import dev.lunikodevelopment.ludoform.data.FeelToggle
import dev.lunikodevelopment.ludoform.data.QuickMenuItem
import dev.lunikodevelopment.ludoform.data.ScreenSetting
import dev.lunikodevelopment.ludoform.data.Screens
import dev.lunikodevelopment.ludoform.data.ThemeChoice
import dev.lunikodevelopment.ludoform.data.Zones
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.model.GameSummary
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.theme.Chip
import dev.lunikodevelopment.ludoform.theme.CookieBacking
import dev.lunikodevelopment.ludoform.theme.ExpressiveShapes
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoIcon
import dev.lunikodevelopment.ludoform.theme.LudoIcons
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.SectionLabel
import dev.lunikodevelopment.ludoform.theme.cookieFocus
import dev.lunikodevelopment.ludoform.theme.tap

/** The screen an overlay (Quick Menu, Quick Settings) was opened over, drawn without focus. */
fun AppState.under(): AppState = copy(navigation = navigation.dropLast(1), focus = focus.copy(current = null))

/** The Quick Menu over the Stage's art. */
@Composable
fun QuickMenuOverlay(
    state: AppState,
    events: UiEvents,
) {
    val colors = Ludo.colors
    Box(Modifier.fillMaxSize().background(Color(0x99000000))) {
        Column(
            Modifier.align(Alignment.CenterStart).offset(x = 96.dp).width(520.dp)
                .background(colors.bg.copy(alpha = 0.94f), RoundedCornerShape(48.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(colors.org, ExpressiveShapes.clover), Alignment.Center) {
                    Text("L3", style = LudoType.label, color = colors.orgD)
                }
                Text("Quick Menu", style = LudoType.headline.copy(fontSize = 34.sp), color = colors.on)
            }
            QuickMenuItem.entries.forEachIndexed { index, item ->
                val focused = state.isFocused(Zones.QUICK_MENU, index)
                Row(
                    Modifier.fillMaxWidth().cookieFocus(focused, ExpressiveShapes.wavy, colors.lav, outset = 4.dp, scale = 1.03f)
                        .height(72.dp)
                        .background(if (focused) colors.lav else colors.sf2, RoundedCornerShape(36.dp))
                        .tap(item.label) { events.tap(FocusSpot(Zones.QUICK_MENU, index), true) }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val icon =
                        when (item) {
                            QuickMenuItem.SEARCH -> LudoIcons.more
                            QuickMenuItem.RANDOM -> LudoIcons.sparkle
                            QuickMenuItem.MOST_PLAYED -> LudoIcons.star
                            QuickMenuItem.UNPLAYED -> LudoIcons.gamepad
                            QuickMenuItem.RECENT -> LudoIcons.clock
                            QuickMenuItem.FAVORITES -> LudoIcons.heart
                        }
                    LudoIcon(icon, size = 24.dp, tint = if (focused) colors.lavD else colors.on)
                    Text(item.label, style = LudoType.label.copy(fontSize = 22.sp), color = if (focused) colors.lavD else colors.on)
                }
            }
        }
    }
}

/** The Deck under the Quick Menu: dimmed, with what L3 and R3 do. */
@Composable
fun QuickMenuDeckHint() {
    val colors = Ludo.colors
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)), Alignment.Center) {
        Text("L3 Quick Menu · R3 Quick Settings · B closes", style = LudoType.label, color = colors.on)
    }
}

/** Quick Settings as a full-height Deck sheet; the Stage behind shows the change live. B closes. */
@Composable
fun QuickSettingsDeck(
    state: AppState,
    events: UiEvents,
) {
    val colors = Ludo.colors
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 28.dp, top = 22.dp, end = 28.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(colors.lav, ExpressiveShapes.clover), Alignment.Center) {
                    Text("R3", style = LudoType.label, color = colors.lavD)
                }
                Text("Quick Settings", style = LudoType.headline.copy(fontSize = 30.sp), color = colors.on)
            }
            SectionLabel("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChoice.entries.forEachIndexed { index, theme ->
                    Chip(
                        themeName(theme).substringBefore(" ·"),
                        Modifier.weight(1f),
                        selected = state.theme == theme,
                        focused = state.isFocused(Zones.QS_THEME, index),
                        height = 46.dp,
                    ) { events.tap(FocusSpot(Zones.QS_THEME, index), true) }
                }
            }
            SectionLabel("Colour source")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorSource.entries.forEachIndexed { index, source ->
                    Chip(
                        colorSourceName(source),
                        Modifier.weight(1f),
                        selected = state.feel.colorSource == source,
                        focused = state.isFocused(Zones.QS_COLOR, index),
                        height = 46.dp,
                    ) { events.tap(FocusSpot(Zones.QS_COLOR, index), true) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeelToggle.entries.forEachIndexed { index, toggle ->
                    val (label, on) =
                        when (toggle) {
                            FeelToggle.HAPTICS -> "Haptics" to state.feel.haptics
                            FeelToggle.SOUNDS -> "UI sounds" to state.feel.sounds
                            FeelToggle.MUSIC -> "Music" to state.feel.music
                        }
                    Chip(
                        label + if (on) " · on" else "",
                        Modifier.weight(1f),
                        icon = if (on) LudoIcons.check else null,
                        selected = on,
                        focused = state.isFocused(Zones.QS_FEEL, index),
                        height = 46.dp,
                    ) { events.tap(FocusSpot(Zones.QS_FEEL, index), true) }
                }
            }
            SectionLabel("Game screen")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScreenSetting.entries.forEachIndexed { index, setting ->
                    Chip(
                        when (setting) {
                            ScreenSetting.TOP -> "Top"
                            ScreenSetting.BOTTOM -> "Bottom"
                            ScreenSetting.ASK -> "Ask each time"
                        },
                        Modifier.weight(1f),
                        icon = LudoIcons.screens,
                        selected = state.gameScreen == setting,
                        focused = state.isFocused(Zones.QS_SCREEN, index),
                        height = 46.dp,
                    ) { events.tap(FocusSpot(Zones.QS_SCREEN, index), true) }
                }
            }
            Chip(
                when {
                    state.couchActive -> "Couch mode · on, playing on the TV"
                    state.couchEnabled -> "Couch mode · on, waiting for a TV"
                    else -> "Couch mode · off"
                },
                Modifier.fillMaxWidth(),
                icon = LudoIcons.screens,
                selected = state.couchEnabled,
                focused = state.isFocused(Zones.QS_COUCH, 0),
                height = 46.dp,
            ) { events.tap(FocusSpot(Zones.QS_COUCH, 0), true) }
            Text(
                if (state.feel.music && state.feel.musicName != null) {
                    "Music: ${state.feel.musicName}. Changes apply as you choose them. B closes."
                } else {
                    "Changes apply as you choose them. B closes."
                },
                style = LudoType.caption,
                color = colors.on2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun colorSourceName(source: ColorSource): String =
    when (source) {
        ColorSource.LUDOFORM -> "Ludoform"
        ColorSource.ART -> "Art"
        ColorSource.CONSOLE -> "Console"
        ColorSource.WALLPAPER -> "Wallpaper"
    }

/** Search's Stage: the focused (or first) result, large. */
@Composable
fun SearchStage(
    state: AppState,
    library: LibrarySnapshot,
    destination: Destination.Search,
) {
    val colors = Ludo.colors
    val results = Screens.search(library, destination)
    val focused = results.getOrNull(state.focus.indexIn(Zones.SEARCH_RESULTS)) ?: results.firstOrNull()
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        if (focused != null) {
            Image(
                gameArt(focused),
                null,
                Modifier.align(Alignment.TopEnd).size(900.dp, 1080.dp).clip(RoundedCornerShape(bottomStart = 200.dp)),
                contentScale = ContentScale.Crop,
                alignment = gameArtAlignment(focused),
            )
        }
        CookieBacking(Modifier.matchParentSize())
        Column(Modifier.offset(80.dp, 140.dp).width(900.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val count = results.size
            Text(
                if (destination.query.isBlank()) {
                    "Search your library"
                } else {
                    "“${destination.query}” · $count result${if (count == 1) "" else "s"}"
                },
                style = LudoType.sub,
                color = colors.on2,
            )
            if (focused != null) {
                SectionLabel(platformInfo(focused.platformId).shortName.uppercase(), style = LudoType.sectionLarge)
                Text(
                    focused.title,
                    style = LudoType.display.copy(fontSize = 88.sp, lineHeight = 88.sp),
                    color = colors.on,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(resultStatus(focused), style = LudoType.sub, color = colors.on2)
            } else if (destination.query.isNotBlank()) {
                Text("Nothing matches.", style = LudoType.headline.copy(fontSize = 56.sp), color = colors.on)
            }
        }
    }
}

private fun resultStatus(game: GameSummary): String {
    val minutes = game.playTimeMillis / 60_000
    val played = if (minutes > 0) " · ${minutes / 60}h ${minutes % 60}m played" else ""
    return (if (game.remoteOnly) "On RomM" else "Installed") + played
}

/** Search's Deck: the system filter, the keys, then the results. */
@Composable
fun SearchDeck(
    state: AppState,
    library: LibrarySnapshot,
    destination: Destination.Search,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val results = Screens.search(library, destination)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 24.dp, top = 20.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    destination.query.ifEmpty { "Search games…" },
                    Modifier.weight(1f).background(colors.sf2, RoundedCornerShape(28.dp)).padding(horizontal = 22.dp, vertical = 12.dp),
                    style = LudoType.label.copy(fontSize = 22.sp),
                    color = if (destination.query.isEmpty()) colors.on2 else colors.on,
                    maxLines = 1,
                )
                Chip(
                    destination.platformId?.let { platformInfo(it).shortName } ?: "All systems",
                    icon = LudoIcons.console,
                    focused = state.isFocused(Zones.SEARCH_FILTER, 0),
                    height = 48.dp,
                ) { events.tap(FocusSpot(Zones.SEARCH_FILTER, 0), true) }
            }
            Screens.searchKeys.chunked(Screens.SEARCH_KEY_COLUMNS).forEachIndexed { row, keys ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    keys.forEachIndexed { column, key ->
                        val index = row * Screens.SEARCH_KEY_COLUMNS + column
                        val focused = state.isFocused(Zones.SEARCH_KEYS, index)
                        Box(
                            Modifier.weight(1f).height(50.dp)
                                .cookieFocus(focused, ExpressiveShapes.wavy, colors.lav, outset = 3.dp, scale = 1.06f)
                                .background(if (focused) colors.lav else colors.sf2, RoundedCornerShape(16.dp))
                                .tap(key.ifBlank { "Space" }) { events.tap(FocusSpot(Zones.SEARCH_KEYS, index), true) },
                            Alignment.Center,
                        ) {
                            Text(
                                key.ifBlank { "Space" },
                                style = LudoType.label.copy(fontSize = if (key.isBlank()) 15.sp else 20.sp),
                                color = if (focused) colors.lavD else colors.on,
                            )
                        }
                    }
                }
            }
            Text("Touch the keys, or type with the D-pad. Y clears.", style = LudoType.caption, color = colors.on2)
            val scroll = rememberScrollState()
            val focusedRow = state.focusedIndex(Zones.SEARCH_RESULTS)
            LaunchedEffect(focusedRow) { if (focusedRow != null) scroll.animateScrollTo((focusedRow - 1).coerceAtLeast(0) * 66) }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                results.forEachIndexed { index, game ->
                    val focused = state.isFocused(Zones.SEARCH_RESULTS, index)
                    Row(
                        Modifier.fillMaxWidth().height(60.dp)
                            .background(if (focused) colors.lav else colors.sf2, RoundedCornerShape(30.dp))
                            .tap(game.title) { events.tap(FocusSpot(Zones.SEARCH_RESULTS, index), true) }
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            highlighted(game.title, destination.query),
                            Modifier.weight(1f),
                            style = LudoType.label.copy(fontSize = 18.sp),
                            color = if (focused) colors.lavD else colors.on,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            platformInfo(game.platformId).shortName + if (game.remoteOnly) " · on RomM" else " · installed",
                            style = LudoType.caption,
                            color = if (focused) colors.lavD else colors.on2,
                        )
                    }
                }
            }
        }
    }
}

/** The title with the typed part in bold. */
private fun highlighted(
    title: String,
    query: String,
) = buildAnnotatedString {
    val plain = Screens.plain(title)
    val at = if (query.isBlank()) -1 else plain.indexOf(Screens.plain(query).trim())
    if (at < 0 || plain.length != title.length) {
        append(title)
    } else {
        val end = at + Screens.plain(query).trim().length
        append(title.substring(0, at))
        withStyle(SpanStyle(fontWeight = FontWeight.Black)) { append(title.substring(at, end)) }
        append(title.substring(end))
    }
}
