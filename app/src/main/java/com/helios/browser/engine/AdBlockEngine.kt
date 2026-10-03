package com.helios.browser.engine

import com.helios.browser.core.url.UrlNormalizer
import java.util.Locale

/**
 * Entry point for blocking decisions, plus the small pure helpers that do not need a rule engine.
 *
 * ## Where the decision is actually made
 * [shouldBlock] hands everything to [NativeAdBlock] — adblock-rust, the engine Brave ships — with
 * EasyList and EasyPrivacy loaded. That is the real answer and it is authoritative.
 *
 * The compiled-in [blockedDomains] set below is a **fallback for the window before the engine is
 * ready**, which is the first few seconds of a cold start. It is a plain host list with no option
 * parsing, so it may only be consulted when there is no engine at all: running it alongside a
 * loaded engine would over-block, because it cannot tell a site's own tracker from a third party's.
 * [shouldBlock] spells that ordering out.
 *
 * ## Testing
 * [matchesHostAndPath], [isTrackingParameter] and [sanitizeFileName] are free of `android.net.Uri`
 * and are unit tested on a plain JVM. [shouldBlock] needs the native library, so it is not covered
 * by `AdBlockEngineTest`; the engine's own matching is covered by the Rust tests in
 * `native/helios-adblock`.
 */
object AdBlockEngine {

    /**
     * The decision, in one place.
     *
     * @param url the request URL.
     * @param sourceUrl the top-level document making the request, or empty when unknown. Passing
     *   this is not optional: `$third-party` and `$domain=` rules are resolved against it, and
     *   without it the engine must assume every request is third-party, which takes sites off
     *   their own CDNs.
     * @param requestType an adblock-rust content type token. See [WebRequestClassifier].
     * @param method the HTTP method.
     * @param enabled the user's blocking switch.
     */
    fun shouldBlock(
        url: String,
        sourceUrl: String,
        requestType: String,
        method: String = "GET",
        enabled: Boolean = true
    ): Boolean {
        if (!enabled) return false
        // Loaded engine wins outright, including when it says "allow". Falling through to the host
        // list on a negative result would re-introduce every false positive the engine just
        // corrected — the `/adserver/` path markers alone would break first-party scripts.
        if (NativeAdBlock.isReady) {
            return NativeAdBlock.shouldBlock(url, sourceUrl, requestType, method)
        }
        return isAdOrTracker(url)
    }

    /** Ad exchanges, analytics endpoints and telemetry collectors. */
    val blockedDomains: Set<String> = setOf(
        // Google ad services and analytics
        "pagead2.googlesyndication.com",
        "googleads.g.doubleclick.net",
        "adservice.google.com",
        "pubads.g.doubleclick.net",
        "tpc.googlesyndication.com",
        "securepubads.g.doubleclick.net",
        "googletagmanager.com",
        "google-analytics.com",
        "analytics.google.com",
        "stats.g.doubleclick.net",

        // Ad exchanges and networks
        "criteo.com", "criteo.net", "taboola.com", "outbrain.com", "adnxs.com",
        "rubiconproject.com", "openx.net", "casalemedia.com", "advertising.com",
        "exponential.com", "media.net", "bidswitch.net", "pubmatic.com",
        "adcolony.com", "applovin.com", "unityads.unity3d.com", "vungle.com",
        "ironsrc.com", "chartboost.com", "inmobi.com", "moatads.com", "adroll.com",
        "admob.com", "adsafeprotected.com", "smartadserver.com", "tribalfusion.com",
        "popads.net", "propellerads.com", "exoclick.com", "juicyads.com",
        "clickadu.com", "adcash.com",

        // Trackers and telemetry
        "connect.facebook.net", "pixel.facebook.com", "analytics.tiktok.com",
        "ads-twitter.com", "analytics.twitter.com", "bat.bing.com", "hotjar.com",
        "clarity.ms", "segment.io", "segment.com", "branch.io", "adjust.com",
        "appsflyer.com", "mixpanel.com", "amplitude.com", "scorecardresearch.com",
        "quantserve.com", "newrelic.com", "sentry.io", "bugsnag.com"
    )

    /** Query parameters stripped from navigations. */
    val trackingParameters: Set<String> = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_name", "fbclid", "gclid", "msclkid", "mc_eid", "yclid", "igshid",
        "_hsenc", "_hsmi", "wbraid", "gbraid"
    )

    private val adPathMarkers = listOf(
        "/adserver/", "/ads.js", "/prebid.js", "/gtag/js", "/pixel.js", "/adview"
    )

    /**
     * Pure matching core for the pre-engine fallback list.
     *
     * Parent-domain matching walks the host's labels rather than scanning the whole blocklist, so
     * the cost is proportional to the number of labels instead of the size of the list. The bare
     * TLD is never tested, so `something.com` cannot match `com`.
     */
    fun matchesHostAndPath(host: String?, pathAndQuery: String): Boolean {
        if (host.isNullOrBlank()) return false
        val normalisedHost = host.lowercase(Locale.ROOT).trimEnd('.')
        if (normalisedHost in blockedDomains) return true

        val labels = normalisedHost.split('.')
        if (labels.size > 2) {
            for (index in 1 until labels.size - 1) {
                if (labels.subList(index, labels.size).joinToString(".") in blockedDomains) return true
            }
        }

        val needle = pathAndQuery.lowercase(Locale.ROOT)
        return adPathMarkers.any { needle.contains(it) }
    }

    /** Host-list check used when no rule engine is loaded. See [shouldBlock] for the caveat. */
    fun isAdOrTracker(url: String, enabled: Boolean = true): Boolean {
        if (!enabled) return false
        return matchesHostAndPath(UrlNormalizer.hostOf(url), UrlNormalizer.pathAndQueryOf(url))
    }

    fun isTrackingParameter(name: String): Boolean =
        trackingParameters.contains(name.lowercase(Locale.ROOT))

    /**
     * Removes tracking parameters, preserving all others. Returns [url] unchanged when there is
     * nothing to strip or the URL cannot be parsed.
     *
     * Note this is Helios's own short list, not the engine's: the rule engine has no opinion about
     * rewriting navigations, and cleaning the URL before the request is made is the only way to
     * avoid the tracker seeing the click at all.
     */
    fun cleanUrl(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            if (!uri.isHierarchical) return url
            val names = uri.queryParameterNames
            if (names.isEmpty() || names.none { isTrackingParameter(it) }) return url

            val builder = uri.buildUpon().clearQuery()
            for (name in names) {
                if (isTrackingParameter(name)) continue
                for (value in uri.getQueryParameters(name)) {
                    builder.appendQueryParameter(name, value)
                }
            }
            builder.build().toString()
        } catch (_: Exception) {
            url
        }
    }

    /** Filename for a downloaded file, with path separators and control characters removed. */
    fun sanitizeFileName(candidate: String?, fallbackUrl: String): String {
        val raw = candidate?.substringAfterLast('/')?.substringAfterLast('\\')?.trim()
        val cleaned = raw?.map { char ->
            if (char.isLetterOrDigit() || char in "._- ()&[]'!,+@") char else '_'
        }?.joinToString("")?.trim('.', ' ')
        return cleaned?.takeIf { it.isNotBlank() }
            ?: UrlNormalizer.hostOf(fallbackUrl)?.replace(Regex("[^a-zA-Z0-9.-]"), "-")
                ?.plus("-download")
            ?: "download"
    }
}
