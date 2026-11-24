package dev.lunikodevelopment.ludoform.display

/** A fresh snapshot; display IDs must never be persisted. */
internal data class DisplayCandidate(
    val id: Int,
    val isDefault: Boolean,
    val isPrivate: Boolean,
    val isValid: Boolean,
    val inPresentationCategory: Boolean,
    val presentationFlag: Boolean,
)

internal fun selectDeck(candidates: List<DisplayCandidate>): DisplayCandidate? {
    val eligible = candidates.filter { !it.isDefault && !it.isPrivate && it.isValid }
    return eligible.firstOrNull { it.inPresentationCategory }
        ?: eligible.firstOrNull { it.presentationFlag }
        ?: eligible.firstOrNull()
}
