package com.helios.browser.ui.browser

import androidx.compose.runtime.Immutable
import com.helios.browser.core.url.UrlNormalizer
import com.helios.browser.domain.model.AppSettings
import com.helios.browser.domain.model.BlockingPreset
import com.helios.browser.domain.model.Bookmark
import com.helios.browser.domain.model.BrowserTab
import com.helios.browser.domain.model.HistoryEntry
import com.helios.browser.engine.BlockListSource
import com.helios.browser.engine.BlockingEngineSnapshot

/** Which full-screen surface, if any, is currently covering the page. */
@Immutable
sealed interface BrowserOverlay {
    data object None : BrowserOverlay
    data object Tabs : BrowserOverlay
    data object Shields : BrowserOverlay
    data object Menu : BrowserOverlay
    data object Bookmarks : BrowserOverlay
    data object History : BrowserOverlay
    data object Downloads : BrowserOverlay
}

/** A finished download, shown in the downloads sheet and the shields stats. */
@Immutable
data class DownloadRecord(
    val id: Long,
    val fileName: String,
    val sourceUrl: String
)

/**
 * A snapshot of the adblock engine, kept in state so the shields sheet can report it truthfully.
 *
 * This is the UI's own value type rather than [com.helios.browser.engine.BlockingEngineSnapshot]
 * because `BrowserState` is a Compose state object: it carries `@Immutable` so recomposition can be
 * skipped, and it is the shape the screens actually read. [toUiState] is the only conversion.
 *
 * @param preset what the user chose.
 * @param activePreset what the running engine actually contains. These differ while a preset switch
 *   is rebuilding, and the shields sheet has to say which one it is describing.
 */
@Immutable
data class BlockingEngineState(
    /** False when the app was built without the Rust toolchain, leaving only the small host list. */
    val isAvailable: Boolean = false,
    val isReady: Boolean = false,
    val isRefreshing: Boolean = false,
    val source: BlockListSource = BlockListSource.NONE,
    val preset: BlockingPreset = BlockingPreset.DEFAULT,
    val activePreset: BlockingPreset = BlockingPreset.DEFAULT,
    val cacheSizeBytes: Long = 0L
) {
    /**
     * True when the engine is not currently the lists the user asked for.
     *
     * Shown as a "rebuilding" state rather than silently continuing with the old rules.
     */
    val isRebuilding: Boolean get() = preset != activePreset
}

/**
 * Copies the engine's snapshot into the UI's value type.
 *
 * [preset] comes from settings rather than the snapshot: it is the user's choice, while the
 * snapshot's own field is what has actually been built. Passing the choice in separately is what
 * lets [BlockingEngineState.isRebuilding] be meaningful.
 */
fun BlockingEngineSnapshot.toUiState(chosen: BlockingPreset): BlockingEngineState = BlockingEngineState(
    isAvailable = isAvailable,
    isReady = isReady,
    isRefreshing = isRefreshing,
    source = source,
    preset = chosen,
    activePreset = preset,
    cacheSizeBytes = cacheSizeBytes
)

@Immutable
data class BrowserState(
    val settingsLoaded: Boolean = false,
    val tabs: List<BrowserTab> = emptyList(),
    val currentTabId: String = "",
    val isPrivateMode: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val bookmarks: List<Bookmark> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val downloads: List<DownloadRecord> = emptyList(),
    val overlay: BrowserOverlay = BrowserOverlay.None,
    val sessionBlockedCount: Int = 0,
    val blockingEngine: BlockingEngineState = BlockingEngineState()
) {
    val currentTab: BrowserTab? get() = tabs.firstOrNull { it.id == currentTabId } ?: tabs.firstOrNull()

    /** Tabs shown in the omnibox counter, tab switcher and swipe navigation. */
    val visibleTabs: List<BrowserTab> get() = tabs.filter { it.isIncognito == isPrivateMode }

    /**
     * Bookmark state for the current tab. Both sides are HTTPS-normalised because
     * [BookmarkRepository.toggle] stores the upgraded URL, so a tab still showing `http://`
     * would otherwise never light up the star.
     */
    val isCurrentBookmarked: Boolean
        get() = currentTab?.let { tab ->
            if (tab.isStartPage) {
                false
            } else {
                val normalised = UrlNormalizer.upgradeToHttps(tab.url)
                bookmarks.any { UrlNormalizer.upgradeToHttps(it.url) == normalised }
            }
        } ?: false
}

/** Everything the UI can ask the browser to do. */
sealed interface BrowserIntent {

    // Omnibox / navigation
    data class Navigate(val input: String) : BrowserIntent

    /** A link whose scheme is not http(s); must be handed to another installed app. */
    data class ExternalNavigate(val url: String) : BrowserIntent

    data object GoHome : BrowserIntent
    data object Reload : BrowserIntent
    data object Stop : BrowserIntent
    data object GoBack : BrowserIntent
    data object GoForward : BrowserIntent

    // Tabs
    data object NewTab : BrowserIntent
    data object NewPrivateTab : BrowserIntent
    data class SelectTab(val id: String) : BrowserIntent
    data class CloseTab(val id: String) : BrowserIntent
    data object CloseAllTabs : BrowserIntent
    data class SetPrivateMode(val enabled: Boolean) : BrowserIntent

    // Overlays
    data class ShowOverlay(val overlay: BrowserOverlay) : BrowserIntent
    data object DismissOverlay : BrowserIntent

    // Settings
    data class SetSearchEngine(val engine: com.helios.browser.domain.model.SearchEngine) : BrowserIntent
    data class SetBlockAds(val enabled: Boolean) : BrowserIntent
    data class SetCosmeticFilters(val enabled: Boolean) : BrowserIntent
    data class SetCleanUrls(val enabled: Boolean) : BrowserIntent
    data class SetUpgradeHttps(val enabled: Boolean) : BrowserIntent
    data class SetSafeBrowsing(val enabled: Boolean) : BrowserIntent
    data class SetSaveHistory(val enabled: Boolean) : BrowserIntent
    data class SetDesktopModeForTab(val enabled: Boolean) : BrowserIntent

    /** Re-downloads the filter lists and recompiles the adblock engine. */
    data object RefreshBlockLists : BrowserIntent

    /**
     * Puts a message in front of the user without changing any state.
     *
     * Needed for the features that are stubbed rather than absent: the menu offers them, so the
     * button has to answer with something honest rather than doing nothing. A menu item that
     * silently swallows a tap is worse than one that says "not yet".
     */
    data class ShowMessage(val message: String) : BrowserIntent

    /**
     * Switches which filter lists the engine is built from.
     *
     * Persisted and applied immediately; the rebuild itself runs in the repository, because the
     * engine is process-wide state the ViewModel does not own.
     */
    data class SetBlockingPreset(val preset: BlockingPreset) : BrowserIntent

    // WebView callbacks
    data class PageStarted(val url: String, val canGoBack: Boolean, val canGoForward: Boolean) : BrowserIntent
    data class PageFinished(val url: String, val title: String?, val canGoBack: Boolean, val canGoForward: Boolean) : BrowserIntent
    data class ProgressChanged(val percent: Int) : BrowserIntent
    data class TitleChanged(val title: String?) : BrowserIntent
    data object RequestBlocked : BrowserIntent
    data class DownloadRequested(val request: com.helios.browser.engine.DownloadRequest) : BrowserIntent

    // Bookmarks and history
    data object ToggleBookmark : BrowserIntent
    data class DeleteBookmark(val id: Long) : BrowserIntent
    data class OpenBookmark(val url: String) : BrowserIntent
    data class OpenHistoryEntry(val url: String) : BrowserIntent
    data class DeleteHistoryEntry(val id: Long) : BrowserIntent
    data object ClearHistory : BrowserIntent

    // Permissions
    data class DownloadPermissionResult(val granted: Boolean) : BrowserIntent

    // Actions that need an Activity or the platform
    data class SharePage(val url: String, val title: String?) : BrowserIntent
    data class CopyLink(val url: String) : BrowserIntent
}

/** One-shot commands the UI must carry out because they need an Activity or a WebView. */
sealed interface BrowserEffect {
    data class LoadUrl(val url: String) : BrowserEffect
    data object Reload : BrowserEffect
    data object Stop : BrowserEffect
    data object GoBack : BrowserEffect
    data object GoForward : BrowserEffect
    data class LaunchExternalApp(val url: String) : BrowserEffect
    data class SharePage(val url: String, val title: String?) : BrowserEffect
    data class CopyToClipboard(val text: String) : BrowserEffect
    data class DownloadPermissionRequired(val permissions: List<String>) : BrowserEffect
    data object CreateShortcut : BrowserEffect
    data class ShowMessage(val message: String) : BrowserEffect
}