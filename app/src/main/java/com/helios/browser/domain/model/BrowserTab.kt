package com.helios.browser.domain.model

import com.helios.browser.core.url.UrlNormalizer
import java.util.UUID

/**
 * A browser tab. Every field is a `val`, so a tab is replaced rather than mutated. That is what
 * makes `StateFlow<List<BrowserTab>>` observable: any change produces a new list instance and a new
 * tab instance, which Compose sees as a change. Do not reintroduce `var` fields here.
 */
data class BrowserTab(
    val id: String,
    val url: String = START_PAGE_URL,
    val title: String = DEFAULT_TITLE,
    val isIncognito: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isDesktopMode: Boolean = false,
    val blockedCount: Int = 0
) {
    val isStartPage: Boolean get() = url == START_PAGE_URL

    /** Host shown in the omnibox, or a placeholder while on the start page. */
    val displayHost: String
        get() = if (isStartPage) {
            "Search or type URL"
        } else {
            UrlNormalizer.hostOf(url) ?: url
        }

    val isSecure: Boolean get() = url.startsWith("https://", ignoreCase = true)

    companion object {
        /** Sentinel URL meaning "no page loaded". Never pass this to a real WebView. */
        const val START_PAGE_URL = "helios://start"

        /** Title shown while the start page is displayed. */
        const val DEFAULT_TITLE = "Start Page"

        fun start(
            id: String = UUID.randomUUID().toString(),
            incognito: Boolean = false
        ): BrowserTab = BrowserTab(id = id, isIncognito = incognito)
    }
}