package dev.lunikodevelopment.ludoform.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.Screens
import dev.lunikodevelopment.ludoform.data.StageTab
import dev.lunikodevelopment.ludoform.data.UiAction
import dev.lunikodevelopment.ludoform.data.Zones
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.theme.Brand
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoIcon
import dev.lunikodevelopment.ludoform.theme.LudoIcons
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.StageTabs
import dev.lunikodevelopment.ludoform.theme.WavyProgressIndicator

/** Callbacks from the screens. A tap on an art tile focuses it first; a tap on a control activates it. */
class UiEvents(
    val tap: (spot: FocusSpot, activate: Boolean) -> Unit,
    val perform: (UiAction) -> Unit,
)

fun AppState.isFocused(
    zone: String,
    index: Int,
): Boolean = focus.current == FocusSpot(zone, index)

fun AppState.focusedIndex(zone: String): Int? = focus.current?.takeIf { it.zone == zone }?.index

/** True while the saved RomM server is down (D35); the status line then says "RomM offline" (board 19). */
val LocalRommOffline = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Clock · battery · network, as text (Library and Details boards) or in a pill (Home board). */
@Composable
fun StatusLine(
    modifier: Modifier = Modifier,
    style: TextStyle = LudoType.status,
    color: Color = Ludo.colors.on,
    showNetwork: Boolean = true,
) {
    val status = rememberDeviceStatus()
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(status.time, style = style, color = color)
        if (status.battery != null) {
            Text("·", style = style, color = color)
            LudoIcon(LudoIcons.battery, size = 20.dp, tint = color)
            Text("${status.battery}%", style = style, color = color)
        }
        if (showNetwork) {
            Text("·", style = style, color = color)
            val rommOffline = status.online && LocalRommOffline.current
            LudoIcon(if (status.online && !rommOffline) LudoIcons.wifi else LudoIcons.wifiOff, size = 20.dp, tint = color)
            Text(
                when {
                    !status.online -> "Offline"
                    rommOffline -> "RomM offline"
                    else -> "Online"
                },
                style = style,
                color = color,
            )
        }
    }
}

@Composable
fun StatusPill(modifier: Modifier = Modifier) {
    Box(
        modifier.height(40.dp).background(Ludo.colors.sf2.copy(alpha = 0.86f), CircleShape).padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { StatusLine() }
}

private val tabLabels =
    mapOf(
        StageTab.HOME to ("Home" to LudoIcons.home),
        StageTab.CONSOLES to ("Consoles" to LudoIcons.console),
        StageTab.LIBRARY to ("Library" to LudoIcons.gamepad),
        StageTab.SETTINGS to ("Settings" to LudoIcons.settings),
    )

/** Stage navigation row of the Library, Details and Settings boards: brand, tabs, status. */
@Composable
fun StageNav(
    state: AppState,
    events: UiEvents,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(44.dp).padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Brand(Modifier.padding(end = 12.dp))
        StageTabs(
            labels = Screens.stageTabs.map { tabLabels.getValue(it) },
            active = Screens.stageTabs.indexOf(Screens.tabFor(state.destination)),
            focused = state.focusedIndex(Zones.TABS),
        ) { index -> events.tap(FocusSpot(Zones.TABS, index), true) }
        Box(Modifier.weight(1f))
        StatusLine(showNetwork = true)
    }
}

/**
 * The boards' uppercase display title. It keeps the board size when the name fits and otherwise steps down until no
 * word is split and the title fits [maxLines], so long ROM names never break mid-word.
 */
@Composable
fun DisplayTitle(
    title: String,
    modifier: Modifier = Modifier,
    base: TextStyle = LudoType.display,
    color: Color = Ludo.colors.on,
    maxLines: Int = 3,
) {
    val measurer = rememberTextMeasurer()
    val text = title.uppercase()
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val style =
            remember(text, width, base) {
                val words = text.split(' ').filter { it.isNotEmpty() }
                val steps = listOf(1f, 0.9f, 0.8f, 0.7f, 0.6f, 0.5f)
                steps.map { f -> base.copy(fontSize = base.fontSize * f, lineHeight = base.lineHeight * f) }.firstOrNull { candidate ->
                    val widest = words.maxOfOrNull { measurer.measure(it, candidate, maxLines = 1, softWrap = false).size.width } ?: 0
                    widest <= width && measurer.measure(text, candidate, constraints = Constraints(maxWidth = width)).lineCount <= maxLines
                } ?: base.copy(fontSize = base.fontSize * 0.5f, lineHeight = base.lineHeight * 0.5f)
            }
        Text(text, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

/** A wavy indicator that keeps moving while work of unknown length runs. */
@Composable
fun WavyActivity(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "activity")
    val progress by transition.animateFloat(
        0.12f,
        0.88f,
        infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "activity progress",
    )
    WavyProgressIndicator(progress, modifier)
}

/** File sizes as the Deck shows them: "512 KB", "4.0 MB", "1.2 GB". */
fun formatSize(bytes: Long): String {
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "${value.toLong().coerceAtLeast(1)} KB" else String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unit])
}
