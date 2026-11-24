package dev.lunikodevelopment.ludoform.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.Destination
import dev.lunikodevelopment.ludoform.data.EmulatorChoice
import dev.lunikodevelopment.ludoform.data.PreferenceScope
import dev.lunikodevelopment.ludoform.data.Screens
import dev.lunikodevelopment.ludoform.data.Zones
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.theme.Blob
import dev.lunikodevelopment.ludoform.theme.Brand
import dev.lunikodevelopment.ludoform.theme.CookieBacking
import dev.lunikodevelopment.ludoform.theme.ExpressiveShapes
import dev.lunikodevelopment.ludoform.theme.GlyphBadge
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoIcon
import dev.lunikodevelopment.ludoform.theme.LudoIcons
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.PrimaryPill
import dev.lunikodevelopment.ludoform.theme.cookieFocus
import dev.lunikodevelopment.ludoform.theme.focusState
import dev.lunikodevelopment.ludoform.theme.tap

// Board 15 (Emulator and core) and board 14 (Choose disc). Colours not in the palette are copied from the boards.
private val rowFill = Color(0xFF2A2631)
private val rowCaption = Color(0xFFC9C1D3)
private val sheetHandle = Color(0xFF5A5363)
private val cancelFill = Color(0xFF35303D)
private val detailCard = Color(0xFF2E2836)
private val iconTile = Color(0xFF3B2D5A)
private val focusRing = Color(0xFFCDB6FF).copy(alpha = 0.24f)
private val discIcon = Color(0xFF8C7A9E)
private val discIconFocused = Color(0xFF5B3F9E)
private val discHole = Color(0xFFEEE7F4)

/** Rows past what the Deck shows scroll; each row with its gap is this tall in board units. */
private const val EMULATOR_ROW_STEP = 74
private const val DISC_ROW_STEP = 90

@Composable
fun EmulatorStage(
    state: AppState,
    library: LibrarySnapshot,
    sheet: Destination.Emulator,
) {
    val colors = Ludo.colors
    val game = library.game(sheet.gameId)
    val choice = Screens.selectedChoice(state, sheet)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Blob(colors.decoPurple, Modifier.align(Alignment.TopEnd).offset(70.dp, 40.dp).size(260.dp, 240.dp))
        Blob(colors.decoOrange, Modifier.align(Alignment.BottomEnd).offset((-150).dp, 80.dp).size(220.dp, 200.dp))
        CookieBacking(Modifier.matchParentSize())
        SheetNav()
        val info = platformInfo(game?.platformId ?: "", game?.let { library.platform(it.platformId)?.name } ?: "")
        Box(
            Modifier.offset(56.dp, 110.dp).size(210.dp).background(iconTile, ExpressiveShapes.sunny),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(PlatformArt.icon(info.artKey)), null, Modifier.size(130.dp), contentScale = ContentScale.Fit)
        }
        Column(Modifier.offset(310.dp, 110.dp).width(560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Eyebrow("EMULATOR")
            Text(
                choice?.name ?: "No emulator",
                style = LudoType.headline.copy(fontSize = 54.sp, lineHeight = 58.sp, fontWeight = FontWeight(800)),
                color = colors.on,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    choice == null -> "Nothing installed can run ${info.shortName} games"
                    choice.installed -> "Installed · ${choice.packageName}"
                    else -> "Not installed · ${choice.packageName}"
                },
                style = LudoType.sub,
                color = colors.on2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Column(
                Modifier.padding(
                    top = 6.dp,
                ).fillMaxWidth().background(detailCard, ExpressiveShapes.wavy).padding(horizontal = 30.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DetailLine("Launch method", choice?.method ?: "—")
                DetailLine("Game", game?.title ?: "—")
                DetailLine(
                    "Saved for",
                    if (sheet.scope == PreferenceScope.GAME) "This game only" else "All ${info.shortName} games",
                    valueColor = colors.ok,
                )
            }
        }
    }
}

@Composable
fun EmulatorDeck(
    state: AppState,
    library: LibrarySnapshot,
    sheet: Destination.Emulator,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val game = library.game(sheet.gameId)
    val platform = game?.let { platformInfo(it.platformId, library.platform(it.platformId)?.name ?: it.platformId) }
    val selected = state.focus.indexIn(Zones.EMULATOR_LIST, sheet.current)
    SheetFrame(decoTop = false) {
        Text(
            "Run ${game?.title ?: "this game"} with",
            style = LudoType.deckTitle.copy(fontSize = 24.sp, fontWeight = FontWeight(800)),
            color = colors.on,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        (sheet.message ?: state.notice)?.let { SheetNotice(it) }
        Row(Modifier.width(300.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("This game", "All ${platform?.shortName ?: "games"}").forEachIndexed { index, label ->
                val focused = state.isFocused(Zones.EMULATOR_SCOPE, index)
                val on = sheet.scope == PreferenceScope.entries[index]
                Box(
                    Modifier.weight(1f)
                        .cookieFocus(focused, ExpressiveShapes.wavy, colors.lav.copy(alpha = 0.55f), outset = 5.dp, scale = 1.06f)
                        .focusState(focused)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) colors.lav else colors.sf3)
                        .tap(label) { events.tap(FocusSpot(Zones.EMULATOR_SCOPE, index), true) },
                    contentAlignment = Alignment.Center,
                ) { Text(label, style = LudoType.chip, color = if (on) colors.lavD else colors.on2, maxLines = 1) }
            }
        }
        val scroll = rememberScrollState()
        val step = with(LocalDensity.current) { EMULATOR_ROW_STEP.dp.roundToPx() }
        LaunchedEffect(selected) { scroll.animateScrollTo((selected - 2).coerceAtLeast(0) * step) }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (sheet.choices.isEmpty()) {
                Text("No known emulator runs these games yet.", style = LudoType.chip, color = colors.on2)
            }
            sheet.choices.forEachIndexed { index, choice ->
                EmulatorRow(choice, index, focused = state.isFocused(Zones.EMULATOR_LIST, index), selected = index == selected) {
                    events.tap(FocusSpot(Zones.EMULATOR_LIST, index), false)
                }
            }
        }
        val choice = sheet.choices.getOrNull(selected)
        SheetActions(
            state,
            Zones.EMULATOR_ACTIONS,
            secondary = "Cancel",
            primary =
                when {
                    choice == null -> null
                    choice.installed -> "Use ${choice.name}"
                    else -> "Get ${choice.name}"
                },
            events = events,
        )
    }
}

@Composable
private fun EmulatorRow(
    choice: EmulatorChoice,
    index: Int,
    focused: Boolean,
    selected: Boolean,
    onTap: () -> Unit,
) {
    val colors = Ludo.colors
    val (iconFill, iconTint, icon) = emulatorIcon(choice, index)
    Row(
        Modifier.fillMaxWidth()
            .focusState(focused)
            .then(if (focused) Modifier.scale(1.02f).background(focusRing, CircleShape).padding(4.dp) else Modifier.padding(4.dp))
            .alpha(if (choice.installed || focused) 1f else 0.6f)
            .height(64.dp)
            .clip(CircleShape)
            .background(if (focused) colors.lav else rowFill)
            .tap(choice.name, onTap)
            .padding(start = 10.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(46.dp).background(iconFill, ExpressiveShapes.cookie9), contentAlignment = Alignment.Center) {
            LudoIcon(icon, size = 22.dp, tint = iconTint)
        }
        Column(Modifier.weight(1f)) {
            Text(
                choice.name,
                style = LudoType.label.copy(fontSize = 18.sp, fontWeight = FontWeight(700)),
                color = if (focused) colors.lavD else colors.on,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                choice.detail,
                style = LudoType.caption.copy(fontSize = 13.sp),
                color = if (focused) Color(0xFF3B2A6E) else rowCaption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (choice.installed) {
            Radio(selected, focused)
        } else {
            Box(
                Modifier.height(32.dp).background(if (focused) colors.lavD else colors.sf2, CircleShape).padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Get", style = LudoType.chip, color = if (focused) colors.lav else colors.on) }
        }
    }
}

private fun emulatorIcon(
    choice: EmulatorChoice,
    index: Int,
): Triple<Color, Color, ImageVector> =
    when {
        !choice.installed -> Triple(Color(0xFF5A5266), Color(0xFFEEE7F4), LudoIcons.download)
        choice.packageName == "com.retroarch" -> Triple(Color(0xFFF5A576), Color(0xFF4A1F06), LudoIcons.layers)
        index % 2 == 0 -> Triple(Color(0xFFCDB6FF), Color(0xFF2A1260), LudoIcons.gamepad)
        else -> Triple(Color(0xFFB8E3C0), Color(0xFF173B20), LudoIcons.gamepad)
    }

/** The board's radio: an outlined ring, or a lavender dot in a thick dark ring when selected. */
@Composable
private fun Radio(
    on: Boolean,
    onLavender: Boolean,
) {
    val colors = Ludo.colors
    if (on) {
        Box(
            Modifier.size(24.dp).background(if (onLavender) Color(0xFF2A1260) else colors.lavD, CircleShape).padding(7.dp)
                .background(colors.lav, CircleShape),
        )
    } else {
        Box(Modifier.size(24.dp).background(Color(0xFF938F99), CircleShape).padding(2.5.dp).background(rowFill, CircleShape))
    }
}

@Composable
fun DiscStage(
    library: LibrarySnapshot,
    sheet: Destination.Disc,
    state: AppState,
) {
    val colors = Ludo.colors
    val game = library.game(sheet.gameId)
    val index = Screens.selectedDisc(state, sheet)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        if (game != null) {
            Image(
                gameArt(game),
                null,
                Modifier.align(Alignment.TopEnd).size(620.dp, 540.dp),
                contentScale = ContentScale.Crop,
                alignment = gameArtAlignment(game),
            )
        }
        Box(
            Modifier.fillMaxHeight().width(420.dp)
                .background(Brush.horizontalGradient(0.7f to colors.bg, 1f to colors.bg.copy(alpha = 0f))),
        )
        Blob(colors.blobBack, Modifier.offset((-60).dp, 110.dp).size(500.dp, 380.dp))
        Box(Modifier.offset(28.dp, 24.dp)) { StatusPill() }
        Column(Modifier.offset(48.dp, 124.dp).width(440.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Eyebrow("DISC ${index + 1} OF ${sheet.discs.size}")
            DisplayTitle(game?.title ?: "Choose disc", Modifier.fillMaxWidth(), LudoType.display.copy(fontSize = 66.sp, lineHeight = 62.sp))
            Text(
                listOfNotNull(game?.let { platformInfo(it.platformId).shortName }, "M3U playlist").joinToString(" · "),
                style = LudoType.sub,
                color = colors.lav,
            )
            Text(
                sheet.discs.getOrNull(index).orEmpty(),
                style = LudoType.chip.copy(fontSize = 17.sp),
                color = rowCaption,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text("Change discs later from the emulator's own menu.", style = LudoType.chip.copy(fontSize = 17.sp), color = rowCaption)
        }
    }
}

@Composable
fun DiscDeck(
    state: AppState,
    sheet: Destination.Disc,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val selected = Screens.selectedDisc(state, sheet)
    SheetFrame(decoTop = true, padding = 40) {
        Text("Choose disc", style = LudoType.deckTitle.copy(fontSize = 30.sp, fontWeight = FontWeight(800)), color = colors.on)
        state.notice?.let { SheetNotice(it) }
        val scroll = rememberScrollState()
        val step = with(LocalDensity.current) { DISC_ROW_STEP.dp.roundToPx() }
        LaunchedEffect(selected) { scroll.animateScrollTo((selected - 2).coerceAtLeast(0) * step) }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            sheet.discs.forEachIndexed { index, name ->
                DiscRow(index, name, focused = state.isFocused(Zones.DISC_LIST, index)) {
                    events.tap(FocusSpot(Zones.DISC_LIST, index), false)
                }
            }
        }
        SheetActions(state, Zones.DISC_ACTIONS, secondary = "Back", primary = "Play disc ${selected + 1}", events = events)
    }
}

@Composable
private fun DiscRow(
    index: Int,
    fileName: String,
    focused: Boolean,
    onTap: () -> Unit,
) {
    val colors = Ludo.colors
    Row(
        Modifier.fillMaxWidth()
            .focusState(focused)
            .then(if (focused) Modifier.scale(1.02f).background(focusRing, CircleShape).padding(4.dp) else Modifier.padding(4.dp))
            .height(74.dp)
            .clip(CircleShape)
            .background(if (focused) colors.lav else rowFill)
            .tap("Disc ${index + 1}", onTap)
            .padding(start = 14.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Box(
            Modifier.size(52.dp).background(if (focused) discIconFocused else discIcon, ExpressiveShapes.flower8),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(24.dp).background(if (focused) discIconFocused else discHole, CircleShape).padding(4.dp)
                    .background(if (focused) discIconFocused else rowFill, CircleShape),
            )
        }
        Text(
            "Disc ${index + 1}",
            style = LudoType.label.copy(fontSize = 21.sp, fontWeight = FontWeight(if (focused) 800 else 600)),
            color = if (focused) Color(0xFF2A1260) else colors.on,
        )
        Text(
            fileName.substringBeforeLast('.'),
            Modifier.weight(1f),
            style = LudoType.caption.copy(fontSize = 15.sp),
            color = if (focused) Color(0xFF3B2A6E) else rowCaption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The Deck sheet: drag handle, corner cookies and a column of content, as on boards 14 and 15. */
@Composable
private fun SheetFrame(
    decoTop: Boolean,
    padding: Int = 28,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = Ludo.colors
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        if (decoTop) {
            Blob(colors.decoOrange, Modifier.align(Alignment.TopEnd).offset(50.dp, (-50).dp).size(190.dp, 150.dp), ExpressiveShapes.cookie9)
            Blob(colors.lavC, Modifier.align(Alignment.BottomStart).offset((-50).dp, 60.dp).size(150.dp, 140.dp), ExpressiveShapes.cookie9)
        } else {
            Blob(colors.decoPurple, Modifier.align(Alignment.BottomEnd).offset(50.dp, 60.dp).size(160.dp, 150.dp), ExpressiveShapes.cookie9)
        }
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = padding.dp, top = 26.dp, end = padding.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.align(
                    Alignment.CenterHorizontally,
                ).offset(y = (-10).dp).size(48.dp, 5.dp).background(sheetHandle, RoundedCornerShape(3.dp)),
            )
            content()
        }
    }
}

/** B chip on the left, the orange A button filling the rest. Both are focus stops and both work by touch. */
@Composable
private fun SheetActions(
    state: AppState,
    zone: String,
    secondary: String,
    primary: String?,
    events: UiEvents,
) {
    val colors = Ludo.colors
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val backFocused = state.isFocused(zone, 0)
        Row(
            Modifier
                .cookieFocus(backFocused, ExpressiveShapes.wavyLarge, colors.lav, outset = 6.dp, scale = 1.04f)
                .focusState(backFocused)
                .height(56.dp)
                .clip(CircleShape)
                .background(if (backFocused) colors.lav else cancelFill)
                .tap(secondary) { events.tap(FocusSpot(zone, 0), true) }
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlyphBadge("B", light = true)
            Text(secondary, style = LudoType.chip.copy(fontSize = 18.sp), color = if (backFocused) colors.lavD else colors.on)
        }
        if (primary != null) {
            PrimaryPill(
                primary,
                Modifier.weight(1f),
                glyph = "A",
                focused = state.isFocused(zone, 1),
            ) { events.tap(FocusSpot(zone, 1), true) }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SheetNotice(message: String) {
    val colors = Ludo.colors
    Row(
        Modifier.fillMaxWidth().background(colors.sf3, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LudoIcon(LudoIcons.info, tint = colors.lav)
        Text(message, style = LudoType.chip, color = colors.on, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SheetNav() {
    Row(
        Modifier.fillMaxWidth().offset(y = 20.dp).height(44.dp).padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Brand()
        Spacer(Modifier.weight(1f))
        StatusLine()
    }
}

@Composable
private fun Eyebrow(text: String) {
    val colors = Ludo.colors
    Box(Modifier.height(26.dp).background(colors.org, CircleShape).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, style = LudoType.eyebrow, color = colors.orgD)
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
    valueColor: Color = Ludo.colors.on,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = LudoType.chip.copy(fontSize = 17.sp), color = Ludo.colors.on2)
        Text(
            value,
            style = LudoType.chip.copy(fontSize = 17.sp, fontWeight = FontWeight(700)),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
