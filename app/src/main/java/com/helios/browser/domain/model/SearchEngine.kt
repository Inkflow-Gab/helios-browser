package com.helios.browser.domain.model

/**
 * Search providers offered in the omnibox and in first-run setup.
 *
 * [searchUrl] must contain a single `%s` placeholder for the encoded query.
 */
enum class SearchEngine(
    val title: String,
    val searchUrl: String,
    val homeUrl: String
) {
    DUCKDUCKGO(
        title = "DuckDuckGo",
        searchUrl = "https://duckduckgo.com/?q=%s",
        homeUrl = "https://duckduckgo.com"
    ),
    BRAVE(
        title = "Brave Search",
        searchUrl = "https://search.brave.com/search?q=%s",
        homeUrl = "https://search.brave.com"
    ),
    GOOGLE(
        title = "Google",
        searchUrl = "https://www.google.com/search?q=%s",
        homeUrl = "https://www.google.com"
    ),
    STARTPAGE(
        title = "Startpage",
        searchUrl = "https://www.startpage.com/sp/search?query=%s",
        homeUrl = "https://www.startpage.com"
    );

    /** Builds a search URL for [query] with the query percent-encoded. */
    fun searchUrlFor(query: String): String =
        String.format(searchUrl, java.net.URLEncoder.encode(query, "UTF-8"))
}