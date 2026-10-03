package com.helios.browser.data.repository

import com.helios.browser.core.url.UrlNormalizer
import com.helios.browser.data.local.BookmarkDao
import com.helios.browser.di.IoDispatcher
import com.helios.browser.domain.model.Bookmark
import com.helios.browser.domain.model.BrowserTab
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val dao: BookmarkDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    val bookmarks: Flow<List<Bookmark>> = dao.observeAll()

    /** Pure helper so the UI can derive the star state without touching the database. */
    fun isBookmarked(bookmarks: List<Bookmark>, url: String): Boolean {
        if (url.isBlank() || url == BrowserTab.START_PAGE_URL) return false
        val normalised = UrlNormalizer.upgradeToHttps(url)
        return bookmarks.any { UrlNormalizer.upgradeToHttps(it.url) == normalised }
    }

    /** Adds [url] unless already bookmarked, in which case the existing row is removed. */
    suspend fun toggle(url: String, title: String?): Boolean = withContext(io) {
        val normalised = UrlNormalizer.upgradeToHttps(url)
        val existing = dao.findByUrl(normalised)
        if (existing != null) {
            dao.deleteById(existing.id)
            false
        } else {
            dao.insert(
                Bookmark(
                    url = normalised,
                    title = title?.takeIf { it.isNotBlank() } ?: Bookmark.fallbackTitle(normalised)
                )
            )
            true
        }
    }

    suspend fun delete(id: Long): Unit = withContext(io) { dao.deleteById(id) }

    suspend fun count(): Int = withContext(io) { dao.count() }
}