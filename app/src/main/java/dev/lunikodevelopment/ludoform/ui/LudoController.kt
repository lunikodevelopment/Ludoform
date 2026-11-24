package dev.lunikodevelopment.ludoform.ui

import android.view.KeyEvent
import dev.lunikodevelopment.ludoform.data.AppIntent
import dev.lunikodevelopment.ludoform.data.AppStore
import dev.lunikodevelopment.ludoform.data.Destination
import dev.lunikodevelopment.ludoform.data.EmulatorChoice
import dev.lunikodevelopment.ludoform.data.LaunchRequest
import dev.lunikodevelopment.ludoform.data.LibrarySort
import dev.lunikodevelopment.ludoform.data.PreferenceScope
import dev.lunikodevelopment.ludoform.data.Screens
import dev.lunikodevelopment.ludoform.data.UiAction
import dev.lunikodevelopment.ludoform.input.FocusDirection
import dev.lunikodevelopment.ludoform.input.FocusSpot

/** Platform work the screens can ask for; implemented by the activity. */
interface PlatformActions {
    fun pickFolder()

    fun rescan()

    fun saveScanReport()

    fun exportLog()

    fun reattachDeck()

    fun openFiles()

    fun openNetwork()

    fun play(request: LaunchRequest)

    fun playWith(gameId: String)

    fun useEmulator(
        gameId: String,
        choice: EmulatorChoice,
        scope: PreferenceScope,
    )

    fun getEmulator(packageName: String)

    fun playOnScreen(action: UiAction.PlayOnScreen)

    fun cycleGameScreen()

    fun releaseDeck()

    fun record(message: String)
}

/** Turns gamepad keys, taps and actions into store intents and platform calls, for both windows. */
class LudoController(
    private val store: AppStore,
    private val platform: PlatformActions,
) {
    val events = UiEvents(tap = ::tap, perform = ::perform)

    fun tap(
        spot: FocusSpot,
        activate: Boolean,
    ) {
        val wasFocused = store.state.value.focus.current == spot
        store.dispatch(AppIntent.SelectFocus(spot))
        if (activate || wasFocused) perform(Screens.actionFor(store.state.value, spot))
    }

    fun perform(action: UiAction) {
        when (action) {
            is UiAction.Navigate -> store.dispatch(AppIntent.Navigate(action.destination))
            is UiAction.Replace -> store.dispatch(AppIntent.Replace(action.destination))
            UiAction.Back -> store.dispatch(AppIntent.Back)
            UiAction.PickFolder -> platform.pickFolder()
            UiAction.Rescan -> platform.rescan()
            UiAction.SaveScanReport -> platform.saveScanReport()
            UiAction.ButtonTester -> store.dispatch(AppIntent.SetButtonTester(true))
            UiAction.ExportLog -> platform.exportLog()
            UiAction.Reattach -> platform.reattachDeck()
            UiAction.OpenFiles -> platform.openFiles()
            UiAction.OpenNetwork -> platform.openNetwork()
            is UiAction.Play -> platform.play(action.request)
            is UiAction.PlayWith -> platform.playWith(action.gameId)
            is UiAction.UseEmulator -> platform.useEmulator(action.gameId, action.choice, action.scope)
            is UiAction.GetEmulator -> platform.getEmulator(action.packageName)
            is UiAction.PlayOnScreen -> platform.playOnScreen(action)
            UiAction.CycleGameScreen -> platform.cycleGameScreen()
            UiAction.ReleaseDeck -> platform.releaseDeck()
            UiAction.None -> Unit
        }
    }

    /** Returns true when the key was handled. */
    fun onKey(event: KeyEvent): Boolean {
        val state = store.state.value
        if (state.buttonTesterActive) {
            if (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP) {
                val action = if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"
                val description = "$action ${KeyEvent.keyCodeToString(event.keyCode)} (${event.keyCode}), scan ${event.scanCode}"
                store.dispatch(AppIntent.RecordButtonEvent(description))
                platform.record("Button tester: $description")
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    store.dispatch(AppIntent.SetButtonTester(false))
                }
            }
            return true
        }
        val direction =
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> FocusDirection.UP
                KeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.DOWN
                KeyEvent.KEYCODE_DPAD_LEFT -> FocusDirection.LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> FocusDirection.RIGHT
                else -> null
            }
        val handled =
            direction != null ||
                event.keyCode in
                setOf(
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
                    KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
                ) ||
                (event.keyCode == KeyEvent.KEYCODE_BACK && state.canGoBack)
        if (!handled) return false
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (direction != null) {
            store.dispatch(AppIntent.MoveFocus(direction))
            return true
        }
        if (event.repeatCount > 0) return true
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A ->
                state.focus.current?.let { perform(Screens.actionFor(state, it)) }
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> store.dispatch(AppIntent.Back)
            KeyEvent.KEYCODE_BUTTON_X -> perform(UiAction.Navigate(Destination.Consoles(Screens.focusedGame(state)?.platformId)))
            KeyEvent.KEYCODE_BUTTON_Y -> perform(UiAction.Navigate(Destination.Library(null, LibrarySort.TITLE)))
            KeyEvent.KEYCODE_BUTTON_L1 -> store.dispatch(AppIntent.Shoulder(-1))
            KeyEvent.KEYCODE_BUTTON_R1 -> store.dispatch(AppIntent.Shoulder(1))
        }
        return true
    }
}
