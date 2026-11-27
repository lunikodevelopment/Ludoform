package dev.lunikodevelopment.ludoform.menu

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lunikodevelopment.ludoform.theme.ExpressiveShapes
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoIcon
import dev.lunikodevelopment.ludoform.theme.LudoIcons
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.cookieFocus
import kotlinx.coroutines.delay

/** The in-game menu over the running game, on the top screen. */
@Composable
fun InGameMenuOverlay(service: InGameMenuService) {
    val colors = Ludo.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(1180.dp).background(colors.bg, RoundedCornerShape(48.dp)).padding(horizontal = 56.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("In-game menu", style = LudoType.label, color = colors.on2)
                    Text(
                        service.game.title.ifEmpty { "Ludoform" },
                        style = LudoType.headline.copy(fontSize = 44.sp),
                        color = colors.on,
                        maxLines = 1,
                    )
                }
                service.game.startedAt?.let { started ->
                    Text(clock(now - started), style = LudoType.headline.copy(fontSize = 34.sp), color = colors.lav)
                }
            }
            when (service.panel) {
                MenuPanel.TILES -> {
                    InGameMenu.rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { item -> Tile(item, service, now, Modifier.weight(1f)) }
                            // A short row keeps the tile width of the full ones.
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Text(
                        service.message ?: InGameMenu.why(service.abilities) ?: "The game keeps running underneath.",
                        style = LudoType.chip,
                        color = colors.on2,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                        Legend("A", "Select")
                        Legend("B", "Resume")
                        Legend("◉◉", "Hold to close")
                    }
                }
                MenuPanel.ACHIEVEMENTS -> AchievementsPanel(service)
                MenuPanel.MANUAL -> ManualPanel(service)
            }
        }
    }
}

@Composable
private fun AchievementsPanel(service: InGameMenuService) {
    val colors = Ludo.colors
    val extras = service.extras ?: return
    Text(
        "Achievements · ${extras.earned} of ${extras.achievements.size} · ${extras.earnedPoints} of ${extras.points} points",
        style = LudoType.headline.copy(fontSize = 28.sp),
        color = colors.on,
    )
    val list = rememberLazyListState()
    LaunchedEffect(service.achievementIndex) { list.animateScrollToItem((service.achievementIndex - 2).coerceAtLeast(0)) }
    LazyColumn(Modifier.height(420.dp), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(extras.achievements) { index, achievement ->
            val focused = index == service.achievementIndex
            Row(
                Modifier.fillMaxWidth().height(72.dp)
                    .background(if (focused) colors.lav else colors.sf2, RoundedCornerShape(24.dp))
                    .padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val content = if (focused) colors.lavD else colors.on
                LudoIcon(
                    if (achievement.earned) LudoIcons.check else LudoIcons.lock,
                    size = 24.dp,
                    tint = if (achievement.earned && !focused) colors.ok else content,
                )
                Column(Modifier.weight(1f)) {
                    Text(achievement.title, style = LudoType.label, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        achievement.description,
                        style = LudoType.caption,
                        color = if (focused) colors.lavD else colors.on2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${achievement.points} pt" + if (achievement.earnedHardcore) " · hardcore" else "",
                    style = LudoType.label,
                    color = content,
                )
            }
        }
    }
    Text(
        "From RetroAchievements through RomM, as of when the game started.",
        style = LudoType.chip,
        color = colors.on2,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
        Legend("↕", "Scroll")
        Legend("B", "Back")
    }
}

@Composable
private fun ManualPanel(service: InGameMenuService) {
    val colors = Ludo.colors
    Box(Modifier.fillMaxWidth().height(560.dp).background(colors.sf2, RoundedCornerShape(24.dp)), Alignment.Center) {
        service.manualBitmap?.let { page ->
            Image(page.asImageBitmap(), "Manual page ${service.manualPage + 1}", Modifier.fillMaxSize().padding(12.dp))
        } ?: Text("This page couldn't be drawn.", style = LudoType.label, color = colors.on2)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Page ${service.manualPage + 1} of ${service.manualPages}", Modifier.weight(1f), style = LudoType.label, color = colors.on)
        Legend("◀ ▶", "Turn pages")
        Legend("B", "Back")
    }
}

@Composable
private fun Tile(
    item: MenuItem,
    service: InGameMenuService,
    now: Long,
    modifier: Modifier,
) {
    val colors = Ludo.colors
    val enabled = service.abilities.enabled(item)
    val focused = service.focused == item
    val container =
        when {
            focused -> colors.lav
            item == MenuItem.RESUME -> colors.org
            else -> colors.sf2
        }
    val content =
        when {
            focused -> colors.lavD
            item == MenuItem.RESUME -> colors.orgD
            enabled -> colors.on
            else -> colors.on2.copy(alpha = 0.45f)
        }
    Column(
        modifier.cookieFocus(focused, ExpressiveShapes.wavy, colors.lav, outset = 5.dp, scale = 1.04f)
            .height(104.dp)
            .background(container.copy(alpha = if (enabled) 1f else 0.5f), RoundedCornerShape(28.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LudoIcon(icon(item), size = 26.dp, tint = content)
        val recording = RecordService.startedAt
        val label = if (item == MenuItem.RECORD && recording != null) "Stop · " + clock(now - recording).removePrefix("00:") else item.label
        Text(label, style = LudoType.label, color = content, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun Legend(
    key: String,
    label: String,
) {
    val colors = Ludo.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(width = if (key.length > 1) 52.dp else 34.dp, height = 34.dp).background(colors.sf3, RoundedCornerShape(17.dp)),
            Alignment.Center,
        ) {
            Text(key, style = LudoType.label, color = colors.on)
        }
        Text(label, style = LudoType.label, color = colors.on2)
    }
}

private fun icon(item: MenuItem): ImageVector =
    when (item) {
        MenuItem.RESUME -> LudoIcons.play
        MenuItem.SCREENSHOT -> LudoIcons.camera
        MenuItem.RECORD -> LudoIcons.record
        MenuItem.HOME -> LudoIcons.home
        MenuItem.SYNC_SAVES -> LudoIcons.cloud
        MenuItem.ACHIEVEMENTS -> LudoIcons.star
        MenuItem.MANUAL -> LudoIcons.book
        MenuItem.VOLUME_DOWN, MenuItem.VOLUME_UP -> LudoIcons.volume
        MenuItem.BRIGHTNESS_DOWN, MenuItem.BRIGHTNESS_UP -> LudoIcons.sun
    }

/** `00:42:18`. */
fun clock(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
}
