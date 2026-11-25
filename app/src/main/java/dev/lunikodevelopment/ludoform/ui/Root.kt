package dev.lunikodevelopment.ludoform.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lunikodevelopment.ludoform.TransferText
import dev.lunikodevelopment.ludoform.data.AppIntent
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.Destination
import dev.lunikodevelopment.ludoform.data.LibraryStatus
import dev.lunikodevelopment.ludoform.data.ScreenSetting
import dev.lunikodevelopment.ludoform.data.Screens
import dev.lunikodevelopment.ludoform.data.SettingsItem
import dev.lunikodevelopment.ludoform.data.SettingsSection
import dev.lunikodevelopment.ludoform.data.ThemeChoice
import dev.lunikodevelopment.ludoform.data.Zones
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.input.Screen
import dev.lunikodevelopment.ludoform.model.LibrarySnapshot
import dev.lunikodevelopment.ludoform.theme.Board
import dev.lunikodevelopment.ludoform.theme.CookieBacking
import dev.lunikodevelopment.ludoform.theme.CookieHost
import dev.lunikodevelopment.ludoform.theme.DesignCanvas
import dev.lunikodevelopment.ludoform.theme.ExpressiveShapes
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoIcon
import dev.lunikodevelopment.ludoform.theme.LudoIcons
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.PrimaryPill
import dev.lunikodevelopment.ludoform.theme.SectionLabel
import dev.lunikodevelopment.ludoform.theme.cookieFocus
import dev.lunikodevelopment.ludoform.theme.tap

/** Any touch on a screen first moves the shared focus to that screen (touch-to-focus sync). */
fun Modifier.touchSync(
    screen: Screen,
    onIntent: (AppIntent) -> Unit,
): Modifier =
    pointerInput(screen) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onIntent(AppIntent.TouchScreen(screen))
        }
    }

/** A key for screen transitions: the destination type, so focus changes inside a screen do not re-animate it. */
private fun AppState.screenKey(): Any =
    when (val destination = destination) {
        Destination.Home -> "home:" + Screens.status(this).name
        is Destination.Library -> "library:" + destination.platformId
        // The scope chips and a new message replace the sheet in place; that is not a new screen.
        is Destination.Emulator -> "emulator:" + destination.gameId
        is Destination.Disc -> "disc:" + destination.gameId
        is Destination.Screen -> "screen:" + destination.gameId
        // The Cancel all dialog opens over the list, not as a new screen.
        is Destination.Transfers -> "transfers"
        // The Installed chip filters the grid in place.
        is Destination.ServerUnreachable -> "offline"
        // Each key replaces the query in place.
        is Destination.Search -> "search"
        is Destination.ImportReview -> "import"
        else -> destination
    }

@Composable
fun StageRoot(
    state: AppState,
    events: UiEvents,
    onIntent: (AppIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    DesignCanvas(Board.STAGE_WIDTH, Board.HEIGHT, modifier.background(Ludo.colors.bg).touchSync(Screen.STAGE, onIntent)) {
        CookieHost {
            androidx.compose.runtime.CompositionLocalProvider(LocalRommOffline provides (state.romm.offline != null)) {
                AnimatedContent(
                    state,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                    contentKey = { it.screenKey() to (it.gameOnDeck != null) },
                    label = "stage screen",
                ) { shown -> StageContent(shown, events) }
            }
        }
    }
}

@Composable
private fun StageContent(
    state: AppState,
    events: UiEvents,
) {
    if (state.gameOnDeck != null) return PlayingOnDeckStage(state, events)
    val library = state.library ?: return LoadingStage()
    when (val destination = state.destination) {
        Destination.Home ->
            if (Screens.status(state) == LibraryStatus.READY) {
                HomeStage(
                    state,
                    library,
                    events,
                )
            } else {
                LibraryStateStage(state)
            }
        is Destination.Consoles -> PickerStage(state, library)
        is Destination.Library -> LibraryStage(state, library, destination, events)
        is Destination.Details -> DetailsStage(state, library, destination, events)
        is Destination.Emulator -> EmulatorStage(state, library, destination)
        is Destination.Disc -> DiscStage(library, destination, state)
        is Destination.Screen -> ScreenStage(state, library, destination)
        Destination.Settings -> SettingsStage(state, events)
        Destination.Romm -> RommStage(state, events)
        is Destination.Transfers -> TransfersStage(state, library, events)
        is Destination.SaveConflict -> SaveConflictStage(state, library, destination, events)
        is Destination.ServerUnreachable -> ServerUnreachableStage(state)
        Destination.UploadServers -> UploadServersStage(state, events)
        is Destination.UploadServerEdit -> ServerEditStage(state, destination, events)
        is Destination.TrustServer -> TrustServerStage(state, destination)
        is Destination.ImportReview -> ImportReviewStage(state, destination, events)
        Destination.Quickstart -> QuickstartStage(state)
        Destination.QuickMenu ->
            Box(Modifier.fillMaxSize()) {
                StageContent(state.under(), events)
                QuickMenuOverlay(state, events)
            }
        Destination.QuickSettings -> StageContent(state.under(), events)
        is Destination.Search -> SearchStage(state, library, destination)
        Destination.MenuButton -> MenuButtonStage()
    }
}

@Composable
fun DeckRoot(
    state: AppState,
    events: UiEvents,
    onIntent: (AppIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    DesignCanvas(Board.DECK_WIDTH, Board.HEIGHT, modifier.background(Ludo.colors.bg).touchSync(Screen.DECK, onIntent)) {
        CookieHost {
            AnimatedContent(
                state,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                contentKey = { it.screenKey() to it.buttonTesterActive },
                label = "deck screen",
            ) { shown -> DeckContent(shown, events, onIntent) }
        }
    }
}

@Composable
private fun DeckContent(
    state: AppState,
    events: UiEvents,
    onIntent: (AppIntent) -> Unit,
) {
    if (state.buttonTesterActive) return ButtonTesterDeck(state, onIntent)
    val library: LibrarySnapshot = state.library ?: return LoadingDeck()
    when (val destination = state.destination) {
        Destination.Home ->
            if (Screens.status(state) == LibraryStatus.READY) {
                HomeDeck(
                    state,
                    library,
                    events,
                )
            } else {
                LibraryStateDeck(state, events)
            }
        is Destination.Consoles -> PickerDeck(state, library, events)
        is Destination.Library -> LibraryDeck(state, library, destination, events)
        is Destination.Details -> DetailsDeck(state, library, destination, events)
        is Destination.Emulator -> EmulatorDeck(state, library, destination, events)
        is Destination.Disc -> DiscDeck(state, destination, events)
        is Destination.Screen -> ScreenDeck(state, destination, events)
        Destination.Settings -> SettingsDeck(state, events)
        Destination.Romm -> RommDeck(state, events)
        is Destination.Transfers -> TransfersDeck(state, destination, events)
        is Destination.SaveConflict -> SaveConflictDeck(state, library, destination, events)
        is Destination.ServerUnreachable -> ServerUnreachableDeck(state, library, destination, events)
        Destination.UploadServers -> UploadServersDeck(state, events)
        is Destination.UploadServerEdit -> ServerEditDeck(state, destination, events)
        is Destination.TrustServer -> TrustServerDeck(state, destination, events)
        is Destination.ImportReview -> ImportReviewDeck(state, destination, events)
        Destination.Quickstart -> QuickstartDeck(state, events)
        Destination.QuickMenu ->
            Box(Modifier.fillMaxSize()) {
                DeckContent(state.under(), events, onIntent)
                QuickMenuDeckHint()
            }
        Destination.QuickSettings -> QuickSettingsDeck(state, events)
        is Destination.Search -> SearchDeck(state, library, destination, events)
        Destination.MenuButton -> MenuButtonDeck(state, events)
    }
}

/**
 * Without a second display, both boards share one window: stacked like the Thor's panels on a tall screen, side by
 * side like a docked console on a wide one.
 */
@Composable
fun SingleScreenRoot(
    state: AppState,
    events: UiEvents,
    onIntent: (AppIntent) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Ludo.colors.scrim)) {
        if (maxWidth > maxHeight * 1.4f) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StageRoot(state, events, onIntent, Modifier.weight(Board.STAGE_WIDTH))
                DeckRoot(state, events, onIntent, Modifier.weight(Board.DECK_WIDTH))
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StageRoot(state, events, onIntent, Modifier.weight(1f))
                DeckRoot(state, events, onIntent, Modifier.weight(1f))
            }
        }
    }
}

/** A setting's words on the Stage: title, what it is set to, icon. */
fun SettingsItem.copy(state: AppState): Triple<String, String, androidx.compose.ui.graphics.vector.ImageVector> =
    when (this) {
        SettingsItem.ROMM ->
            Triple(
                "Server and sign-in",
                state.romm.address?.let {
                    "$it · ${state.romm.title}"
                } ?: state.romm.title,
                LudoIcons.cloud,
            )
        SettingsItem.ROMM_SYNC -> Triple("Sync now", state.romm.syncLine.ifEmpty { "Fetch the RomM library" }, LudoIcons.refresh)
        SettingsItem.UPLOAD_SERVERS ->
            Triple(
                "Upload servers",
                when (state.uploadServers.size) {
                    0 -> "Put games in your RomM library"
                    1 -> "1 server"
                    else -> "${state.uploadServers.size} servers"
                },
                LudoIcons.upload,
            )
        SettingsItem.UPLOAD_MISSING -> Triple("Upload missing", "Library games RomM doesn't have", LudoIcons.upload)
        SettingsItem.IMPORT_FOLDER -> Triple("Import a folder", "Review a folder's games before uploading", LudoIcons.folder)
        SettingsItem.ADD_FOLDER -> {
            val folders = state.library?.folders?.size ?: 0
            Triple("Add games folder", "$folders folder${if (folders == 1) "" else "s"} now", LudoIcons.folder)
        }
        SettingsItem.RESCAN -> Triple("Scan again", state.scan.status, LudoIcons.refresh)
        SettingsItem.SAVE_REPORT -> Triple("Scan report", "Save what was skipped and why", LudoIcons.download)
        SettingsItem.GAME_SCREEN ->
            Triple(
                "Game screen",
                when (state.gameScreen) {
                    ScreenSetting.TOP -> "Top · where games open"
                    ScreenSetting.BOTTOM -> "Bottom · where games open"
                    ScreenSetting.ASK -> "Ask before each game"
                },
                LudoIcons.screens,
            )
        SettingsItem.REATTACH -> Triple("Re-attach the Deck", "If the lower screen goes blank after sleep", LudoIcons.screens)
        SettingsItem.COUCH_MODE ->
            Triple(
                "Couch mode",
                when {
                    state.couchActive -> "On · playing on the TV"
                    state.couchEnabled -> "On · plug in a TV over USB-C"
                    else -> "Off · plug in a TV and turn this on"
                },
                LudoIcons.screens,
            )
        SettingsItem.QUICKSTART -> Triple("Quickstart", "Walk through setup again", LudoIcons.sparkle)
        SettingsItem.OPEN_ACCESSIBILITY ->
            Triple(
                "Turn the menu on or off",
                if (state.menu.enabled) "On · Android Accessibility" else "Off · Android Accessibility",
                LudoIcons.gamepad,
            )
        SettingsItem.MENU_BUTTON -> Triple("Menu button", "Hold ${state.menu.buttons}", LudoIcons.gamepad)
        SettingsItem.WIFI_ONLY -> Triple("Network", if (state.holds.wifiOnly) "Wi-Fi only" else "Any network", LudoIcons.wifi)
        SettingsItem.CHARGING_ONLY ->
            Triple(
                "Battery",
                if (state.holds.chargingOnly) "Only while charging" else "On battery too",
                LudoIcons.battery,
            )
        SettingsItem.SPEED_LIMIT ->
            Triple(
                "Speed limit",
                if (state.holds.speedLimit == 0L) "No limit" else formatSize(state.holds.speedLimit) + "/s",
                LudoIcons.download,
            )
        SettingsItem.OPEN_TRANSFERS -> Triple("Transfers", TransferText.summary(state.downloads, state.uploads), LudoIcons.download)
        SettingsItem.THEME_DARK -> themeRow(state, ThemeChoice.DARK)
        SettingsItem.THEME_LIGHT -> themeRow(state, ThemeChoice.LIGHT)
        SettingsItem.THEME_AUTO -> themeRow(state, ThemeChoice.AUTO)
        SettingsItem.THEME_BLACK -> themeRow(state, ThemeChoice.BLACK)
        SettingsItem.EXPORT_LOG -> Triple("Export diagnostics", "Save the display and library log", LudoIcons.upload)
        SettingsItem.BUTTON_TESTER -> Triple("Button tester", "See the code of every button", LudoIcons.gamepad)
    }

private fun themeRow(
    state: AppState,
    theme: ThemeChoice,
) = Triple(
    themeName(theme),
    if (state.theme == theme) "In use" else "Use this theme",
    if (state.theme == theme) LudoIcons.check else LudoIcons.sparkle,
)

/** Board 18's Deck: the sections as a stadium list. */
@Composable
fun SettingsDeck(
    state: AppState,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val current = Screens.settingsSection(state)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 26.dp, top = 22.dp, end = 26.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionLabel("Settings", style = LudoType.sectionLarge)
            SettingsSection.entries.forEachIndexed { index, section ->
                val focused = state.isFocused(Zones.SETTINGS_SECTIONS, index)
                Row(
                    Modifier.fillMaxWidth().cookieFocus(focused, ExpressiveShapes.wavy, colors.lav, outset = 4.dp, scale = 1.02f)
                        .height(84.dp)
                        .background(
                            when {
                                focused -> colors.lav
                                section == current -> colors.sf3
                                else -> colors.sf2
                            },
                            RoundedCornerShape(42.dp),
                        )
                        .tap(section.title) { events.tap(FocusSpot(Zones.SETTINGS_SECTIONS, index), true) }
                        .padding(horizontal = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        section.title,
                        Modifier.weight(1f),
                        style = LudoType.label.copy(fontSize = 22.sp),
                        color = if (focused) colors.lavD else colors.on,
                    )
                    LudoIcon(LudoIcons.chevron, size = 20.dp, tint = if (focused) colors.lavD else colors.on2)
                }
            }
        }
    }
}

@Composable
fun ButtonTesterDeck(
    state: AppState,
    onIntent: (AppIntent) -> Unit,
) {
    val colors = Ludo.colors
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 30.dp, top = 30.dp, end = 30.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("Button tester", style = LudoType.sectionLarge)
            Text(
                "Press and release each button. Back ends the test. The AYN button is not visible to apps.",
                style = LudoType.chip,
                color = colors.on2,
            )
            state.recentButtonEvents.asReversed().forEach {
                Text(
                    it,
                    Modifier.fillMaxWidth().background(
                        colors.sf2,
                        androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    ).padding(horizontal = 14.dp, vertical = 6.dp),
                    style = LudoType.chip,
                    color = colors.on,
                )
            }
            Spacer(Modifier.weight(1f))
            PrimaryPill("Done", Modifier.width(220.dp), glyph = "B") { onIntent(AppIntent.SetButtonTester(false)) }
        }
    }
}
