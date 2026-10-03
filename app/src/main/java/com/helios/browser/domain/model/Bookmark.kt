package com.helios.browser.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.helios.browser.core.url.UrlNormalizer

/**
 * A user bookmark. Room entity is also the domain model: this app has no reason to keep two copies.
 */
@Entity(
    tableName = "bookmarks",
    indices = [Index(value = ["url"], unique = true)]
)
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    val host: String
        get() = UrlNormalizer.hostOf(url) ?: url

    companion object {
        /** Title used when a page has not reported one yet. */
        fun fallbackTitle(url: String): String = UrlNormalizer.hostOf(url) ?: url
    }
}

/** Seeds offered on the start page before the user has any bookmarks of their own. */
object DefaultShortcuts {
    val items: List<Triple<String, String, String>> = listOf(
        Triple("GitHub", "https://github.com", "GH"),
        Triple("YouTube", "https://youtube.com", "YT"),
        Triple("Reddit", "https://reddit.com", "RD"),
        Triple("Wikipedia", "https://wikipedia.org", "WK"),
        Triple("Hacker News", "https://news.ycombinator.com", "HN"),
        Triple("DuckDuckGo", "https://duckduckgo.com", "DD")
    )
}