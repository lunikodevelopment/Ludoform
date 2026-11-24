package dev.lunikodevelopment.ludoform.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.Destination
import dev.lunikodevelopment.ludoform.data.LibraryModel
import dev.lunikodevelopment.ludoform.data.LibrarySort
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
import dev.lunikodevelopment.ludoform.theme.tap

private const val PAGE = Screens.GRID_COLUMNS * 3

@Composable
fun LibraryStage(
    state: AppState,
    library: LibrarySnapshot,
    destination: Destination.Library,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val model = Screens.library(library, destination)
    val game = Screens.focusedGame(state)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Blob(colors.decoPurple, Modifier.align(Alignment.TopEnd).offset(70.dp, 30.dp).size(240.dp, 230.dp))
        Blob(colors.decoOrange, Modifier.align(Alignment.BottomEnd).offset(40.dp, 60.dp).size(220.dp, 210.dp))
        CookieBacking(Modifier.matchParentSize())
        StageNav(state, events, Modifier.offset(y = 20.dp))
        if (game != null) {
            Crossfade(
                game,
                Modifier.offset(40.dp, 96.dp).size(430.dp, 330.dp),
                animationSpec = tween(220),
                label = "library art",
            ) { shown ->
                Image(
                    gameArt(shown),
                    shown.title,
                    Modifier.fillMaxSize().clip(ExpressiveShapes.blob),
                    contentScale = ContentScale.Crop,
                    alignment = gameArtAlignment(shown),
                )
            }
            val platform = library.platform(game.platformId)
            Column(Modifier.offset(500.dp, 120.dp).width(400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    platformInfo(game.platformId, platform?.name ?: game.platformId).shortName.uppercase(),
                    style = LudoType.stageLabel,
                    color = colors.org,
                )
                DisplayTitle(game.title, Modifier.fillMaxWidth(), LudoType.display.copy(fontSize = 60.sp), maxLines = 2)
                Text(
                    listOfNotNull(
                        game.region,
                        if (game.fileCount > 1) "${game.fileCount} files" else null,
                        game.primaryFile.substringAfterLast(
                            '.',
                            "",
                        ).uppercase().ifEmpty {
                            null
                        },
                    )
                        .joinToString(" · ").ifEmpty { "Local game" },
                    style = LudoType.sub,
                    color = colors.on2,
                )
                PrimaryPill("On this device", Modifier.padding(top = 10.dp), icon = LudoIcons.folder) {}
            }
            val pages = (model.games.size + PAGE - 1) / PAGE
            if (pages > 1) {
                val page = state.focus.indexIn(Zones.LIBRARY_GRID) / PAGE
                val window = 6
                val first = (page - window / 2).coerceIn(0, maxOf(pages - window, 0))
                Row(Modifier.offset(500.dp, 490.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (first until minOf(first + window, pages)).forEach {
                        Box(Modifier.size(10.dp).background(if (it == page) colors.lav else Color(0xFF5A5363), CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryDeck(
    state: AppState,
    library: LibrarySnapshot,
    destination: Destination.Library,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val model = Screens.library(library, destination)
    val focusedIndex = state.focus.indexIn(Zones.LIBRARY_GRID)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Blob(colors.decoPurple, Modifier.align(Alignment.BottomStart).offset((-60).dp, 70.dp).size(180.dp, 170.dp))
        Blob(colors.decoPurple, Modifier.align(Alignment.BottomEnd).offset(70.dp, 60.dp).size(170.dp, 160.dp))
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LudoIcon(LudoIcons.console, tint = colors.on)
                val name = model.platform?.let { platformInfo(it.id, it.name).shortName } ?: "All games"
                Text(
                    "$name · ${model.games.size} game${if (model.games.size == 1) "" else "s"}",
                    style = LudoType.deckTitle,
                    color = colors.on,
                    maxLines = 1,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibrarySort.entries.forEachIndexed { index, sort ->
                    Chip(
                        if (sort == LibrarySort.TITLE) "A–Z" else "Recently added",
                        icon = if (sort == LibrarySort.TITLE) LudoIcons.sortAz else LudoIcons.sparkle,
                        selected = destination.sort == sort,
                        focused = state.isFocused(Zones.LIBRARY_SORT, index),
                    ) { events.tap(FocusSpot(Zones.LIBRARY_SORT, index), true) }
                }
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShelfGrid(state, model, focusedIndex, events, Modifier.weight(1f).fillMaxHeight())
                if (model.letters.size > 1) LetterRail(model, focusedIndex, events, Modifier.width(22.dp).fillMaxHeight())
                ScrollTrack(focusedIndex, model.games.size, Modifier.width(22.dp).fillMaxHeight())
            }
            if (model.games.isNotEmpty()) {
                PrimaryPill(
                    "Open",
                    Modifier.align(Alignment.CenterHorizontally).width(340.dp),
                    glyph = "A",
                    height = 58.dp,
                    style = LudoType.button.copy(fontSize = 22.sp),
                    focused = state.isFocused(Zones.LIBRARY_OPEN, 0),
                ) { events.tap(FocusSpot(Zones.LIBRARY_OPEN, 0), true) }
            }
        }
    }
}

@Composable
private fun ShelfGrid(
    state: AppState,
    model: LibraryModel,
    focusedIndex: Int,
    events: UiEvents,
    modifier: Modifier,
) {
    // A new sort starts at the top instead of following the previously first item to its new position.
    val grid = androidx.compose.runtime.key(model.sort) { rememberLazyGridState() }
    LaunchedEffect(focusedIndex, model.games.size) {
        val visible = grid.layoutInfo.visibleItemsInfo
        val fullyVisible =
            visible.firstOrNull { it.index == focusedIndex }?.let {
                it.offset.y >= 0 && it.offset.y + it.size.height <= grid.layoutInfo.viewportEndOffset
            } == true
        if (!fullyVisible && focusedIndex in model.games.indices) {
            val row = focusedIndex / Screens.GRID_COLUMNS
            val firstVisibleRow = (visible.firstOrNull()?.index ?: 0) / Screens.GRID_COLUMNS
            val target = if (row <= firstVisibleRow) row else maxOf(row - 2, 0)
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
                Modifier.fillMaxWidth().height(96.dp),
                focused = state.isFocused(Zones.LIBRARY_GRID, index),
                corner = 16.dp,
                titleStyle = LudoType.tileTitle,
                haloShape = ExpressiveShapes.wavy,
                haloOutset = 8.dp,
                alignment = gameArtAlignment(game),
            ) { events.tap(FocusSpot(Zones.LIBRARY_GRID, index), false) }
        }
    }
}

/** Jump-to-letter rail; the current letter sits on a lavender disc. Letters share the rail height evenly. */
@Composable
private fun LetterRail(
    model: LibraryModel,
    focusedIndex: Int,
    events: UiEvents,
    modifier: Modifier,
) {
    val colors = Ludo.colors
    val current = model.letters.indexOfLast { it.second <= focusedIndex }
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val slot = minOf(22.dp, maxHeight / model.letters.size)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            model.letters.forEachIndexed { position, (letter, index) ->
                val on = position == current
                Box(
                    Modifier.size(slot).background(if (on) colors.lav else Color.Transparent, CircleShape)
                        .tap("Jump to $letter") { events.tap(FocusSpot(Zones.LIBRARY_GRID, index), false) },
                    contentAlignment = Alignment.Center,
                ) {
                    val size = minOf(11f, slot.value * 0.62f)
                    Text(
                        letter.toString(),
                        style = LudoType.caption.copy(fontSize = size.sp, lineHeight = size.sp),
                        color = if (on) colors.lavD else colors.on2,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScrollTrack(
    index: Int,
    count: Int,
    modifier: Modifier,
) {
    val colors = Ludo.colors
    val fraction = if (count <= 1) 0f else index / (count - 1f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LudoIcon(LudoIcons.grid, size = 18.dp, tint = colors.on2)
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.weight(1f).width(20.dp)) {
            val travel = maxHeight - 20.dp
            Box(Modifier.align(Alignment.TopCenter).width(6.dp).fillMaxHeight().background(colors.sf3, RoundedCornerShape(3.dp)))
            Box(
                Modifier.align(
                    Alignment.TopCenter,
                ).width(6.dp).height(travel * fraction + 10.dp).background(colors.org, RoundedCornerShape(3.dp)),
            )
            Box(Modifier.offset(y = travel * fraction).size(20.dp).background(colors.org, CircleShape))
        }
        Spacer(Modifier.height(2.dp))
    }
}
