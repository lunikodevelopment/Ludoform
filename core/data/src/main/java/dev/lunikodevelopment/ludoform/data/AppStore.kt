package dev.lunikodevelopment.ludoform.data

import dev.lunikodevelopment.ludoform.input.FocusDirection
import dev.lunikodevelopment.ludoform.input.FocusState
import dev.lunikodevelopment.ludoform.input.FocusTarget
import dev.lunikodevelopment.ludoform.input.Screen
import dev.lunikodevelopment.ludoform.input.move
import dev.lunikodevelopment.ludoform.input.select
import dev.lunikodevelopment.ludoform.input.touchScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/** Navigation destinations are placeholders until their milestone screens are implemented. */
enum class Destination { HOME, LIBRARY, SETTINGS }

data class AppState(
    val navigation: List<Destination> = listOf(Destination.HOME),
    val deckAttached: Boolean = false,
    val focus: FocusState = FocusState(),
) {
    val destination: Destination get() = navigation.last()
    val canGoBack: Boolean get() = navigation.size > 1
}

sealed interface AppIntent {
    data class Navigate(val destination: Destination) : AppIntent

    data object Back : AppIntent

    data class MoveFocus(val direction: FocusDirection) : AppIntent

    data class SelectFocus(val target: FocusTarget) : AppIntent

    data class TouchScreen(val screen: Screen) : AppIntent

    data class DeckConnectionChanged(val attached: Boolean) : AppIntent
}

@Singleton
class AppStore
    @Inject
    constructor() {
        private val mutableState = MutableStateFlow(AppState())
        val state: StateFlow<AppState> = mutableState.asStateFlow()

        fun dispatch(intent: AppIntent) {
            mutableState.update { current ->
                when (intent) {
                    is AppIntent.Navigate ->
                        if (intent.destination == current.destination) {
                            current
                        } else {
                            current.copy(
                                navigation = current.navigation + intent.destination,
                            )
                        }
                    AppIntent.Back -> if (current.canGoBack) current.copy(navigation = current.navigation.dropLast(1)) else current
                    is AppIntent.DeckConnectionChanged -> current.copy(deckAttached = intent.attached)
                    is AppIntent.MoveFocus -> current.copy(focus = current.focus.move(intent.direction))
                    is AppIntent.SelectFocus -> current.copy(focus = current.focus.select(intent.target))
                    is AppIntent.TouchScreen -> current.copy(focus = current.focus.touchScreen(intent.screen))
                }
            }
        }
    }
