package dev.lunikodevelopment.ludoform.display

import android.app.Activity
import android.app.Presentation
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View

/** Owns the presentation only while the activity is started. */
class DeckController(
    private val activity: Activity,
    private val createDeckView: (Context) -> View,
    private val onAttachedChanged: (Boolean) -> Unit,
    private val onKeyEvent: (KeyEvent) -> Boolean,
    private val onDiagnostic: (String) -> Unit,
) {
    private val displays = activity.getSystemService(DisplayManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var presentation: Presentation? = null
    private var started = false

    /** True while a game runs on the Deck's display; the presentation stays away until [release]. */
    private var held = false

    /** The display the Deck is shown on, if any. */
    val deckDisplayId: Int? get() = presentation?.display?.displayId
    private val retry = Runnable { refresh() }
    private val wakeReattach = Runnable { reattach() }
    private val listener =
        object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                onDiagnostic("Display added: $displayId")
                refresh()
            }

            override fun onDisplayRemoved(displayId: Int) {
                onDiagnostic("Display removed: $displayId")
                refresh()
            }

            override fun onDisplayChanged(displayId: Int) = refresh()
        }
    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == Intent.ACTION_SCREEN_ON) {
                    onDiagnostic("Screen on: schedule Deck re-attach")
                    handler.removeCallbacks(retry)
                    handler.removeCallbacks(wakeReattach)
                    handler.postDelayed(wakeReattach, 700)
                }
            }
        }

    /** Frees the Deck's display for a game. Display changes do not bring the presentation back while held. */
    fun hold() {
        if (held) return
        held = true
        onDiagnostic("Deck held for a game on its display")
        handler.removeCallbacks(retry)
        handler.removeCallbacks(wakeReattach)
        dismiss()
    }

    fun release() {
        if (!held) return
        held = false
        onDiagnostic("Deck released")
        refresh()
    }

    fun start() {
        if (started) return
        started = true
        held = false
        onDiagnostic("Display discovery started")
        displays.registerDisplayListener(listener, handler)
        activity.registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON), Context.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacksAndMessages(null)
        displays.unregisterDisplayListener(listener)
        activity.unregisterReceiver(screenReceiver)
        dismiss()
    }

    fun reattach() {
        if (!started || held) return
        onDiagnostic("Re-attaching Deck")
        dismiss()
        refresh()
    }

    private fun dismiss() {
        val old = presentation
        presentation = null
        old?.setOnDismissListener(null)
        old?.dismiss()
        onAttachedChanged(false)
    }

    private fun refresh() {
        if (!started || held) return
        val available = displays.displays
        val candidate = selectDeck(candidates(displays))
        if (presentation?.display?.displayId == candidate?.id && candidate != null) return
        dismiss()
        val display = available.firstOrNull { it.displayId == candidate?.id }
        if (display == null) {
            onDiagnostic("No eligible Deck display; using single-screen mode")
            return
        }
        try {
            val next =
                object : Presentation(activity, display) {
                    override fun dispatchKeyEvent(event: KeyEvent): Boolean = onKeyEvent(event) || super.dispatchKeyEvent(event)
                }
            // Back is routed through onKeyEvent; a cancelable Presentation would close itself on Back instead.
            next.setCancelable(false)
            next.setContentView(createDeckView(next.context))
            next.setOnDismissListener {
                if (presentation === next) {
                    presentation = null
                    onDiagnostic("Deck dismissed; scheduling rediscovery")
                    onAttachedChanged(false)
                    if (started) {
                        handler.removeCallbacks(retry)
                        handler.postDelayed(retry, 700)
                    }
                }
            }
            next.show()
            presentation = next
            onDiagnostic("Deck attached: display ${display.displayId} (${display.name})")
            onAttachedChanged(true)
        } catch (_: android.view.WindowManager.InvalidDisplayException) {
            onDiagnostic("Deck display became invalid; scheduling retry")
            onAttachedChanged(false)
            handler.removeCallbacks(retry)
            handler.postDelayed(retry, 700)
        }
    }
}
