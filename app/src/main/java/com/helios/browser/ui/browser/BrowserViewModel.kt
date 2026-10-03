package com.helios.browser.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helios.browser.core.url.UrlNormalizer
import com.helios.browser.data.repository.BookmarkRepository
import com.helios.browser.data.repository.HistoryRepository
import com.helios.browser.data.repository.SettingsRepository
import com.helios.browser.di.IoDispatcher
import com.helios.browser.domain.model.AppSettings
import com.helios.browser.domain.model.BlockingConfig
import com.helios.browser.domain.model.BlockingPreset
import com.helios.browser.domain.model.BrowserTab
import com.helios.browser.engine.AdBlockEngine
import com.helios.browser.engine.BlockingEngineSnapshot
import com.helios.browser.engine.BlockingEngineStatus
import com.helios.browser.engine.DownloadDispatcher
import com.helios.browser.engine.DownloadRequest
import com.helios.browser.engine.DownloadResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * The single owner of browser state.
 *
 * Never holds a `WebView` or any Android permission object: navigation and permission plumbing are
 * pushed out as [BrowserEffect]s, and WebView callbacks arrive as [BrowserIntent]s. `onIntent` is
 * the only entry point, and it is safe to call from any thread (WebView callbacks are not on the
 * main thread).
 */
@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val historyRepository: HistoryRepository,
    private val downloadDispatcher: DownloadDispatcher,
    /**
     * Injected as the narrow [BlockingEngineStatus] interface, not the concrete repository. The
     * repository owns an `ApplicationContext` to read assets and write its cache, and this
     * ViewModel must not hold Android objects.
     */
    private val blockingEngine: BlockingEngineStatus,
    @IoDispatcher private val io: CoroutineDispatcher
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    private val _effects = Channel<BrowserEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** Guards compound updates, because WebView callbacks arrive off the main thread. */
    private val lock = Any()

    /**
     * Live blocking configuration for the WebView clients. Read from a WebView background thread by
     * `shouldInterceptRequest`, hence the volatile backing field rather than a Flow read.
     */
    @Volatile
    private var currentBlockingConfig: BlockingConfig = BlockingConfig.from(AppSettings.PRIVACY_DEFAULTS)

    val blockingConfig: BlockingConfig get() = currentBlockingConfig

    private val blockedCount = AtomicInteger(0)
    @Volatile
    private var pendingDownload: DownloadRequest? = null
    private var saveSessionJob: Job? = null

    /**
     * Deliberately not [viewModelScope]: that scope is already cancelled by the time
     * [onCleared] runs, so a flush launched there would never execute.
     */
    private val flushScope = CoroutineScope(SupervisorJob() + io)

    init {
        observeSettings()
        observeBookmarks()
        observeHistory()
        restoreSession()
        observeBlockingEngine()
    }

    /**
     * Mirrors the adblock engine's status into state.
     *
     * Collects a Flow rather than polling. The earlier version woke once a second and rebuilt
     * [BrowserState] every time, which meant a full recomposition of the browser screen forever and a
     * `File.exists()` stat on the main thread each round — and all to learn something that changes
     * perhaps four times per session. The repository publishes on real transitions instead.
     */
    private fun observeBlockingEngine() = viewModelScope.launch {
        blockingEngine.status.collect { snapshot ->
            // The chosen preset comes from settings, which arrives on its own flow. Reading it from
            // state here rather than closing over it keeps the two independent.
            _state.update { current ->
                current.copy(blockingEngine = snapshot.toUiState(current.settings.blockingPreset))
            }
        }
    }

    /**
     * Switches the filter preset, rebuilds the engine, and persists the choice.
     *
     * The rebuild happens in the repository because it owns the engine; the write happens here so
     * the setting is durable even if the rebuild fails. Order matters: persist first, then rebuild,
     * so a failed rebuild leaves the user's choice visible in the settings with the old engine still
     * running — which is the honest state — rather than a silently reverted choice.
     */
    private fun setBlockingPreset(preset: BlockingPreset) {
        if (_state.value.settings.blockingPreset == preset) return
        updateSettings { copy(blockingPreset = preset) }
        viewModelScope.launch {
            val loaded = blockingEngine.setPreset(preset)
            emit(
                BrowserEffect.ShowMessage(
                    if (loaded) {
                        "Switched to ${preset.title}"
                    } else {
                        "Could not build the ${preset.title} engine; keeping the current lists"
                    }
                )
            )
        }
    }

    /**
     * Downloads fresh filter lists and recompiles the engine.
     *
     * Reports the outcome either way. A silent failure would leave the user tapping "Update" with
     * no idea whether it worked, which is the one thing a settings screen must not do.
     *
     * The progress flag comes from the collected status rather than being set here, so there is one
     * source of truth for it.
     */
    private fun refreshBlockLists() {
        if (_state.value.blockingEngine.isRefreshing) return
        viewModelScope.launch {
            val updated = blockingEngine.refresh()
            emit(
                BrowserEffect.ShowMessage(
                    if (updated) {
                        "Filter lists updated"
                    } else {
                        "Could not reach the filter lists; keeping the current ones"
                    }
                )
            )
        }
    }

    // region wiring

    private fun observeSettings() = viewModelScope.launch {
        settingsRepository.settings.collect { settings ->
            currentBlockingConfig = BlockingConfig.from(settings)
            _state.update { current ->
                current.copy(settings = settings, settingsLoaded = true)
            }
        }
    }

    private fun observeBookmarks() = viewModelScope.launch {
        bookmarkRepository.bookmarks.collect { list ->
            _state.update { it.copy(bookmarks = list) }
        }
    }

    private fun observeHistory() = viewModelScope.launch {
        historyRepository.recent().collect { list ->
            _state.update { it.copy(history = list) }
        }
    }

    private fun restoreSession() = viewModelScope.launch {
        val snapshot = settingsRepository.loadSession()
        val restored = snapshot?.tabs
            ?.map { tab -> tab.copy(isDesktopMode = _state.value.settings.desktopModeByDefault) }
            .orEmpty()
        val tabs = restored.ifEmpty { listOf(BrowserTab.start()) }
        val currentId = snapshot?.currentTabId?.takeIf { id -> tabs.any { it.id == id } }
            ?: tabs.first().id
        _state.update { it.copy(tabs = tabs, currentTabId = currentId) }
    }

    // endregion

    fun onIntent(intent: BrowserIntent) {
        when (intent) {
            is BrowserIntent.Navigate -> navigate(intent.input)
            BrowserIntent.GoHome -> navigate(BrowserTab.START_PAGE_URL)
            BrowserIntent.Reload -> emit(BrowserEffect.Reload)
            BrowserIntent.Stop -> emit(BrowserEffect.Stop)
            BrowserIntent.GoBack -> emit(BrowserEffect.GoBack)
            BrowserIntent.GoForward -> emit(BrowserEffect.GoForward)

            BrowserIntent.NewTab -> openTab(incognito = _state.value.isPrivateMode)
            BrowserIntent.NewPrivateTab -> openTab(incognito = true)
            is BrowserIntent.SelectTab -> selectTab(intent.id)
            is BrowserIntent.CloseTab -> closeTab(intent.id)
            BrowserIntent.CloseAllTabs -> closeAllTabs()
            is BrowserIntent.SetPrivateMode -> setPrivateMode(intent.enabled)

            is BrowserIntent.ShowOverlay -> _state.update { it.copy(overlay = intent.overlay) }
            BrowserIntent.DismissOverlay -> _state.update { it.copy(overlay = BrowserOverlay.None) }

            is BrowserIntent.SetSearchEngine -> updateSettings { copy(searchEngine = intent.engine) }
            is BrowserIntent.SetBlockAds -> updateSettings { copy(blockAdsAndTrackers = intent.enabled) }
            is BrowserIntent.SetCosmeticFilters -> updateSettings { copy(cosmeticFilters = intent.enabled) }
            is BrowserIntent.SetCleanUrls -> updateSettings { copy(cleanTrackingParams = intent.enabled) }
            is BrowserIntent.SetUpgradeHttps -> updateSettings { copy(upgradeToHttps = intent.enabled) }
            is BrowserIntent.SetSafeBrowsing -> updateSettings { copy(safeBrowsingEnabled = intent.enabled) }
            is BrowserIntent.SetSaveHistory -> updateSettings { copy(saveHistoryEnabled = intent.enabled) }
            is BrowserIntent.SetDesktopModeForTab -> setDesktopMode(intent.enabled)
            BrowserIntent.RefreshBlockLists -> refreshBlockLists()
            is BrowserIntent.SetBlockingPreset -> setBlockingPreset(intent.preset)
            is BrowserIntent.ShowMessage -> emit(BrowserEffect.ShowMessage(intent.message))

            is BrowserIntent.PageStarted -> applyToCurrentTab {
                it.copy(url = intent.url, isLoading = true, canGoBack = intent.canGoBack, canGoForward = intent.canGoForward)
            }
            is BrowserIntent.PageFinished -> onPageFinished(intent)
            is BrowserIntent.ProgressChanged -> applyToCurrentTab {
                it.copy(progress = intent.percent, isLoading = intent.percent < 100)
            }
            is BrowserIntent.TitleChanged -> applyToCurrentTab { tab ->
                val newTitle = intent.title
                if (newTitle.isNullOrBlank()) tab else tab.copy(title = newTitle)
            }
            BrowserIntent.RequestBlocked -> onRequestBlocked()
            is BrowserIntent.DownloadRequested -> queueDownload(intent.request)

            BrowserIntent.ToggleBookmark -> toggleBookmark()
            is BrowserIntent.DeleteBookmark -> viewModelScope.launch { bookmarkRepository.delete(intent.id) }
            is BrowserIntent.OpenBookmark -> {
                dismissOverlay()
                navigate(intent.url)
            }
            is BrowserIntent.OpenHistoryEntry -> {
                dismissOverlay()
                navigate(intent.url)
            }

            // Non-web links (mailto:, tel:, intent:, market:, app schemes) must never reach the
            // WebView: it would fail silently. They go to whichever app claims the scheme.
            is BrowserIntent.ExternalNavigate -> onExternalNavigation(intent.url)
            is BrowserIntent.DeleteHistoryEntry -> viewModelScope.launch { historyRepository.delete(intent.id) }
            BrowserIntent.ClearHistory -> viewModelScope.launch { historyRepository.clear() }

            is BrowserIntent.DownloadPermissionResult -> onDownloadPermissionResult(intent.granted)

            is BrowserIntent.SharePage -> emit(BrowserEffect.SharePage(intent.url, intent.title))
            is BrowserIntent.CopyLink -> emit(BrowserEffect.CopyToClipboard(intent.url))
        }
    }

    // region navigation

    private fun navigate(input: String) {
        if (_state.value.currentTab == null) return
        val target = if (input == BrowserTab.START_PAGE_URL) {
            input
        } else {
            UrlNormalizer.resolve(
                input = input,
                engine = _state.value.settings.searchEngine,
                upgradeToHttps = _state.value.settings.upgradeToHttps
            )
        }
        applyToCurrentTab { it.copy(url = target, isLoading = target != BrowserTab.START_PAGE_URL) }
        if (target != BrowserTab.START_PAGE_URL) emit(BrowserEffect.LoadUrl(target))
        scheduleSessionSave()
    }

    fun onExternalNavigation(url: String) {
        emit(BrowserEffect.LaunchExternalApp(url))
    }

    /**
     * Called when no installed app claimed the link. The tab is put back on the start page, because
     * the WebView never navigated anywhere and showing the pending URL would be a lie.
     */
    fun onExternalAppFailed(url: String) {
        applyToCurrentTab { it.copy(url = BrowserTab.START_PAGE_URL, isLoading = false) }
        emit(BrowserEffect.ShowMessage("No app can open that link"))
        scheduleSessionSave()
    }

    private fun onPageFinished(intent: BrowserIntent.PageFinished) {
        val tab = _state.value.currentTab ?: return
        val url = intent.url.ifBlank { tab.url }
        applyToCurrentTab {
            it.copy(
                url = url,
                title = intent.title?.takeIf { title -> title.isNotBlank() } ?: it.title,
                isLoading = false,
                progress = 100,
                canGoBack = intent.canGoBack,
                canGoForward = intent.canGoForward
            )
        }
        // Incognito visits are never written to disk, in any form.
        if (!tab.isIncognito && _state.value.settings.saveHistoryEnabled && !url.startsWith("helios://")) {
            viewModelScope.launch { historyRepository.record(url, intent.title) }
        }
        scheduleSessionSave()
    }

    // endregion

    // region tabs

    private fun openTab(incognito: Boolean) {
        val tab = BrowserTab.start(incognito = incognito)
        _state.update { current ->
            current.copy(
                tabs = current.tabs + tab,
                currentTabId = tab.id,
                isPrivateMode = incognito
            )
        }
        scheduleSessionSave()
    }

    private fun selectTab(id: String) {
        val tab = _state.value.tabs.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(currentTabId = id, isPrivateMode = tab.isIncognito) }
        scheduleSessionSave()
    }

    /** Returns true when the caller should let the WebView handle the back press itself. */
    fun closeTab(id: String): Boolean {
        val current = _state.value
        val index = current.tabs.indexOfFirst { it.id == id }
        if (index == -1) return false

        val remaining = current.tabs.filterNot { it.id == id }
        if (remaining.isEmpty()) {
            _state.update {
                val fresh = BrowserTab.start(incognito = it.isPrivateMode)
                it.copy(tabs = listOf(fresh), currentTabId = fresh.id, overlay = BrowserOverlay.None)
            }
            scheduleSessionSave()
            return false
        }

        val next = if (current.currentTabId == id) {
            remaining[(index - 1).coerceAtLeast(0)]
        } else {
            current.currentTab
        }
        _state.update {
            it.copy(
                tabs = remaining,
                currentTabId = next?.id ?: remaining.first().id,
                overlay = if (it.overlay == BrowserOverlay.Tabs) BrowserOverlay.None else it.overlay
            )
        }
        scheduleSessionSave()
        return true
    }

    private fun closeAllTabs() {
        val keepIncognito = _state.value.isPrivateMode
        val remaining = _state.value.tabs.filterNot { it.isIncognito == keepIncognito }
        val fresh = BrowserTab.start(incognito = keepIncognito)
        _state.update {
            it.copy(tabs = remaining + fresh, currentTabId = fresh.id, overlay = BrowserOverlay.None)
        }
        scheduleSessionSave()
    }

    private fun setPrivateMode(enabled: Boolean) {
        val current = _state.value
        if (current.isPrivateMode == enabled) return

        val candidate = current.tabs.firstOrNull { it.isIncognito == enabled }
        if (candidate == null) {
            // Entering a mode with no tabs: create the first one for that mode.
            val tab = BrowserTab.start(incognito = enabled)
            _state.update {
                it.copy(
                    isPrivateMode = enabled,
                    tabs = it.tabs + tab,
                    currentTabId = tab.id
                )
            }
        } else {
            // Switch to the most recently used tab of the target mode rather than leaving
            // currentTabId pointing at a tab from the other mode.
            _state.update { it.copy(isPrivateMode = enabled, currentTabId = candidate.id) }
        }
        scheduleSessionSave()
    }

    private fun setDesktopMode(enabled: Boolean) {
        applyToCurrentTab { it.copy(isDesktopMode = enabled) }
        emit(BrowserEffect.Reload)
        scheduleSessionSave()
    }

    // endregion

    // region blocking and downloads

    private fun onRequestBlocked() {
        val total = blockedCount.incrementAndGet()
        synchronized(lock) {
            _state.update { current ->
                current.copy(
                    sessionBlockedCount = total,
                    tabs = current.tabs.map { tab ->
                        if (tab.id == current.currentTabId) {
                            tab.copy(blockedCount = tab.blockedCount + 1)
                        } else {
                            tab
                        }
                    }
                )
            }
        }
    }

    private fun queueDownload(request: DownloadRequest) {
        val fileName = AdBlockEngine.sanitizeFileName(request.contentDisposition, request.url)
        pendingDownload = request
        when (val result = downloadDispatcher.dispatch(request)) {
            is DownloadResult.Queued -> {
                pendingDownload = null
                _state.update {
                    it.copy(
                        downloads = it.downloads + DownloadRecord(result.id, fileName, request.url)
                    )
                }
                val destination = if (result.isPublicDestination) "" else " (app-private folder)"
                emit(BrowserEffect.ShowMessage("Downloading $fileName$destination"))
            }

            is DownloadResult.NeedsPermission -> {
                emit(BrowserEffect.DownloadPermissionRequired(result.permissions))
            }

            DownloadResult.Failed -> {
                pendingDownload = null
                emit(BrowserEffect.ShowMessage("Could not start that download"))
            }
        }
    }

    /** Called after the runtime permission dialog resolves. */
    private fun onDownloadPermissionResult(granted: Boolean) {
        val request = pendingDownload
        if (request == null) {
            if (!granted) emit(BrowserEffect.ShowMessage("Storage permission denied"))
            return
        }
        if (!granted) {
            pendingDownload = null
            emit(BrowserEffect.ShowMessage("Storage permission needed to download"))
            return
        }
        // Retry now that the permission exists; the dispatcher will no longer ask for it.
        pendingDownload = null
        queueDownload(request)
    }

    // endregion

    // region bookmarks, settings, helpers

    fun toggleBookmark() {
        val tab = _state.value.currentTab ?: return
        if (tab.isStartPage) {
            emit(BrowserEffect.ShowMessage("Open a page first"))
            return
        }
        viewModelScope.launch {
            val added = bookmarkRepository.toggle(tab.url, tab.title)
            emit(
                BrowserEffect.ShowMessage(
                    if (added) "Bookmark added" else "Bookmark removed"
                )
            )
        }
    }

    private fun updateSettings(transform: AppSettings.() -> AppSettings) {
        viewModelScope.launch { settingsRepository.update { it.transform() } }
    }

    private fun dismissOverlay() = _state.update { it.copy(overlay = BrowserOverlay.None) }

    private fun applyToCurrentTab(transform: (BrowserTab) -> BrowserTab) {
        val id = _state.value.currentTabId
        synchronized(lock) {
            _state.update { current ->
                current.copy(
                    tabs = current.tabs.map { if (it.id == id) transform(it) else it }
                )
            }
        }
    }

    /** Session persistence is debounced so a burst of WebView callbacks does not thrash disk. */
    private fun scheduleSessionSave() {
        saveSessionJob?.cancel()
        saveSessionJob = viewModelScope.launch {
            delay(SESSION_SAVE_DEBOUNCE_MS)
            val snapshot = _state.value
            withContext(io) {
                settingsRepository.saveSession(snapshot.tabs, snapshot.currentTabId)
            }
        }
    }

    private fun emit(effect: BrowserEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    override fun onCleared() {
        val snapshot = _state.value
        // viewModelScope is cancelled before this runs, so the flush needs its own scope.
        saveSessionJob?.cancel()
        flushScope.launch {
            runCatching { settingsRepository.saveSession(snapshot.tabs, snapshot.currentTabId) }
        }
        super.onCleared()
    }

    private companion object {
        const val SESSION_SAVE_DEBOUNCE_MS = 600L
    }
}