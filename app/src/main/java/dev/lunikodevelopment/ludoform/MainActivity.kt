package dev.lunikodevelopment.ludoform

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
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
import dev.lunikodevelopment.ludoform.display.DeckController
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
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
                            Text("Ludoform · Stage")
                            Text("Current screen: ${state.destination.label}")
                            Text(if (state.deckAttached) "Deck connected" else "Deck unavailable · single-screen mode")
                            DestinationButtons(state = state, onIntent = store::dispatch)
                            Button(onClick = { deckController.reattach() }) { Text("Re-attach the Deck") }
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

    override fun onStart() {
        super.onStart()
        deckController.start()
    }

    override fun onStop() {
        deckController.stop()
        super.onStop()
    }
}
