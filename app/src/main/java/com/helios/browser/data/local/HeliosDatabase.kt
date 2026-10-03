package com.helios.browser.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.helios.browser.domain.model.Bookmark
import com.helios.browser.domain.model.HistoryEntry

@Database(
    entities = [Bookmark::class, HistoryEntry::class],
    version = 1,
    exportSchema = true
)
abstract class HeliosDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun historyDao(): HistoryDao

    companion object {
        const val NAME = "helios.db"
    }
}