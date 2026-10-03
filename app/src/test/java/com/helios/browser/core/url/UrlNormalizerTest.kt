package com.helios.browser.core.url

import com.helios.browser.domain.model.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain JVM tests: [UrlNormalizer] avoids `android.net.Uri` precisely so it can be tested here.
 */
class UrlNormalizerTest {

    @Test
    fun `isWebUrl accepts only http and https`() {
        assertTrue(UrlNormalizer.isWebUrl("https://example.com"))
        assertTrue(UrlNormalizer.isWebUrl("http://example.com"))
        assertTrue(UrlNormalizer.isWebUrl("HTTPS://EXAMPLE.COM"))
        assertFalse(UrlNormalizer.isWebUrl("mailto:someone@example.com"))
        assertFalse(UrlNormalizer.isWebUrl("tel:+15551234567"))
        assertFalse(UrlNormalizer.isWebUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(UrlNormalizer.isWebUrl("helios://start"))
        assertFalse(UrlNormalizer.isWebUrl("about:blank"))
    }

    @Test
    fun `upgradeToHttps rewrites http only`() {
        assertEquals("https://example.com/a", UrlNormalizer.upgradeToHttps("http://example.com/a"))
        assertEquals("https://example.com", UrlNormalizer.upgradeToHttps("HTTP://example.com"))
        assertEquals("https://example.com", UrlNormalizer.upgradeToHttps("https://example.com"))
        assertEquals(
            "mailto:a@b.com",
            UrlNormalizer.upgradeToHttps("mailto:a@b.com")
        )
    }

    @Test
    fun `hostOf lowercases and rejects junk`() {
        assertEquals("example.com", UrlNormalizer.hostOf("https://EXAMPLE.com/path"))
        assertEquals("sub.example.co.uk", UrlNormalizer.hostOf("https://sub.example.co.uk"))
        assertEquals(null, UrlNormalizer.hostOf("not a url"))
        assertEquals(null, UrlNormalizer.hostOf(""))
    }

    @Test
    fun `looksLikeUrl requires a plausible domain`() {
        assertTrue(UrlNormalizer.looksLikeUrl("example.com"))
        assertTrue(UrlNormalizer.looksLikeUrl("https://example.com"))
        assertTrue(UrlNormalizer.looksLikeUrl("sub.example.co.uk/path"))
        assertTrue(UrlNormalizer.looksLikeUrl("localhost"))
        assertTrue(UrlNormalizer.looksLikeUrl("192.168.1.1"))

        assertFalse(UrlNormalizer.looksLikeUrl("how to cook rice"))
        assertFalse(UrlNormalizer.looksLikeUrl("hello"))
        assertFalse(UrlNormalizer.looksLikeUrl("what is a browser"))
        assertFalse(UrlNormalizer.looksLikeUrl(""))
    }

    @Test
    fun `resolve upgrades http when https enforcement is on`() {
        assertEquals(
            "https://example.com/a",
            UrlNormalizer.resolve("http://example.com/a", SearchEngine.DUCKDUCKGO, upgradeToHttps = true)
        )
    }

    @Test
    fun `resolve keeps http when https enforcement is off`() {
        assertEquals(
            "http://example.com/a",
            UrlNormalizer.resolve("http://example.com/a", SearchEngine.DUCKDUCKGO, upgradeToHttps = false)
        )
    }

    @Test
    fun `resolve adds a scheme to bare hosts`() {
        assertEquals(
            "https://example.com",
            UrlNormalizer.resolve("example.com", SearchEngine.DUCKDUCKGO, upgradeToHttps = true)
        )
    }

    @Test
    fun `resolve searches multi-word input`() {
        val result = UrlNormalizer.resolve("how to cook rice", SearchEngine.DUCKDUCKGO, upgradeToHttps = true)
        assertTrue(result.startsWith("https://duckduckgo.com/?q="))
        assertTrue(result.contains("how+to+cook+rice"))
    }

    @Test
    fun `resolve escapes query characters`() {
        val result = UrlNormalizer.resolve("a & b", SearchEngine.DUCKDUCKGO, upgradeToHttps = true)
        // URLEncoder turns spaces into '+' and '&' into %26, so the query cannot be injected.
        assertFalse(result.substringAfter("?q=").contains("&"))
    }

    @Test
    fun `resolve passes through other schemes untouched`() {
        assertEquals(
            "mailto:someone@example.com",
            UrlNormalizer.resolve("mailto:someone@example.com", SearchEngine.DUCKDUCKGO, upgradeToHttps = true)
        )
    }
}