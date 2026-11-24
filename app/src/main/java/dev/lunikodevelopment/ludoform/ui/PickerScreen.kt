package dev.lunikodevelopment.ludoform.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.Screens
import dev.lunikodevelopment.ludoform.data.Zones
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.theme.Brand
import dev.lunikodevelopment.ludoform.theme.CookieBacking
import dev.lunikodevelopment.ludoform.theme.CoverTile
import dev.lunikodevelopment.ludoform.theme.ExpressiveShapes
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.PrimaryPill
import dev.lunikodevelopment.ludoform.theme.ShoulderBadge
import dev.lunikodevelopment.ludoform.theme.cookieFocus
import dev.lunikodevelopment.ludoform.theme.focusState
import dev.lunikodevelopment.ludoform.theme.tap

private val warmScrim = Color(0xFF1E0C0A)

@Composable
fun PickerStage(
    state: AppState,
    library: LibrarySnapshot,
) {
    val picker = Screens.picker(library, state.focus.indexIn(Zones.PICKER_CONSOLES))
    val selected = picker.selected
    Box(Modifier.fillMaxSize().background(Ludo.colors.bg)) {
        Crossfade(selected, Modifier.fillMaxSize(), animationSpec = tween(300), label = "console hero") { platform ->
            if (platform != null) {
                val info = platformInfo(platform.id, platform.name)
                Image(painterResource(PlatformArt.hero(info.artKey)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to warmScrim.copy(alpha = 0.9f),
                    0.4f to warmScrim.copy(alpha = 0.55f),
                    0.62f to warmScrim.copy(alpha = 0f),
                ),
            ),
        )
        CookieBacking(Modifier.matchParentSize())
        Brand(
            Modifier.offset(40.dp, 30.dp),
            markColor = Color(0xFFD9A6FF),
            textColor = Color(0xFFE7C6FF),
            shape = ExpressiveShapes.flower8,
            markSize = 28.dp,
        )
        StatusLine(
            Modifier.align(Alignment.TopEnd).padding(top = 30.dp, end = 36.dp),
            style = LudoType.status.copy(fontSize = 19.sp),
            showNetwork = true,
        )
        if (selected != null) {
            val info = platformInfo(selected.id, selected.name)
            val sub = LudoType.sub.copy(fontSize = 28.sp, lineHeight = 40.sp)
            Column(Modifier.offset(48.dp, 118.dp).padding(end = 400.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    selected.name,
                    style =
                        if (selected.name.length > 14) {
                            LudoType.consoleTitle.copy(
                                fontSize = 56.sp,
                                lineHeight = 62.sp,
                            )
                        } else {
                            LudoType.consoleTitle
                        },
                    color = Ludo.colors.on,
                    maxLines = 2,
                )
                listOfNotNull(
                    listOfNotNull(info.maker, info.year?.toString()).joinToString(" · ").ifEmpty { null },
                    "${selected.gameCount} game${if (selected.gameCount == 1) "" else "s"}",
                    picker.games.firstOrNull()?.let { "Newest: ${it.title}" },
                ).forEach { Text(it, style = sub, color = Color(0xFFF4ECEA), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
fun PickerDeck(
    state: AppState,
    library: LibrarySnapshot,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val selectedIndex = state.focus.indexIn(Zones.PICKER_CONSOLES)
    val picker = Screens.picker(library, selectedIndex)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().padding(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ShoulderBadge("L1")
                Text(
                    "Consoles",
                    Modifier.weight(1f),
                    style = LudoType.sectionLarge.copy(fontSize = 26.sp),
                    color = colors.on,
                    textAlign = TextAlign.Center,
                )
                ShoulderBadge("R1")
            }
            if (picker.platforms.isEmpty()) {
                Spacer(Modifier.weight(1f))
                Text(
                    "No consoles yet. Add a games folder from Home and Ludoform sorts it by console.",
                    style = LudoType.body,
                    color = colors.on2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.weight(1f))
            } else {
                Carousel(state, picker.platforms.size, selectedIndex, events) { index -> picker.platforms[index] }
                Text(
                    "Recently added",
                    Modifier.padding(top = 22.dp, bottom = 14.dp),
                    style = LudoType.sectionLarge.copy(fontSize = 22.sp),
                    color = colors.on,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    picker.games.forEachIndexed { index, game ->
                        Column(
                            Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CoverTile(
                                gameArt(game),
                                null,
                                Modifier.fillMaxWidth().height(108.dp),
                                focused = state.isFocused(Zones.PICKER_GAMES, index),
                                corner = 18.dp,
                                haloShape = ExpressiveShapes.wavy,
                                haloOutset = 9.dp,
                                alignment = gameArtAlignment(game),
                            ) { events.tap(FocusSpot(Zones.PICKER_GAMES, index), false) }
                            Text(
                                game.title,
                                style = LudoType.label.copy(fontSize = 17.sp),
                                color = colors.on,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    repeat(Screens.HOME_ROW - picker.games.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (picker.platforms.isNotEmpty()) {
                    PrimaryPill(
                        "Browse",
                        Modifier.weight(1f),
                        glyph = "A",
                        container = Color(0xFFF7A257),
                        content = Color(0xFF2B1404),
                        height = 62.dp,
                        style = LudoType.button.copy(fontSize = 24.sp),
                        focused = state.isFocused(Zones.PICKER_ACTIONS, 0),
                    ) { events.tap(FocusSpot(Zones.PICKER_ACTIONS, 0), true) }
                }
                val backIndex = if (picker.platforms.isEmpty()) 0 else 1
                PrimaryPill(
                    "Back",
                    Modifier.weight(1f),
                    glyph = "B",
                    container = Color(0xFF3B3542),
                    content = colors.on,
                    height = 62.dp,
                    style = LudoType.button.copy(fontSize = 22.sp, fontWeight = FontWeight(600)),
                    focused = state.isFocused(Zones.PICKER_ACTIONS, backIndex),
                ) { events.tap(FocusSpot(Zones.PICKER_ACTIONS, backIndex), true) }
            }
        }
    }
}

/** Five equal slots with the selected console centred on a sunny backing; fewer consoles sit centred. */
@Composable
private fun Carousel(
    state: AppState,
    count: Int,
    selected: Int,
    events: UiEvents,
    platformAt: (Int) -> dev.lunikodevelopment.ludoform.model.PlatformSummary,
) {
    val slots = if (count >= 5) (-2..2).map { Math.floorMod(selected + it, count) } else (0 until count).toList()
    val carouselFocused = state.focus.current?.zone == Zones.PICKER_CONSOLES
    Row(Modifier.fillMaxWidth().padding(top = 26.dp), horizontalArrangement = Arrangement.Center) {
        val padding = (5 - slots.size) / 2f
        if (padding > 0) Spacer(Modifier.weight(padding))
        slots.forEach { index ->
            val platform = platformAt(index)
            val info = platformInfo(platform.id, platform.name)
            val isSelected = index == selected
            Column(
                Modifier.weight(1f).tap(platform.name) { events.tap(FocusSpot(Zones.PICKER_CONSOLES, index), isSelected) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painterResource(PlatformArt.icon(info.artKey)),
                    null,
                    Modifier
                        .cookieFocus(
                            isSelected,
                            ExpressiveShapes.sunny,
                            Color(0xFFF58C46).copy(alpha = if (carouselFocused) 0.32f else 0.18f),
                            outset = 17.dp,
                        )
                        .focusState(isSelected && carouselFocused)
                        .size(84.dp),
                    contentScale = ContentScale.Fit,
                )
                Text(
                    info.shortName,
                    style = LudoType.label.copy(fontWeight = FontWeight(if (isSelected) 800 else 600)),
                    color = Ludo.colors.on,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (padding > 0) Spacer(Modifier.weight(padding))
    }
}
