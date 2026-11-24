package dev.lunikodevelopment.ludoform.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** A shape from a normalized polygon, stretched to the item's bounds like the boards' clip-paths. */
class PolygonShape internal constructor(val polygon: RoundedPolygon) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = polygon.toPath().asComposePath()
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

/**
 * The boards' wavy outline: a rounded rectangle whose straight edges ripple inward by [amplitude], with a fixed
 * number of waves per edge so short and long items read alike.
 */
class WavyRectShape(
    private val corner: Dp,
    private val amplitude: Dp,
    private val wavesPerEdge: Int,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val r = min(with(density) { corner.toPx() }, min(size.width, size.height) / 2)
        val a = with(density) { amplitude.toPx() }
        val path = Path()

        fun edge(
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            nx: Float,
            ny: Float,
        ) {
            val steps = 48
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val inset = a * (1f - sin(2f * PI.toFloat() * wavesPerEdge * t + PI.toFloat() / 2)) / 2f
                val x = x0 + (x1 - x0) * t + nx * inset
                val y = y0 + (y1 - y0) * t + ny * inset
                if (i == 0 && path.isEmpty) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }
        val w = size.width
        val h = size.height
        edge(r, 0f, w - r, 0f, 0f, 1f)
        path.quadraticTo(w, 0f, w, r)
        edge(w, r, w, h - r, -1f, 0f)
        path.quadraticTo(w, h, w - r, h)
        edge(w - r, h, r, h, 0f, -1f)
        path.quadraticTo(0f, h, 0f, h - r)
        edge(0f, h - r, 0f, r, 1f, 0f)
        path.quadraticTo(0f, 0f, r, 0f)
        path.close()
        return Outline.Generic(path)
    }
}

private fun polygon(points: FloatArray) = PolygonShape(RoundedPolygon(vertices = points))

internal fun morphShape(
    from: Shape,
    to: Shape,
    progress: Float,
): Shape {
    if (from !is PolygonShape || to !is PolygonShape) return to
    val morph = Morph(from.polygon, to.polygon)
    return object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline {
            val path = morph.toPath(progress = progress).asComposePath()
            path.transform(Matrix().apply { scale(size.width, size.height) })
            return Outline.Generic(path)
        }
    }
}

/** Shapes traced from `docs/design/boards/ludoform.css`. */
object ExpressiveShapes {
    val cookie9: Shape = polygon(BoardPolygons.cookie9)
    val sunny: Shape = polygon(BoardPolygons.sunny)
    val flower8: Shape = polygon(BoardPolygons.flower8)
    val clover: Shape = polygon(BoardPolygons.clover)
    val blob: Shape = polygon(BoardPolygons.blob)
    val wavy: Shape = WavyRectShape(corner = 30.dp, amplitude = 6.dp, wavesPerEdge = 4)
    val wavyLarge: Shape = WavyRectShape(corner = 44.dp, amplitude = 10.dp, wavesPerEdge = 6)
}
