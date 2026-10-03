package com.helios.browser.engine

import android.net.Uri
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback

/**
 * Platform plumbing that needs an Activity (runtime permission dialogs, activity results) and
 * therefore cannot live in the ViewModel.
 *
 * Implemented by `BrowserScreen`. The ViewModel stays free of `WebView`, `Uri` and
 * `PermissionRequest` objects; this interface is the only seam between the two.
 */
interface WebPermissionHost {

    /**
     * A page asked for camera and/or microphone. [request] must be answered exactly once, either
     * with [PermissionRequest.grant] or [PermissionRequest.deny].
     */
    fun onWebPermissionRequest(request: PermissionRequest)

    /** An `<input type="file">` was tapped. [callback] must receive a URI array or null. */
    fun onFileChooserRequest(callback: ValueCallback<Array<Uri>>)

    /** A page asked for geolocation. [callback] must be invoked exactly once. */
    fun onGeolocationRequest(origin: String, callback: GeolocationPermissions.Callback)
}