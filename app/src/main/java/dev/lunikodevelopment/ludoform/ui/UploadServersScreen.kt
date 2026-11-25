package dev.lunikodevelopment.ludoform.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lunikodevelopment.ludoform.UploadServerText
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.Destination
import dev.lunikodevelopment.ludoform.data.Screens
import dev.lunikodevelopment.ludoform.data.ServerAction
import dev.lunikodevelopment.ludoform.data.ServerField
import dev.lunikodevelopment.ludoform.data.Zones
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.theme.Blob
import dev.lunikodevelopment.ludoform.theme.Chip
import dev.lunikodevelopment.ludoform.theme.CookieBacking
import dev.lunikodevelopment.ludoform.theme.ExpressiveShapes
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoIcon
import dev.lunikodevelopment.ludoform.theme.LudoIcons
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.PrimaryPill
import dev.lunikodevelopment.ludoform.theme.SectionLabel
import dev.lunikodevelopment.ludoform.theme.cookieFocus
import dev.lunikodevelopment.ludoform.theme.tap

/**
 * What the player is typing for an upload server. Like the RomM form it stays out of [AppState], so the password
 * never travels through the shared store or the log; it is cleared once saved.
 */
class ServerForm {
    var id by mutableStateOf<Long?>(null)
    var sftp by mutableStateOf(false)
    var name by mutableStateOf("")
    var host by mutableStateOf("")
    var port by mutableStateOf("")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var passwordSaved by mutableStateOf(false)
    var folder by mutableStateOf("")
    var editing by mutableStateOf<ServerField?>(null)
    var problem by mutableStateOf<String?>(null)

    fun value(field: ServerField): String =
        when (field) {
            ServerField.NAME -> name
            ServerField.HOST -> host
            ServerField.PORT -> port
            ServerField.USERNAME -> username
            ServerField.PASSWORD -> password
            ServerField.FOLDER -> folder
        }

    fun update(
        field: ServerField,
        text: String,
    ) {
        when (field) {
            ServerField.NAME -> name = text
            ServerField.HOST -> host = text
            ServerField.PORT -> port = text.filter { it.isDigit() }.take(5)
            ServerField.USERNAME -> username = text
            ServerField.PASSWORD -> password = text
            ServerField.FOLDER -> folder = text
        }
        problem = null
    }

    fun label(field: ServerField): String =
        when (field) {
            ServerField.NAME -> "Name"
            ServerField.HOST -> "Server"
            ServerField.PORT -> "Port"
            ServerField.USERNAME -> "User name"
            ServerField.PASSWORD -> "Password"
            ServerField.FOLDER -> if (sftp) "RomM library folder" else "Share and folder"
        }

    fun placeholder(field: ServerField): String =
        when (field) {
            ServerField.NAME -> "Optional · shown in the list"
            ServerField.HOST -> if (sftp) "homelab.local" else "nas.local"
            ServerField.PORT -> if (sftp) "22" else "445"
            ServerField.USERNAME -> if (sftp) "Your SSH user" else "user, or DOMAIN\\user"
            ServerField.PASSWORD -> if (passwordSaved) "Saved · type to replace" else "Password"
            ServerField.FOLDER -> if (sftp) "/romm/library/roms" else "roms/library"
        }
}

/** One form for both screens: the Stage shows the fields and the Deck the protocol, in separate compositions. */
val SharedServerForm = ServerForm()

val LocalServerForm = staticCompositionLocalOf { SharedServerForm }

@Composable
fun UploadServersStage(
    state: AppState,
    events: UiEvents,
) {
    val colors = Ludo.colors
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Blob(colors.decoPurple, Modifier.align(Alignment.TopEnd).offset(70.dp, 30.dp).size(240.dp, 230.dp))
        CookieBacking(Modifier.matchParentSize())
        StageNav(state, events, Modifier.offset(y = 20.dp))
        Column(Modifier.offset(48.dp, 120.dp).width(760.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Upload servers", style = LudoType.headline.copy(fontSize = 48.sp), color = colors.on)
            Text(
                "Games go to the folder RomM reads its library from, over SFTP or SMB, not through the RomM API. " +
                    "Add the machine that holds that folder.",
                style = LudoType.sub,
                color = colors.on2,
            )
            Text("Passwords are stored encrypted on this device.", style = LudoType.chip, color = colors.on2)
        }
    }
}

@Composable
fun UploadServersDeck(
    state: AppState,
    events: UiEvents,
) {
    val colors = Ludo.colors
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 26.dp, top = 24.dp, end = 26.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel("Upload servers", style = LudoType.sectionLarge)
            if (state.uploadServers.isEmpty()) {
                Text("No servers yet. Add the machine that holds your RomM library folder.", style = LudoType.label, color = colors.on2)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.uploadServers.forEachIndexed { index, row ->
                    Row(
                        Modifier.fillMaxWidth().background(
                            colors.sf2,
                            RoundedCornerShape(24.dp),
                        ).padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val focusedRow = state.isFocused(Zones.SERVER_LIST, index * 2)
                        Column(
                            Modifier.weight(1f)
                                .cookieFocus(focusedRow, ExpressiveShapes.wavy, colors.lav, outset = 6.dp, scale = 1.01f)
                                .tap(row.name) { events.tap(FocusSpot(Zones.SERVER_LIST, index * 2), true) },
                        ) {
                            Text(row.name, style = LudoType.deckTitle, color = if (focusedRow) colors.lav else colors.on, maxLines = 1)
                            Text(
                                "${row.protocol} · ${row.folder} · ${row.status}",
                                style = LudoType.chip,
                                color = if (row.problem) colors.warn else colors.on2,
                                maxLines = 2,
                            )
                        }
                        Chip(
                            UploadServerText.action(row),
                            icon = if (row.offeredKey != null) LudoIcons.lock else LudoIcons.refresh,
                            container = if (row.offeredKey != null) colors.lav else colors.sf3,
                            content = if (row.offeredKey != null) colors.lavD else colors.on,
                            focused = state.isFocused(Zones.SERVER_LIST, index * 2 + 1),
                        ) { events.tap(FocusSpot(Zones.SERVER_LIST, index * 2 + 1), true) }
                    }
                }
            }
            if (state.uploadServers.any { it.ready }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip(
                        "Upload missing",
                        Modifier.weight(1f),
                        icon = LudoIcons.upload,
                        height = 48.dp,
                        focused = state.isFocused(Zones.SERVER_IMPORT, 0),
                    ) { events.tap(FocusSpot(Zones.SERVER_IMPORT, 0), true) }
                    Chip(
                        "Import a folder",
                        Modifier.weight(1f),
                        icon = LudoIcons.folder,
                        height = 48.dp,
                        focused = state.isFocused(Zones.SERVER_IMPORT, 1),
                    ) { events.tap(FocusSpot(Zones.SERVER_IMPORT, 1), true) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryPill(
                    "Add server",
                    Modifier.weight(1f),
                    icon = LudoIcons.upload,
                    height = 56.dp,
                    focused = state.isFocused(Zones.SERVER_LIST_ACTIONS, 0),
                ) { events.tap(FocusSpot(Zones.SERVER_LIST_ACTIONS, 0), true) }
                PrimaryPill(
                    "Back",
                    glyph = "B",
                    container = colors.sf3,
                    content = colors.on,
                    height = 56.dp,
                    focused = state.isFocused(Zones.SERVER_LIST_ACTIONS, 1),
                ) { events.tap(FocusSpot(Zones.SERVER_LIST_ACTIONS, 1), true) }
            }
        }
    }
}

@Composable
fun ServerEditStage(
    state: AppState,
    destination: Destination.UploadServerEdit,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val form = LocalServerForm.current
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        StageNav(state, events, Modifier.offset(y = 20.dp))
        Column(Modifier.offset(48.dp, 88.dp).width(820.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (destination.id == null) "Add an upload server" else "Edit upload server",
                style = LudoType.headline.copy(fontSize = 34.sp),
                color = colors.on,
            )
            val rows =
                listOf(
                    listOf(ServerField.NAME),
                    listOf(ServerField.HOST, ServerField.PORT),
                    listOf(ServerField.USERNAME, ServerField.PASSWORD),
                    listOf(ServerField.FOLDER),
                )
            rows.forEach { fields ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    fields.forEach { field ->
                        Box(Modifier.weight(if (field == ServerField.PORT) 0.35f else 1f)) {
                            FormTextField(
                                label = form.label(field),
                                placeholder = form.placeholder(field),
                                value = form.value(field),
                                onValueChange = { form.update(field, it) },
                                focused = state.isFocused(Zones.SERVER_FIELDS, field.ordinal),
                                editing = form.editing == field,
                                anyEditing = form.editing != null,
                                onStartEditing = {
                                    form.editing = field
                                    events.tap(FocusSpot(Zones.SERVER_FIELDS, field.ordinal), false)
                                },
                                onNext = { form.editing = ServerField.entries.getOrNull(field.ordinal + 1) },
                                keyboardType =
                                    when (field) {
                                        ServerField.HOST -> KeyboardType.Uri
                                        ServerField.PORT -> KeyboardType.Number
                                        else -> KeyboardType.Ascii
                                    },
                                password = field == ServerField.PASSWORD,
                                last = field == ServerField.FOLDER,
                                height = 40.dp,
                            )
                        }
                    }
                }
            }
            form.problem?.let { Text(it, style = LudoType.label, color = colors.err) }
        }
    }
}

@Composable
fun ServerEditDeck(
    state: AppState,
    destination: Destination.UploadServerEdit,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val form = LocalServerForm.current
    val row = state.uploadServers.firstOrNull { it.id == destination.id }
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 26.dp, top = 24.dp, end = 26.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("How Ludoform connects", style = LudoType.sectionLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("SFTP" to true, "SMB" to false).forEachIndexed { index, (label, sftp) ->
                    Chip(
                        label,
                        selected = form.sftp == sftp,
                        focused = state.isFocused(Zones.SERVER_PROTOCOL, index),
                        height = 48.dp,
                    ) { events.tap(FocusSpot(Zones.SERVER_PROTOCOL, index), true) }
                }
            }
            Text(
                if (form.sftp) {
                    "SFTP over SSH. The first connection shows the server's key to check before it is trusted."
                } else {
                    "SMB 2 or 3, a Windows or Samba share. Write the share first, then the folder: roms/library."
                },
                style = LudoType.chip,
                color = colors.on2,
            )
            row?.let {
                Text("Last test: ${it.status}", style = LudoType.label, color = if (it.problem) colors.warn else colors.on2)
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Screens.serverActions(destination).forEachIndexed { index, action ->
                    val (label, glyph) =
                        when (action) {
                            ServerAction.SAVE -> "Save and test" to "A"
                            ServerAction.REMOVE -> "Remove" to null
                            ServerAction.BACK -> "Back" to "B"
                        }
                    PrimaryPill(
                        label,
                        if (action == ServerAction.SAVE) Modifier.weight(1f) else Modifier,
                        glyph = glyph,
                        container = if (action == ServerAction.SAVE) colors.org else colors.sf3,
                        content = if (action == ServerAction.SAVE) colors.orgD else colors.on,
                        height = 56.dp,
                        padding = PaddingValues(horizontal = 20.dp),
                        focused = state.isFocused(Zones.SERVER_ACTIONS, index),
                    ) { events.tap(FocusSpot(Zones.SERVER_ACTIONS, index), true) }
                }
            }
        }
    }
}

@Composable
fun TrustServerStage(
    state: AppState,
    destination: Destination.TrustServer,
) {
    val colors = Ludo.colors
    val row = state.uploadServers.firstOrNull { it.id == destination.id }
    val key = row?.offeredKey
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Blob(colors.decoOrange, Modifier.align(Alignment.BottomEnd).offset(60.dp, 70.dp).size(240.dp, 220.dp))
        StatusPill(Modifier.offset(28.dp, 24.dp))
        Column(Modifier.offset(80.dp, 110.dp).width(900.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                if (row?.keyChanged == true) "This server's key changed" else "Trust this server?",
                style = LudoType.headline.copy(fontSize = 48.sp),
                color = colors.on,
            )
            Text(
                if (row?.keyChanged == true) {
                    "${row.address} now shows a different key than the one you trusted. That happens after a reinstall, " +
                        "or when something else answers in its place. Only trust it if you know why it changed."
                } else {
                    "First connection to ${row?.address ?: "this server"} over SFTP. " +
                        "Check that this fingerprint matches your server before continuing."
                },
                style = LudoType.sub,
                color = colors.on2,
            )
            if (key != null) {
                Column(
                    Modifier.background(colors.sf2, RoundedCornerShape(28.dp)).padding(horizontal = 28.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "${dev.lunikodevelopment.ludoform.transfer.HostKeys.typeLabel(key)} · SHA256",
                        style = LudoType.eyebrow,
                        color = colors.lav,
                    )
                    dev.lunikodevelopment.ludoform.transfer.HostKeys.groups(key).chunked(5).forEach { line ->
                        Text(
                            line.joinToString(" "),
                            style = LudoType.sub.copy(fontFamily = FontFamily.Monospace, fontSize = 26.sp),
                            color = colors.on,
                        )
                    }
                }
            } else {
                Text("The server hasn't offered a key to check. Test it again from the list.", style = LudoType.label, color = colors.warn)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LudoIcon(LudoIcons.lock, size = 18.dp, tint = colors.on2)
                Text("Passwords and keys are stored encrypted on this device.", style = LudoType.chip, color = colors.on2)
            }
        }
    }
}

@Composable
fun TrustServerDeck(
    state: AppState,
    destination: Destination.TrustServer,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val row = state.uploadServers.firstOrNull { it.id == destination.id }
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 26.dp, top = 24.dp, end = 26.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionLabel(row?.name ?: "Upload server", style = LudoType.sectionLarge)
            row?.let { Text("${it.protocol} · ${it.folder}", style = LudoType.label, color = colors.on2) }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryPill(
                    "Don't trust",
                    Modifier.weight(1f),
                    glyph = "B",
                    container = colors.sf3,
                    content = colors.on,
                    height = 56.dp,
                    focused = state.isFocused(Zones.TRUST_ACTIONS, 0),
                ) { events.tap(FocusSpot(Zones.TRUST_ACTIONS, 0), true) }
                PrimaryPill(
                    "Trust and connect",
                    Modifier.weight(1.4f),
                    glyph = "A",
                    height = 56.dp,
                    focused = state.isFocused(Zones.TRUST_ACTIONS, 1),
                ) { events.tap(FocusSpot(Zones.TRUST_ACTIONS, 1), true) }
            }
        }
    }
}
