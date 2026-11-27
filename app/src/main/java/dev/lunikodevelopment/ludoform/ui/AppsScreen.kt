package dev.lunikodevelopment.ludoform.ui

import android.content.pm.PackageManager
import android.util.LruCache
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import dev.lunikodevelopment.ludoform.data.AppDrawer
import dev.lunikodevelopment.ludoform.data.AppGroup
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.Destination
import dev.lunikodevelopment.ludoform.data.InstalledApp
import dev.lunikodevelopment.ludoform.data.UiAction
import dev.lunikodevelopment.ludoform.data.Zones
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.theme.ActionBar
import dev.lunikodevelopment.ludoform.theme.BarAction
import dev.lunikodevelopment.ludoform.theme.Blob
import dev.lunikodevelopment.ludoform.theme.Chip
import dev.lunikodevelopment.ludoform.theme.CookieBacking
import dev.lunikodevelopment.ludoform.theme.ExpressiveShapes
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoIcon
import dev.lunikodevelopment.ludoform.theme.LudoIcons
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.SectionLabel
import dev.lunikodevelopment.ludoform.theme.cookieFocus
import dev.lunikodevelopment.ludoform.theme.tap

private fun groupName(group: AppGroup): String =
    when (group) {
        AppGroup.EMULATORS -> "Emulator"
        AppGroup.GAMES -> "Game"
        AppGroup.ALL, AppGroup.OTHER -> "App"
    }

private fun shownApps(
    state: AppState,
    destination: Destination.Apps,
): List<InstalledApp> = AppDrawer.visible(state.apps.orEmpty(), destination.group)

/** Icons decoded once per app and kept small: a drawer of a hundred apps must not hold a hundred full-size icons. */
private val iconCache = LruCache<String, ImageBitmap>(160)

@Composable
private fun appIcon(packageName: String): ImageBitmap? {
    val manager = LocalContext.current.packageManager
    return remember(packageName) {
        iconCache.get(packageName) ?: try {
            manager.getApplicationIcon(packageName).toBitmap(144, 144).asImageBitmap().also { iconCache.put(packageName, it) }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}

@Composable
private fun AppIcon(
    app: InstalledApp,
    size: Dp,
) {
    val icon = appIcon(app.packageName)
    if (icon != null) {
        Image(icon, app.label, Modifier.size(size))
    } else {
        LudoIcon(LudoIcons.grid, size = size * 0.5f, tint = Ludo.colors.on2)
    }
}

/** The app drawer's Stage (D51): the focused app, large, on its group's backing shape. */
@Composable
fun AppsStage(
    state: AppState,
    destination: Destination.Apps,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val apps = shownApps(state, destination)
    val app = apps.getOrNull(state.focus.indexIn(Zones.APPS_GRID)) ?: apps.firstOrNull()
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Blob(colors.decoPurple, Modifier.align(Alignment.TopEnd).offset(80.dp, (-40).dp).size(460.dp, 420.dp))
        Blob(colors.decoOrange, Modifier.align(Alignment.BottomStart).offset((-90).dp, 110.dp).size(340.dp, 300.dp))
        CookieBacking(Modifier.matchParentSize())
        StageNav(state, events, Modifier.offset(y = 20.dp))
        Row(
            Modifier.offset(96.dp, 150.dp).width(1100.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(56.dp),
        ) {
            if (app != null) {
                val shape =
                    when (app.group) {
                        AppGroup.EMULATORS -> ExpressiveShapes.cookie9
                        AppGroup.GAMES -> ExpressiveShapes.flower8
                        AppGroup.ALL, AppGroup.OTHER -> ExpressiveShapes.clover
                    }
                Box(Modifier.size(300.dp).background(colors.lav, shape), Alignment.Center) { AppIcon(app, 150.dp) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SectionLabel(
                    state.apps?.let { "APPS · ${it.size}" } ?: "APPS",
                    style = LudoType.sectionLarge,
                )
                Text(
                    app?.label ?: if (state.apps == null) "Finding apps…" else "No apps here",
                    style = LudoType.display.copy(fontSize = 84.sp, lineHeight = 90.sp),
                    color = colors.on,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (app != null) {
                    Text(groupName(app.group) + " · opens on this screen", style = LudoType.sub, color = colors.on2)
                }
            }
        }
    }
}

/** The app drawer's Deck (D51): a chip per group with apps in it, then the apps as a grid of icons. */
@Composable
fun AppsDeck(
    state: AppState,
    destination: Destination.Apps,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val all = state.apps.orEmpty()
    val apps = shownApps(state, destination)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 24.dp, top = 22.dp, end = 24.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Apps", style = LudoType.headline.copy(fontSize = 30.sp), color = colors.on)
            if (all.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppDrawer.groups(all).forEachIndexed { index, group ->
                        Chip(
                            group.label,
                            selected = destination.group == group,
                            focused = state.isFocused(Zones.APPS_FILTER, index),
                            height = 40.dp,
                        ) { events.tap(FocusSpot(Zones.APPS_FILTER, index), true) }
                    }
                }
            }
            val scroll = rememberScrollState()
            val focused = state.focusedIndex(Zones.APPS_GRID)
            LaunchedEffect(focused) {
                if (focused != null) scroll.animateScrollTo(((focused / AppDrawer.COLUMNS) - 1).coerceAtLeast(0) * ROW_STEP)
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.apps == null) Text("Finding apps…", style = LudoType.body, color = colors.on2)
                apps.chunked(AppDrawer.COLUMNS).forEachIndexed { row, items ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items.forEachIndexed { column, app ->
                            val index = row * AppDrawer.COLUMNS + column
                            AppTile(app, state.isFocused(Zones.APPS_GRID, index), Modifier.weight(1f)) {
                                events.tap(FocusSpot(Zones.APPS_GRID, index), true)
                            }
                        }
                        repeat(AppDrawer.COLUMNS - items.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            ActionBar(
                listOf(
                    BarAction("Open", glyph = "A") {
                        state.focus.current?.let { events.perform(dev.lunikodevelopment.ludoform.data.Screens.actionFor(state, it)) }
                    },
                    BarAction("Back", glyph = "B") { events.perform(UiAction.Back) },
                ),
                Modifier.fillMaxWidth().height(60.dp),
            )
        }
    }
}

@Composable
private fun AppTile(
    app: InstalledApp,
    focused: Boolean,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val colors = Ludo.colors
    Column(
        modifier
            .cookieFocus(focused, ExpressiveShapes.wavy, colors.lav.copy(alpha = 0.55f), outset = 4.dp, scale = 1.06f)
            .height(ROW_STEP.dp - 10.dp)
            .background(if (focused) colors.sf3 else colors.sf2, RoundedCornerShape(22.dp))
            .tap(app.label, onTap)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        AppIcon(app, 52.dp)
        Text(
            app.label,
            style = LudoType.caption,
            color = colors.on,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private const val ROW_STEP = 110
