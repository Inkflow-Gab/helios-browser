package com.helios.browser.ui.onboarding

import androidx.compose.runtime.Immutable
import com.helios.browser.domain.model.AppSettings
import com.helios.browser.domain.model.OmniboxPosition
import com.helios.browser.domain.model.SearchEngine

@Immutable
data class OnboardingState(
    val page: Int = 0,
    val draft: AppSettings = AppSettings.PRIVACY_DEFAULTS,
    /** False until DataStore has answered, so the first page never flashes the wrong defaults. */
    val ready: Boolean = false,
    /**
     * Mirrors the persisted `onboardingCompleted` flag. Kept separate from [draft] because the
     * draft is user-editable while this is owned by DataStore: setup finishing is what flips it,
     * and the host swaps itself to the browser when it turns true.
     */
    val completed: Boolean = false
) {
    val isLastPage: Boolean get() = page == LAST_PAGE

    companion object {
        /** Welcome, privacy defaults, permission explainer, search engine, summary. */
        const val PAGE_COUNT = 5
        const val LAST_PAGE = PAGE_COUNT - 1

        const val PAGE_WELCOME = 0
        const val PAGE_PRIVACY = 1
        const val PAGE_PERMISSIONS = 2
        const val PAGE_SEARCH = 3
        const val PAGE_SUMMARY = 4
    }
}

sealed interface OnboardingIntent {
    data object Next : OnboardingIntent
    data object Back : OnboardingIntent
    data class GoToPage(val page: Int) : OnboardingIntent
    data object Skip : OnboardingIntent

    data class SetSearchEngine(val engine: SearchEngine) : OnboardingIntent
    data class SetOmniboxPosition(val position: OmniboxPosition) : OnboardingIntent
    data class SetBlockAds(val enabled: Boolean) : OnboardingIntent
    data class SetCosmeticFilters(val enabled: Boolean) : OnboardingIntent
    data class SetUpgradeHttps(val enabled: Boolean) : OnboardingIntent
    data class SetSaveHistory(val enabled: Boolean) : OnboardingIntent
    data class SetSafeBrowsing(val enabled: Boolean) : OnboardingIntent

    data object Finish : OnboardingIntent
}