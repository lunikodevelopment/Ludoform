package dev.lunikodevelopment.ludoform.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalTextApi::class)
private fun flex(weight: Int) =
    Font(
        R.font.roboto_flex,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

/** Roboto Flex, bundled as one variable font; each weight is a variation instance. */
val RobotoFlex = FontFamily(flex(400), flex(500), flex(600), flex(700), flex(800), flex(900), flex(1000))

private fun style(
    size: Int,
    weight: Int,
    line: Int = (size * 1.25f).toInt(),
    spacing: TextUnit = 0.sp,
) = TextStyle(
    fontFamily = RobotoFlex,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = spacing,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/**
 * Type roles of the boards. Sizes are in design units: inside a [DesignCanvas] one sp is one board pixel.
 */
object LudoType {
    val display = style(66, 900, 62, (-0.01).em)
    val consoleTitle = style(76, 800, 84)
    val headline = style(40, 800, 46)
    val sub = style(22, 400, 30)
    val body = style(19, 400, 26)
    val brand = style(17, 800, 22, 0.06.em)
    val tab = style(15, 600, 20)
    val status = style(15, 600, 20)
    val eyebrow = style(12, 800, 16, 0.18.em)
    val stageLabel = style(15, 800, 20, 0.3.em)
    val button = style(20, 800, 24)
    val section = style(18, 700, 24)
    val sectionLarge = style(24, 700, 30)
    val deckTitle = style(23, 700, 28)
    val chip = style(15, 600, 20)
    val label = style(16, 600, 20)
    val caption = style(13, 500, 18)
    val bar = style(20, 700, 24)
    val glyph = style(15, 800, 18)
    private val artShadow = Shadow(Color(0x73000000), blurRadius = 8f)
    val coverTitle = style(19, 800, 21).copy(color = Color.White, shadow = artShadow)
    val tileTitle = style(15, 800, 16).copy(color = Color.White, shadow = artShadow)
}
