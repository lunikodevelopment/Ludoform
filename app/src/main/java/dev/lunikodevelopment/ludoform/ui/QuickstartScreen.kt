package dev.lunikodevelopment.ludoform.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.QuickstartStep
import dev.lunikodevelopment.ludoform.data.Zones
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.theme.Blob
import dev.lunikodevelopment.ludoform.theme.Chip
import dev.lunikodevelopment.ludoform.theme.CookieBacking
import dev.lunikodevelopment.ludoform.theme.ExpressiveShapes
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoIcon
import dev.lunikodevelopment.ludoform.theme.LudoIcons
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.PrimaryPill
import dev.lunikodevelopment.ludoform.theme.SectionLabel

/** Quickstart's Stage: a greeting and the steps, numbered. */
@Composable
fun QuickstartStage(state: AppState) {
    val colors = Ludo.colors
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Blob(colors.decoOrange, Modifier.align(Alignment.TopEnd).offset(60.dp, (-40).dp).size(420.dp, 380.dp))
        Blob(colors.decoPurple, Modifier.align(Alignment.BottomStart).offset((-80).dp, 90.dp).size(320.dp, 280.dp))
        CookieBacking(Modifier.matchParentSize())
        Column(Modifier.offset(96.dp, 120.dp).width(1100.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            SectionLabel("QUICKSTART", style = LudoType.sectionLarge)
            Text("Hello, player", style = LudoType.display.copy(fontSize = 96.sp, lineHeight = 96.sp), color = colors.on)
            Text("Two things to set up, then your library is ready.", style = LudoType.sub, color = colors.on2)
            Spacer(Modifier.height(8.dp))
            StageStep(1, "Connect RomM", if (state.romm.configured) state.romm.title else "optional")
            StageStep(2, "In-game menu", if (state.menu.enabled) "on" else "now")
            StageStep(3, "ROM folders and emulators", "later in Settings")
        }
    }
}

@Composable
private fun StageStep(
    number: Int,
    title: String,
    note: String,
) {
    val colors = Ludo.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(60.dp).background(colors.org, ExpressiveShapes.clover), Alignment.Center) {
            Text("$number", style = LudoType.headline.copy(fontSize = 28.sp), color = colors.orgD)
        }
        Text(title, style = LudoType.headline.copy(fontSize = 36.sp), color = colors.on)
        Text("· $note", style = LudoType.sub, color = colors.on2)
    }
}

/** Quickstart's Deck card: each step with its button, then Finish. */
@Composable
fun QuickstartDeck(
    state: AppState,
    events: UiEvents,
) {
    val colors = Ludo.colors
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 28.dp, top = 26.dp, end = 28.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Quickstart", style = LudoType.headline.copy(fontSize = 32.sp), color = colors.on)
            Text("Two things to set up. You can change both later in Settings.", style = LudoType.label, color = colors.on2)
            QuickstartStep.entries.forEachIndexed { index, step ->
                val (title, status, button) =
                    when (step) {
                        QuickstartStep.ROMM ->
                            Triple(
                                "Connect RomM",
                                if (state.romm.configured) state.romm.title else "Optional · games from your server",
                                if (state.romm.configured) "Change" else "Set up",
                            )
                        QuickstartStep.FOLDERS -> {
                            val folders = state.library?.folders?.size ?: 0
                            Triple(
                                "ROM folders",
                                if (folders == 0) "None yet" else "$folders folder${if (folders == 1) "" else "s"}",
                                "Add folder",
                            )
                        }
                        QuickstartStep.ACCESSIBILITY ->
                            Triple(
                                "In-game menu",
                                if (state.menu.enabled) "On · opens on the top screen over any game" else "Off",
                                "Open Accessibility",
                            )
                        QuickstartStep.MENU_BUTTON -> Triple("Menu button", "Hold ${state.menu.buttons}", "Choose button")
                    }
                Row(
                    Modifier.fillMaxWidth().background(colors.sf2, RoundedCornerShape(24.dp)).padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = LudoType.label.copy(fontSize = 18.sp), color = colors.on)
                        Text(status, style = LudoType.caption, color = colors.on2, maxLines = 1)
                    }
                    Chip(button, focused = state.isFocused(Zones.QUICKSTART_STEPS, index), height = 42.dp) {
                        events.tap(FocusSpot(Zones.QUICKSTART_STEPS, index), true)
                    }
                }
            }
            if (!state.menu.enabled) {
                Text(
                    "Android asks you to turn on Ludoform in-game menu under Accessibility. It only listens for the menu " +
                        "buttons you choose: by default, holding both round hinge buttons.",
                    style = LudoType.caption,
                    color = colors.on2,
                )
            }
            Spacer(Modifier.weight(1f))
            PrimaryPill(
                "Finish",
                Modifier.fillMaxWidth(),
                glyph = "A",
                height = 58.dp,
                focused = state.isFocused(Zones.QUICKSTART_ACTIONS, 0),
            ) { events.tap(FocusSpot(Zones.QUICKSTART_ACTIONS, 0), true) }
        }
    }
}

/** The menu button Stage: why the hinge buttons are the default. */
@Composable
fun MenuButtonStage() {
    val colors = Ludo.colors
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Blob(colors.decoPurple, Modifier.align(Alignment.TopEnd).offset(60.dp, (-30).dp).size(380.dp, 340.dp))
        CookieBacking(Modifier.matchParentSize())
        Column(Modifier.offset(96.dp, 140.dp).width(1000.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            SectionLabel("QUICKSTART · MENU BUTTON", style = LudoType.sectionLarge)
            Text("Hold both to open", style = LudoType.display.copy(fontSize = 84.sp, lineHeight = 88.sp), color = colors.on)
            Text(
                "The two small round buttons above the lower screen are free in almost every game, so they are the default. " +
                    "Hold both for about a second.",
                style = LudoType.sub,
                color = colors.on2,
            )
            Text("Single presses still reach the game. Only the held pair opens the menu.", style = LudoType.label, color = colors.on2)
        }
    }
}

/** The menu button Deck card: the pair detected or just pressed, Use these buttons, Cancel. */
@Composable
fun MenuButtonDeck(
    state: AppState,
    events: UiEvents,
) {
    val colors = Ludo.colors
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 28.dp, top = 26.dp, end = 28.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Choose the menu button", style = LudoType.headline.copy(fontSize = 32.sp), color = colors.on)
            Text(
                "Press two buttons your games don't use, together. They will open the in-game menu.",
                style = LudoType.label,
                color = colors.on2,
            )
            Column(
                Modifier.fillMaxWidth().weight(1f).background(colors.sf2, RoundedCornerShape(36.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(96.dp).background(colors.lav, ExpressiveShapes.cookie9), Alignment.Center) {
                    LudoIcon(LudoIcons.gamepad, size = 40.dp, tint = colors.lavD)
                }
                Text("HOLD", style = LudoType.label, color = colors.on2)
                val candidate = state.menu.candidate
                Text(
                    if (candidate != null) "Pressed: $candidate" else "Now: ${state.menu.buttons}",
                    style = LudoType.headline.copy(fontSize = 26.sp),
                    color = colors.on,
                )
                Text(
                    if (candidate != null) "Use these buttons, or press another pair." else "Press another pair together to change it.",
                    style = LudoType.label,
                    color = colors.on2,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryPill(
                    if (state.menu.candidate != null) "Use these buttons" else "Keep these buttons",
                    Modifier.weight(1f),
                    glyph = "A",
                    height = 58.dp,
                    focused = state.isFocused(Zones.MENU_BUTTON_ACTIONS, 0),
                ) { events.tap(FocusSpot(Zones.MENU_BUTTON_ACTIONS, 0), true) }
                PrimaryPill(
                    "Cancel",
                    glyph = "B",
                    container = colors.sf3,
                    content = colors.on,
                    height = 58.dp,
                    padding = PaddingValues(horizontal = 24.dp),
                    focused = state.isFocused(Zones.MENU_BUTTON_ACTIONS, 1),
                ) { events.tap(FocusSpot(Zones.MENU_BUTTON_ACTIONS, 1), true) }
            }
        }
    }
}
