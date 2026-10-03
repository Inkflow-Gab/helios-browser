package com.helios.browser.di

import android.content.Context
import androidx.room.Room
import com.helios.browser.data.local.BookmarkDao
import com.helios.browser.data.local.HeliosDatabase
import com.helios.browser.data.local.HistoryDao
import com.helios.browser.engine.BlockListRepository
import com.helios.browser.engine.BlockingEngineStatus
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HeliosDatabase =
        Room.databaseBuilder(context, HeliosDatabase::class.java, HeliosDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideBookmarkDao(database: HeliosDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun provideHistoryDao(database: HeliosDatabase): HistoryDao = database.historyDao()
}

/**
 * Binds the narrow [BlockingEngineStatus] interface to [BlockListRepository].
 *
 * Needed because Hilt does not infer interface → implementation from an `@Inject` constructor alone.
 * The seam exists so `BrowserViewModel` can report engine status without depending on a class that
 * owns an `ApplicationContext`, and so a unit test can stand in a fake.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BlockingEngineModule {

    @Binds
    @Singleton
    abstract fun bindBlockingEngineStatus(
        repository: BlockListRepository
    ): BlockingEngineStatus
}

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}