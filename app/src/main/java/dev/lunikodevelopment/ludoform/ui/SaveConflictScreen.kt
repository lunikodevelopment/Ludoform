package dev.lunikodevelopment.ludoform.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.Destination
import dev.lunikodevelopment.ludoform.data.SaveConflictInfo
import dev.lunikodevelopment.ludoform.data.Zones
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.theme.CookieBacking
import dev.lunikodevelopment.ludoform.theme.ExpressiveShapes
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoIcon
import dev.lunikodevelopment.ludoform.theme.LudoIcons
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.PrimaryPill
import dev.lunikodevelopment.ludoform.theme.SectionLabel
import dev.lunikodevelopment.ludoform.theme.tap
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** "Today 21:04", "Yesterday 18:20" or a short date. */
private fun savedAt(millis: Long?): String {
    if (millis == null || millis <= 0) return "Unknown"
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))
    val day = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    val days = today.get(Calendar.DAY_OF_YEAR) - day.get(Calendar.DAY_OF_YEAR) + 365 * (today.get(Calendar.YEAR) - day.get(Calendar.YEAR))
    return when (days) {
        0 -> "Today $time"
        1 -> "Yesterday $time"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis)) + " " + time
    }
}

@Composable
fun SaveConflictStage(
    state: AppState,
    library: LibrarySnapshot,
    destination: Destination.SaveConflict,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val info = state.saveConflicts[destination.gameId]
    val newerOnDevice = (info?.deviceAt ?: 0) >= (info?.serverAt ?: 0)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        StageNav(state, events, Modifier.offset(y = 20.dp))
        Column(Modifier.offset(48.dp, 104.dp).width(864.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Choose a save", style = LudoType.headline.copy(fontSize = 48.sp), color = colors.on)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SaveCard("This device", savedAt(info?.deviceAt), newerOnDevice, "Use device save", LudoIcons.gamepad, Modifier.weight(1f)) {
                    events.tap(FocusSpot(Zones.SAVE_ACTIONS, 0), true)
                }
                SaveCard("RomM server", savedAt(info?.serverAt), !newerOnDevice, "Use server save", LudoIcons.cloud, Modifier.weight(1f)) {
                    events.tap(FocusSpot(Zones.SAVE_ACTIONS, 1), true)
                }
            }
            library.game(destination.gameId)?.let { Text(it.title, style = LudoType.chip, color = colors.on2) }
        }
    }
}

@Composable
private fun SaveCard(
    title: String,
    time: String,
    newest: Boolean,
    action: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val colors = Ludo.colors
    Column(
        modifier.background(colors.sf2, RoundedCornerShape(32.dp)).tap(action, onTap).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LudoIcon(icon, tint = colors.lav)
            Text(title, Modifier.weight(1f), style = LudoType.deckTitle, color = colors.on)
            if (newest) {
                Text(
                    "Newest",
                    Modifier.background(colors.org, CircleShape).padding(horizontal = 12.dp, vertical = 4.dp),
                    style = LudoType.chip,
                    color = colors.orgD,
                )
            }
        }
        Text(time, style = LudoType.sub, color = colors.on2)
        Text(action, style = LudoType.label, color = colors.lav)
    }
}

@Composable
fun SaveConflictDeck(
    state: AppState,
    library: LibrarySnapshot,
    destination: Destination.SaveConflict,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val info: SaveConflictInfo? = state.saveConflicts[destination.gameId]
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 26.dp, top = 24.dp, end = 26.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("${library.game(destination.gameId)?.title ?: "This game"} · two saves", style = LudoType.section)
            Column(
                Modifier.fillMaxWidth().background(colors.sf2, ExpressiveShapes.wavy).padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CompareRow("", "DEVICE", "ROMM", header = true)
                CompareRow("Saved", savedAt(info?.deviceAt), savedAt(info?.serverAt))
                CompareRow("Size", info?.deviceSize?.let(::formatSize) ?: "–", info?.serverSize?.let(::formatSize) ?: "–")
                CompareRow("From", "This Thor", info?.serverFrom ?: "RomM")
            }
            Text("The save you don't pick is kept as a backup for 30 days.", style = LudoType.caption, color = colors.on2)
            Spacer(Modifier.weight(1f))
            listOf(
                listOf("Use device save" to null, "Use server save" to null),
                listOf("Keep both" to "X", "Don't launch" to "B"),
            ).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEachIndexed { column, (label, glyph) ->
                        val index = row * 2 + column
                        PrimaryPill(
                            label,
                            Modifier.weight(1f),
                            glyph = glyph,
                            container = if (index < 2) colors.lav else colors.sf3,
                            content = if (index < 2) colors.lavD else colors.on,
                            height = 52.dp,
                            style = LudoType.button.copy(fontSize = 18.sp),
                            focused = state.isFocused(Zones.SAVE_ACTIONS, index),
                        ) { events.tap(FocusSpot(Zones.SAVE_ACTIONS, index), true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareRow(
    label: String,
    device: String,
    server: String,
    header: Boolean = false,
) {
    val colors = Ludo.colors
    val style = if (header) LudoType.eyebrow else LudoType.chip
    Row(Modifier.fillMaxWidth().height(if (header) 18.dp else 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(110.dp), style = style, color = colors.on2)
        Text(device, Modifier.weight(1f), style = style, color = colors.on, maxLines = 1)
        Text(server, Modifier.weight(1f), style = style, color = colors.on, maxLines = 1)
    }
}
