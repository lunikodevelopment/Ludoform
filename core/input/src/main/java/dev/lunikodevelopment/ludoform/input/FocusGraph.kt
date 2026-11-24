package dev.lunikodevelopment.ludoform.input

enum class FocusTarget {
    STAGE_HOME,
    STAGE_LIBRARY,
    STAGE_SETTINGS,
    STAGE_REATTACH,
    DECK_HOME,
    DECK_LIBRARY,
    DECK_SETTINGS,
    DECK_BACK,
}

enum class FocusDirection { UP, DOWN, LEFT, RIGHT }

enum class Screen { STAGE, DECK }

val FocusTarget.screen: Screen
    get() =
        when (this) {
            FocusTarget.STAGE_HOME, FocusTarget.STAGE_LIBRARY, FocusTarget.STAGE_SETTINGS, FocusTarget.STAGE_REATTACH -> Screen.STAGE
            else -> Screen.DECK
        }

private val stageRow = listOf(FocusTarget.STAGE_HOME, FocusTarget.STAGE_LIBRARY, FocusTarget.STAGE_SETTINGS, FocusTarget.STAGE_REATTACH)
private val deckRow = listOf(FocusTarget.DECK_HOME, FocusTarget.DECK_LIBRARY, FocusTarget.DECK_SETTINGS)

data class FocusState(
    val current: FocusTarget = FocusTarget.DECK_HOME,
    val lastDeck: FocusTarget = FocusTarget.DECK_HOME,
    val lastDeckTop: FocusTarget = FocusTarget.DECK_HOME,
)

fun FocusState.select(target: FocusTarget): FocusState =
    copy(
        current = target,
        lastDeck = if (target.screen == Screen.DECK) target else lastDeck,
        lastDeckTop = if (target in deckRow) target else lastDeckTop,
    )

fun FocusState.touchScreen(screen: Screen): FocusState =
    when (screen) {
        Screen.DECK -> if (current.screen == Screen.DECK) this else select(lastDeck)
        Screen.STAGE -> if (current.screen == Screen.STAGE) this else select(FocusTarget.STAGE_HOME)
    }

fun FocusState.move(direction: FocusDirection): FocusState {
    val next =
        when (direction) {
            FocusDirection.UP ->
                when {
                    current == FocusTarget.DECK_BACK -> lastDeckTop
                    current.screen == Screen.DECK -> stageRow[deckRow.indexOf(current)]
                    else -> current
                }
            FocusDirection.DOWN ->
                when {
                    current.screen == Screen.STAGE -> lastDeck
                    current in deckRow -> FocusTarget.DECK_BACK
                    else -> current
                }
            FocusDirection.LEFT, FocusDirection.RIGHT -> {
                val row = if (current.screen == Screen.STAGE) stageRow else deckRow
                val index = row.indexOf(current)
                if (index < 0) current else row[(index + if (direction == FocusDirection.LEFT) -1 else 1).coerceIn(0, row.lastIndex)]
            }
        }
    return select(next)
}
