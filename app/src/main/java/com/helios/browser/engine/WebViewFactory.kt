package com.helios.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebSettings
import android.webkit.WebView
import com.helios.browser.domain.model.BlockingConfig
import com.helios.browser.ui.browser.BrowserIntent

/**
 * Builds the one kind of WebView Helios uses.
 *
 * Kept out of the composable so WebView configuration is reviewable in a single place. Incognito
 * tabs get cookies and third-party cookies disabled at construction time, which is the closest
 * approximation of isolation available inside a single WebView process; see the note in
 * [syncPrivacy] for the limit of that approach.
 */
object WebViewFactory {

    const val DESKTOP_USER_AGENT: String =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/128.0.0.0 Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        isIncognito: Boolean,
        isDesktopMode: Boolean,
        blockingConfigProvider: () -> BlockingConfig,
        permissionHost: WebPermissionHost,
        downloadListener: (DownloadRequest) -> Unit,
        onIntent: (BrowserIntent) -> Unit
    ): WebView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            // Helios is HTTPS-only: never weaken a secure page with cleartext subresources.
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        applyDesktopMode(this, isDesktopMode)
        applyIncognito(this, isIncognito)

        webViewClient = HeliosWebViewClient(
            configProvider = blockingConfigProvider,
            onPageStarted = { url, canGoBack, canGoForward ->
                onIntent(BrowserIntent.PageStarted(url, canGoBack, canGoForward))
            },
            onPageFinished = { url, title, canGoBack, canGoForward ->
                onIntent(BrowserIntent.PageFinished(url, title, canGoBack, canGoForward))
            },
            onRequestBlocked = { onIntent(BrowserIntent.RequestBlocked) },
            onExternalNavigation = { url -> onIntent(BrowserIntent.ExternalNavigate(url)) },
            // HTTPS upgrades and tracking-parameter rewrites arrive already-resolved; re-running
            // them through Navigate would be a no-op, so the URL is applied verbatim.
            onNavigationStarted = { url -> onIntent(BrowserIntent.Navigate(url)) },
            onDownload = { request -> downloadListener(request) }
        )

        webChromeClient = HeliosChromeClient(
            host = permissionHost,
            onProgressChanged = { percent -> onIntent(BrowserIntent.ProgressChanged(percent)) },
            onTitleReceived = { title -> onIntent(BrowserIntent.TitleChanged(title)) }
        )

        setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            downloadListener(
                DownloadRequest(
                    url = url,
                    userAgent = userAgent,
                    contentDisposition = contentDisposition,
                    mimeType = mimeType,
                    contentLength = contentLength
                )
            )
        }

        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = WebView.OVER_SCROLL_NEVER
    }

    /**
     * Applies desktop or mobile user agent.
     *
     * Assigning null restores the WebView default, which is why this is a single function rather
     * than two branches at each call site.
     */
    fun applyDesktopMode(webView: WebView, isDesktopMode: Boolean) {
        webView.settings.userAgentString = if (isDesktopMode) DESKTOP_USER_AGENT else null
    }

    /**
     * Applies cookie policy for the tab's privacy mode.
     *
     * Real isolation would need a separate WebView data directory, which is only possible by
     * putting incognito in a separate Android process. Until that exists, incognito tabs block
     * first- and third-party cookies and are excluded from history, bookmarks and session restore,
     * which is the strongest guarantee available in-process.
     */
    fun applyIncognito(webView: WebView, isIncognito: Boolean) {
        val cookies = CookieManager.getInstance()
        cookies.setAcceptThirdPartyCookies(webView, !isIncognito)
        if (isIncognito) {
            cookies.setAcceptCookie(false)
        } else {
            cookies.setAcceptCookie(true)
        }
    }

    /** Re-applies [applyIncognito] without recreating the WebView. */
    fun syncPrivacy(webView: WebView, isIncognito: Boolean) {
        applyIncognito(webView, isIncognito)
    }
}