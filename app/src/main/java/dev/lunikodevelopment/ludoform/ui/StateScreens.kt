package dev.lunikodevelopment.ludoform.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.LibraryStatus
import dev.lunikodevelopment.ludoform.data.Screens
import dev.lunikodevelopment.ludoform.data.SettingsSection
import dev.lunikodevelopment.ludoform.data.StateAction
import dev.lunikodevelopment.ludoform.data.Zones
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.theme.Blob
import dev.lunikodevelopment.ludoform.theme.Brand
import dev.lunikodevelopment.ludoform.theme.CookieBacking
import dev.lunikodevelopment.ludoform.theme.ExpressiveShapes
import dev.lunikodevelopment.ludoform.theme.ListRow
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoIcons
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.PrimaryPill
import dev.lunikodevelopment.ludoform.theme.SectionLabel

private data class StateCopy(val title: String, val body: String, val deckTitle: String, val deckBody: String)

private fun copyFor(
    status: LibraryStatus,
    state: AppState,
): StateCopy =
    when (status) {
        LibraryStatus.NO_FOLDERS ->
            StateCopy(
                "Let's find\nyour games",
                "Choose the folder that holds your ROMs. Ludoform only reads it.",
                "Add your games",
                "Pick a folder such as roms on your SD card. Ludoform sorts it by console folders like gba, nds or psx. " +
                    "Nothing is moved or deleted.",
            )
        LibraryStatus.SCANNING ->
            StateCopy(
                "Looking through\nyour games",
                "Big folders can take a minute. You can keep using the Deck meanwhile.",
                "Scanning",
                "Reading ${state.library?.folders?.size ?: 0} folder${if (state.library?.folders?.size == 1) "" else "s"}. " +
                    "Games appear as soon as the scan finishes.",
            )
        LibraryStatus.NO_GAMES ->
            StateCopy(
                "No games\nfound yet",
                "Ludoform looks for console folders such as gba, nds or psx inside the folder you chose.",
                "Nothing to show yet",
                "Add another folder, scan again, or save the scan report to see what was skipped and why.",
            )
        LibraryStatus.NO_ACCESS ->
            StateCopy(
                "Can't reach\nyour games",
                "Android no longer lets Ludoform read your games folder. This happens when an SD card is removed or a folder moves.",
                "Folder access lost",
                "Insert the SD card, or choose the folder again to restore access. Your games and saves are untouched.",
            )
        LibraryStatus.LOADING, LibraryStatus.READY -> StateCopy("", "", "", "")
    }

private fun StateAction.label(): Pair<String, androidx.compose.ui.graphics.vector.ImageVector> =
    when (this) {
        StateAction.ADD_FOLDER -> "Add games folder" to LudoIcons.folder
        StateAction.CHOOSE_AGAIN -> "Choose folder again" to LudoIcons.lock
        StateAction.RESCAN -> "Scan again" to LudoIcons.refresh
        StateAction.SAVE_REPORT -> "Save scan report" to LudoIcons.download
        StateAction.SETTINGS -> "Settings" to LudoIcons.settings
    }

@Composable
private fun pulse(): Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    val value by transition.animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse scale")
    return value
}

/** Loading: the brand mark breathes while the library database opens (normally a blink). */
@Composable
fun LoadingStage() {
    Box(Modifier.fillMaxSize().background(Ludo.colors.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Box(Modifier.size(96.dp).scale(pulse()).background(Ludo.colors.lav, ExpressiveShapes.flower8))
            Brand(markColor = Color.Transparent, markSize = 0.dp)
        }
    }
}

@Composable
fun LoadingDeck() {
    Box(Modifier.fillMaxSize().background(Ludo.colors.bg), contentAlignment = Alignment.Center) {
        Box(Modifier.size(64.dp).scale(pulse()).background(Ludo.colors.org, ExpressiveShapes.sunny))
    }
}

@Composable
private fun ShapedIcon(
    key: String,
    shape: Shape,
    color: Color,
    size: Int,
    modifier: Modifier,
) {
    Box(modifier.size(size.dp).background(color, shape), contentAlignment = Alignment.Center) {
        Image(painterResource(PlatformArt.icon(key)), null, Modifier.size((size * 0.56f).dp))
    }
}

@Composable
fun LibraryStateStage(state: AppState) {
    val colors = Ludo.colors
    val status = Screens.status(state)
    val copy = copyFor(status, state)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Blob(colors.blobBack, Modifier.offset((-60).dp, 110.dp).size(520.dp, 390.dp))
        Brand(Modifier.offset(40.dp, 30.dp))
        StatusPill(Modifier.align(Alignment.TopEnd).padding(top = 22.dp, end = 28.dp))
        // Console marks on expressive shapes, from the design pack.
        ShapedIcon("gba", ExpressiveShapes.cookie9, colors.lavC, 190, Modifier.offset(560.dp, 90.dp))
        ShapedIcon("ds", ExpressiveShapes.sunny, colors.org.copy(alpha = 0.85f), 150, Modifier.offset(760.dp, 220.dp))
        ShapedIcon("playstation", ExpressiveShapes.flower8, colors.pur, 140, Modifier.offset(590.dp, 320.dp))
        Column(Modifier.offset(48.dp, 130.dp).width(470.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(copy.title.uppercase(), style = LudoType.display.copy(fontSize = 58.sp, lineHeight = 58.sp), color = Color(0xFFF3ECFF))
            Text(copy.body, style = LudoType.sub, color = colors.lav)
            if (status == LibraryStatus.SCANNING) WavyActivity(Modifier.width(320.dp).padding(top = 8.dp))
        }
    }
}

@Composable
fun LibraryStateDeck(
    state: AppState,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val status = Screens.status(state)
    val copy = copyFor(status, state)
    val actions = Screens.stateActions(status)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Blob(colors.decoPurple, Modifier.align(Alignment.BottomEnd).offset(70.dp, 60.dp).size(170.dp, 160.dp))
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 30.dp, top = 30.dp, end = 30.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionLabel(copy.deckTitle, style = LudoType.sectionLarge)
            Text(copy.deckBody, style = LudoType.body, color = colors.on2)
            if (status == LibraryStatus.SCANNING) WavyActivity()
            Spacer(Modifier.weight(1f))
            actions.forEachIndexed { index, action ->
                val (label, icon) = action.label()
                val primary = index == 0 && action != StateAction.SETTINGS
                PrimaryPill(
                    label,
                    Modifier.fillMaxWidth(),
                    icon = icon,
                    container = if (primary) colors.org else colors.sf3,
                    content = if (primary) colors.orgD else colors.on,
                    height = if (primary) 62.dp else 54.dp,
                    focused = state.isFocused(Zones.STATE_ACTIONS, index),
                ) { events.tap(FocusSpot(Zones.STATE_ACTIONS, index), true) }
            }
        }
    }
}

@Composable
fun SettingsStage(
    state: AppState,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val section = Screens.settingsSection(state)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Blob(colors.decoPurple, Modifier.align(Alignment.TopEnd).offset(70.dp, 30.dp).size(240.dp, 230.dp))
        CookieBacking(Modifier.matchParentSize())
        StageNav(state, events, Modifier.offset(y = 20.dp))
        Column(Modifier.offset(56.dp, 130.dp).width(1300.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(section.title, style = LudoType.headline.copy(fontSize = 52.sp), color = colors.on)
            if (section == SettingsSection.ABOUT) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
                Text("Ludoform ${version.orEmpty()}", style = LudoType.chip, color = colors.on2)
            }
            Screens.settingsItems(state, section).forEachIndexed { index, item ->
                val (title, subtitle, icon) = item.copy(state)
                ListRow(title, Modifier.fillMaxWidth(), subtitle, icon, focused = state.isFocused(Zones.SETTINGS_LIST, index)) {
                    events.tap(FocusSpot(Zones.SETTINGS_LIST, index), true)
                }
            }
        }
    }
}
