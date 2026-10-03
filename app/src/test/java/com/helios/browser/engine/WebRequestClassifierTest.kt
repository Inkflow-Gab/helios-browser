package com.helios.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the content-type inference that [WebRequestClassifier] performs.
 *
 * This matters more than it looks. Filter lists are written against exact resource types: `$script`
 * must not touch an image, `$image` must not touch the page, and the `@@` exceptions that keep sites
 * working only fire for the right type. Android gives `shouldInterceptRequest` no resource type at
 * all, so every misclassification is a request judged by the wrong rules.
 */
class WebRequestClassifierTest {

    @Test
    fun `main frame wins over everything else`() {
        // Even with a .js URL: the top-level document is not a subresource, and letting the
        // extension table decide here would type a navigation as a script.
        assertEquals(
            WebRequestClassifier.MAIN_FRAME,
            WebRequestClassifier.classify("https://x.test/app.js", "*/*", isForMainFrame = true)
        )
        assertEquals(
            WebRequestClassifier.MAIN_FRAME,
            WebRequestClassifier.classify("https://x.test/", null, isForMainFrame = true)
        )
    }

    @Test
    fun `script extensions are recognised`() {
        for (path in listOf("/a.js", "/a.mjs", "/a.cjs", "/deep/nested/path/app.JS")) {
            assertEquals(
                "expected $path to be a script",
                WebRequestClassifier.SCRIPT,
                WebRequestClassifier.classify("https://x.test$path", "*/*", isForMainFrame = false)
            )
        }
    }

    @Test
    fun `stylesheet image font and media extensions are recognised`() {
        val cases = mapOf(
            "/a.css" to WebRequestClassifier.STYLESHEET,
            "/a.png" to WebRequestClassifier.IMAGE,
            "/a.SVG" to WebRequestClassifier.IMAGE,
            "/a.avif" to WebRequestClassifier.IMAGE,
            "/a.woff2" to WebRequestClassifier.FONT,
            "/a.ttf" to WebRequestClassifier.FONT,
            "/a.mp4" to WebRequestClassifier.MEDIA,
            "/a.webm" to WebRequestClassifier.MEDIA,
            "/a.m4a" to WebRequestClassifier.MEDIA
        )
        for ((path, expected) in cases) {
            assertEquals(
                "expected $path to be $expected",
                expected,
                WebRequestClassifier.classify("https://x.test$path", "*/*", isForMainFrame = false)
            )
        }
    }

    @Test
    fun `html that is not the main frame is a sub frame`() {
        assertEquals(
            WebRequestClassifier.SUB_FRAME,
            WebRequestClassifier.classify(
                "https://x.test/embed.html",
                "text/html",
                isForMainFrame = false
            )
        )
    }

    @Test
    fun `query strings do not decide the type`() {
        // A download link ending in .pdf?file=tracker.js is not a script, and a host must never
        // contribute an extension either.
        assertEquals(
            WebRequestClassifier.OTHER,
            WebRequestClassifier.classify(
                "https://x.test/get?file=thing.js",
                "application/octet-stream",
                isForMainFrame = false
            )
        )
        assertEquals(
            WebRequestClassifier.OTHER,
            WebRequestClassifier.classify("https://something.com", null, isForMainFrame = false)
        )
    }

    @Test
    fun `accept header is used when the url has no extension`() {
        assertEquals(
            WebRequestClassifier.STYLESHEET,
            WebRequestClassifier.classify(
                "https://x.test/styles",
                "text/css,*/*;q=0.1",
                isForMainFrame = false
            )
        )
        assertEquals(
            WebRequestClassifier.IMAGE,
            WebRequestClassifier.classify(
                "https://x.test/pixel",
                "image/avif,image/webp,*/*",
                isForMainFrame = false
            )
        )
        assertEquals(
            WebRequestClassifier.FONT,
            WebRequestClassifier.classify(
                "https://x.test/font",
                "font/woff2,*/*",
                isForMainFrame = false
            )
        )
        // No extension and an Accept of `*/*` is genuinely ambiguous, and `other` is the honest
        // answer: `other` still matches every untyped rule, so nothing is silently skipped.
        assertEquals(
            WebRequestClassifier.OTHER,
            WebRequestClassifier.classify(
                "https://x.test/runtime",
                "*/*",
                isForMainFrame = false
            )
        )
    }

    @Test
    fun `x requested with implies fetch`() {
        assertEquals(
            WebRequestClassifier.XML_HTTP_REQUEST,
            WebRequestClassifier.classify(
                "https://x.test/collect",
                "*/*",
                isForMainFrame = false,
                hasXRequestedWith = true
            )
        )
    }

    @Test
    fun `x requested with outranks the accept header`() {
        // A fetch returning JSON must be typed xhr, not sub_frame, even when a browser sends a
        // text/html Accept along with it.
        assertEquals(
            WebRequestClassifier.XML_HTTP_REQUEST,
            WebRequestClassifier.classify(
                "https://x.test/api",
                "text/html",
                isForMainFrame = false,
                hasXRequestedWith = true
            )
        )
    }

    @Test
    fun `null accept and empty url fall back to other`() {
        assertEquals(
            WebRequestClassifier.OTHER,
            WebRequestClassifier.classify("https://x.test/", null, isForMainFrame = false)
        )
        assertEquals(
            WebRequestClassifier.OTHER,
            WebRequestClassifier.classify("", null, isForMainFrame = false)
        )
    }

    @Test
    fun `fragment is stripped before looking at the extension`() {
        assertEquals(
            WebRequestClassifier.IMAGE,
            WebRequestClassifier.classify("https://x.test/a.png#zoom", null, isForMainFrame = false)
        )
    }

    @Test
    fun `tokens are the ones adblock rust understands`() {
        // A typo here is invisible until a rule silently stops matching, so the set is pinned.
        val emitted = listOf(
            WebRequestClassifier.MAIN_FRAME,
            WebRequestClassifier.SUB_FRAME,
            WebRequestClassifier.SCRIPT,
            WebRequestClassifier.STYLESHEET,
            WebRequestClassifier.IMAGE,
            WebRequestClassifier.FONT,
            WebRequestClassifier.MEDIA,
            WebRequestClassifier.XML_HTTP_REQUEST,
            WebRequestClassifier.BEACON,
            WebRequestClassifier.OTHER
        )
        val expected = setOf(
            "main_frame", "sub_frame", "script", "stylesheet", "image",
            "font", "media", "xhr", "beacon", "other"
        )
        assertEquals(expected, emitted.toSet())
        assertFalse(emitted.any { it.isBlank() })
        assertTrue(emitted.all { it == it.lowercase() })
    }
}
