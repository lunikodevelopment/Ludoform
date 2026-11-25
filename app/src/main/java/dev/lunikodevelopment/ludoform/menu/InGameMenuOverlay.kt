package dev.lunikodevelopment.ludoform.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lunikodevelopment.ludoform.theme.ExpressiveShapes
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoIcon
import dev.lunikodevelopment.ludoform.theme.LudoIcons
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.cookieFocus
import kotlinx.coroutines.delay

/** Board 12: the in-game menu over the running game, on the top screen. */
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
            InGameMenu.items.chunked(InGameMenu.COLUMNS).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { item -> Tile(item, service, Modifier.weight(1f)) }
                }
            }
            Text(
                service.message ?: "The game keeps running underneath. Items that can't work yet are greyed out.",
                style = LudoType.chip,
                color = colors.on2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend("A", "Select")
                Legend("B", "Resume")
                Legend("◉◉", "Hold to close")
            }
        }
    }
}

@Composable
private fun Tile(
    item: MenuItem,
    service: InGameMenuService,
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
        Text(item.label, style = LudoType.label, color = content, textAlign = TextAlign.Center, maxLines = 1)
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
        MenuItem.SCREENSHOT -> LudoIcons.sparkle
        MenuItem.RECORD -> LudoIcons.clock
        MenuItem.HOME -> LudoIcons.home
        MenuItem.SWAP_SCREENS -> LudoIcons.swap
        MenuItem.SYNC_SAVES -> LudoIcons.cloud
        MenuItem.ACHIEVEMENTS -> LudoIcons.star
        MenuItem.MANUAL -> LudoIcons.info
        MenuItem.VOLUME_DOWN, MenuItem.VOLUME_UP -> LudoIcons.more
        MenuItem.BRIGHTNESS_DOWN, MenuItem.BRIGHTNESS_UP -> LudoIcons.sparkle
    }

/** `00:42:18`. */
fun clock(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    return "%02d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
}
