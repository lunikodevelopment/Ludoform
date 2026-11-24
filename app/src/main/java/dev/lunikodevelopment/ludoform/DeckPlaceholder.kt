package dev.lunikodevelopment.ludoform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.lunikodevelopment.ludoform.data.AppIntent
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.Destination

val Destination.label: String
    get() =
        when (this) {
            Destination.HOME -> "Home"
            Destination.LIBRARY -> "Library"
            Destination.SETTINGS -> "Settings"
        }

@Composable
fun DeckPlaceholder(
    state: AppState,
    onIntent: (AppIntent) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(verticalArrangement = Arrangement.SpaceEvenly) {
            Text("Ludoform · Deck")
            Text("${state.destination.label} · screen coming later")
            DestinationButtons(state = state, onIntent = onIntent)
            Button(onClick = { onIntent(AppIntent.Back) }, enabled = state.canGoBack) { Text("Back") }
        }
    }
}

@Composable
fun DestinationButtons(
    state: AppState,
    onIntent: (AppIntent) -> Unit,
) {
    Row {
        Destination.entries.forEach { destination ->
            Button(
                onClick = { onIntent(AppIntent.Navigate(destination)) },
                enabled = state.destination != destination,
            ) { Text(destination.label) }
        }
    }
}
