package com.helios.browser.data.repository

import com.helios.browser.data.local.HistoryDao
import com.helios.browser.di.IoDispatcher
import com.helios.browser.domain.model.Bookmark
import com.helios.browser.domain.model.BrowserTab
import com.helios.browser.domain.model.HistoryEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(
    private val dao: HistoryDao,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    fun recent(limit: Int = MAX_ENTRIES): Flow<List<HistoryEntry>> = dao.observeRecent(limit)

    fun search(query: String, limit: Int = MAX_ENTRIES): Flow<List<HistoryEntry>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return dao.observeRecent(limit)
        val escaped = trimmed
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return dao.search("%$escaped%", limit)
    }

    /**
     * Records a visit. Re-visiting a URL moves the existing row back to the top instead of appending
     * a duplicate, then the table is trimmed back to [MAX_ENTRIES].
     *
     * Never called for incognito tabs; that is enforced by the caller.
     */
    suspend fun record(url: String, title: String?): Unit = withContext(io) {
        if (url.isBlank() || url == BrowserTab.START_PAGE_URL) return@withContext
        dao.findByUrl(url)?.let { dao.deleteByIds(listOf(it.id)) }
        dao.insert(
            HistoryEntry(
                url = url,
                title = title?.takeIf { it.isNotBlank() } ?: Bookmark.fallbackTitle(url)
            )
        )
        val stale = dao.idsBeyond(MAX_ENTRIES)
        if (stale.isNotEmpty()) dao.deleteByIds(stale)
    }

    suspend fun delete(id: Long): Unit = withContext(io) { dao.deleteByIds(listOf(id)) }

    suspend fun clear(): Unit = withContext(io) { dao.clear() }

    companion object {
        /** Keeps the history table bounded; older rows are trimmed on write. */
        const val MAX_ENTRIES = 1000
    }
}