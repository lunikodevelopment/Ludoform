package dev.lunikodevelopment.ludoform.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

private class CookieState {
    var origin by mutableStateOf(Offset.Zero)
    var target by mutableStateOf<Rect?>(null)
    var visible by mutableStateOf(false)
    var owner: Any? = null
    var shape by mutableStateOf<Shape>(ExpressiveShapes.cookie9)
}

private val LocalCookieState = staticCompositionLocalOf<CookieState?> { null }

/** Place one host around a group of focusable items to share the animated backing. */
@Composable
fun TravelingCookieHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = remember { CookieState() }
    val density = LocalDensity.current
    var fromShape by remember { mutableStateOf(state.shape) }
    var toShape by remember { mutableStateOf(state.shape) }
    val morph = remember { Animatable(1f) }
    LaunchedEffect(state.shape) {
        if (toShape != state.shape) {
            fromShape = toShape
            toShape = state.shape
            morph.snapTo(0f)
            morph.animateTo(1f, spring(stiffness = 500f))
        }
    }
    val backingShape =
        remember(fromShape, toShape, morph.value) {
            morphShape(fromShape, toShape, morph.value.coerceIn(0f, 1f))
        }
    val target = state.target
    val relative = target?.translate(-state.origin)
    val animation = spring<androidx.compose.ui.unit.Dp>(stiffness = 500f)
    val x by animateDpAsState(with(density) { (relative?.left ?: 0f).toDp() }, animation, label = "cookie x")
    val y by animateDpAsState(with(density) { (relative?.top ?: 0f).toDp() }, animation, label = "cookie y")
    val width by animateDpAsState(with(density) { (relative?.width ?: 0f).toDp() }, animation, label = "cookie width")
    val height by animateDpAsState(with(density) { (relative?.height ?: 0f).toDp() }, animation, label = "cookie height")
    val opacity by animateFloatAsState(if (state.visible) 1f else 0f, spring(stiffness = 500f), label = "cookie opacity")
    Box(modifier.onGloballyPositioned { state.origin = it.positionInRoot() }) {
        if (width > 0.dp && height > 0.dp) {
            Box(
                Modifier.offset { IntOffset(x.roundToPx(), y.roundToPx()) }
                    .size(width, height)
                    .scale(1.08f)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = opacity), backingShape),
            )
        }
        CompositionLocalProvider(LocalCookieState provides state, content = content)
    }
}

/** A host shares the backing; without one, an item receives a local backing. */
@Composable
fun Modifier.travelingCookie(
    focused: Boolean,
    shape: Shape = ExpressiveShapes.cookie9,
): Modifier {
    val host = LocalCookieState.current
    val identity = remember { Any() }
    var bounds by remember { mutableStateOf<Rect?>(null) }
    LaunchedEffect(host, focused, bounds, shape) {
        if (focused && bounds != null && host != null) {
            host.owner = identity
            host.shape = shape
            host.target = bounds
            host.visible = true
        } else if (host?.owner === identity) {
            host.owner = null
            host.visible = false
        }
    }
    DisposableEffect(host) {
        onDispose {
            if (host?.owner === identity) {
                host.owner = null
                host.visible = false
            }
        }
    }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, spring(stiffness = 500f), label = "focus scale")
    val backing by animateFloatAsState(if (focused && host == null) 1f else 0f, spring(stiffness = 500f), label = "focus backing")
    return this.onGloballyPositioned {
        val position = it.positionInRoot()
        bounds = Rect(position.x, position.y, position.x + it.size.width, position.y + it.size.height)
    }.scale(scale).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = backing), shape)
}
