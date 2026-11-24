package dev.lunikodevelopment.ludoform

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import dev.lunikodevelopment.ludoform.data.AppIntent
import dev.lunikodevelopment.ludoform.data.AppStore
import dev.lunikodevelopment.ludoform.data.Destination
import dev.lunikodevelopment.ludoform.display.DeckController
import dev.lunikodevelopment.ludoform.input.FocusDirection
import dev.lunikodevelopment.ludoform.input.FocusTarget
import dev.lunikodevelopment.ludoform.input.Screen
import dev.lunikodevelopment.ludoform.theme.LudoformTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var store: AppStore
    private lateinit var deckController: DeckController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deckController =
            DeckController(
                activity = this,
                createDeckView = { deckContext ->
                    ComposeView(deckContext).apply {
                        setViewTreeLifecycleOwner(this@MainActivity)
                        setViewTreeViewModelStoreOwner(this@MainActivity)
                        setViewTreeSavedStateRegistryOwner(this@MainActivity)
                        setContent {
                            val state by store.state.collectAsState()
                            LudoformTheme { DeckPlaceholder(state = state, onIntent = store::dispatch) }
                        }
                    }
                },
                onAttachedChanged = { store.dispatch(AppIntent.DeckConnectionChanged(it)) },
                onKeyEvent = ::handleKey,
            )
        setContent {
            val state by store.state.collectAsState()
            BackHandler(enabled = state.canGoBack) { store.dispatch(AppIntent.Back) }
            LudoformTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    Column {
                        Column(
                            modifier = Modifier.weight(1f).touchFocus(Screen.STAGE, store::dispatch),
                            verticalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Text("Ludoform · Stage")
                            Text("Current screen: ${state.destination.label}")
                            Text(if (state.deckAttached) "Deck connected" else "Deck unavailable · single-screen mode")
                            DestinationButtons(state = state, onIntent = store::dispatch, screen = Screen.STAGE)
                            FocusButton(
                                FocusTarget.STAGE_REATTACH,
                                state,
                                store::dispatch,
                                { deckController.reattach() },
                            ) { Text("Re-attach the Deck") }
                        }
                        if (!state.deckAttached) {
                            Column(modifier = Modifier.weight(1f)) {
                                DeckPlaceholder(state = state, onIntent = store::dispatch)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = handleKey(event) || super.dispatchKeyEvent(event)

    private fun handleKey(event: KeyEvent): Boolean {
        val direction =
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> FocusDirection.UP
                KeyEvent.KEYCODE_DPAD_DOWN -> FocusDirection.DOWN
                KeyEvent.KEYCODE_DPAD_LEFT -> FocusDirection.LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> FocusDirection.RIGHT
                else -> null
            }
        val select =
            event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                event.keyCode == KeyEvent.KEYCODE_ENTER
        val back = event.keyCode == KeyEvent.KEYCODE_BUTTON_B
        if (direction == null && !select && !back) return false
        if (event.action != KeyEvent.ACTION_DOWN) return true
        if (direction != null) store.dispatch(AppIntent.MoveFocus(direction))
        if (event.repeatCount == 0 && back) store.dispatch(AppIntent.Back)
        if (event.repeatCount == 0 && select) {
            when (store.state.value.focus.current) {
                FocusTarget.STAGE_HOME, FocusTarget.DECK_HOME -> store.dispatch(AppIntent.Navigate(Destination.HOME))
                FocusTarget.STAGE_LIBRARY, FocusTarget.DECK_LIBRARY -> store.dispatch(AppIntent.Navigate(Destination.LIBRARY))
                FocusTarget.STAGE_SETTINGS, FocusTarget.DECK_SETTINGS -> store.dispatch(AppIntent.Navigate(Destination.SETTINGS))
                FocusTarget.DECK_BACK -> store.dispatch(AppIntent.Back)
                FocusTarget.STAGE_REATTACH -> deckController.reattach()
            }
        }
        return true
    }

    override fun onStart() {
        super.onStart()
        deckController.start()
    }

    override fun onStop() {
        deckController.stop()
        super.onStop()
    }
}
