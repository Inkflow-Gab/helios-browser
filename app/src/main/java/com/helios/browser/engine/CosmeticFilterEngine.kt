package com.helios.browser.engine

import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebView
import org.json.JSONObject

/**
 * Applies cosmetic filtering to a page, using the rules the native engine holds.
 *
 * ## The three layers, in the order they run
 * 1. **Site-specific selectors.** EasyList contains thousands of `example.com##.ad-slot` rules.
 *    These are exact and cheap: one native call per navigation returns the whole list for that URL.
 * 2. **Scriptlets.** uBlock Origin's `##+js(name)` rules run small snippets of page JavaScript that
 *    break anti-adblock interstitials and pop-up walls. They are injected verbatim and execute in
 *    the page's own context, which is the only place they can work.
 * 3. **Generic hiding.** A `##.ad` rule with no host applies everywhere, but only hides things
 *    whose class or id is actually on the page. Finding out what is on the page means asking the
 *    page, which is the round trip in [requestGenericHiding].
 *
 * ## How the page is asked, without a JavaScript bridge
 * `evaluateJavascript` returns the value of the script's final expression, so the collector script
 * below simply *returns* an object and Helios reads it from the callback. That is worth the
 * indirection: adding a `@JavascriptInterface` would expose a native method to every page the
 * browser ever visits, and there is no need for one.
 *
 * Only main-frame script runs here, which is exactly the scope cosmetic rules apply to.
 */
object CosmeticFilterEngine {

    private const val TAG = "CosmeticFilterEngine"
    private const val STYLE_ELEMENT_ID = "helios_cosmetic_style"
    private const val MAX_CLASS_CHARS = 6_000
    private const val MAX_ID_CHARS = 4_000

    /**
     * Collects every class name and element id on the page, deduplicated, as a JSON object.
     *
     * Returns rather than assigns, because the return value is what reaches the Kotlin callback.
     * `Object.create(null)` matters: a plain `{}` would collide with `constructor` and friends,
     * and a real page does have elements with those ids.
     */
    private val COLLECT_SCRIPT = """
        (function () {
            try {
                var classes = Object.create(null);
                var ids = Object.create(null);
                var elements = document.querySelectorAll('[class],[id]');
                for (var i = 0; i < elements.length; i++) {
                    var element = elements[i];
                    if (element.id) { ids[element.id] = 1; }
                    var value = element.getAttribute && element.getAttribute('class');
                    if (value) {
                        var parts = value.split(/\s+/);
                        for (var j = 0; j < parts.length; j++) {
                            if (parts[j]) { classes[parts[j]] = 1; }
                        }
                    }
                }
                return { classes: Object.keys(classes).join(','), ids: Object.keys(ids).join(',') };
            } catch (error) {
                return { classes: '', ids: '' };
            }
        })()
    """.trimIndent()

    /**
     * Applies all three layers to [webView] for [url].
     *
     * Cheap to call and safe to call repeatedly: the style element is replaced by id, and the
     * generic-hide round trip only fires once per navigation.
     */
    fun apply(webView: WebView, url: String) {
        if (url.isBlank() || !NativeAdBlock.isAvailable) return

        injectHiddenSelectors(webView, url)
        injectScriptlets(webView, url)
        requestGenericHiding(webView, url)
    }

    /**
     * Replaces the stylesheet with one built from the engine's selectors for this URL.
     *
     * Using a single `<style>` keyed by id rather than appending means a second injection on the
     * same page cannot leave two conflicting rulesets behind.
     */
    private fun injectHiddenSelectors(webView: WebView, url: String) {
        val selectors = NativeAdBlock.hideSelectors(url)
        if (selectors.isBlank()) return
        // A trailing `,` on any list would produce an invalid selector and the browser would drop
        // the entire rule, so the join is explicit about the empty case.
        val css = selectors.lineSequence()
            .filter { it.isNotBlank() }
            .joinToString(",\n")
            .plus(" { display: none !important; }")
        evaluate(webView, styleInjectionScript(css))
    }

    /** Runs the page's scriptlets, if it has any. */
    private fun injectScriptlets(webView: WebView, url: String) {
        val script = NativeAdBlock.injectedScript(url)
        if (script.isBlank()) return
        evaluate(webView, script)
    }

    /**
     * Asks the page what classes and ids it uses, then asks the engine which of those to hide.
     *
     * Skipped entirely when the page carries `$generichide`, which is a list author saying "do not
     * guess about my markup". Pages with nothing to hide still cost one `evaluateJavascript`
     * round trip each, which is the price of generic hiding.
     */
    private fun requestGenericHiding(webView: WebView, url: String) {
        if (NativeAdBlock.isGenericHideDisabled(url)) return

        evaluate(webView, COLLECT_SCRIPT) { result ->
            val payload = result?.takeIf { it.isNotBlank() && it != "null" } ?: return@evaluate
            val selectors = runCatching {
                val json = JSONObject(payload)
                val classes = json.optString("classes").take(MAX_CLASS_CHARS)
                val ids = json.optString("ids").take(MAX_ID_CHARS)
                NativeAdBlock.genericHideSelectors(url, classes, ids)
            }.getOrElse { error ->
                Log.w(TAG, "Generic hide lookup failed for $url", error)
                return@evaluate
            }
            if (selectors.isBlank()) return@evaluate
            val css = selectors.lineSequence()
                .filter { it.isNotBlank() }
                .joinToString(",\n")
                .plus(" { display: none !important; }")
            evaluate(webView, styleInjectionScript(css))
        }
    }

    /**
     * Builds the script that installs [css] under a fixed element id.
     *
     * `JSONObject.quote` is used for the CSS text rather than string concatenation: selectors come
     * from third-party filter lists, and one containing a quote or a newline must not be able to
     * break out of the JavaScript string.
     */
    private fun styleInjectionScript(css: String): String =
        "(function(){try{" +
            "var id='$STYLE_ELEMENT_ID';" +
            "var head=document.head||document.documentElement;" +
            "if(!head)return;" +
            "var style=document.getElementById(id);" +
            "if(!style){style=document.createElement('style');style.id=id;" +
            "style.setAttribute('data-helios','cosmetic');head.appendChild(style);}" +
            "style.textContent=${JSONObject.quote(css)};" +
            "}catch(e){}})();"

    /**
     * Runs [script], optionally delivering its result to [callback].
     *
     * Wrapped because `evaluateJavascript` throws if the WebView has already been detached from
     * its window, which is routine during tab teardown.
     */
    private fun evaluate(
        webView: WebView,
        script: String,
        callback: ValueCallback<String>? = null
    ) {
        try {
            webView.evaluateJavascript(script, callback)
        } catch (error: Exception) {
            // Expected on a destroyed WebView; not worth logging.
            Log.d(TAG, "Skipped injection on a detached WebView: ${error.message}")
        }
    }
}
