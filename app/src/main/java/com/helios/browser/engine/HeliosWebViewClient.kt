package com.helios.browser.engine

import android.graphics.Bitmap
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.helios.browser.core.url.UrlNormalizer
import java.io.ByteArrayInputStream

/** Everything a download started by the page needs in order to be queued. */
data class DownloadRequest(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    val contentLength: Long
)

/**
 * Intercepts requests, cleans navigations and applies cosmetic filters.
 *
 * Holds no mutable browser state: every observable change is reported through a callback so the
 * ViewModel stays the single owner of state.
 */
class HeliosWebViewClient(
    private val configProvider: () -> com.helios.browser.domain.model.BlockingConfig,
    private val onPageStarted: (url: String, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    private val onPageFinished: (url: String, title: String?, canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    private val onRequestBlocked: () -> Unit,
    private val onExternalNavigation: (url: String) -> Unit,
    private val onNavigationStarted: (url: String) -> Unit,
    private val onDownload: (DownloadRequest) -> Unit
) : WebViewClient() {

    /**
     * The response returned for a blocked request.
     *
     * A fresh instance per call, not a shared one: `WebResourceResponse` holds a live
     * `InputStream` and `shouldInterceptRequest` runs on several WebView threads at once.
     *
     * Image requests get a 1x1 transparent GIF rather than nothing. Blocking an ad with an empty
     * body collapses whatever layout reserved space for it and makes the page jump; a transparent
     * pixel of the right type leaves the layout intact. Everything else gets an empty 200, which is
     * what lets the page's own error handling take over rather than seeing a network failure.
     */
    private fun blockedResponse(requestType: String): WebResourceResponse {
        val body: ByteArray = if (requestType == WebRequestClassifier.IMAGE) {
            Base64.decode(TRANSPARENT_GIF, Base64.NO_WRAP)
        } else {
            ByteArray(0)
        }
        val mimeType = if (requestType == WebRequestClassifier.IMAGE) "image/gif" else "text/plain"
        return WebResourceResponse(mimeType, "UTF-8", ByteArrayInputStream(body))
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val resourceRequest = request ?: return null
        val url = resourceRequest.url.toString()
        // `helios://` and friends never reach the network, and `data:`/`blob:` have no host for the
        // engine to work with. Letting them through is not optional.
        if (!UrlNormalizer.isWebUrl(url)) return null

        val config = configProvider()
        if (!config.blockAdsAndTrackers) return null

        // The document that made the request. This is what `$third-party` and `$domain=` rules are
        // resolved against, so it cannot be skipped: without it the engine has to assume every
        // request is third-party, which blocks sites off their own CDNs.
        val sourceUrl = view?.url?.takeIf { UrlNormalizer.isWebUrl(it) }.orEmpty()
        val requestType = WebRequestClassifier.fromRequest(resourceRequest)

        val blocked = AdBlockEngine.shouldBlock(
            url = url,
            sourceUrl = sourceUrl,
            requestType = requestType,
            method = resourceRequest.method ?: "GET",
            enabled = true
        )
        if (!blocked) return null

        onRequestBlocked()
        return blockedResponse(requestType)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        val config = configProvider()

        // Anything that is not http(s) is handed to the system: mailto:, tel:, intent:, market:,
        // sms: and third-party app schemes. Returning false would make the WebView fail silently.
        if (!UrlNormalizer.isWebUrl(url)) {
            onExternalNavigation(url)
            return true
        }

        if (config.upgradeToHttps && url.startsWith("http://", ignoreCase = true)) {
            val upgraded = UrlNormalizer.upgradeToHttps(url)
            onNavigationStarted(upgraded)
            return true
        }

        if (config.cleanTrackingParams) {
            val cleaned = AdBlockEngine.cleanUrl(url)
            if (cleaned != url) {
                onNavigationStarted(cleaned)
                return true
            }
        }

        // https:// calls are always granted to the WebView: matching the origin is what makes
        // first-party cookies and service workers work at all.
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStarted(
            url.orEmpty(),
            view?.canGoBack() == true,
            view?.canGoForward() == true
        )
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        // This is the earliest point the DOM exists, so cosmetic filtering lands here as well as
        // on finish: waiting for `onPageFinished` means ads are visible for the whole load.
        applyCosmeticFilters(view, url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // Re-applied because the engine's generic-hide pass needs the classes and ids of the fully
        // built document, which do not exist yet at commit-visible time.
        applyCosmeticFilters(view, url)
        onPageFinished(
            url.orEmpty(),
            view?.title,
            view?.canGoBack() == true,
            view?.canGoForward() == true
        )
    }

    override fun onDownloadRequest(view: WebView?, url: String?, userAgent: String?,
                                  contentDisposition: String?, mimetype: String?,
                                  contentLength: Long) {
        super.onDownloadRequest(view, url, userAgent, contentDisposition, mimetype, contentLength)
        val target = url ?: return
        if (!UrlNormalizer.isWebUrl(target)) return
        onDownload(
            DownloadRequest(
                url = target,
                userAgent = userAgent,
                contentDisposition = contentDisposition,
                mimeType = mimetype,
                contentLength = contentLength
            )
        )
    }

    private fun applyCosmeticFilters(view: WebView?, url: String?) {
        val target = view ?: return
        val pageUrl = url ?: return
        if (!UrlNormalizer.isWebUrl(pageUrl)) return
        if (!configProvider().cosmeticFilters) return
        CosmeticFilterEngine.apply(target, pageUrl)
    }

    private companion object {
        /**
         * The smallest valid GIF: one transparent pixel.
         *
         * Returned as base64 rather than a file so it costs 70 bytes of source instead of a
         * resource, and so it is never a candidate for the download dispatcher.
         */
        const val TRANSPARENT_GIF = "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7"
    }
}
