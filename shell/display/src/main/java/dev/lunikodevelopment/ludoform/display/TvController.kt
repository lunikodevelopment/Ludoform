package dev.lunikodevelopment.ludoform.display

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View

/**
 * Couch mode: while [enabled] and a TV is plugged in, Ludoform draws on the TV in a presentation and reports its
 * display so games open there too. Owns the presentation only while the activity is started, like the Deck.
 */
class TvController(
    private val activity: Activity,
    private val createView: (Context) -> View,
    private val onTvChanged: (Int?) -> Unit,
    private val onKeyEvent: (KeyEvent) -> Boolean,
    private val onDiagnostic: (String) -> Unit,
) {
    private val displays = activity.getSystemService(DisplayManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var presentation: Presentation? = null
    private var started = false

    var enabled: Boolean = false
        set(value) {
            field = value
            refresh()
        }

    /** True while a game runs on the TV; Ludoform's presentation stays off it until [release]. */
    private var held = false

    /** Frees the TV for a game. */
    fun hold() {
        held = true
        dismiss()
    }

    fun release() {
        held = false
        refresh()
    }

    /** The TV a game should open on: the one Ludoform is shown on, or the one it stepped off for a game. */
    val gameDisplayId: Int? get() = tvDisplayId ?: if (enabled) selectTv(candidates(displays))?.id else null

    /** The TV Ludoform is shown on, if any. */
    val tvDisplayId: Int? get() = presentation?.display?.displayId

    /** A TV is plugged in, whether or not couch mode is on. */
    val tvConnected: Boolean get() = selectTv(candidates(displays)) != null

    private val listener =
        object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = refresh()

            override fun onDisplayRemoved(displayId: Int) = refresh()

            override fun onDisplayChanged(displayId: Int) = refresh()
        }

    fun start() {
        if (started) return
        started = true
        displays.registerDisplayListener(listener, handler)
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        displays.unregisterDisplayListener(listener)
        dismiss()
    }

    private fun dismiss() {
        val old = presentation ?: return
        presentation = null
        old.setOnDismissListener(null)
        old.dismiss()
        onTvChanged(null)
    }

    private fun refresh() {
        val candidate = if (started && enabled && !held) selectTv(candidates(displays)) else null
        if (candidate == null) {
            if (presentation != null && !held) onDiagnostic("Couch mode: TV gone or off")
            dismiss()
            onTvChanged(null)
            return
        }
        if (presentation?.display?.displayId == candidate.id) return
        dismiss()
        val display = displays.displays.firstOrNull { it.displayId == candidate.id } ?: return
        try {
            val next =
                object : Presentation(activity, display) {
                    override fun dispatchKeyEvent(event: KeyEvent): Boolean = onKeyEvent(event) || super.dispatchKeyEvent(event)
                }
            next.setCancelable(false)
            next.setContentView(createView(next.context))
            next.setOnDismissListener {
                if (presentation === next) {
                    presentation = null
                    onTvChanged(null)
                }
            }
            next.show()
            presentation = next
            onDiagnostic("Couch mode: on display ${display.displayId} (${display.name})")
            onTvChanged(display.displayId)
        } catch (_: android.view.WindowManager.InvalidDisplayException) {
            onDiagnostic("Couch mode: the TV display became invalid")
            onTvChanged(null)
        }
    }
}
