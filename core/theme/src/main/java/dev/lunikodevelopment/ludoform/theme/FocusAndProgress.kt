package dev.lunikodevelopment.ludoform.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun WavyProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val fraction = progress.coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val active = MaterialTheme.colorScheme.primary
    Canvas(
        modifier.fillMaxWidth().height(Dimens.progressHeight).semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
        },
    ) {
        val stroke = Dimens.progressStroke.toPx()
        val amplitude = Dimens.progressAmplitude.toPx()
        val wavelength = Dimens.progressWavelength.toPx()

        fun wave(end: Float): Path =
            Path().apply {
                moveTo(0f, size.height / 2)
                var x = 0f
                while (x < end) {
                    lineTo(x, size.height / 2 + amplitude * sin((2 * PI * x / wavelength).toFloat()))
                    x += 1f
                }
                lineTo(end, size.height / 2 + amplitude * sin((2 * PI * end / wavelength).toFloat()))
            }
        drawPath(wave(size.width), track, style = Stroke(stroke, cap = StrokeCap.Round))
        if (fraction > 0f) drawPath(wave(size.width * fraction), active, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
