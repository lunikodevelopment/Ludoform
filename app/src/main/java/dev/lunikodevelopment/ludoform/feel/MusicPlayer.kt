package dev.lunikodevelopment.ludoform.feel

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

/**
 * Background music : the audio file the player chose, looped quietly while Ludoform is in front and
 * no game runs. A file that can't be played is reported once through [onProblem] and not retried.
 */
class MusicPlayer(
    private val context: Context,
    private val onProblem: (String) -> Unit,
) {
    private var player: MediaPlayer? = null
    private var playing: String? = null
    private var failed: String? = null

    /** Plays [track] when [wanted], stops otherwise. Calling it again with the same track keeps it playing. */
    fun update(
        track: String?,
        wanted: Boolean,
    ) {
        if (!wanted || track == null || track == failed) return stop()
        if (playing == track && player != null) {
            player?.let { if (!it.isPlaying) it.start() }
            return
        }
        stop()
        val created =
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build(),
                    )
                    setDataSource(context, Uri.parse(track))
                    isLooping = true
                    setVolume(VOLUME, VOLUME)
                    prepare()
                    start()
                }
            }
        created.onSuccess {
            player = it
            playing = track
        }.onFailure {
            playing = null
            failed = track
            onProblem("Music: can't play the chosen file (${it.javaClass.simpleName})")
        }
    }

    fun pause() {
        player?.let { if (it.isPlaying) it.pause() }
    }

    fun stop() {
        player?.let { runCatching { it.release() } }
        player = null
        playing = null
    }

    private companion object {
        const val VOLUME = 0.35f
    }
}
