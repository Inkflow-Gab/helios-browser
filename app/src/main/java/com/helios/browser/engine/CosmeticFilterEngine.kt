package com.helios.browser.engine

import android.util.Base64
import android.webkit.WebView

object CosmeticFilterEngine {

    // Common CSS selectors for ad containers and banners
    private val cosmeticCss = """
        [id^="google_ads"],
        [id*="google_ads_iframe"],
        .ad-container,
        .ad-slot,
        .ad-wrapper,
        .ad_banner,
        .ad-banner,
        .advertisement,
        .adsbygoogle,
        .sponsored-post,
        .sponsor-container,
        [aria-label="advertisement"],
        [aria-label="Sponsored"],
        .trc_related_container,
        .outbrain_widget,
        .taboola-ad,
        #carbonads,
        .header-ad,
        .footer-ad,
        .sidebar-ad,
        .sticky-ad {
            display: none !important;
            visibility: hidden !important;
            height: 0 !important;
            min-height: 0 !important;
            max-height: 0 !important;
            opacity: 0 !important;
            pointer-events: none !important;
        }
    """.trimIndent()

    private val injectionScript = """
        (function() {
            var cssId = 'helios_cosmetic_filter';
            if (!document.getElementById(cssId)) {
                var head = document.getElementsByTagName('head')[0] || document.documentElement;
                var style = document.createElement('style');
                style.id = cssId;
                style.type = 'text/css';
                style.innerHTML = `${cosmeticCss.replace("\n", " ")}`;
                head.appendChild(style);
            }
        })();
    """.trimIndent()

    fun injectCosmeticFilters(webView: WebView) {
        try {
            webView.evaluateJavascript(injectionScript, null)
        } catch (_: Exception) {
            // Silently handle if webView was detached
        }
    }
}
