package dev.lunikodevelopment.ludoform.data

import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.model.GameSummary
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.model.PlatformSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Favourites: Details marks a game, the Library and the Quick Menu list them. */
class FavoritesTest {
    private fun game(
        id: String,
        title: String,
        favorite: Boolean = false,
    ) = GameSummary(id, "gc", title, 1, 1, "$title.iso", favorite = favorite)

    private val library =
        LibrarySnapshot(
            folders = listOf("content://roms"),
            platforms = listOf(PlatformSummary("gc", "GameCube", 3)),
            games = listOf(game("tide", "Tidefield Harbor", true), game("orbital", "Orbital Garden"), game("ember", "Ember Vale", true)),
        )

    private fun store() = AppStore().apply { dispatch(AppIntent.LibraryChanged(library)) }

    @Test
    fun favoritesOrderListsOnlyFavouritesByTitle() {
        val games = Screens.library(library, Destination.Library(null, LibrarySort.FAVORITES)).games
        assertEquals(listOf("ember", "tide"), games.map { it.id })
    }

    @Test
    fun detailsOffersFavoriteAndTheQuickMenuOpensTheList() {
        val store = store()
        store.dispatch(AppIntent.Navigate(Destination.Details("orbital")))
        val state = store.state.value
        val actions = Screens.detailsActions(state, "orbital")
        assertTrue(DetailsAction.FAVORITE in actions)
        val index = actions.indexOf(DetailsAction.FAVORITE)
        assertEquals(UiAction.ToggleFavorite("orbital"), Screens.actionFor(state, FocusSpot(Zones.DETAILS_ACTIONS, index)))

        store.dispatch(AppIntent.Navigate(Destination.QuickMenu))
        assertEquals(
            UiAction.Replace(Destination.Library(null, LibrarySort.FAVORITES)),
            Screens.actionFor(store.state.value, FocusSpot(Zones.QUICK_MENU, QuickMenuItem.FAVORITES.ordinal)),
        )
    }

    @Test
    fun theColourSourceFollowsTheGameBeneathQuickSettings() {
        val store = store()
        store.dispatch(AppIntent.Navigate(Destination.Details("ember")))
        store.dispatch(AppIntent.FeelChanged(FeelState(colorSource = ColorSource.ART)))
        assertEquals(AccentSubject("gc", null, "ember"), Screens.accentSubject(store.state.value))
        store.dispatch(AppIntent.Navigate(Destination.QuickSettings))
        assertEquals("ember", Screens.accentSubject(store.state.value)?.gameId)
        store.dispatch(AppIntent.FeelChanged(FeelState(colorSource = ColorSource.CONSOLE)))
        assertEquals(AccentSubject("gc", null, null), Screens.accentSubject(store.state.value))
        store.dispatch(AppIntent.FeelChanged(FeelState(colorSource = ColorSource.LUDOFORM)))
        assertEquals(null, Screens.accentSubject(store.state.value))
    }
}
