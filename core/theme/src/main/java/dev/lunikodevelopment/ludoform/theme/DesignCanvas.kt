package dev.lunikodevelopment.ludoform.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Lays out a board at its design size (960 × 540 for the Stage, 620 × 540 for the Deck) and scales it to the window.
 * On the Thor both windows measure 2 px per design unit, which the boards assume; the measured 369 dpi would make one
 * dp 2.31 px and overflow the board, so dp inside a canvas means design unit (D19). Text is not scaled by the system
 * font size inside a canvas, so the boards' proportions hold.
 */
@Composable
fun DesignCanvas(
    designWidth: Float,
    designHeight: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val pxPerUnit = min(constraints.maxWidth / designWidth, constraints.maxHeight / designHeight)
        CompositionLocalProvider(LocalDensity provides Density(pxPerUnit, 1f)) {
            Box(Modifier.requiredSize(designWidth.dp, designHeight.dp), content = content)
        }
    }
}

object Board {
    const val STAGE_WIDTH = 960f
    const val DECK_WIDTH = 620f
    const val HEIGHT = 540f
}
