package dev.lunikodevelopment.ludoform.input

enum class FocusDirection { UP, DOWN, LEFT, RIGHT }

enum class Screen { STAGE, DECK }

/** One focusable item: a zone and the item's index inside it. */
data class FocusSpot(val zone: String, val index: Int = 0)

/**
 * A group of focusable items laid out as a grid of [columns]; a single row when [columns] is at least [size].
 * [wrap] makes Left/Right cycle, as the console carousel does.
 */
data class FocusZone(
    val id: String,
    val screen: Screen,
    val size: Int,
    val columns: Int = Int.MAX_VALUE,
    val wrap: Boolean = false,
) {
    val rows: Int get() = if (size == 0) 0 else (size + cols - 1) / cols
    val cols: Int get() = columns.coerceIn(1, maxOf(size, 1))
}

/**
 * The focus map of one destination. Zones are listed top to bottom per screen. Up from the Deck's top zone enters
 * the Stage at its bottom zone; Down from the Stage's bottom zone returns to the Deck item focus left.
 */
data class FocusLayout(val stage: List<FocusZone>, val deck: List<FocusZone>, val initial: FocusSpot? = null) {
    private val all get() = stage + deck

    fun zone(id: String): FocusZone? = all.firstOrNull { it.id == id }

    fun isValid(spot: FocusSpot?): Boolean = spot != null && zone(spot.zone)?.let { spot.index in 0 until it.size } == true

    fun first(screen: Screen): FocusSpot? = zones(screen).firstOrNull { it.size > 0 }?.let { FocusSpot(it.id, 0) }

    fun zones(screen: Screen): List<FocusZone> = if (screen == Screen.STAGE) stage else deck
}

data class FocusState(
    val current: FocusSpot? = null,
    val lastDeck: FocusSpot? = null,
    val remembered: Map<String, Int> = emptyMap(),
) {
    fun indexIn(
        zone: String,
        fallback: Int = 0,
    ): Int = if (current?.zone == zone) current.index else remembered[zone] ?: fallback
}

fun FocusState.select(
    spot: FocusSpot,
    layout: FocusLayout,
): FocusState {
    val zone = layout.zone(spot.zone) ?: return this
    if (zone.size == 0) return this
    val clamped = spot.copy(index = spot.index.coerceIn(0, zone.size - 1))
    return copy(
        current = clamped,
        lastDeck = if (zone.screen == Screen.DECK) clamped else lastDeck,
        remembered = remembered + (clamped.zone to clamped.index),
    )
}

/** Keeps focus on a valid item after the layout changes, preferring the remembered item of the same zone. */
fun FocusState.normalize(layout: FocusLayout): FocusState {
    if (layout.isValid(current)) return this
    val sameZone = current?.let { layout.zone(it.zone) }?.takeIf { it.size > 0 }
    val target =
        when {
            sameZone != null -> FocusSpot(sameZone.id, current!!.index.coerceIn(0, sameZone.size - 1))
            layout.initial != null && layout.isValid(layout.initial) -> layout.initial
            else -> layout.first(Screen.DECK) ?: layout.first(Screen.STAGE)
        }
    return if (target == null) copy(current = null) else select(target, layout)
}

fun FocusState.touchScreen(
    screen: Screen,
    layout: FocusLayout,
): FocusState {
    val currentScreen = current?.let { layout.zone(it.zone)?.screen }
    if (currentScreen == screen) return this
    val target =
        when (screen) {
            Screen.DECK -> lastDeck?.takeIf { layout.isValid(it) } ?: layout.first(Screen.DECK)
            Screen.STAGE -> layout.stage.lastOrNull { it.size > 0 }?.let { FocusSpot(it.id, remembered[it.id] ?: 0) }
        }
    return target?.let { select(it, layout) } ?: this
}

fun FocusState.move(
    direction: FocusDirection,
    layout: FocusLayout,
): FocusState {
    val state = normalize(layout)
    val spot = state.current ?: return state
    val zone = layout.zone(spot.zone) ?: return state
    val col = spot.index % zone.cols
    val row = spot.index / zone.cols
    return when (direction) {
        FocusDirection.LEFT, FocusDirection.RIGHT -> {
            val step = if (direction == FocusDirection.LEFT) -1 else 1
            val rowStart = row * zone.cols
            val rowEnd = minOf(rowStart + zone.cols, zone.size) - 1
            val next = spot.index + step
            when {
                next in rowStart..rowEnd -> state.select(spot.copy(index = next), layout)
                zone.wrap && zone.rows == 1 -> state.select(spot.copy(index = (next + zone.size) % zone.size), layout)
                else -> state
            }
        }
        FocusDirection.UP, FocusDirection.DOWN -> {
            val step = if (direction == FocusDirection.UP) -1 else 1
            val targetRow = row + step
            if (targetRow in 0 until zone.rows) {
                state.select(spot.copy(index = minOf(targetRow * zone.cols + col, zone.size - 1)), layout)
            } else {
                state.leaveZone(zone, direction, layout)
            }
        }
    }
}

private fun FocusState.leaveZone(
    zone: FocusZone,
    direction: FocusDirection,
    layout: FocusLayout,
): FocusState {
    val sameScreen = layout.zones(zone.screen).filter { it.size > 0 || it.id == zone.id }
    val position = sameScreen.indexOfFirst { it.id == zone.id }
    val neighbour = if (direction == FocusDirection.UP) sameScreen.getOrNull(position - 1) else sameScreen.getOrNull(position + 1)
    if (neighbour != null) return select(FocusSpot(neighbour.id, entryIndex(neighbour, direction)), layout)
    return when {
        zone.screen == Screen.DECK && direction == FocusDirection.UP -> {
            val stageZone = layout.stage.lastOrNull { it.size > 0 } ?: return this
            select(FocusSpot(stageZone.id, entryIndex(stageZone, direction)), layout).copy(lastDeck = current)
        }
        zone.screen == Screen.STAGE && direction == FocusDirection.DOWN ->
            (lastDeck?.takeIf { layout.isValid(it) } ?: layout.first(Screen.DECK))?.let { select(it, layout) } ?: this
        else -> this
    }
}

/** Re-entering a zone restores its remembered item; a grid entered from below starts on its last row. */
private fun FocusState.entryIndex(
    zone: FocusZone,
    direction: FocusDirection,
): Int {
    val remembered = remembered[zone.id]
    if (remembered != null && remembered in 0 until zone.size) return remembered
    return if (direction == FocusDirection.UP && zone.rows > 1) (zone.rows - 1) * zone.cols else 0
}
