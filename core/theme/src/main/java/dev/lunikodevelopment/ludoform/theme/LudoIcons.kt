package dev.lunikodevelopment.ludoform.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

private fun rect(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    r: Float,
): String {
    val arc = "a$r $r 0 0 1"
    return "M${x + r} ${y}h${w - 2 * r}$arc $r ${r}v${h - 2 * r}$arc -$r ${r}h-${w - 2 * r}$arc -$r -${r}v-${h - 2 * r}$arc $r -${r}z"
}

private fun circle(
    cx: Float,
    cy: Float,
    r: Float,
) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 -${2 * r} 0z"

private fun icon(
    name: String,
    vararg paths: String,
    filled: Boolean = false,
): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { data ->
            addPath(
                pathData = PathParser().parsePathString(data).toNodes(),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = if (filled) null else SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

/** Stroke icons traced from the boards' inline SVGs (24-unit grid, 2-unit round strokes). */
object LudoIcons {
    val play = icon("play", "M7 5l12 7-12 7z", filled = true)
    val dropdown = icon("dropdown", "M7 10l5 5 5-5z", filled = true)
    val battery = icon("battery", rect(2f, 7f, 18f, 10f, 2f), "M22 11v2")
    val wifi = icon("wifi", "M2 9a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0")
    val wifiOff = icon("wifiOff", "M8.5 16a5 5 0 0 1 7 0", "M3 3l18 18")
    val star = icon("star", "M12 3l2.7 5.6 6.1.8-4.5 4.2 1.1 6.1L12 16.8 6.6 19.7l1.1-6.1L3.2 9.4l6.1-.8z")
    val heart = icon("heart", "M12 20s-7.5-4.6-9.2-9.4A5 5 0 0 1 12 6.2a5 5 0 0 1 9.2 4.4C19.5 15.4 12 20 12 20z")
    val heartFilled =
        icon("heartFilled", "M12 20s-7.5-4.6-9.2-9.4A5 5 0 0 1 12 6.2a5 5 0 0 1 9.2 4.4C19.5 15.4 12 20 12 20z", filled = true)
    val record = icon("record", circle(12f, 12f, 8f), circle(12f, 12f, 3.5f))
    val camera = icon("camera", rect(3f, 7f, 18f, 13f, 3f), circle(12f, 13.5f, 3.5f), "M8 7l1.5-3h5L16 7")
    val sun =
        icon(
            "sun",
            circle(12f, 12f, 4f),
            "M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4",
        )
    val volume = icon("volume", "M4 10v4h4l5 4V6L8 10z", "M16.5 9a4 4 0 0 1 0 6M19 6.5a8 8 0 0 1 0 11")
    val book = icon("book", "M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2z", "M4 21V5", "M8 7h7")
    val layers = icon("layers", "M12 3l9 5-9 5-9-5zM3 13l9 5 9-5")
    val chevron = icon("chevron", "M9 6l6 6-6 6")
    val settings =
        icon("settings", circle(12f, 12f, 3f), "M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1")
    val folder = icon("folder", "M3 6a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z")
    val gamepad = icon("gamepad", rect(2f, 7f, 20f, 11f, 4f), "M7 11v3M5.5 12.5h3M16 12h.01M18 13h.01")
    val cloud = icon("cloud", "M7 18h10a4 4 0 0 0 .5-8 6 6 0 0 0-11.5 1.5A3.5 3.5 0 0 0 7 18z")
    val cloudOff = icon("cloudOff", "M7 18h10a4 4 0 0 0 .5-8 6 6 0 0 0-11.5 1.5A3.5 3.5 0 0 0 7 18z", "M4 4l16 16")
    val console = icon("console", rect(3f, 5f, 18f, 13f, 2f), "M3 9h18")
    val grid =
        icon(
            "grid",
            rect(4f, 4f, 7f, 7f, 1.5f),
            rect(13f, 4f, 7f, 7f, 1.5f),
            rect(4f, 13f, 7f, 7f, 1.5f),
            rect(13f, 13f, 7f, 7f, 1.5f),
        )
    val clock = icon("clock", circle(12f, 12f, 8f), "M12 8v4l3 2")
    val check = icon("check", "M5 12l5 5 9-10")
    val more = icon("more", circle(5f, 12f, 1f), circle(12f, 12f, 1f), circle(19f, 12f, 1f))
    val swap = icon("swap", rect(2f, 5f, 12f, 9f, 2f), rect(10f, 10f, 12f, 9f, 2f))
    val download = icon("download", "M12 4v11M7 10l5 5 5-5M5 20h14")
    val upload = icon("upload", "M12 15V4M7 9l5-5 5 5M5 20h14")
    val chip = icon("chip", rect(7f, 7f, 10f, 10f, 2f), "M9 3v4M15 3v4M9 17v4M15 17v4")
    val screens = icon("screens", rect(4f, 4f, 16f, 7f, 2f), rect(4f, 13f, 16f, 7f, 2f))
    val info = icon("info", circle(12f, 12f, 9f), "M12 8h.01M11 12h1v5h1")
    val back = icon("back", "M15 6l-6 6 6 6")
    val home = icon("home", "M4 11l8-7 8 7M6 10v9h12v-9")
    val refresh = icon("refresh", "M20 12a8 8 0 1 1-2.3-5.6M20 4v4h-4")
    val sortAz = icon("sortAz", "M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01")
    val sparkle = icon("sparkle", "M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5L18 18M6 18l2.5-2.5M15.5 8.5L18 6")
    val close = icon("close", "M6 6l12 12M18 6L6 18")
    val pause = icon("pause", "M9 6v12M15 6v12")
    val lock = icon("lock", rect(5f, 11f, 14f, 9f, 2f), "M8 11V8a4 4 0 0 1 8 0v3")
}
