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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lunikodevelopment.ludoform.TransferText
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.Destination
import dev.lunikodevelopment.ludoform.data.DownloadRow
import dev.lunikodevelopment.ludoform.data.DownloadRowState
import dev.lunikodevelopment.ludoform.data.Screens
import dev.lunikodevelopment.ludoform.data.TransferHold
import dev.lunikodevelopment.ludoform.data.TransferTab
import dev.lunikodevelopment.ludoform.data.TransferTopAction
import dev.lunikodevelopment.ludoform.data.UploadHold
import dev.lunikodevelopment.ludoform.data.UploadRowState
import dev.lunikodevelopment.ludoform.data.Zones
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.theme.Blob
import dev.lunikodevelopment.ludoform.theme.Chip
import dev.lunikodevelopment.ludoform.theme.CookieBacking
import dev.lunikodevelopment.ludoform.theme.ExpressiveShapes
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoIcon
import dev.lunikodevelopment.ludoform.theme.LudoIcons
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.PrimaryPill
import dev.lunikodevelopment.ludoform.theme.WavyProgressIndicator
import dev.lunikodevelopment.ludoform.theme.cookieFocus
import dev.lunikodevelopment.ludoform.theme.focusState
import dev.lunikodevelopment.ludoform.theme.tap

@Composable
fun TransfersStage(
    state: AppState,
    library: LibrarySnapshot,
    events: UiEvents,
) {
    val colors = Ludo.colors
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        val first = (state.downloads.map { it.gameId } + state.uploads.map { it.gameId }).firstNotNullOfOrNull { library.game(it) }
        if (first != null) {
            androidx.compose.foundation.Image(
                gameArt(first),
                null,
                Modifier.align(Alignment.TopEnd).offset(y = 64.dp).size(560.dp, 476.dp)
                    .clip(RoundedCornerShape(topStart = 120.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alignment = gameArtAlignment(first),
            )
        }
        Blob(colors.decoOrange, Modifier.align(Alignment.BottomStart).offset((-70).dp, 80.dp).size(260.dp, 220.dp))
        CookieBacking(Modifier.matchParentSize())
        StageNav(state, events, Modifier.offset(y = 20.dp))
        Column(Modifier.offset(56.dp, 130.dp).width(420.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            val uploading = state.uploads.any { it.state != UploadRowState.DONE }
            Text(
                when {
                    state.downloads.isNotEmpty() -> "Your next adventure is on its way."
                    uploading -> "Sending your games to RomM."
                    else -> "Nothing on its way yet."
                },
                style = LudoType.headline.copy(fontSize = 52.sp, lineHeight = 56.sp),
                color = colors.on,
            )
            Text(TransferText.summary(state.downloads, state.uploads), style = LudoType.sub, color = colors.on2)
        }
    }
}

@Composable
fun TransfersDeck(
    state: AppState,
    destination: Destination.Transfers,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val tab = Screens.transferTab(state, destination)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 24.dp, top = 22.dp, end = 24.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Transfers", Modifier.weight(1f), style = LudoType.headline.copy(fontSize = 30.sp), color = colors.on)
                Screens.transferTopActions(state, tab).forEachIndexed { index, action ->
                    PrimaryPill(
                        if (action == TransferTopAction.CANCEL_ALL) "Cancel all" else "Clear finished",
                        icon = if (action == TransferTopAction.CANCEL_ALL) LudoIcons.close else LudoIcons.check,
                        container = colors.sf3,
                        content = colors.on,
                        height = 40.dp,
                        style = LudoType.label,
                        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        focused = state.isFocused(Zones.TRANSFER_TOP, index),
                    ) { events.tap(FocusSpot(Zones.TRANSFER_TOP, index), true) }
                }
            }
            if (state.showsUploads) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransferTab.entries.forEachIndexed { index, entry ->
                        Chip(
                            if (entry == TransferTab.DOWNLOAD) "Download" else "Upload",
                            icon = if (entry == TransferTab.DOWNLOAD) LudoIcons.download else LudoIcons.upload,
                            selected = entry == tab,
                            focused = state.isFocused(Zones.TRANSFER_TABS, index),
                            height = 40.dp,
                        ) { events.tap(FocusSpot(Zones.TRANSFER_TABS, index), true) }
                    }
                }
            }
            val scroll = rememberScrollState()
            val focused = state.focusedIndex(Zones.TRANSFER_ROWS)
            LaunchedEffect(focused) { if (focused != null) scroll.animateScrollTo(((focused / 2) - 1).coerceAtLeast(0) * ROW_STEP) }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tab == TransferTab.UPLOAD) {
                    if (state.uploads.isEmpty()) {
                        Text(
                            "Open a game that is only on this device and choose Upload to RomM. It shows here until RomM has it.",
                            Modifier.padding(top = 8.dp),
                            style = LudoType.body,
                            color = colors.on2,
                        )
                    }
                    state.uploads.forEachIndexed { index, row ->
                        val (primary, label) =
                            when (row.state) {
                                UploadRowState.ACTIVE -> LudoIcons.pause to "Pause"
                                UploadRowState.PAUSED -> LudoIcons.play to "Resume"
                                UploadRowState.FAILED -> LudoIcons.refresh to "Retry"
                                UploadRowState.DONE -> LudoIcons.info to "Open game"
                            }
                        val (secondary, secondaryLabel) =
                            if (row.state == UploadRowState.DONE) LudoIcons.check to "Clear" else LudoIcons.close to "Cancel"
                        QueueRow(
                            row.gameId,
                            row.title,
                            row.status,
                            row.state == UploadRowState.FAILED,
                            row.percent.takeIf { row.state == UploadRowState.ACTIVE },
                            primary to label,
                            secondary to secondaryLabel,
                            state,
                            index,
                            events,
                        )
                    }
                } else {
                    if (state.downloads.isEmpty()) {
                        Text(
                            "Open a dimmed game and choose Download. It shows here until it is on your device.",
                            Modifier.padding(top = 8.dp),
                            style = LudoType.body,
                            color = colors.on2,
                        )
                    }
                    state.downloads.forEachIndexed { index, row -> TransferRow(row, state, index, events) }
                }
            }
            if (tab == TransferTab.UPLOAD) UploadHoldsRow(state, events) else HoldsRow(state, events)
        }
        if (destination.confirmCancelAll) CancelAllDialog(state, tab, events)
    }
}

@Composable
private fun TransferRow(
    row: DownloadRow,
    state: AppState,
    index: Int,
    events: UiEvents,
) {
    val primary =
        when (row.state) {
            DownloadRowState.ACTIVE -> LudoIcons.pause to "Pause"
            DownloadRowState.PAUSED -> LudoIcons.play to "Resume"
            DownloadRowState.FAILED -> LudoIcons.refresh to "Retry"
        }
    QueueRow(
        row.gameId,
        row.title,
        row.status,
        row.state == DownloadRowState.FAILED,
        row.percent.takeIf { row.state == DownloadRowState.ACTIVE },
        primary,
        LudoIcons.close to "Cancel",
        state,
        index,
        events,
    )
}

/** One row of either queue: art, title, status, progress while it runs, and two round buttons. */
@Composable
private fun QueueRow(
    gameId: String,
    title: String,
    status: String,
    failed: Boolean,
    percent: Int?,
    primary: Pair<ImageVector, String>,
    secondary: Pair<ImageVector, String>,
    state: AppState,
    index: Int,
    events: UiEvents,
) {
    val colors = Ludo.colors
    Row(
        Modifier.fillMaxWidth().height(62.dp).background(colors.sf2, CircleShape).padding(start = 10.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.library?.game(gameId)?.let { game ->
            androidx.compose.foundation.Image(
                gameArt(game),
                null,
                Modifier.size(46.dp).clip(ExpressiveShapes.clover),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        } ?: Box(Modifier.size(46.dp).background(colors.org, ExpressiveShapes.clover))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    Modifier.weight(1f),
                    style = LudoType.label.copy(fontSize = 17.sp),
                    color = colors.on,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    status,
                    Modifier.padding(start = 8.dp),
                    style = LudoType.caption,
                    color = if (failed) colors.err else colors.on2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (percent != null && percent > 0) {
                WavyProgressIndicator(percent / 100f, Modifier.fillMaxWidth().height(12.dp))
            }
        }
        RoundButton(primary.first, primary.second, state.isFocused(Zones.TRANSFER_ROWS, index * 2)) {
            events.tap(FocusSpot(Zones.TRANSFER_ROWS, index * 2), true)
        }
        RoundButton(secondary.first, secondary.second, state.isFocused(Zones.TRANSFER_ROWS, index * 2 + 1)) {
            events.tap(FocusSpot(Zones.TRANSFER_ROWS, index * 2 + 1), true)
        }
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    label: String,
    focused: Boolean,
    onTap: () -> Unit,
) {
    val colors = Ludo.colors
    Box(
        Modifier
            .cookieFocus(focused, ExpressiveShapes.wavy, colors.lav, outset = 5.dp, scale = 1.08f)
            .focusState(focused)
            .size(40.dp)
            .background(if (focused) colors.lav else colors.sf3, CircleShape)
            .tap(label, onTap),
        contentAlignment = Alignment.Center,
    ) { LudoIcon(icon, size = 18.dp, tint = if (focused) colors.lavD else colors.on) }
}

@Composable
private fun HoldsRow(
    state: AppState,
    events: UiEvents,
) {
    val holds = state.holds
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        TransferHold.entries.forEachIndexed { index, hold ->
            val (label, icon) =
                when (hold) {
                    TransferHold.WIFI -> (if (holds.wifiOnly) "Wi-Fi only" else "Any network") to LudoIcons.wifi
                    TransferHold.CHARGING -> (if (holds.chargingOnly) "While charging" else "On battery too") to LudoIcons.battery
                    TransferHold.SPEED -> {
                        val limit = holds.speedLimit
                        (if (limit == 0L) "No speed limit" else "Limit " + formatSize(limit) + "/s") to LudoIcons.download
                    }
                }
            Chip(
                label,
                Modifier.weight(1f),
                icon = icon,
                container = Ludo.colors.sf2,
                height = 44.dp,
                focused = state.isFocused(Zones.TRANSFER_HOLDS, index),
            ) { events.tap(FocusSpot(Zones.TRANSFER_HOLDS, index), true) }
        }
    }
}

@Composable
private fun UploadHoldsRow(
    state: AppState,
    events: UiEvents,
) {
    val holds = state.uploadHolds
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        UploadHold.entries.forEachIndexed { index, hold ->
            val (label, icon) =
                when (hold) {
                    UploadHold.WIFI -> (if (holds.wifiOnly) "Wi-Fi only" else "Any network") to LudoIcons.wifi
                    UploadHold.SCAN -> (if (holds.scanAfter) "Scan RomM after uploads" else "No RomM scan") to LudoIcons.refresh
                }
            Chip(
                label,
                Modifier.weight(1f),
                icon = icon,
                container = Ludo.colors.sf2,
                height = 44.dp,
                focused = state.isFocused(Zones.TRANSFER_HOLDS, index),
            ) { events.tap(FocusSpot(Zones.TRANSFER_HOLDS, index), true) }
        }
    }
}

/** "Cancel all asks first, in a cookie dialog". */
@Composable
private fun CancelAllDialog(
    state: AppState,
    tab: TransferTab,
    events: UiEvents,
) {
    val colors = Ludo.colors
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.size(
                420.dp,
                300.dp,
            ).background(Color(0xFF3B3445), ExpressiveShapes.cookie9).padding(horizontal = 44.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        ) {
            Box(Modifier.size(54.dp).background(colors.org, ExpressiveShapes.clover), Alignment.Center) {
                LudoIcon(LudoIcons.close, size = 26.dp, tint = colors.orgD)
            }
            Text("Cancel all transfers?", style = LudoType.deckTitle.copy(fontSize = 24.sp), color = colors.on)
            val count = if (tab == TransferTab.UPLOAD) state.uploads.count { it.state != UploadRowState.DONE } else state.downloads.size
            val plural = if (count == 1) "" else "s"
            Text(
                if (tab == TransferTab.UPLOAD) {
                    "This will stop $count upload$plural. Files already on the server stay there."
                } else {
                    "This will stop $count transfer$plural and delete what was downloaded so far."
                },
                style = LudoType.chip,
                color = colors.on2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryPill(
                    "Keep transferring",
                    container = Color(0xFF4E4659),
                    content = colors.on,
                    height = 46.dp,
                    style = LudoType.label,
                    padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                    focused = state.isFocused(Zones.TRANSFER_CONFIRM, 0),
                ) { events.tap(FocusSpot(Zones.TRANSFER_CONFIRM, 0), true) }
                PrimaryPill(
                    "Cancel all",
                    height = 46.dp,
                    style = LudoType.label,
                    padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                    focused = state.isFocused(Zones.TRANSFER_CONFIRM, 1),
                ) { events.tap(FocusSpot(Zones.TRANSFER_CONFIRM, 1), true) }
            }
        }
    }
}

/** One queue row with its gap, in board units, for scrolling the focused row into view. */
private const val ROW_STEP = 70
