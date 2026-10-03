package com.helios.browser.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTabTest {

    @Test
    fun `new tab starts on the start page`() {
        val tab = BrowserTab.start()
        assertEquals(BrowserTab.START_PAGE_URL, tab.url)
        assertTrue(tab.isStartPage)
        assertFalse(tab.isSecure)
    }

    @Test
    fun `secure flag only reflects https`() {
        assertTrue(BrowserTab(id = "1", url = "https://example.com").isSecure)
        assertFalse(BrowserTab(id = "1", url = "http://example.com").isSecure)
        // Schemes are case-insensitive per RFC 3986, so an uppercase HTTPS is still secure and the
        // lock icon must show. This asserts the flag, not the string.
        assertTrue(BrowserTab(id = "1", url = "HTTPS://EXAMPLE.COM").isSecure)
        // The start page sentinel is not a page at all, so it is not secure.
        assertFalse(BrowserTab(id = "1", url = BrowserTab.START_PAGE_URL).isSecure)
    }

    @Test
    fun `display host falls back for the start page`() {
        assertEquals("Search or type URL", BrowserTab.start().displayHost)
        assertEquals(
            "example.com",
            BrowserTab(id = "1", url = "https://example.com/deep/path?q=1").displayHost
        )
    }

    @Test
    fun `ids are unique per tab`() {
        val ids = (1..500).map { BrowserTab.start().id }
        assertEquals(500, ids.toSet().size)
    }

    @Test
    fun `copy produces a distinct instance with the same id`() {
        val tab = BrowserTab.start()
        val updated = tab.copy(progress = 50)
        assertEquals(tab.id, updated.id)
        assertEquals(50, updated.progress)
        assertEquals(0, tab.progress)
    }
}