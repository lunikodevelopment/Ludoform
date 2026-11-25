package dev.lunikodevelopment.ludoform.display

/** A fresh snapshot; display IDs must never be persisted. */
internal data class DisplayCandidate(
    val id: Int,
    val isDefault: Boolean,
    val isPrivate: Boolean,
    val isValid: Boolean,
    val inPresentationCategory: Boolean,
    val presentationFlag: Boolean,
    /** A TV or monitor on USB-C video out, never a built-in panel (D44). */
    val isExternal: Boolean = false,
)

internal fun selectDeck(candidates: List<DisplayCandidate>): DisplayCandidate? {
    // An external display is never the Deck: it is the TV in couch mode (D44).
    val eligible = candidates.filter { !it.isDefault && !it.isPrivate && it.isValid && !it.isExternal }
    return eligible.firstOrNull { it.inPresentationCategory }
        ?: eligible.firstOrNull { it.presentationFlag }
        ?: eligible.firstOrNull()
}

/** The TV for couch mode: a valid public external display (D44). */
internal fun selectTv(candidates: List<DisplayCandidate>): DisplayCandidate? =
    candidates.firstOrNull { !it.isDefault && !it.isPrivate && it.isValid && it.isExternal }

/** Whether [display] is plugged in (HDMI or USB-C video) rather than one of the device's own panels. */
internal fun isExternal(display: android.view.Display): Boolean {
    // Display.getType() is hidden; TYPE_EXTERNAL is 2. Without it, an external display is one that sent EDID data.
    val type = runCatching { android.view.Display::class.java.getMethod("getType").invoke(display) as Int }.getOrNull()
    return if (type != null) type == TYPE_EXTERNAL else display.deviceProductInfo != null
}

private const val TYPE_EXTERNAL = 2

internal fun candidates(displays: android.hardware.display.DisplayManager): List<DisplayCandidate> {
    val presentationIds =
        displays.getDisplays(android.hardware.display.DisplayManager.DISPLAY_CATEGORY_PRESENTATION).map { it.displayId }.toSet()
    return displays.displays.map {
        DisplayCandidate(
            id = it.displayId,
            isDefault = it.displayId == android.view.Display.DEFAULT_DISPLAY,
            isPrivate = it.flags and android.view.Display.FLAG_PRIVATE != 0,
            isValid = it.isValid,
            inPresentationCategory = it.displayId in presentationIds,
            presentationFlag = it.flags and android.view.Display.FLAG_PRESENTATION != 0,
            isExternal = isExternal(it),
        )
    }
}
