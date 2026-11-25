package dev.lunikodevelopment.ludoform.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Touch on any stop: the screens decide whether a tap focuses or activates. */
fun Modifier.tap(
    label: String? = null,
    onTap: () -> Unit,
): Modifier = clickable(interactionSource = null, indication = null, onClickLabel = label, role = Role.Button, onClick = onTap)

fun Modifier.focusState(focused: Boolean): Modifier = semantics { if (focused) stateDescription = "Focused" }

@Composable
fun LudoIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = Color.Unspecified,
) {
    Icon(
        icon,
        contentDescription = null,
        modifier = modifier.size(size),
        tint = if (tint == Color.Unspecified) androidx.compose.material3.LocalContentColor.current else tint,
    )
}

/** A, B, X, Y glyph: a dark disc with the button letter, as on the boards' action bars. */
@Composable
fun GlyphBadge(
    letter: String,
    modifier: Modifier = Modifier,
    light: Boolean = false,
) {
    val colors = Ludo.colors
    Box(
        modifier.size(30.dp).background(if (light) colors.on else Color(0xFF16131B), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(letter, style = LudoType.glyph, color = if (light) Color(0xFF16131B) else Color.White) }
}

/** L1 / R1 key cap. */
@Composable
fun ShoulderBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.height(32.dp).sizeIn(minWidth = 52.dp).background(Ludo.colors.sf3, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = LudoType.chip.copy(fontWeight = androidx.compose.ui.text.font.FontWeight(700)), color = Ludo.colors.on) }
}

/** Section heading with its small expressive shape. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    shape: Shape = ExpressiveShapes.clover,
    color: Color = Ludo.colors.org,
    style: TextStyle = LudoType.section,
    markSize: Dp = 20.dp,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(markSize).background(color, shape))
        Text(text, style = style, color = Ludo.colors.on, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** LUDOFORM wordmark with its clover (or flower on the console picker). */
@Composable
fun Brand(
    modifier: Modifier = Modifier,
    markColor: Color = Ludo.colors.org,
    textColor: Color = Ludo.colors.on,
    shape: Shape = ExpressiveShapes.clover,
    markSize: Dp = 24.dp,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(markSize).background(markColor, shape))
        Text("LUDOFORM", style = LudoType.brand, color = textColor)
    }
}

/** Stadium chip; `rd` on the boards. Selected chips fill lavender. */
@Composable
fun Chip(
    text: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    focused: Boolean = false,
    round: Boolean = true,
    container: Color = Ludo.colors.sf2,
    content: Color = Ludo.colors.on,
    height: Dp = 40.dp,
    onTap: () -> Unit = {},
) {
    val colors = Ludo.colors
    Row(
        modifier
            .cookieFocus(focused, ExpressiveShapes.wavy, colors.lav.copy(alpha = 0.55f), outset = 5.dp, scale = 1.06f)
            .focusState(focused)
            .height(height)
            .clip(if (round) CircleShape else RoundedCornerShape(14.dp))
            .background(if (selected) colors.lav else container)
            .tap(text, onTap)
            .padding(horizontal = if (text == null) 10.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        val tint = if (selected) colors.lavD else content
        if (icon != null) LudoIcon(icon, size = 18.dp, tint = tint)
        if (text != null) Text(text, style = LudoType.chip, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Round "more" button that ends a Home rail. */
@Composable
fun MoreButton(
    focused: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    Box(
        modifier
            .cookieFocus(focused, ExpressiveShapes.sunny, Ludo.colors.lav.copy(alpha = 0.55f), outset = 6.dp)
            .focusState(focused)
            .size(40.dp)
            .background(Ludo.colors.sf2, CircleShape)
            .tap("More", onTap),
        contentAlignment = Alignment.Center,
    ) { LudoIcon(LudoIcons.chevron, tint = Ludo.colors.on) }
}

/** The boards' `pbtn`: a tall orange (or lavender) stadium. */
@Composable
fun PrimaryPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    glyph: String? = null,
    container: Color = Ludo.colors.org,
    content: Color = Ludo.colors.orgD,
    height: Dp = 56.dp,
    style: TextStyle = LudoType.button,
    focused: Boolean = false,
    padding: PaddingValues = PaddingValues(horizontal = 26.dp),
    focusScale: Float = 1.04f,
    onTap: () -> Unit = {},
) {
    Row(
        modifier
            .cookieFocus(focused, ExpressiveShapes.wavyLarge, Ludo.colors.lav, outset = 7.dp, scale = focusScale)
            .focusState(focused)
            .height(height)
            .clip(CircleShape)
            .background(container)
            .tap(text, onTap)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        if (glyph != null) GlyphBadge(glyph)
        if (icon != null) LudoIcon(icon, tint = content)
        Text(text, style = style, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

data class BarAction(val label: String, val glyph: String? = null, val icon: ImageVector? = null, val onTap: () -> Unit)

/** Connected lavender button bar with thin dividers (`.bar`). It mirrors the gamepad and also works by touch. */
@Composable
fun ActionBar(
    actions: List<BarAction>,
    modifier: Modifier = Modifier,
) {
    val colors = Ludo.colors
    Row(
        modifier.fillMaxWidth().height(60.dp).clip(CircleShape).background(colors.lav),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEachIndexed { index, action ->
            if (index > 0) Box(Modifier.width(1.5.dp).height(32.dp).background(colors.lavD.copy(alpha = 0.3f)))
            Row(
                Modifier.weight(1f).fillMaxHeight().tap(action.label, action.onTap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            ) {
                if (action.glyph != null) GlyphBadge(action.glyph)
                if (action.icon != null) LudoIcon(action.icon, tint = colors.lavD)
                Text(action.label, style = LudoType.bar, color = colors.lavD, maxLines = 1)
            }
        }
    }
}

/** Connected tonal button group with dividers, icon above label (`.grp` on the details board). */
@Composable
fun ButtonGroup(
    items: List<Pair<String, ImageVector>>,
    focusedIndex: Int?,
    modifier: Modifier = Modifier,
    onTap: (Int) -> Unit,
) {
    val colors = Ludo.colors
    Row(modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFF3A3343))) {
        items.forEachIndexed { index, (label, icon) ->
            val focused = focusedIndex == index
            if (index > 0) Box(Modifier.width(1.5.dp).padding(vertical = 12.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.14f)))
            Column(
                Modifier.weight(
                    1f,
                ).fillMaxHeight().focusState(focused).background(if (focused) colors.lav else Color.Transparent).tap(label) {
                    onTap(index)
                },
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val tint = if (focused) colors.lavD else colors.on
                LudoIcon(icon, size = 20.dp, tint = tint)
                Text(label, style = LudoType.label, color = tint, maxLines = 1)
            }
        }
    }
}

/** Stage navigation tabs; the active tab fills lavender, the focused tab sits on a cookie. */
@Composable
fun StageTabs(
    labels: List<Pair<String, ImageVector?>>,
    active: Int,
    focused: Int?,
    modifier: Modifier = Modifier,
    onTap: (Int) -> Unit,
) {
    val colors = Ludo.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, (label, icon) ->
            val isActive = index == active
            val isFocused = index == focused
            val container =
                when {
                    isActive -> colors.lav
                    isFocused -> colors.sf3
                    else -> Color.Transparent
                }
            Row(
                Modifier
                    .cookieFocus(isFocused, ExpressiveShapes.cookie9, colors.lav, outset = 8.dp)
                    .focusState(isFocused)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(container)
                    .tap(label) { onTap(index) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val tint =
                    if (isActive) {
                        colors.lavD
                    } else if (isFocused) {
                        colors.on
                    } else {
                        colors.on2
                    }
                if (icon != null) LudoIcon(icon, size = 18.dp, tint = tint)
                Text(label, style = LudoType.tab, color = tint)
            }
        }
    }
}

/** Artwork tile with an optional title over the art (`.cov` / `.tl`). */
@Composable
fun CoverTile(
    art: Painter,
    title: String?,
    modifier: Modifier = Modifier,
    focused: Boolean = false,
    corner: Dp = 22.dp,
    titleStyle: TextStyle = LudoType.coverTitle,
    haloShape: Shape = ExpressiveShapes.wavyLarge,
    haloOutset: Dp = 12.dp,
    scale: Float = 1.04f,
    alignment: Alignment = Alignment.Center,
    /** A game that is only on the RomM server: faded art and a cloud badge. */
    dimmed: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
    onTap: () -> Unit,
) {
    Box(
        modifier
            .cookieFocus(focused, haloShape, Ludo.colors.lav, outset = haloOutset, scale = scale)
            .focusState(focused)
            .clip(RoundedCornerShape(corner))
            .background(Ludo.colors.sf2)
            .tap(title, onTap),
    ) {
        Image(
            art,
            null,
            Modifier.fillMaxSize().tonalLift(focused).then(if (dimmed) Modifier.alpha(0.38f) else Modifier),
            contentScale = ContentScale.Crop,
            alignment = alignment,
        )
        if (title != null) {
            Box(
                Modifier.fillMaxSize().background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0x8C000000)),
                ),
            )
            Text(
                title,
                style = titleStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 10.dp, bottom = 12.dp),
            )
        }
        if (dimmed) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).background(Ludo.colors.sf3.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { LudoIcon(LudoIcons.cloud, size = 16.dp, tint = Ludo.colors.on) }
        }
        overlay()
    }
}

/** Corner blob decoration. */
@Composable
fun Blob(
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = ExpressiveShapes.blob,
) {
    Box(modifier.background(color, shape))
}

/** Rounded list row; focused rows fill lavender with a soft ring (`.li` / `.fr`). */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    focused: Boolean = false,
    onTap: () -> Unit,
) {
    val colors = Ludo.colors
    val ring = remember { RoundedCornerShape(50) }
    Row(
        modifier
            .focusState(focused)
            .then(if (focused) Modifier.background(colors.lav.copy(alpha = 0.24f), ring).padding(4.dp) else Modifier.padding(4.dp))
            .clip(ring)
            .background(if (focused) colors.lav else colors.sf2)
            .tap(title, onTap)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .sizeIn(minHeight = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val tint = if (focused) colors.lavD else colors.on
        if (icon != null) LudoIcon(icon, tint = tint)
        Column(Modifier.weight(1f)) {
            Text(title, style = LudoType.label, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = LudoType.caption,
                    color = if (focused) Color(0xFF3B2A6E) else colors.on2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun RowScope.Grow() {
    Spacer(Modifier.weight(1f))
}
