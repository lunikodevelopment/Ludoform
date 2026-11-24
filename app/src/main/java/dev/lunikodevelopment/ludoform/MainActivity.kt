package dev.lunikodevelopment.ludoform

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import dev.lunikodevelopment.ludoform.display.DeckController
import dev.lunikodevelopment.ludoform.theme.LudoformTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var deckAttached by mutableStateOf(false)
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
                            LudoformTheme {
                                DeckPlaceholder()
                            }
                        }
                    }
                },
                onAttachedChanged = { deckAttached = it },
            )
        setContent {
            LudoformTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    Column(verticalArrangement = Arrangement.SpaceEvenly) {
                        Text("Ludoform · Stage")
                        Text(if (deckAttached) "Deck connected" else "Deck unavailable · single-screen mode")
                        Button(onClick = { deckController.reattach() }) { Text("Re-attach the Deck") }
                        if (!deckAttached) DeckPlaceholder()
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
