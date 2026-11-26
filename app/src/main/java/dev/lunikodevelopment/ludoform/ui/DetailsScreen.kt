package dev.lunikodevelopment.ludoform.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.Destination
import dev.lunikodevelopment.ludoform.data.DetailsAction
import dev.lunikodevelopment.ludoform.data.Screens
import dev.lunikodevelopment.ludoform.data.Zones
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.model.PlayHistory
import dev.lunikodevelopment.ludoform.theme.Blob
import dev.lunikodevelopment.ludoform.theme.ButtonGroup
import dev.lunikodevelopment.ludoform.theme.Chip
import dev.lunikodevelopment.ludoform.theme.CookieBacking
import dev.lunikodevelopment.ludoform.theme.ExpressiveShapes
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoIcon
import dev.lunikodevelopment.ludoform.theme.LudoIcons
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.PrimaryPill
import dev.lunikodevelopment.ludoform.theme.cookieFocus
import dev.lunikodevelopment.ludoform.theme.focusState
import dev.lunikodevelopment.ludoform.theme.tap

private val tonalCard = Color(0xFF3A3343)

@Composable
fun DetailsStage(
    state: AppState,
    library: LibrarySnapshot,
    destination: Destination.Details,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val game = library.game(destination.gameId)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        if (game != null) {
            Image(
                gameArt(game),
                null,
                Modifier.align(Alignment.TopEnd).offset(y = 64.dp).size(590.dp, 476.dp).clip(RoundedCornerShape(topStart = 28.dp))
                    .alpha(if (game.remoteOnly) 0.45f else 1f),
                contentScale = ContentScale.Crop,
                alignment = gameArtAlignment(game),
            )
        }
        CookieBacking(Modifier.matchParentSize())
        StageNav(state, events, Modifier.offset(y = 20.dp))
        if (game == null) {
            Text("This game is no longer in your library.", Modifier.offset(44.dp, 140.dp), style = LudoType.sub, color = colors.on2)
            return@Box
        }
        val platform = library.platform(game.platformId)
        val info = platformInfo(game.platformId, platform?.name ?: game.platformId)
        Column(Modifier.offset(44.dp, 128.dp).width(380.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            DisplayTitle(game.title, Modifier.fillMaxWidth(), LudoType.display.copy(fontSize = 58.sp, lineHeight = 56.sp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.height(28.dp).background(colors.org, CircleShape).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(info.shortName, style = LudoType.eyebrow.copy(fontSize = 14.sp, letterSpacing = 0.sp), color = colors.orgD)
                }
                val extras =
                    if (game.remoteOnly) {
                        listOfNotNull(game.region, game.rommSizeBytes?.let(::formatSize))
                    } else {
                        listOfNotNull(game.region, "${game.fileCount} file${if (game.fileCount == 1) "" else "s"}")
                    }
                Text("· " + extras.joinToString(" · "), style = LudoType.label, color = colors.on)
            }
            Text(
                listOfNotNull(
                    platform?.name,
                    info.maker,
                    info.year?.toString(),
                    game.lastPlayedAt?.let { "Last played ${PlayHistory.lastPlayed(it, System.currentTimeMillis())}" }
                        ?: if (game.remoteOnly) "On your RomM server" else "On this device",
                ).distinct().joinToString(" · "),
                Modifier.padding(top = 18.dp),
                style = LudoType.chip,
                color = colors.on2,
            )
        }
    }
}

@Composable
fun DetailsDeck(
    state: AppState,
    library: LibrarySnapshot,
    destination: Destination.Details,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val game = library.game(destination.gameId)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Blob(colors.decoPurple, Modifier.align(Alignment.TopEnd).offset(50.dp, (-40).dp).size(150.dp, 140.dp))
        Blob(colors.decoOrange, Modifier.align(Alignment.BottomStart).offset((-60).dp, 60.dp).size(170.dp, 170.dp))
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 30.dp, top = 30.dp, end = 30.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (game != null && game.remoteOnly) {
                val download = state.downloads.firstOrNull { it.gameId == game.id }
                // With RomM down, a game that is not queued yet explains why it can't download now (D35).
                val blocked = download == null && state.romm.offline != null
                RemoteGameCard(game, download?.status ?: state.romm.offline?.let(::cantDownload))
                PrimaryPill(
                    when {
                        download != null -> "Open Transfers"
                        blocked -> "Server unreachable"
                        else -> "Download"
                    },
                    Modifier.fillMaxWidth(),
                    icon = if (blocked) LudoIcons.cloudOff else LudoIcons.download,
                    container = Color(0xFFD9C8FF),
                    content = Color(0xFF16131B),
                    height = 64.dp,
                    style = LudoType.button.copy(fontSize = 24.sp),
                    focused = state.isFocused(Zones.DETAILS_PLAY, 0),
                ) { events.tap(FocusSpot(Zones.DETAILS_PLAY, 0), true) }
            } else if (game != null) {
                Box(Modifier.fillMaxWidth().height(72.dp)) {
                    Blob(colors.decoPurple, Modifier.offset((-20).dp, (-16).dp).size(150.dp, 110.dp), ExpressiveShapes.cookie9)
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SplitHalf(
                            "Play",
                            LudoIcons.play,
                            leading = true,
                            focused = state.isFocused(Zones.DETAILS_PLAY, 0),
                            modifier = Modifier.weight(1.3f).height(72.dp),
                        ) { events.tap(FocusSpot(Zones.DETAILS_PLAY, 0), true) }
                        SplitHalf(
                            "Play with…",
                            LudoIcons.dropdown,
                            leading = false,
                            focused = state.isFocused(Zones.DETAILS_PLAY, 1),
                            modifier = Modifier.weight(1f).height(68.dp),
                        ) { events.tap(FocusSpot(Zones.DETAILS_PLAY, 1), true) }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().background(tonalCard, ExpressiveShapes.wavy).padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LudoIcon(LudoIcons.folder, tint = colors.org)
                        Text(
                            "Files",
                            Modifier.weight(1f),
                            style = LudoType.section.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(600)),
                            color = colors.on,
                        )
                        Text("${game.fileCount}", style = LudoType.button.copy(fontSize = 22.sp), color = colors.on)
                    }
                    Text(game.primaryFile, style = LudoType.chip, color = colors.on2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Chip(
                        if (game.lastPlayedAt == null) "Not played yet" else "${PlayHistory.playTime(game.playTimeMillis)} played",
                        Modifier.weight(1f),
                        icon = LudoIcons.clock,
                        container = tonalCard,
                        height = 48.dp,
                    )
                    Chip(
                        if (game.remoteOnly) "On RomM" else "On this device",
                        Modifier.weight(1f),
                        icon = LudoIcons.folder,
                        container = Color(0xFF4B3328),
                        content = Color(0xFFF5B88E),
                        height = 48.dp,
                    )
                }
            } else {
                Text("This game was removed or its folder is unavailable.", style = LudoType.body, color = colors.on2)
            }
            val notice = state.notice
            if (notice != null) {
                Row(
                    Modifier.fillMaxWidth().background(colors.sf3, RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LudoIcon(LudoIcons.info, tint = colors.lav)
                    Text(notice, style = LudoType.chip, color = colors.on)
                }
            }
            Spacer(Modifier.weight(1f))
            val moreLabel = game?.let { "More ${platformInfo(it.platformId).shortName}" } ?: "Library"
            val upload = state.uploads.firstOrNull { it.gameId == destination.gameId }
            ButtonGroup(
                Screens.detailsActions(state, destination.gameId).map {
                    when (it) {
                        DetailsAction.UPLOAD ->
                            when (upload?.state) {
                                null -> "Upload" to LudoIcons.upload
                                dev.lunikodevelopment.ludoform.data.UploadRowState.DONE -> "Uploaded" to LudoIcons.check
                                else -> "Uploading" to LudoIcons.upload
                            }
                        DetailsAction.BACK -> "Back" to LudoIcons.back
                        DetailsAction.FAVORITE ->
                            if (game?.favorite == true) "Favorite" to LudoIcons.heartFilled else "Favorite" to LudoIcons.heart
                        DetailsAction.MORE_FROM_PLATFORM -> moreLabel to LudoIcons.grid
                        DetailsAction.RESCAN -> "Rescan" to LudoIcons.refresh
                    }
                },
                state.focusedIndex(Zones.DETAILS_ACTIONS),
            ) { index -> events.tap(FocusSpot(Zones.DETAILS_ACTIONS, index), true) }
        }
    }
}

/** Why a RomM-only game can't be downloaded while RomM is down (D35). */
private fun cantDownload(offline: dev.lunikodevelopment.ludoform.data.RommOffline): String =
    when (offline.outage) {
        dev.lunikodevelopment.ludoform.data.RommOutage.SIGN_IN -> "Can't download now: RomM refused the sign-in"
        dev.lunikodevelopment.ludoform.data.RommOutage.SERVER_ERROR -> "Can't download now: RomM answered with an error"
        dev.lunikodevelopment.ludoform.data.RommOutage.UNREACHABLE -> "Can't download now: RomM didn't answer"
    }

/** Shown above Download for a game that is only on the RomM server; [progress] once it is in Transfers. */
@Composable
private fun RemoteGameCard(
    game: dev.lunikodevelopment.ludoform.model.GameSummary,
    progress: String?,
) {
    val colors = Ludo.colors
    Row(
        Modifier.fillMaxWidth().background(tonalCard, ExpressiveShapes.wavy).padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LudoIcon(LudoIcons.cloud, size = 30.dp, tint = colors.lav)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("On your RomM server", style = LudoType.deckTitle, color = colors.on)
            Text(
                listOfNotNull(game.rommSizeBytes?.let(::formatSize), progress ?: "Not on this device yet").joinToString(" · "),
                style = LudoType.chip,
                color = colors.on2,
            )
        }
    }
}

/** One half of the split Play / Play with… button: separate focus stops joined at a tight seam. */
@Composable
private fun SplitHalf(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    leading: Boolean,
    focused: Boolean,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val shape =
        if (leading) {
            RoundedCornerShape(topStart = 999.dp, bottomStart = 999.dp, topEnd = 26.dp, bottomEnd = 26.dp)
        } else {
            RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp, topEnd = 999.dp, bottomEnd = 999.dp)
        }
    Row(
        modifier
            .cookieFocus(focused, ExpressiveShapes.wavyLarge, Color(0xFFB79CFF), outset = 6.dp, scale = 1.04f)
            .focusState(focused)
            .clip(shape)
            .background(if (leading) Color(0xFFD9C8FF) else Color(0xFFC3AEF5))
            .tap(label, onTap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        val style =
            if (leading) {
                LudoType.button.copy(
                    fontSize = 28.sp,
                )
            } else {
                LudoType.button.copy(fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight(600))
            }
        if (leading) LudoIcon(icon, size = 26.dp, tint = Color(0xFF16131B))
        Text(label, style = style, color = Color(0xFF16131B))
        if (!leading) LudoIcon(icon, size = 22.dp, tint = Color(0xFF16131B))
    }
}
