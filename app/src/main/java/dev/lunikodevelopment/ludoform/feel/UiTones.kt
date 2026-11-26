package dev.lunikodevelopment.ludoform.feel

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Ludoform's UI sounds (D47), made from sine tones at run time so the app ships no audio files. Pure, so it is tested. */
enum class UiTone { MOVE, SELECT, BACK }

object UiTones {
    const val SAMPLE_RATE = 44_100

    /** 16-bit mono samples for [tone]: a short tick for moving, a rising pair for selecting, a falling pair for back. */
    fun pcm(tone: UiTone): ShortArray =
        when (tone) {
            UiTone.MOVE -> blip(listOf(1760.0), 0.022, 0.18)
            UiTone.SELECT -> blip(listOf(880.0, 1320.0), 0.045, 0.22)
            UiTone.BACK -> blip(listOf(1320.0, 880.0), 0.045, 0.2)
        }

    private fun blip(
        frequencies: List<Double>,
        secondsEach: Double,
        volume: Double,
    ): ShortArray {
        val each = (SAMPLE_RATE * secondsEach).toInt()
        val out = ShortArray(each * frequencies.size)
        frequencies.forEachIndexed { part, frequency ->
            for (i in 0 until each) {
                val t = i.toDouble() / SAMPLE_RATE
                // A 2 ms fade-in avoids a click; the decay keeps it soft.
                val attack = (i / (SAMPLE_RATE * 0.002)).coerceAtMost(1.0)
                val envelope = attack * exp(-t / (secondsEach / 3))
                out[part * each + i] = (sin(2 * PI * frequency * t) * envelope * volume * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out
    }
}
