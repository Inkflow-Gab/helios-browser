package com.helios.browser.domain.model

/**
 * Every user-tunable browser setting, persisted as a single object in DataStore.
 *
 * DataStore is the single source of truth for these values: both [com.helios.browser.ui.browser.BrowserViewModel]
 * and [com.helios.browser.ui.onboarding.OnboardingViewModel] observe the same repository flow.
 */
data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val blockAdsAndTrackers: Boolean = true,
    val cosmeticFilters: Boolean = true,
    val cleanTrackingParams: Boolean = true,
    val upgradeToHttps: Boolean = true,
    val safeBrowsingEnabled: Boolean = true,
    val desktopModeByDefault: Boolean = false,
    val saveHistoryEnabled: Boolean = true,
    val omniboxPosition: OmniboxPosition = OmniboxPosition.BOTTOM
) {
    companion object {
        /** Privacy-first defaults used by first-run setup. */
        val PRIVACY_DEFAULTS = AppSettings()
    }
}

/**
 * The blocking knobs consulted by the WebView clients. Kept separate from [AppSettings] so the
 * WebView layer can read a cheap immutable snapshot from any thread.
 *
 * Safe Browsing lives here too even though it is not a blocking rule: it is applied per-WebView in
 * `WebViewFactory` at construction time, and this is the snapshot that reaches it.
 */
data class BlockingConfig(
    val blockAdsAndTrackers: Boolean = true,
    val cosmeticFilters: Boolean = true,
    val cleanTrackingParams: Boolean = true,
    val upgradeToHttps: Boolean = true,
    val safeBrowsingEnabled: Boolean = true
) {
    companion object {
        fun from(settings: AppSettings) = BlockingConfig(
            blockAdsAndTrackers = settings.blockAdsAndTrackers,
            cosmeticFilters = settings.cosmeticFilters,
            cleanTrackingParams = settings.cleanTrackingParams,
            upgradeToHttps = settings.upgradeToHttps,
            safeBrowsingEnabled = settings.safeBrowsingEnabled
        )
    }
}