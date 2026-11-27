package dev.lunikodevelopment.ludoform.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.Destination
import dev.lunikodevelopment.ludoform.data.OfflineModel
import dev.lunikodevelopment.ludoform.data.RommOffline
import dev.lunikodevelopment.ludoform.data.RommOutage
import dev.lunikodevelopment.ludoform.data.RommStatus
import dev.lunikodevelopment.ludoform.data.Screens
import dev.lunikodevelopment.ludoform.data.Zones
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.theme.Blob
import dev.lunikodevelopment.ludoform.theme.Chip
import dev.lunikodevelopment.ludoform.theme.CookieBacking
import dev.lunikodevelopment.ludoform.theme.CoverTile
import dev.lunikodevelopment.ludoform.theme.ExpressiveShapes
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoIcon
import dev.lunikodevelopment.ludoform.theme.LudoIcons
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.PrimaryPill
import kotlinx.coroutines.delay

/** What the Server unreachable Stage says, in the player's words. */
object OfflineText {
    fun title(
        offline: RommOffline?,
        online: Boolean,
    ): String =
        when {
            !online -> "You're offline"
            offline?.outage == RommOutage.SIGN_IN -> "RomM refused the sign-in"
            offline == null -> "Your server is back"
            else -> "Can't reach your server"
        }

    fun detail(
        offline: RommOffline?,
        server: String,
        online: Boolean,
    ): String =
        when {
            !online -> "This Thor has no network. Installed games still play, and saves sync when it's back."
            offline == null -> "RomM answered again. Saves made meanwhile are going up now."
            offline.outage == RommOutage.SIGN_IN ->
                "$server didn't accept the user name or password. Installed games still play; check the sign-in in Server settings."
            offline.outage == RommOutage.SERVER_ERROR ->
                "$server answered with an error. Installed games still play, and saves sync when it's back."
            else -> "$server didn't answer. Installed games still play, and saves sync when it's back."
        }

    /** "Next try in 0:42", "Checking…" or "Waiting for a network". */
    fun nextTry(
        offline: RommOffline?,
        online: Boolean,
        checking: Boolean,
        now: Long,
    ): String? {
        offline ?: return null
        if (!online) return "Waiting for a network"
        if (checking) return "Checking…"
        val left = (offline.nextTryAt - now).coerceAtLeast(0) / 1000
        return if (left == 0L) "Checking…" else "Next try in %d:%02d".format(left / 60, left % 60)
    }

    fun savesWaiting(count: Int): String? =
        when (count) {
            0 -> null
            1 -> "1 save waiting"
            else -> "$count saves waiting"
        }
}

@Composable
fun ServerUnreachableStage(state: AppState) {
    val colors = Ludo.colors
    val offline = state.romm.offline
    val online = rememberDeviceStatus().online
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(offline?.nextTryAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val server = state.romm.address ?: "Your server"
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Blob(colors.decoPurple, Modifier.align(Alignment.BottomStart).offset((-80).dp, 90.dp).size(300.dp, 250.dp))
        Blob(colors.decoOrange, Modifier.align(Alignment.TopEnd).offset(60.dp, (-70).dp).size(240.dp, 220.dp))
        StatusPill(Modifier.offset(28.dp, 24.dp))
        Box(
            Modifier.offset(80.dp, 130.dp).size(250.dp).background(Color(0xFF3B2D5A), ExpressiveShapes.sunny),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(150.dp).background(colors.lav, ExpressiveShapes.cookie9), contentAlignment = Alignment.Center) {
                LudoIcon(if (online) LudoIcons.cloudOff else LudoIcons.wifiOff, size = 72.dp, tint = colors.lavD)
            }
        }
        Column(Modifier.offset(380.dp, 128.dp).width(520.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (offline == null && online) "BACK ONLINE" else "PLAYING OFFLINE", style = LudoType.eyebrow, color = colors.org)
            Text(OfflineText.title(offline, online), style = LudoType.headline.copy(fontSize = 48.sp), color = colors.on)
            Text(OfflineText.detail(offline, server, online), style = LudoType.sub, color = colors.on2)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OfflineText.nextTry(offline, online, state.romm.status == RommStatus.CHECKING, now)?.let {
                    Chip(it, icon = LudoIcons.clock, container = colors.sf2)
                }
                OfflineText.savesWaiting(offline?.savesWaiting ?: 0)?.let {
                    Chip(it, container = Color(0xFF4B3328), content = Color(0xFFF5B88E))
                }
            }
        }
    }
}

@Composable
fun ServerUnreachableDeck(
    state: AppState,
    library: LibrarySnapshot,
    destination: Destination.ServerUnreachable,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val model = Screens.offline(library, destination)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    model.platform?.let { "${it.name} · ${it.gameCount} games" } ?: "No games yet",
                    Modifier.weight(1f),
                    style = LudoType.sectionLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                    color = colors.on,
                    maxLines = 1,
                )
                if (model.installed > 0) {
                    Chip(
                        "Installed · ${model.installed}",
                        selected = model.installedOnly,
                        focused = state.isFocused(Zones.OFFLINE_FILTER, 0),
                    ) { events.tap(FocusSpot(Zones.OFFLINE_FILTER, 0), true) }
                }
            }
            OfflineGrid(state, model, state.focusedIndex(Zones.OFFLINE_GRID) ?: -1, events, Modifier.weight(1f).fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryPill(
                    "Server settings",
                    icon = LudoIcons.settings,
                    container = colors.sf3,
                    content = colors.on,
                    height = 56.dp,
                    style = LudoType.button.copy(fontSize = 17.sp),
                    focused = state.isFocused(Zones.OFFLINE_ACTIONS, 0),
                    padding = PaddingValues(horizontal = 20.dp),
                ) { events.tap(FocusSpot(Zones.OFFLINE_ACTIONS, 0), true) }
                PrimaryPill(
                    "Retry now",
                    Modifier.weight(1f),
                    glyph = "X",
                    height = 56.dp,
                    focused = state.isFocused(Zones.OFFLINE_ACTIONS, 1),
                ) { events.tap(FocusSpot(Zones.OFFLINE_ACTIONS, 1), true) }
            }
        }
    }
}

@Composable
private fun OfflineGrid(
    state: AppState,
    model: OfflineModel,
    focusedIndex: Int,
    events: UiEvents,
    modifier: Modifier,
) {
    val grid = androidx.compose.runtime.key(model.installedOnly, model.platform?.id) { rememberLazyGridState() }
    LaunchedEffect(focusedIndex, model.games.size) {
        val visible = grid.layoutInfo.visibleItemsInfo
        val fullyVisible =
            visible.firstOrNull { it.index == focusedIndex }?.let {
                it.offset.y >= 0 && it.offset.y + it.size.height <= grid.layoutInfo.viewportEndOffset
            } == true
        if (!fullyVisible && focusedIndex in model.games.indices) {
            val row = focusedIndex / Screens.GRID_COLUMNS
            val firstVisibleRow = (visible.firstOrNull()?.index ?: 0) / Screens.GRID_COLUMNS
            val target = if (row <= firstVisibleRow) row else maxOf(row - 1, 0)
            grid.animateScrollToItem(target * Screens.GRID_COLUMNS)
        }
    }
    LazyVerticalGrid(
        GridCells.Fixed(Screens.GRID_COLUMNS),
        modifier,
        state = grid,
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(model.games, key = { _, game -> game.id }) { index, game ->
            CoverTile(
                gameArt(game),
                game.title,
                Modifier.fillMaxWidth().height(100.dp),
                focused = state.isFocused(Zones.OFFLINE_GRID, index),
                corner = 20.dp,
                titleStyle = LudoType.tileTitle,
                haloShape = ExpressiveShapes.wavy,
                haloOutset = 8.dp,
                alignment = gameArtAlignment(game),
                dimmed = game.remoteOnly,
            ) { events.tap(FocusSpot(Zones.OFFLINE_GRID, index), false) }
        }
    }
}
