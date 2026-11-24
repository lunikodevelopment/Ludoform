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
import android.view.View

/** Owns the presentation only while the activity is started. */
class DeckController(
    private val activity: Activity,
    private val createDeckView: (Context) -> View,
    private val onAttachedChanged: (Boolean) -> Unit,
) {
    private val displays = activity.getSystemService(DisplayManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var presentation: Presentation? = null
    private var started = false
    private val retry = Runnable { refresh() }
    private val wakeReattach = Runnable { reattach() }
    private val listener =
        object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = refresh()

            override fun onDisplayRemoved(displayId: Int) = refresh()

            override fun onDisplayChanged(displayId: Int) = refresh()
        }
    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == Intent.ACTION_SCREEN_ON) {
                    handler.removeCallbacks(retry)
                    handler.removeCallbacks(wakeReattach)
                    handler.postDelayed(wakeReattach, 700)
                }
            }
        }

    fun start() {
        if (started) return
        started = true
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
        val display = available.firstOrNull { it.displayId == candidate?.id } ?: return
        try {
            val next = Presentation(activity, display)
            next.setContentView(createDeckView(next.context))
            next.setOnDismissListener {
                if (presentation === next) {
                    presentation = null
                    onAttachedChanged(false)
                    if (started) {
                        handler.removeCallbacks(retry)
                        handler.postDelayed(retry, 700)
                    }
                }
            }
            next.show()
            presentation = next
            onAttachedChanged(true)
        } catch (_: android.view.WindowManager.InvalidDisplayException) {
            onAttachedChanged(false)
            handler.removeCallbacks(retry)
            handler.postDelayed(retry, 700)
        }
    }
}
