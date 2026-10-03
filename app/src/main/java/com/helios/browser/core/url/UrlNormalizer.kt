package com.helios.browser.core.url

import com.helios.browser.domain.model.SearchEngine
import java.util.Locale

/**
 * URL parsing and normalisation that deliberately avoids `android.net.Uri` so it can be unit
 * tested on a plain JVM. Runtime-only behaviour lives in the WebView clients instead.
 */
object UrlNormalizer {

    fun isWebUrl(url: String): Boolean =
        url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

    /** Rewrites `http://` to `https://`. Other schemes are returned untouched. */
    fun upgradeToHttps(url: String): String =
        if (url.startsWith("http://", ignoreCase = true)) {
            "https://" + url.substring("http://".length)
        } else {
            url
        }

    fun hostOf(url: String): String? = try {
        java.net.URI(url).host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    fun pathAndQueryOf(url: String): String = try {
        val uri = java.net.URI(url)
        buildString {
            append(uri.path ?: "")
            uri.query?.let { append('?').append(it) }
        }.lowercase(Locale.ROOT)
    } catch (_: Exception) {
        ""
    }

    /**
     * Heuristic used by the omnibox to decide whether input is a URL or a search term.
     * Requires a scheme, a dot in the host and either an alphabetic TLD or an IPv4 literal.
     */
    fun looksLikeUrl(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.any { it.isWhitespace() }) return false
        if (trimmed.startsWith("localhost", ignoreCase = true)) return true

        val candidate = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val host = hostOf(candidate) ?: return false
        val tld = host.substringAfterLast('.', "")
        if (tld.isEmpty()) return false
        if (tld.all { it.isDigit() }) return true // IPv4 literal
        return tld.length >= 2 && tld.all { it.isLetter() }
    }

    /**
     * Turns raw omnibox input into a URL that is safe to hand to a WebView: web URLs are upgraded
     * to HTTPS when [upgradeToHttps] is set, bare hostnames get a scheme, everything else is
     * searched with [engine].
     */
    fun resolve(input: String, engine: SearchEngine, upgradeToHttps: Boolean): String {
        val trimmed = input.trim()
        if (isWebUrl(trimmed)) {
            return if (upgradeToHttps) upgradeToHttps(trimmed) else trimmed
        }
        if (trimmed.contains("://")) return trimmed // some other scheme: let the browser decide
        if (looksLikeUrl(trimmed)) {
            val withScheme = "https://$trimmed"
            return if (upgradeToHttps) withScheme else withScheme.replaceFirst("https://", "http://")
        }
        return engine.searchUrlFor(trimmed)
    }
}