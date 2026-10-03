package com.helios.browser.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.helios.browser.data.repository.SettingsRepository
import com.helios.browser.domain.model.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives first-run setup.
 *
 * Choices accumulate in [OnboardingState.draft] and are written to DataStore in one go on
 * [OnboardingIntent.Finish], which flips `onboardingCompleted`. `BrowserViewModel` observes that
 * same flow, so the browser picks up the chosen search engine and shields without any handoff.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Seed the draft from what is stored, but only once: afterwards the draft belongs to the
            // user and must not be clobbered by later DataStore emissions.
            val stored = settingsRepository.settings.first()
            _state.update { it.copy(draft = stored, ready = true) }

            settingsRepository.settings.collect { latest ->
                _state.update { it.copy(completed = latest.onboardingCompleted) }
            }
        }
    }

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.Next -> _state.update {
                it.copy(page = (it.page + 1).coerceAtMost(OnboardingState.LAST_PAGE))
            }

            OnboardingIntent.Back -> _state.update {
                it.copy(page = (it.page - 1).coerceAtLeast(0))
            }

            is OnboardingIntent.GoToPage -> _state.update {
                it.copy(page = intent.page.coerceIn(0, OnboardingState.LAST_PAGE))
            }

            OnboardingIntent.Skip -> viewModelScope.launch {
                // Skipping keeps the privacy defaults and only marks setup as done.
                settingsRepository.replaceAll(_state.value.draft.copy(onboardingCompleted = true))
            }

            is OnboardingIntent.SetSearchEngine -> edit { copy(searchEngine = intent.engine) }
            is OnboardingIntent.SetOmniboxPosition -> edit {
                copy(omniboxPosition = intent.position)
            }
            is OnboardingIntent.SetBlockAds -> edit { copy(blockAdsAndTrackers = intent.enabled) }
            is OnboardingIntent.SetCosmeticFilters -> edit { copy(cosmeticFilters = intent.enabled) }
            is OnboardingIntent.SetUpgradeHttps -> edit { copy(upgradeToHttps = intent.enabled) }
            is OnboardingIntent.SetSaveHistory -> edit { copy(saveHistoryEnabled = intent.enabled) }
            is OnboardingIntent.SetSafeBrowsing -> edit { copy(safeBrowsingEnabled = intent.enabled) }

            OnboardingIntent.Finish -> viewModelScope.launch {
                settingsRepository.replaceAll(_state.value.draft.copy(onboardingCompleted = true))
            }
        }
    }

    private inline fun edit(transform: AppSettings.() -> AppSettings) {
        _state.update { current -> current.copy(draft = current.draft.transform()) }
    }
}