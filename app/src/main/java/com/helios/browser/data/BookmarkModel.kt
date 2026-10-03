package com.helios.browser.data

data class BookmarkModel(
    val title: String,
    val url: String,
    val initial: String
)

object DefaultShortcuts {
    val items = listOf(
        BookmarkModel("GitHub", "https://github.com", "GH"),
        BookmarkModel("YouTube", "https://youtube.com", "YT"),
        BookmarkModel("Reddit", "https://reddit.com", "RD"),
        BookmarkModel("Wikipedia", "https://wikipedia.org", "WK"),
        BookmarkModel("Hacker News", "https://news.ycombinator.com", "HN"),
        BookmarkModel("DuckDuckGo", "https://duckduckgo.com", "DD")
    )
}
