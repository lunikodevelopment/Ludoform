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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.Destination
import dev.lunikodevelopment.ludoform.data.HomeApp
import dev.lunikodevelopment.ludoform.data.HomeNotice
import dev.lunikodevelopment.ludoform.data.LibrarySort
import dev.lunikodevelopment.ludoform.data.Screens
import dev.lunikodevelopment.ludoform.data.UiAction
import dev.lunikodevelopment.ludoform.data.Zones
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.model.PlayHistory
import dev.lunikodevelopment.ludoform.theme.ActionBar
import dev.lunikodevelopment.ludoform.theme.BarAction
import dev.lunikodevelopment.ludoform.theme.Blob
import dev.lunikodevelopment.ludoform.theme.Chip
import dev.lunikodevelopment.ludoform.theme.CookieBacking
import dev.lunikodevelopment.ludoform.theme.CoverTile
import dev.lunikodevelopment.ludoform.theme.ExpressiveShapes
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoIcons
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.MoreButton
import dev.lunikodevelopment.ludoform.theme.PrimaryPill
import dev.lunikodevelopment.ludoform.theme.SectionLabel

@Composable
fun HomeStage(
    state: AppState,
    library: LibrarySnapshot,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val game = Screens.focusedGame(state)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Crossfade(
            game,
            Modifier.align(Alignment.TopEnd).width(620.dp).fillMaxHeight(),
            animationSpec = tween(260),
            label = "home art",
        ) { shown ->
            if (shown != null) {
                Image(
                    gameArt(shown),
                    null,
                    Modifier.fillMaxSize().alpha(if (shown.remoteOnly) 0.45f else 1f),
                    contentScale = ContentScale.Crop,
                    alignment = gameArtAlignment(shown),
                )
            }
        }
        Box(
            Modifier.width(420.dp).fillMaxHeight().background(
                Brush.horizontalGradient(0.7f to colors.bg, 1f to colors.bg.copy(alpha = 0f)),
            ),
        )
        Blob(colors.blobBack, Modifier.offset((-60).dp, 110.dp).size(500.dp, 380.dp))
        CookieBacking(Modifier.matchParentSize())
        StatusPill(Modifier.offset(28.dp, 24.dp))
        if (game != null) {
            val platform = library.platform(game.platformId)
            Column(Modifier.offset(48.dp, 120.dp).width(440.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                DisplayTitle(game.title, Modifier.fillMaxWidth(), color = Color(0xFFF3ECFF))
                Text(
                    listOfNotNull(platform?.name ?: platformInfo(game.platformId).shortName, game.region).joinToString(" · "),
                    style = LudoType.sub.copy(fontSize = 26.sp, lineHeight = 32.sp),
                    color = colors.lav,
                )
                game.lastPlayedAt?.let { lastPlayed ->
                    Text(
                        "${PlayHistory.playTime(game.playTimeMillis)} played · last played " +
                            PlayHistory.lastPlayed(lastPlayed, System.currentTimeMillis()),
                        style = LudoType.chip.copy(fontSize = 18.sp),
                        color = colors.on2,
                    )
                }
                PrimaryPill(
                    "Open game",
                    Modifier.padding(top = 8.dp),
                    icon = LudoIcons.play,
                    focused = state.isFocused(Zones.HOME_PLAY, 0),
                ) { events.tap(FocusSpot(Zones.HOME_PLAY, 0), true) }
            }
        }
    }
}

@Composable
fun HomeDeck(
    state: AppState,
    library: LibrarySnapshot,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val home = Screens.home(library)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (Screens.homeNotice(state)) {
                HomeNotice.FOLDERS -> {
                    val count = state.inaccessibleFolders.size
                    Chip(
                        "$count folder${if (count == 1) "" else "s"} need${if (count == 1) "s" else ""} access again · Choose",
                        icon = LudoIcons.lock,
                        container = colors.warn.copy(alpha = 0.18f),
                        content = colors.warn,
                        focused = state.isFocused(Zones.HOME_NOTICE, 0),
                    ) { events.tap(FocusSpot(Zones.HOME_NOTICE, 0), true) }
                }
                HomeNotice.ROMM_OFFLINE ->
                    Chip(
                        "Can't reach RomM · installed games still play · Details",
                        icon = LudoIcons.cloudOff,
                        container = colors.warn.copy(alpha = 0.18f),
                        content = colors.warn,
                        focused = state.isFocused(Zones.HOME_NOTICE, 0),
                    ) { events.tap(FocusSpot(Zones.HOME_NOTICE, 0), true) }
                null -> Unit
            }
            SectionLabel(if (home.continuing) "Continue playing" else "Start playing", style = LudoType.sectionLarge)
            Row(
                Modifier.padding(start = 8.dp, top = 6.dp, end = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                home.featured.forEachIndexed { index, game ->
                    CoverTile(
                        gameArt(game),
                        game.title,
                        Modifier.size(172.dp, 128.dp),
                        focused = state.isFocused(Zones.HOME_GAMES, index),
                        alignment = gameArtAlignment(game),
                        dimmed = game.remoteOnly,
                    ) { events.tap(FocusSpot(Zones.HOME_GAMES, index), false) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Consoles", Modifier.width(128.dp))
                home.consoles.forEachIndexed { index, platform ->
                    Chip(
                        platformInfo(platform.id, platform.name).shortName,
                        focused = state.isFocused(Zones.HOME_CONSOLES, index),
                    ) { events.tap(FocusSpot(Zones.HOME_CONSOLES, index), true) }
                }
                Spacer(Modifier.weight(1f))
                MoreButton(state.isFocused(Zones.HOME_CONSOLES, home.consoles.size)) {
                    events.tap(FocusSpot(Zones.HOME_CONSOLES, home.consoles.size), true)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(
                    "Recently added",
                    Modifier.width(128.dp),
                    color = Color(0xFFF5C376),
                    style = LudoType.section.copy(fontSize = 17.sp, lineHeight = 21.sp),
                )
                home.recent.forEachIndexed { index, game ->
                    CoverTile(
                        gameArt(game),
                        null,
                        Modifier.size(100.dp, 54.dp),
                        focused = state.isFocused(Zones.HOME_RECENT, index),
                        corner = 14.dp,
                        haloShape = ExpressiveShapes.wavy,
                        haloOutset = 7.dp,
                        alignment = gameArtAlignment(game),
                        dimmed = game.remoteOnly,
                    ) { events.tap(FocusSpot(Zones.HOME_RECENT, index), false) }
                }
                Spacer(Modifier.weight(1f))
                MoreButton(state.isFocused(Zones.HOME_RECENT, home.recent.size)) {
                    events.tap(FocusSpot(Zones.HOME_RECENT, home.recent.size), true)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Apps", Modifier.width(128.dp), shape = ExpressiveShapes.flower8)
                home.apps.forEachIndexed { index, app ->
                    val (label, icon) =
                        when (app) {
                            HomeApp.SETTINGS -> "Settings" to LudoIcons.settings
                            // No count here: four apps fill the row, and the Transfers screen says what is left.
                            HomeApp.TRANSFERS -> "Transfers" to LudoIcons.download
                            HomeApp.FILES -> "Files" to LudoIcons.folder
                            HomeApp.NETWORK -> "Wi-Fi" to LudoIcons.wifi
                        }
                    Chip(label, icon = icon, focused = state.isFocused(Zones.HOME_APPS, index)) {
                        events.tap(FocusSpot(Zones.HOME_APPS, index), true)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            ActionBar(
                listOf(
                    BarAction("Open", glyph = "A") { state.focus.current?.let { events.perform(Screens.actionFor(state, it)) } },
                    BarAction("Consoles", glyph = "X") { events.perform(UiAction.Navigate(Destination.Consoles())) },
                    BarAction("All games", glyph = "Y") { events.perform(UiAction.Navigate(Destination.Library(null, LibrarySort.TITLE))) },
                ),
                Modifier.fillMaxWidth().height(60.dp),
            )
        }
    }
}
