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
import android.view.Display
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

    fun start() {
        if (started) return
        started = true
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
        if (!started) return
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
        if (!started) return
        val available = displays.displays
        val presentationIds = displays.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).map { it.displayId }.toSet()
        val candidate =
            selectDeck(
                available.map {
                    DisplayCandidate(
                        id = it.displayId,
                        isDefault = it.displayId == Display.DEFAULT_DISPLAY,
                        isPrivate = it.flags and Display.FLAG_PRIVATE != 0,
                        isValid = it.isValid,
                        inPresentationCategory = it.displayId in presentationIds,
                        presentationFlag = it.flags and Display.FLAG_PRESENTATION != 0,
                    )
                },
            )
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
