package com.helios.browser

import android.app.Application
import com.helios.browser.engine.BlockListRepository
import com.helios.browser.engine.NativeAdBlock
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HeliosApplication : Application() {

    @Inject
    lateinit var blockListRepository: BlockListRepository

    /**
     * Application-lifetime scope for the filter list load.
     *
     * Not `viewModelScope`, because the engine outlives any screen: it is a process-wide filter set,
     * not per-tab state, and rebuilding it per ViewModel would defeat the whole point. `SupervisorJob`
     * so a failed load cannot cancel the scope for later attempts.
     */
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Safe Browsing is *not* enabled here. There is no process-wide switch for it: the
        // androidx.webkit API is `WebSettingsCompat.setSafeBrowsingEnabled(WebSettings, Boolean)`,
        // which is per-WebView, so `WebViewFactory` applies it when it builds a tab and honours the
        // user's Shields setting. See the note there.
        loadFilterLists()
    }

    /**
     * Compiles the adblock engine in the background, before the first page is likely to be asked
     * for.
     *
     * Kicked off here rather than lazily because the bundled lists take a few seconds to parse, and
     * starting here means blocking is normally live by the time the user reaches the address bar.
     * [com.helios.browser.engine.AdBlockEngine.shouldBlock] falls back to a small compiled-in host
     * list until [NativeAdBlock.isReady], so nothing is unblocked for long and nothing blocks.
     */
    private fun loadFilterLists() {
        startupScope.launch {
            blockListRepository.initialize()
            // Only bother reaching for the network once the bundled engine is working, so a device
            // that has never been online still behaves correctly.
            if (NativeAdBlock.isAvailable && NativeAdBlock.isReady) {
                blockListRepository.refreshInBackground()
            }
        }
    }
}
