package com.helios.browser.engine

import android.net.Uri
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Bridges Chromium's permission and file-picker callbacks to [WebPermissionHost].
 *
 * Every request is forwarded verbatim; this class never decides policy on its own. DRM
 * (`RESOURCE_PROTECTED_MEDIA_ID`) is always denied because Helios does not support Widevine.
 */
class HeliosChromeClient(
    private val host: WebPermissionHost,
    private val onProgressChanged: (Int) -> Unit,
    private val onTitleReceived: (String?) -> Unit
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        onTitleReceived(title)
    }

    override fun onPermissionRequest(request: PermissionRequest?) {
        val pending = request ?: return
        if (pending.resources.isNullOrEmpty()) {
            pending.deny()
            return
        }
        // Protected media needs a Widevine CDM that Helios does not ship.
        if (pending.resources.contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)) {
            pending.deny()
            return
        }
        host.onWebPermissionRequest(pending)
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: android.webkit.ValueCallback<Array<Uri>>?,
        fileChooserParams: FileChooserParams?
    ): Boolean {
        val callback = filePathCallback ?: return false
        host.onFileChooserRequest(callback)
        return true
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        val resolvedCallback = callback ?: return
        val resolvedOrigin = origin ?: return
        host.onGeolocationRequest(resolvedOrigin, resolvedCallback)
    }
}