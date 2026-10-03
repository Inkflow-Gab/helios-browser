package com.helios.browser.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `privacy defaults are the safe choice`() {
        val defaults = AppSettings.PRIVACY_DEFAULTS
        assertTrue(defaults.blockAdsAndTrackers)
        assertTrue(defaults.cosmeticFilters)
        assertTrue(defaults.cleanTrackingParams)
        assertTrue(defaults.upgradeToHttps)
        assertTrue(defaults.safeBrowsingEnabled)
    }

    @Test
    fun `onboarding starts incomplete`() {
        assertFalse(AppSettings.PRIVACY_DEFAULTS.onboardingCompleted)
    }

    @Test
    fun `blocking config mirrors the settings it came from`() {
        val settings = AppSettings(
            blockAdsAndTrackers = false,
            cosmeticFilters = true,
            cleanTrackingParams = false,
            upgradeToHttps = true
        )
        val config = BlockingConfig.from(settings)
        assertFalse(config.blockAdsAndTrackers)
        assertTrue(config.cosmeticFilters)
        assertFalse(config.cleanTrackingParams)
        assertTrue(config.upgradeToHttps)
    }

    @Test
    fun `blocking config defaults match privacy defaults`() {
        assertEquals(
            BlockingConfig.from(AppSettings.PRIVACY_DEFAULTS),
            BlockingConfig()
        )
    }
}