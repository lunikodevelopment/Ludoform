package dev.lunikodevelopment.ludoform

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
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
) {
    Surface(
        modifier = Modifier.fillMaxSize().touchFocus(Screen.DECK, onIntent),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(verticalArrangement = Arrangement.SpaceEvenly) {
            Text("Ludoform · Deck")
            Text("${state.destination.label} · screen coming later")
            DestinationButtons(state = state, onIntent = onIntent, screen = Screen.DECK)
            FocusButton(FocusTarget.DECK_BACK, state, onIntent, { onIntent(AppIntent.Back) }) { Text("Back") }
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
