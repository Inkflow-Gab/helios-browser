package com.helios.browser.data

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

    fun buildQueryUrl(query: String): String {
        val trimmed = query.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else if (trimmed.contains(".") && !trimmed.contains(" ")) {
            "https://$trimmed"
        } else {
            String.format(searchUrl, java.net.URLEncoder.encode(trimmed, "UTF-8"))
        }
    }
}
