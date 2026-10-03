package com.helios.browser.engine

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.helios.browser.data.TabModel
import java.io.ByteArrayInputStream

class HeliosWebViewClient(
    private val tab: TabModel,
    private val onPageStartedCallback: (String) -> Unit,
    private val onPageFinishedCallback: (String) -> Unit,
    private val onBlockedItemCallback: (Int) -> Unit
) : WebViewClient() {

    private val emptyResponse: WebResourceResponse by lazy {
        WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)

        if (AdBlockEngine.isAdOrTracker(url)) {
            tab.blockedAdsCount++
            onBlockedItemCallback(tab.blockedAdsCount)
            return emptyResponse
        }

        return super.shouldInterceptRequest(view, request)
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?
    ): Boolean {
        val url = request?.url?.toString() ?: return false
        val cleanedUrl = AdBlockEngine.cleanUrl(url)

        if (cleanedUrl != url) {
            view?.loadUrl(cleanedUrl)
            return true
        }

        // Allow http / https protocols
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return false
        }

        return super.shouldOverrideUrlLoading(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        val validUrl = url ?: ""
        tab.url = validUrl
        tab.isLoading = true
        tab.canGoBack = view?.canGoBack() == true
        tab.canGoForward = view?.canGoForward() == true
        onPageStartedCallback(validUrl)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        val validUrl = url ?: ""
        tab.url = validUrl
        tab.title = view?.title ?: validUrl
        tab.isLoading = false
        tab.canGoBack = view?.canGoBack() == true
        tab.canGoForward = view?.canGoForward() == true

        view?.let {
            CosmeticFilterEngine.injectCosmeticFilters(it)
        }

        onPageFinishedCallback(validUrl)
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        view?.let {
            CosmeticFilterEngine.injectCosmeticFilters(it)
        }
    }
}
