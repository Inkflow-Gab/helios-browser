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
        // Only the final segment survives, so a traversal cannot escape the download folder. (This
        // expectation used to read "evil.php", which was a copy-paste slip: the input ends in
        // passwd, and the point of the case is that only "passwd" is left.)
        assertEquals(
            "passwd",
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

    /**
     * Servers send `Content-Disposition`, not a bare filename. Sanitising the whole header as if it
     * were the filename produced `attachment__filename__report_pdf_`, because it has no path
     * separators for the traversal guard to key off.
     */
    @Test
    fun `sanitizeFileName parses Content-Disposition headers`() {
        assertEquals(
            "report.pdf",
            AdBlockEngine.sanitizeFileName("attachment; filename=\"report.pdf\"", "https://x.com")
        )
        assertEquals(
            "photo.jpg",
            AdBlockEngine.sanitizeFileName("attachment; filename=photo.jpg", "https://x.com")
        )
        // A bare filename with no parameters must still work.
        assertEquals("notes.txt", AdBlockEngine.sanitizeFileName("notes.txt", "https://x.com"))
        // RFC 5987 extended form wins over the plain one, and its escapes are decoded so the user
        // gets a real name rather than caf_C3_A9.txt.
        assertEquals(
            "café.txt",
            AdBlockEngine.sanitizeFileName(
                "attachment; filename=\"fallback.txt\"; filename*=UTF-8''caf%C3%A9.txt",
                "https://x.com"
            )
        )
        // A literal + is not a space: URLDecoder would break this, so the decode is hand-rolled.
        assertEquals(
            "a+b.txt",
            AdBlockEngine.sanitizeFileName(
                "attachment; filename*=UTF-8''a+b.txt",
                "https://x.com"
            )
        )
    }

    /** A hostile header must not be able to reintroduce a path. */
    @Test
    fun `sanitizeFileName cannot be walked out of the download folder`() {
        assertEquals(
            "passwd",
            AdBlockEngine.sanitizeFileName("attachment; filename=\"../../etc/passwd\"", "https://x.com")
        )
        assertEquals(
            "evil.exe",
            AdBlockEngine.sanitizeFileName("attachment; filename=\"..\\\\..\\\\evil.exe\"", "https://x.com")
        )
    }

    /**
     * The fallback host list used before the native engine is ready. A parent domain has to be
     * listed explicitly: `matchesHostAndPath` walks *up* from a host to its parents, so listing
     * only the subdomains never covers the bare domain.
     */
    @Test
    fun `parent domains are listed so bare hosts are covered`() {
        assertTrue(AdBlockEngine.matchesHostAndPath("doubleclick.net", "/"))
        assertTrue(AdBlockEngine.matchesHostAndPath("doubleclick.net", "/ad"))
        assertTrue(AdBlockEngine.matchesHostAndPath("securepubads.g.doubleclick.net", "/x"))
        assertTrue(AdBlockEngine.matchesHostAndPath("googlesyndication.com", "/"))
        // Not over-broad: an unrelated site must be untouched.
        assertFalse(AdBlockEngine.matchesHostAndPath("notdoubleclick.net", "/"))
    }
}