package dev.lunikodevelopment.ludoform.feel

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.VibrationEffect
import android.os.VibratorManager

/**
 * Haptics and UI sounds (board 11, D47). Each is off until the player turns it on. Sounds play as sonification, so
 * Android's media volume does not apply but muting does.
 */
class UiFeedback(context: Context) {
    private val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    private val tracks = mutableMapOf<UiTone, AudioTrack>()

    val canVibrate: Boolean get() = vibrator?.hasVibrator() == true

    fun play(
        tone: UiTone,
        haptics: Boolean,
        sounds: Boolean,
    ) {
        if (haptics) vibrate(tone)
        if (sounds) sound(tone)
    }

    private fun vibrate(tone: UiTone) {
        val vibrator = vibrator ?: return
        if (!vibrator.hasVibrator()) return
        val effect = if (tone == UiTone.MOVE) VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_CLICK
        runCatching { vibrator.vibrate(VibrationEffect.createPredefined(effect)) }
    }

    private fun sound(tone: UiTone) {
        val track = tracks.getOrPut(tone) { build(UiTones.pcm(tone)) }
        runCatching {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    private fun build(samples: ShortArray): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(UiTones.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(samples.size * 2)
            .build()
            .also { it.write(samples, 0, samples.size) }

    fun release() {
        tracks.values.forEach { runCatching { it.release() } }
        tracks.clear()
    }
}
