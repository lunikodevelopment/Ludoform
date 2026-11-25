package dev.lunikodevelopment.ludoform.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.lunikodevelopment.ludoform.data.AppState
import dev.lunikodevelopment.ludoform.data.Destination
import dev.lunikodevelopment.ludoform.data.ImportAction
import dev.lunikodevelopment.ludoform.data.ImportFilter
import dev.lunikodevelopment.ludoform.data.ImportItem
import dev.lunikodevelopment.ludoform.data.ImportPlan
import dev.lunikodevelopment.ludoform.data.ImportStatus
import dev.lunikodevelopment.ludoform.data.Screens
import dev.lunikodevelopment.ludoform.data.Zones
import dev.lunikodevelopment.ludoform.input.FocusSpot
import dev.lunikodevelopment.ludoform.theme.Blob
import dev.lunikodevelopment.ludoform.theme.Chip
import dev.lunikodevelopment.ludoform.theme.CookieBacking
import dev.lunikodevelopment.ludoform.theme.Ludo
import dev.lunikodevelopment.ludoform.theme.LudoIcons
import dev.lunikodevelopment.ludoform.theme.LudoType
import dev.lunikodevelopment.ludoform.theme.PrimaryPill
import dev.lunikodevelopment.ludoform.theme.SectionLabel

private fun platformName(id: String) = platformInfo(id).shortName

/** The focused file of the import review, or the first one (board 07's Stage). */
private fun focusedItem(
    state: AppState,
    destination: Destination.ImportReview,
): ImportItem? {
    val items = state.importReview?.visible(destination.filter).orEmpty()
    return items.getOrNull(state.focus.indexIn(Zones.IMPORT_LIST)) ?: items.firstOrNull()
}

@Composable
fun ImportReviewStage(
    state: AppState,
    destination: Destination.ImportReview,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val review = state.importReview
    val item = focusedItem(state, destination)
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        Blob(colors.decoPurple, Modifier.align(Alignment.TopEnd).offset(70.dp, 30.dp).size(240.dp, 230.dp))
        CookieBacking(Modifier.matchParentSize())
        StageNav(state, events, Modifier.offset(y = 20.dp))
        Column(Modifier.offset(48.dp, 130.dp).width(1100.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                review == null || review.scanning -> {
                    Text("Looking for games…", style = LudoType.headline.copy(fontSize = 48.sp), color = colors.on)
                    Text(review?.source.orEmpty(), style = LudoType.sub, color = colors.on2)
                }
                item == null -> {
                    Text("Nothing to upload", style = LudoType.headline.copy(fontSize = 48.sp), color = colors.on)
                    Text(
                        "No games that RomM is missing were found in ${review.source}.",
                        style = LudoType.sub,
                        color = colors.on2,
                    )
                }
                else -> {
                    val label =
                        when (item.status) {
                            ImportStatus.NEEDS_REVIEW -> "NEEDS REVIEW"
                            ImportStatus.NO_FOLDER -> "NO ROMM FOLDER"
                            ImportStatus.ON_ROMM -> "ALREADY ON ROMM"
                            ImportStatus.READY -> if (item.chosen) "YOUR CHOICE" else "READY"
                        }
                    SectionLabel(label, style = LudoType.sectionLarge)
                    Text(
                        item.fileName,
                        style = LudoType.headline.copy(fontSize = 44.sp, lineHeight = 50.sp),
                        color = colors.on,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.candidates.size > 1 && item.decidedBy == null) {
                        val extension = item.fileName.substringAfterLast('.', "").lowercase()
                        Text(
                            ".$extension is shared by several systems, so the extension cannot decide alone.",
                            style = LudoType.sub,
                            color = colors.on2,
                        )
                    }
                    Fact("Size", formatSize(item.sizeBytes) + if (item.files.size > 1) " · ${item.files.size} files" else "")
                    item.decidedBy?.let { Fact("Decided by", it) }
                    item.platformId?.let { platform ->
                        Fact("RomM folder", item.fsSlug.orEmpty() + if (item.serverHasFolder) "" else " · not on server yet")
                        Fact("System", platformName(platform))
                    }
                    Fact("Found in", item.foundIn)
                    if (item.candidates.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            item.candidates.forEach { candidate ->
                                Chip(platformName(candidate), selected = candidate == item.platformId, height = 44.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(
    name: String,
    value: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(name, Modifier.width(200.dp), style = LudoType.label, color = Ludo.colors.on2)
        Text(value, style = LudoType.label, color = Ludo.colors.on, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ImportReviewDeck(
    state: AppState,
    destination: Destination.ImportReview,
    events: UiEvents,
) {
    val colors = Ludo.colors
    val review = state.importReview
    Box(Modifier.fillMaxSize().background(colors.bg)) {
        CookieBacking(Modifier.matchParentSize())
        Column(
            Modifier.fillMaxSize().padding(start = 24.dp, top = 22.dp, end = 24.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (review == null || review.scanning) "Review import" else "Review import · ${review.items.size} games",
                    Modifier.weight(1f),
                    style = LudoType.headline.copy(fontSize = 28.sp),
                    color = colors.on,
                )
                if (review != null && !review.scanning) {
                    Text("${review.count(ImportStatus.READY)} ready", style = LudoType.label, color = colors.on2)
                }
            }
            if (review != null && !review.scanning) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImportFilter.entries.forEachIndexed { index, filter ->
                        val text =
                            when (filter) {
                                ImportFilter.ALL -> "All"
                                ImportFilter.NEEDS_REVIEW -> "Needs review · ${review.visible(filter).size}"
                                ImportFilter.NO_FOLDER -> "No folder · ${review.count(ImportStatus.NO_FOLDER)}"
                            }
                        Chip(
                            text,
                            selected = destination.filter == filter,
                            focused = state.isFocused(Zones.IMPORT_FILTER, index),
                            height = 40.dp,
                        ) { events.tap(FocusSpot(Zones.IMPORT_FILTER, index), true) }
                    }
                }
            }
            val items = review?.visible(destination.filter).orEmpty()
            val scroll = rememberScrollState()
            val focused = state.focusedIndex(Zones.IMPORT_LIST)
            LaunchedEffect(focused) { if (focused != null) scroll.animateScrollTo((focused - 2).coerceAtLeast(0) * ROW_STEP) }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (review == null || review.scanning) {
                    Text("Looking for games…", Modifier.padding(top = 8.dp), style = LudoType.body, color = colors.on2)
                }
                items.forEachIndexed { index, item ->
                    Row(
                        Modifier.fillMaxWidth().height(66.dp).background(colors.sf2, RoundedCornerShape(24.dp))
                            .padding(start = 18.dp, end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.fileName,
                                style = LudoType.label.copy(fontSize = 17.sp),
                                color = colors.on,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                ImportPlan.detail(item, ::platformName),
                                style = LudoType.caption,
                                color = if (item.status == ImportStatus.NEEDS_REVIEW) colors.err else colors.on2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Chip(
                            ImportPlan.actionLabel(item, ::platformName),
                            selected = item.include,
                            focused = state.isFocused(Zones.IMPORT_LIST, index),
                            height = 40.dp,
                        ) { events.tap(FocusSpot(Zones.IMPORT_LIST, index), true) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Screens.importActions(state).forEachIndexed { index, action ->
                    when (action) {
                        ImportAction.BACK ->
                            PrimaryPill(
                                "Back",
                                glyph = "B",
                                container = colors.sf3,
                                content = colors.on,
                                height = 56.dp,
                                padding = PaddingValues(horizontal = 24.dp),
                                focused = state.isFocused(Zones.IMPORT_ACTIONS, index),
                            ) { events.tap(FocusSpot(Zones.IMPORT_ACTIONS, index), true) }
                        ImportAction.UPLOAD ->
                            PrimaryPill(
                                "Upload ${review?.uploadCount ?: 0} to RomM",
                                Modifier.weight(1f),
                                icon = LudoIcons.upload,
                                height = 56.dp,
                                focused = state.isFocused(Zones.IMPORT_ACTIONS, index),
                            ) { events.tap(FocusSpot(Zones.IMPORT_ACTIONS, index), true) }
                    }
                }
            }
        }
    }
}

private const val ROW_STEP = 74
