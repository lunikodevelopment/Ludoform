package dev.lunikodevelopment.ludoform.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun radialShape(
    lobes: Int,
    innerRadius: Float,
    rotation: Float = 0f,
): Shape {
    val vertices = FloatArray(lobes * 4)
    for (i in 0 until lobes * 2) {
        val angle = 2.0 * PI * i / (lobes * 2) + rotation
        val radius = if (i % 2 == 0) 0.5f else innerRadius
        vertices[i * 2] = 0.5f + radius * cos(angle).toFloat()
        vertices[i * 2 + 1] = 0.5f + radius * sin(angle).toFloat()
    }
    return PolygonShape(RoundedPolygon(vertices = vertices, rounding = CornerRounding(0.10f)))
}

private class PolygonShape(val polygon: RoundedPolygon) : Shape {
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

private fun wavyShape(): Shape {
    val points = mutableListOf<Float>()
    val steps = 24

    fun add(
        x: Float,
        y: Float,
    ) {
        points.add(x)
        points.add(y)
    }
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        add(0.08f + 0.84f * t, 0.025f + 0.025f * sin(4f * PI.toFloat() * t))
    }
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        add(0.975f - 0.025f * sin(4f * PI.toFloat() * t), 0.08f + 0.84f * t)
    }
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        add(0.92f - 0.84f * t, 0.975f - 0.025f * sin(4f * PI.toFloat() * t))
    }
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        add(0.025f + 0.025f * sin(4f * PI.toFloat() * t), 0.92f - 0.84f * t)
    }
    return PolygonShape(RoundedPolygon(vertices = points.toFloatArray()))
}

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

object ExpressiveShapes {
    val cookie9 = radialShape(9, 0.43f)
    val sunny = radialShape(12, 0.37f)
    val flower8 = radialShape(8, 0.31f)
    val clover = radialShape(4, 0.30f, (PI / 4).toFloat())
    val blob = radialShape(5, 0.39f, 0.2f)
    val wavy = wavyShape()
}
