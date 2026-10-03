package com.helios.browser.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.helios.browser.di.IoDispatcher
import com.helios.browser.domain.model.AppSettings
import com.helios.browser.domain.model.BrowserTab
import com.helios.browser.domain.model.OmniboxPosition
import com.helios.browser.domain.model.SearchEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.heliosDataStore: DataStore<Preferences> by preferencesDataStore(name = "helios")

/** Tabs worth restoring next launch. Incognito tabs are never written to disk. */
data class SessionSnapshot(
    val tabs: List<BrowserTab>,
    val currentTabId: String?
)

/**
 * Owns [AppSettings] and the lightweight session snapshot.
 *
 * This is the single source of truth for settings: both view models observe [settings], so a write
 * from first-run setup is immediately visible to the browser UI without any extra plumbing.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    private val store: DataStore<Preferences> get() = context.heliosDataStore

    val settings: Flow<AppSettings> = store.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { it.toSettings() }

    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings): Unit = withContext(io) {
        store.edit { prefs ->
            val next = transform(prefs.toSettings())
            prefs[KEY_ONBOARDING] = next.onboardingCompleted
            prefs[KEY_SEARCH_ENGINE] = next.searchEngine.name
            prefs[KEY_BLOCK_ADS] = next.blockAdsAndTrackers
            prefs[KEY_COSMETIC] = next.cosmeticFilters
            prefs[KEY_CLEAN_URLS] = next.cleanTrackingParams
            prefs[KEY_UPGRADE_HTTPS] = next.upgradeToHttps
            prefs[KEY_SAFE_BROWSING] = next.safeBrowsingEnabled
            prefs[KEY_DESKTOP_DEFAULT] = next.desktopModeByDefault
            prefs[KEY_SAVE_HISTORY] = next.saveHistoryEnabled
            prefs[KEY_OMNIBOX] = next.omniboxPosition.name
        }
    }

    /** Writes the whole settings object at once. Used when first-run setup finishes. */
    suspend fun replaceAll(next: AppSettings): Unit = withContext(io) {
        update { next }
    }

    suspend fun saveSession(tabs: List<BrowserTab>, currentTabId: String?): Unit = withContext(io) {
        val persistable = tabs.filterNot { it.isIncognito }
        if (persistable.isEmpty()) {
            store.edit { it.remove(KEY_SESSION) }
            return@withContext
        }
        val array = JSONArray()
        persistable.forEach { tab ->
            array.put(
                JSONObject().apply {
                    put(FIELD_ID, tab.id)
                    put(FIELD_URL, tab.url)
                    put(FIELD_TITLE, tab.title)
                    put(FIELD_DESKTOP, tab.isDesktopMode)
                }
            )
        }
        val root = JSONObject().apply {
            put(FIELD_CURRENT, currentTabId ?: "")
            put(FIELD_TABS, array)
        }
        store.edit { it[KEY_SESSION] = root.toString() }
    }

    suspend fun loadSession(): SessionSnapshot? = withContext(io) {
        val raw = store.data.first()[KEY_SESSION] ?: return@withContext null
        runCatching { parseSession(raw) }.getOrElse {
            Log.w(TAG, "Discarding unreadable session snapshot", it)
            null
        }
    }

    suspend fun clearSession(): Unit = withContext(io) {
        store.edit { it.remove(KEY_SESSION) }
    }

    private fun parseSession(raw: String): SessionSnapshot? {
        val root = JSONObject(raw)
        val array = root.optJSONArray(FIELD_TABS) ?: return null
        val tabs = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString(FIELD_ID).takeIf { it.isNotBlank() } ?: continue
                val url = item.optString(FIELD_URL).takeIf { it.isNotBlank() }
                    ?: BrowserTab.START_PAGE_URL
                add(
                    BrowserTab(
                        id = id,
                        url = url,
                        title = item.optString(FIELD_TITLE).ifBlank { BrowserTab.DEFAULT_TITLE },
                        isDesktopMode = item.optBoolean(FIELD_DESKTOP, false)
                    )
                )
            }
        }
        if (tabs.isEmpty()) return null
        val current = root.optString(FIELD_CURRENT).takeIf { it.isNotBlank() }
        return SessionSnapshot(tabs = tabs, currentTabId = current)
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings.PRIVACY_DEFAULTS
        return AppSettings(
            onboardingCompleted = this[KEY_ONBOARDING] ?: defaults.onboardingCompleted,
            searchEngine = this[KEY_SEARCH_ENGINE]
                ?.let { name -> SearchEngine.entries.firstOrNull { it.name == name } }
                ?: defaults.searchEngine,
            blockAdsAndTrackers = this[KEY_BLOCK_ADS] ?: defaults.blockAdsAndTrackers,
            cosmeticFilters = this[KEY_COSMETIC] ?: defaults.cosmeticFilters,
            cleanTrackingParams = this[KEY_CLEAN_URLS] ?: defaults.cleanTrackingParams,
            upgradeToHttps = this[KEY_UPGRADE_HTTPS] ?: defaults.upgradeToHttps,
            safeBrowsingEnabled = this[KEY_SAFE_BROWSING] ?: defaults.safeBrowsingEnabled,
            desktopModeByDefault = this[KEY_DESKTOP_DEFAULT] ?: defaults.desktopModeByDefault,
            saveHistoryEnabled = this[KEY_SAVE_HISTORY] ?: defaults.saveHistoryEnabled,
            omniboxPosition = OmniboxPosition.fromName(this[KEY_OMNIBOX])
        )
    }

    companion object {
        private const val TAG = "SettingsRepository"

        private val KEY_ONBOARDING = booleanPreferencesKey("onboarding_completed")
        private val KEY_SEARCH_ENGINE = stringPreferencesKey("search_engine")
        private val KEY_BLOCK_ADS = booleanPreferencesKey("block_ads")
        private val KEY_COSMETIC = booleanPreferencesKey("cosmetic_filters")
        private val KEY_CLEAN_URLS = booleanPreferencesKey("clean_tracking_params")
        private val KEY_UPGRADE_HTTPS = booleanPreferencesKey("upgrade_https")
        private val KEY_SAFE_BROWSING = booleanPreferencesKey("safe_browsing")
        private val KEY_DESKTOP_DEFAULT = booleanPreferencesKey("desktop_default")
        private val KEY_SAVE_HISTORY = booleanPreferencesKey("save_history")
        private val KEY_OMNIBOX = stringPreferencesKey("omnibox_position")
        private val KEY_SESSION = stringPreferencesKey("session")

        private const val FIELD_TABS = "tabs"
        private const val FIELD_CURRENT = "current"
        private const val FIELD_ID = "id"
        private const val FIELD_URL = "url"
        private const val FIELD_TITLE = "title"
        private const val FIELD_DESKTOP = "desktop"
    }
}