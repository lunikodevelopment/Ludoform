package dev.lunikodevelopment.ludoform.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Clover Cushion: a gentle overshoot that settles in about 280 ms. */
val CloverCushion = spring<Float>(dampingRatio = 0.62f, stiffness = 420f)

private data class Backing(val bounds: Rect, val shape: Shape, val color: Color)

private class CookieHostState {
    var target by mutableStateOf<Backing?>(null)
    var owner: Any? = null
    var current by mutableStateOf<Backing?>(null)
    var previous by mutableStateOf<Backing?>(null)
    val left = Animatable(0f)
    val top = Animatable(0f)
    val right = Animatable(0f)
    val bottom = Animatable(0f)
    val shown = Animatable(0f)
    val fade = Animatable(1f)
}

private val LocalCookieHost = staticCompositionLocalOf<CookieHostState?> { null }

private fun DrawScope.drawBacking(
    rect: Rect,
    shape: Shape,
    color: Color,
    alpha: Float,
) {
    if (alpha <= 0.01f || rect.width <= 0f || rect.height <= 0f) return
    translate(rect.left, rect.top) {
        drawOutline(shape.createOutline(Size(rect.width, rect.height), layoutDirection, this), color, alpha = alpha)
    }
}

/**
 * Traveling Cookie: one tonal backing per screen that springs from the previously focused item to the next one,
 * cross-fading its shape when the zones use different shapes. Place one host at the root of each screen, and a
 * [CookieBacking] layer in each screen above its artwork and below its controls.
 */
@Composable
fun CookieHost(content: @Composable () -> Unit) {
    val state = remember { CookieHostState() }
    val target = state.target
    LaunchedEffect(target) {
        if (target == null) {
            state.shown.animateTo(0f, tween(140))
            return@LaunchedEffect
        }
        val wasHidden = state.shown.value < 0.05f || state.current == null
        if (state.current?.shape != target.shape || state.current?.color != target.color) {
            state.previous = if (wasHidden) null else state.current
            state.current = target
            state.fade.snapTo(if (wasHidden) 1f else 0f)
        } else {
            state.current = target
        }
        val rect = target.bounds
        if (wasHidden) {
            state.left.snapTo(rect.left)
            state.top.snapTo(rect.top)
            state.right.snapTo(rect.right)
            state.bottom.snapTo(rect.bottom)
        }
        coroutineScope {
            launch { state.shown.animateTo(1f, tween(160)) }
            launch { state.fade.animateTo(1f, tween(220)) }
            launch { state.left.animateTo(rect.left, CloverCushion) }
            launch { state.top.animateTo(rect.top, CloverCushion) }
            launch { state.right.animateTo(rect.right, CloverCushion) }
            launch { state.bottom.animateTo(rect.bottom, CloverCushion) }
        }
    }
    CompositionLocalProvider(LocalCookieHost provides state, content = content)
}

/** Draws the host's traveling backing. Place it after a screen's background art so the cookie stays visible. */
@Composable
fun CookieBacking(modifier: Modifier = Modifier) {
    val state = LocalCookieHost.current ?: return
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawBehind {
                val rect = Rect(state.left.value, state.top.value, state.right.value, state.bottom.value).translate(-origin)
                state.previous?.let { drawBacking(rect, it.shape, it.color, state.shown.value * (1f - state.fade.value)) }
                state.current?.let { drawBacking(rect, it.shape, it.color, state.shown.value * state.fade.value) }
            },
    )
}

/**
 * Marks an item as a Traveling Cookie stop. When [focused], the host's backing travels behind it, [outset] beyond its
 * scaled edge, and the item grows to [scale]. Without a host, the item draws its own backing.
 */
@Composable
fun Modifier.cookieFocus(
    focused: Boolean,
    shape: Shape = ExpressiveShapes.cookie9,
    color: Color = Ludo.colors.lav,
    outset: Dp = 10.dp,
    scale: Float = 1.08f,
): Modifier {
    val host = LocalCookieHost.current
    val identity = remember { Any() }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(host, focused, bounds, shape, color) {
        val b = bounds
        if (host == null) return@LaunchedEffect
        if (focused && b != null) {
            host.owner = identity
            host.target = Backing(b, shape, color)
        } else if (host.owner === identity) {
            host.owner = null
            host.target = null
        }
    }
    DisposableEffect(host) {
        onDispose {
            if (host?.owner === identity) {
                host.owner = null
                host.target = null
            }
        }
    }
    val grow by animateFloatAsState(if (focused) scale else 1f, CloverCushion, label = "cookie scale")
    val local by animateFloatAsState(
        if (focused && host == null) 1f else 0f,
        spring(stiffness = Spring.StiffnessMedium),
        label = "local backing",
    )
    val pad = with(LocalDensity.current) { outset.toPx() }
    return this
        .onGloballyPositioned {
            val p = it.positionInRoot()
            val extraX = it.size.width * (scale - 1f) / 2f
            val extraY = it.size.height * (scale - 1f) / 2f
            bounds = Rect(p.x - extraX - pad, p.y - extraY - pad, p.x + it.size.width + extraX + pad, p.y + it.size.height + extraY + pad)
        }
        .drawBehind {
            if (local > 0f) {
                drawBacking(Rect(-pad, -pad, size.width + pad, size.height + pad), shape, color, local)
            }
        }
        .graphicsLayer {
            scaleX = grow
            scaleY = grow
        }
}

/** Tonal Lift: focused artwork shifts to a warmer, brighter tone. */
@Composable
fun Modifier.tonalLift(focused: Boolean): Modifier {
    val lift by animateFloatAsState(if (focused) 1f else 0f, CloverCushion, label = "tonal lift")
    val warm = Ludo.colors.org
    return drawWithContent {
        drawContent()
        if (lift > 0f) drawRect(warm.copy(alpha = 0.14f * lift), blendMode = BlendMode.Screen)
    }
}
