package dev.lunikodevelopment.ludoform

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.lunikodevelopment.ludoform.data.AppIntent
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.Destination
import dev.lunikodevelopment.ludoform.input.FocusTarget
import dev.lunikodevelopment.ludoform.input.Screen
import dev.lunikodevelopment.ludoform.theme.Dimens
import dev.lunikodevelopment.ludoform.theme.travelingCookie

val Destination.label: String
    get() =
        when (this) {
            Destination.HOME -> "Home"
            Destination.LIBRARY -> "Library"
            Destination.SETTINGS -> "Settings"
        }

fun Modifier.touchFocus(
    screen: Screen,
    onIntent: (AppIntent) -> Unit,
): Modifier =
    pointerInput(screen, onIntent) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onIntent(AppIntent.TouchScreen(screen))
        }
    }

private fun Modifier.touchTarget(
    target: FocusTarget,
    onIntent: (AppIntent) -> Unit,
): Modifier =
    pointerInput(target, onIntent) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onIntent(AppIntent.SelectFocus(target))
        }
    }

@Composable
fun FocusButton(
    target: FocusTarget,
    state: AppState,
    onIntent: (AppIntent) -> Unit,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val isFocused = state.focus.current == target
    val borderColor = if (isFocused) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Transparent
    Button(
        onClick = onClick,
        modifier =
            Modifier.touchTarget(target, onIntent)
                .travelingCookie(isFocused)
                .border(Dimens.focusOutline, borderColor, RoundedCornerShape(50))
                .semantics { if (isFocused) stateDescription = "Gamepad focus" },
        content = { content() },
    )
}

@Composable
fun DeckPlaceholder(
    state: AppState,
    onIntent: (AppIntent) -> Unit,
    onExport: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize().touchFocus(Screen.DECK, onIntent),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        if (state.buttonTesterActive) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Button tester", style = MaterialTheme.typography.titleLarge)
                Text("Press and release each button. Use Done or the Android Back key to exit.")
                Text("The AYN/Home button is not available to apps.")
                state.recentButtonEvents.asReversed().forEach { Text(it) }
                Button(onClick = { onIntent(AppIntent.SetButtonTester(false)) }) { Text("Done") }
            }
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.SpaceEvenly) {
                Text("Ludoform · Deck")
                Text("${state.destination.label} · screen coming later")
                DestinationButtons(state = state, onIntent = onIntent, screen = Screen.DECK)
                if (state.destination == Destination.SETTINGS) {
                    Text(if (state.deckAttached) "Deck connected" else "Single-screen mode")
                    Text("Display: ${state.displayDiagnostic}")
                    Row {
                        FocusButton(
                            FocusTarget.DECK_TESTER,
                            state,
                            onIntent,
                            { onIntent(AppIntent.SetButtonTester(true)) },
                        ) { Text("Button tester") }
                        FocusButton(FocusTarget.DECK_EXPORT, state, onIntent, onExport) { Text("Export log") }
                    }
                }
                FocusButton(FocusTarget.DECK_BACK, state, onIntent, { onIntent(AppIntent.Back) }) { Text("Back") }
            }
        }
    }
}

@Composable
fun DestinationButtons(
    state: AppState,
    onIntent: (AppIntent) -> Unit,
    screen: Screen,
) {
    val targets =
        when (screen) {
            Screen.STAGE -> listOf(FocusTarget.STAGE_HOME, FocusTarget.STAGE_LIBRARY, FocusTarget.STAGE_SETTINGS)
            Screen.DECK -> listOf(FocusTarget.DECK_HOME, FocusTarget.DECK_LIBRARY, FocusTarget.DECK_SETTINGS)
        }
    Row {
        Destination.entries.forEachIndexed { index, destination ->
            FocusButton(targets[index], state, onIntent, { onIntent(AppIntent.Navigate(destination)) }) {
                Text(if (state.destination == destination) "${destination.label} •" else destination.label)
            }
        }
    }
}
