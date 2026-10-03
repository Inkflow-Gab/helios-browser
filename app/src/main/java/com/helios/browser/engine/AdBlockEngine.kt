package com.helios.browser.engine

import android.net.Uri
import java.util.Locale

object AdBlockEngine {

    // Domain blocklist of top ad, telemetry, and tracking networks
    private val blockedDomains: Set<String> = hashSetOf(
        // Google AdServices & Analytics
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

        // Major Ad Exchanges & Networks
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "adnxs.com",
        "rubiconproject.com",
        "openx.net",
        "casalemedia.com",
        "advertising.com",
        "exponential.com",
        "media.net",
        "bidswitch.net",
        "pubmatic.com",
        "adcolony.com",
        "applovin.com",
        "unityads.unity3d.com",
        "vungle.com",
        "ironsrc.com",
        "chartboost.com",
        "inmobi.com",
        "moatads.com",
        "adroll.com",
        "admob.com",
        "adsafeprotected.com",
        "smartadserver.com",
        "tribalfusion.com",
        "popads.net",
        "propellerads.com",
        "exoclick.com",
        "juicyads.com",
        "clickadu.com",
        "adcash.com",

        // Tracker & Telemetry Networks
        "connect.facebook.net",
        "pixel.facebook.com",
        "analytics.tiktok.com",
        "ads-twitter.com",
        "analytics.twitter.com",
        "bat.bing.com",
        "hotjar.com",
        "clarity.ms",
        "segment.io",
        "segment.com",
        "branch.io",
        "adjust.com",
        "appsflyer.com",
        "mixpanel.com",
        "amplitude.com",
        "scorecardresearch.com",
        "quantserve.com",
        "newrelic.com",
        "sentry.io",
        "bugsnag.com"
    )

    // Tracking query parameters to strip from visited URLs
    private val trackingParameters = setOf(
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "utm_name",
        "fbclid",
        "gclid",
        "msclkid",
        "mc_eid",
        "yclid",
        "igshid",
        "_hsenc",
        "_hsmi",
        "wbraid",
        "gbraid"
    )

    fun isAdOrTracker(url: String): Boolean {
        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase(Locale.ROOT) ?: return false

            // Check exact host match
            if (blockedDomains.contains(host)) return true

            // Check parent domain match (e.g. sub.adservice.google.com)
            for (domain in blockedDomains) {
                if (host.endsWith(".$domain")) {
                    return true
                }
            }

            // Path-based ad script detection
            val path = uri.path?.lowercase(Locale.ROOT) ?: ""
            if (path.contains("/adserver/") ||
                path.contains("/ads.js") ||
                path.contains("/prebid.js") ||
                path.contains("/gtag/js") ||
                path.contains("/pixel.js") ||
                path.contains("/adview")
            ) {
                return true
            }
        } catch (_: Exception) {
            return false
        }
        return false
    }

    fun cleanUrl(url: String): String {
        try {
            val uri = Uri.parse(url)
            if (!uri.isHierarchical) return url

            val queryNames = uri.queryParameterNames
            if (queryNames.isEmpty()) return url

            var hasTrackingParams = false
            for (param in queryNames) {
                if (trackingParameters.contains(param.lowercase(Locale.ROOT))) {
                    hasTrackingParams = true
                    break
                }
            }

            if (!hasTrackingParams) return url

            val cleanUriBuilder = uri.buildUpon().clearQuery()
            for (param in queryNames) {
                if (!trackingParameters.contains(param.lowercase(Locale.ROOT))) {
                    val values = uri.getQueryParameters(param)
                    for (value in values) {
                        cleanUriBuilder.appendQueryParameter(param, value)
                    }
                }
            }
            return cleanUriBuilder.build().toString()
        } catch (_: Exception) {
            return url
        }
    }
}
