package com.helios.browser.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.helios.browser.core.url.UrlNormalizer

/**
 * One visited page. Written only for non-incognito tabs and only when
 * [AppSettings.saveHistoryEnabled] is on.
 */
@Entity(
    tableName = "history",
    indices = [Index(value = ["visitedAt"]), Index(value = ["url"])]
)
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long = System.currentTimeMillis()
) {
    val host: String
        get() = UrlNormalizer.hostOf(url) ?: url
}