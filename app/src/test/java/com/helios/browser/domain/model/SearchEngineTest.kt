package com.helios.browser.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    @Test
    fun `every engine encodes the query`() {
        SearchEngine.entries.forEach { engine ->
            val url = engine.searchUrlFor("privacy browser")
            assertTrue(url.startsWith("https://"))
            assertTrue(url.contains("privacy+browser"))
            assertTrue(url.contains("%3A") || url.contains("://"))
        }
    }

    @Test
    fun `special characters are percent encoded`() {
        val url = SearchEngine.DUCKDUCKGO.searchUrlFor("a&b=c")
        // The raw query must not be able to append its own parameters.
        assertEquals(-1, url.indexOf("a&b"))
    }

    @Test
    fun `every engine has an https home url`() {
        SearchEngine.entries.forEach { engine ->
            assertTrue(engine.homeUrl.startsWith("https://"))
            assertTrue(engine.searchUrl.contains("%s"))
        }
    }
}