package com.helios.browser.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.helios.browser.domain.model.HistoryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntry>>

    @Query(
        "SELECT * FROM history WHERE url LIKE :pattern ESCAPE '\\' OR title LIKE :pattern ESCAPE '\\' " +
            "ORDER BY visitedAt DESC LIMIT :limit"
    )
    fun search(pattern: String, limit: Int): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): HistoryEntry?

    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    @Query("DELETE FROM history WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Rows beyond the newest [keep] entries, oldest first. Used to cap the table. */
    @Query("SELECT id FROM history ORDER BY visitedAt DESC LIMIT -1 OFFSET :keep")
    suspend fun idsBeyond(keep: Int): List<Long>

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int
}