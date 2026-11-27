package dev.lunikodevelopment.ludoform.ui

import dev.lunikodevelopment.ludoform.model.LibrarySnapshot

/** Release builds carry no design fixtures. */
object DesignFixtures {
    const val AVAILABLE = false

    const val ALWAYS = false

    val snapshot: LibrarySnapshot = LibrarySnapshot.EMPTY

    @Suppress("UNUSED_PARAMETER")
    fun art(key: String): Int? = null
}
