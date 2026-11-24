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
    DECK_TESTER,
    DECK_EXPORT,
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

fun FocusState.move(
    direction: FocusDirection,
    diagnosticsAvailable: Boolean = false,
): FocusState {
    val next =
        when (direction) {
            FocusDirection.UP ->
                when {
                    current == FocusTarget.DECK_BACK -> if (diagnosticsAvailable) FocusTarget.DECK_TESTER else lastDeckTop
                    current == FocusTarget.DECK_TESTER || current == FocusTarget.DECK_EXPORT -> lastDeckTop
                    current.screen == Screen.DECK -> stageRow[deckRow.indexOf(current)]
                    else -> current
                }
            FocusDirection.DOWN ->
                when {
                    current.screen == Screen.STAGE -> lastDeck
                    current in deckRow -> if (diagnosticsAvailable) FocusTarget.DECK_TESTER else FocusTarget.DECK_BACK
                    current == FocusTarget.DECK_TESTER || current == FocusTarget.DECK_EXPORT -> FocusTarget.DECK_BACK
                    else -> current
                }
            FocusDirection.LEFT, FocusDirection.RIGHT -> {
                val row =
                    when {
                        current.screen == Screen.STAGE -> stageRow
                        current == FocusTarget.DECK_TESTER || current == FocusTarget.DECK_EXPORT ->
                            listOf(
                                FocusTarget.DECK_TESTER,
                                FocusTarget.DECK_EXPORT,
                            )
                        else -> deckRow
                    }
                val index = row.indexOf(current)
                if (index < 0) current else row[(index + if (direction == FocusDirection.LEFT) -1 else 1).coerceIn(0, row.lastIndex)]
            }
        }
    return select(next)
}
