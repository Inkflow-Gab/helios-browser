package com.helios.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure matching core of [AdBlockEngine]. `cleanUrl` needs `android.net.Uri` and is
 * exercised on-device instead, so it is deliberately absent here.
 */
class AdBlockEngineTest {

    @Test
    fun `exact host matches`() {
        assertTrue(AdBlockEngine.matchesHostAndPath("criteo.com", "/"))
        assertTrue(AdBlockEngine.matchesHostAndPath("doubleclick.net", "/ad"))
        assertTrue(AdBlockEngine.matchesHostAndPath("google-analytics.com", "/collect"))
    }

    @Test
    fun `subdomains of blocked hosts match`() {
        assertTrue(AdBlockEngine.matchesHostAndPath("static.criteo.com", "/js"))
        assertTrue(AdBlockEngine.matchesHostAndPath("adservice.google.com", "/x"))
        assertTrue(AdBlockEngine.matchesHostAndPath("a.b.connect.facebook.net", "/x"))
    }

    @Test
    fun `host matching is case insensitive and trailing-dot safe`() {
        assertTrue(AdBlockEngine.matchesHostAndPath("CRITEO.COM", "/"))
        assertTrue(AdBlockEngine.matchesHostAndPath("criteo.com.", "/"))
    }

    @Test
    fun `bare tld never matches a suffix`() {
        // Guards the loop bounds: "com" must not be treated as a blocked parent domain.
        assertFalse(AdBlockEngine.matchesHostAndPath("example.com", "/"))
        assertFalse(AdBlockEngine.matchesHostAndPath("notcriteo.com", "/"))
    }

    @Test
    fun `unrelated hosts do not match`() {
        assertFalse(AdBlockEngine.matchesHostAndPath("github.com", "/Inkflow-Gab/helios"))
        assertFalse(AdBlockEngine.matchesHostAndPath("wikipedia.org", "/wiki/Main_Page"))
        assertFalse(AdBlockEngine.matchesHostAndPath("news.ycombinator.com", "/news"))
    }

    @Test
    fun `ad path markers match regardless of host`() {
        assertTrue(AdBlockEngine.matchesHostAndPath("cdn.example.org", "/assets/ads.js"))
        assertTrue(AdBlockEngine.matchesHostAndPath("cdn.example.org", "/prebid.js"))
        assertTrue(AdBlockEngine.matchesHostAndPath("cdn.example.org", "/gtag/js"))
        assertTrue(AdBlockEngine.matchesHostAndPath("cdn.example.org", "/x/pixel.js"))
        assertTrue(AdBlockEngine.matchesHostAndPath("cdn.example.org", "/adserver/serve"))
        assertTrue(AdBlockEngine.matchesHostAndPath("cdn.example.org", "/banner/adview?id=1"))
    }

    @Test
    fun `path matching is case insensitive`() {
        assertTrue(AdBlockEngine.matchesHostAndPath("cdn.example.org", "/ASSETS/ADS.JS"))
    }

    @Test
    fun `blank host is not blocked`() {
        assertFalse(AdBlockEngine.matchesHostAndPath(null, "/ads.js"))
        assertFalse(AdBlockEngine.matchesHostAndPath("", "/ads.js"))
    }

    @Test
    fun `disable switch short-circuits everything`() {
        assertFalse(
            AdBlockEngine.isAdOrTracker("https://criteo.com/anything", enabled = false)
        )
    }

    @Test
    fun `tracking parameter detection ignores case`() {
        assertTrue(AdBlockEngine.isTrackingParameter("utm_source"))
        assertTrue(AdBlockEngine.isTrackingParameter("UTM_Source"))
        assertTrue(AdBlockEngine.isTrackingParameter("fbclid"))
        assertTrue(AdBlockEngine.isTrackingParameter("gclid"))
        assertFalse(AdBlockEngine.isTrackingParameter("q"))
        assertFalse(AdBlockEngine.isTrackingParameter("page"))
    }

    @Test
    fun `sanitizeFileName strips directories and unsafe characters`() {
        assertEquals(
            "report.pdf",
            AdBlockEngine.sanitizeFileName("attachment; filename=\"report.pdf\"", "https://x.com")
        )
        assertEquals(
            "evil.php",
            AdBlockEngine.sanitizeFileName("../../etc/passwd", "https://x.com")
        )
        assertEquals(
            "my file (1).txt",
            AdBlockEngine.sanitizeFileName("my file (1).txt", "https://x.com")
        )
    }

    @Test
    fun `sanitizeFileName falls back to a host based name`() {
        assertEquals(
            "example.com-download",
            AdBlockEngine.sanitizeFileName(null, "https://example.com/file")
        )
        assertEquals("download", AdBlockEngine.sanitizeFileName(null, "not a url"))
    }
}