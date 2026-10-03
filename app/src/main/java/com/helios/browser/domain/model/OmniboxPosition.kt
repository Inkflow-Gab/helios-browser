package com.helios.browser.domain.model

/**
 * Where the omnibox is anchored.
 *
 * The bottom default is the one-handed, thumb-reachable placement Helios shipped with. TOP exists
 * because on a tablet in landscape the bottom bar is a long, awkward reach, and because it is
 * simply what most people picture when they say "browser".
 */
enum class OmniboxPosition(val label: String) {
    BOTTOM("Bottom"),
    TOP("Top");

    fun toggled(): OmniboxPosition = if (this == BOTTOM) TOP else BOTTOM

    companion object {
        /** Falls back to [BOTTOM] for anything unrecognised, including a value from a future build. */
        fun fromName(name: String?): OmniboxPosition =
            entries.firstOrNull { it.name == name } ?: BOTTOM
    }
}