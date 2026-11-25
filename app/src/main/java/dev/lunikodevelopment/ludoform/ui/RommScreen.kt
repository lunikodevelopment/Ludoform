package dev.lunikodevelopment.ludoform.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.RommAction
import dev.lunikodevelopment.ludoform.data.RommField
import dev.lunikodevelopment.ludoform.data.RommStatus
import dev.lunikodevelopment.ludoform.data.Screens
import dev.lunikodevelopment.ludoform.data.Zones
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.theme.Blob
import dev.lunikodevelopment.ludoform.theme.CookieBacking
import dev.lunikodevelopment.ludoform.theme.ExpressiveShapes
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoIcon
import dev.lunikodevelopment.ludoform.theme.LudoIcons
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.PrimaryPill
import dev.lunikodevelopment.ludoform.theme.SectionLabel
import dev.lunikodevelopment.ludoform.theme.cookieFocus

/**
 * What the player is typing in Settings → RomM. It stays out of [AppState]: the password must not travel through
 * the shared store or the diagnostics log, and it is cleared as soon as it is saved.
 */
class RommForm {
    var address by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")

    /** A password is saved, so an empty field keeps it. */
    var passwordSaved by mutableStateOf(false)

    /** The field holding the text cursor; while set, the keyboard and the field get the keys. */
    var editing by mutableStateOf<RommField?>(null)

    /** Why the form could not be saved, shown under the fields until the player edits them. */
    var problem by mutableStateOf<String?>(null)

    fun value(field: RommField): String =
        when (field) {
            RommField.ADDRESS -> address
            RommField.USERNAME -> username
            RommField.PASSWORD -> password
        }

    fun update(
        field: RommField,
        text: String,
    ) {
        when (field) {
            RommField.ADDRESS -> address = text
            RommField.USERNAME -> username = text
            RommField.PASSWORD -> password = text
        }
        problem = null
    }
}

val LocalRommForm = staticCompositionLocalOf { RommForm() }

private fun RommField.label() =
    when (this) {
        RommField.ADDRESS -> "Server address"
        RommField.USERNAME -> "User name"
        RommField.PASSWORD -> "Password"
    }

private fun RommField.placeholder(form: RommForm) =
    when (this) {
        RommField.ADDRESS -> "romm.example.org"
        RommField.USERNAME -> "Your RomM user"
        RommField.PASSWORD -> if (form.passwordSaved) "Saved · type to replace" else "Your RomM password"
    }

@Composable
fun RommStage(
    state: AppState,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val form = LocalRommForm.current
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Blob(colors.decoPurple, Modifier.align(Alignment.TopEnd).offset(70.dp, 30.dp).size(240.dp, 230.dp))
        CookieBacking(Modifier.matchParentSize())
        StageNav(state, events, Modifier.offset(y = 20.dp))
        Column(Modifier.offset(48.dp, 92.dp).width(560.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("ROMM", style = LudoType.headline, color = colors.on)
            Text(
                "Connect your RomM server. Ludoform checks it can reach the server and sign in.",
                style = LudoType.chip,
                color = colors.on2,
            )
            RommField.entries.forEach { field ->
                RommTextField(field, form, state.isFocused(Zones.ROMM_FIELDS, field.ordinal)) {
                    events.tap(FocusSpot(Zones.ROMM_FIELDS, field.ordinal), false)
                }
            }
            form.problem?.let { Text(it, style = LudoType.label, color = colors.err) }
        }
    }
}

@Composable
private fun RommTextField(
    field: RommField,
    form: RommForm,
    focused: Boolean,
    onFocused: () -> Unit,
) {
    val colors = Ludo.colors
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val editing = form.editing == field
    LaunchedEffect(editing) {
        if (editing) {
            requester.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(form.editing) {
        if (form.editing == null) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(field.label(), style = LudoType.caption, color = if (focused) colors.lav else colors.on2)
        Box(
            Modifier
                .cookieFocus(focused && !editing, ExpressiveShapes.wavyLarge, colors.lav, outset = 5.dp, scale = 1.02f)
                .fillMaxWidth()
                .height(44.dp)
                .background(colors.sf2, shape)
                .border(if (editing) 2.dp else 0.dp, if (editing) colors.lav else colors.sf2, shape)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val value = form.value(field)
            if (value.isEmpty()) Text(field.placeholder(form), style = LudoType.label, color = colors.on2.copy(alpha = 0.7f))
            BasicTextField(
                value = value,
                onValueChange = { form.update(field, it) },
                modifier =
                    Modifier.fillMaxWidth().focusRequester(requester).onFocusChanged {
                        if (it.isFocused && form.editing != field) {
                            form.editing = field
                            onFocused()
                        }
                    },
                singleLine = true,
                textStyle = LudoType.label.copy(color = colors.on, fontSize = 17.sp),
                cursorBrush = SolidColor(colors.lav),
                visualTransformation = if (field == RommField.PASSWORD) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType =
                            when (field) {
                                RommField.ADDRESS -> KeyboardType.Uri
                                RommField.USERNAME -> KeyboardType.Ascii
                                RommField.PASSWORD -> KeyboardType.Password
                            },
                        imeAction = if (field == RommField.PASSWORD) ImeAction.Done else ImeAction.Next,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onNext = { form.editing = RommField.entries.getOrNull(field.ordinal + 1) },
                        onDone = { form.editing = null },
                    ),
            )
        }
    }
}

@Composable
fun RommDeck(
    state: AppState,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val romm = state.romm
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 26.dp, top = 26.dp, end = 26.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionLabel("RomM server", style = LudoType.sectionLarge)
            val (tint, icon) =
                when (romm.status) {
                    RommStatus.CONNECTED -> colors.ok to LudoIcons.check
                    RommStatus.PROBLEM -> colors.err to LudoIcons.wifiOff
                    RommStatus.CHECKING -> colors.lav to LudoIcons.refresh
                    RommStatus.NOT_CHECKED, RommStatus.NOT_SET_UP -> colors.on2 to LudoIcons.cloud
                }
            Row(
                Modifier.fillMaxWidth().background(colors.sf2, RoundedCornerShape(24.dp)).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.size(48.dp).background(tint.copy(alpha = 0.18f), RoundedCornerShape(16.dp)), Alignment.Center) {
                    LudoIcon(icon, tint = tint)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(romm.title, style = LudoType.deckTitle, color = colors.on, maxLines = 1)
                    Text(romm.detail, style = LudoType.chip, color = colors.on2, maxLines = 2)
                    romm.address?.let { Text(it, style = LudoType.caption, color = colors.on2, maxLines = 1) }
                }
            }
            if (romm.status == RommStatus.CHECKING) WavyActivity(Modifier.fillMaxWidth())
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Screens.rommActions(state).forEachIndexed { index, action ->
                    val (label, primary) =
                        when (action) {
                            RommAction.SAVE -> (if (romm.configured) "Save and check" else "Connect") to true
                            RommAction.REMOVE -> "Remove server" to false
                            RommAction.BACK -> "Back" to false
                        }
                    PrimaryPill(
                        label,
                        Modifier.weight(if (primary) 1.4f else 1f),
                        glyph = if (action == RommAction.BACK) "B" else null,
                        container = if (primary) colors.org else colors.sf3,
                        content = if (primary) colors.orgD else colors.on,
                        focused = state.isFocused(Zones.ROMM_ACTIONS, index),
                    ) { events.tap(FocusSpot(Zones.ROMM_ACTIONS, index), true) }
                }
            }
        }
    }
}
