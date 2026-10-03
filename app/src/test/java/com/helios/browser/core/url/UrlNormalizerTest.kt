package com.helios.browser.core.url

import com.helios.browser.domain.model.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /**
     * Schemes with nothing after the colon are the ones a `contains("://")` guard misses, and they
     * are the common ones. `mailto:` in particular contains a dot, so `looksLikeUrl` accepts it —
     * which is how this used to come back as `https://mailto:someone@example.com`.
     */
    @Test
    fun `scheme-less bodies are not prefixed with https`() {
        val engine = SearchEngine.DUCKDUCKGO
        assertEquals("mailto:a@b.com", UrlNormalizer.resolve("mailto:a@b.com", engine, upgradeToHttps = true))
        assertEquals("tel:+15551234", UrlNormalizer.resolve("tel:+15551234", engine, upgradeToHttps = true))
        assertEquals("sms:+15551234", UrlNormalizer.resolve("sms:+15551234", engine, upgradeToHttps = true))
        assertEquals("geo:37.7,-122.4", UrlNormalizer.resolve("geo:37.7,-122.4", engine, upgradeToHttps = true))
        // And with the upgrade toggle off, they are still not rewritten.
        assertEquals("tel:+15551234", UrlNormalizer.resolve("tel:+15551234", engine, upgradeToHttps = false))
    }

    @Test
    fun `schemeOf recognises real schemes and rejects search text`() {
        assertEquals("mailto", UrlNormalizer.schemeOf("mailto:a@b.com"))
        assertEquals("https", UrlNormalizer.schemeOf("HTTPS://example.com"))
        assertEquals("intent", UrlNormalizer.schemeOf("intent://scan/#Intent;scheme=zxing;end"))
        // The whole prefix is the scheme: RFC 3986 allows ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ),
        // so + and . are part of it rather than a separator.
        assertEquals("x-custom+1.0", UrlNormalizer.schemeOf("x-custom+1.0:body"))

        // Not schemes.
        assertNull(UrlNormalizer.schemeOf("example.com"))
        assertNull(UrlNormalizer.schemeOf("hello world"))
        assertNull(UrlNormalizer.schemeOf("what time: now"))
        assertNull(UrlNormalizer.schemeOf(":leading colon"))
        assertNull(UrlNormalizer.schemeOf("1abc:thing"))
        assertNull(UrlNormalizer.schemeOf(""))
    }

    /** A colon inside a search term must not turn the whole thing into a scheme. */
    @Test
    fun `a colon in a search query does not make it a url`() {
        val result = UrlNormalizer.resolve("ratio 3:2 explained", SearchEngine.DUCKDUCKGO, upgradeToHttps = true)
        assertTrue("expected a search URL, got $result", result.startsWith("https://duckduckgo.com/?q="))
    }
}