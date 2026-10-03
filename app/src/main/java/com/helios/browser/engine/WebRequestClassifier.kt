package com.helios.browser.engine

import android.webkit.WebResourceRequest
import java.util.Locale

/**
 * Works out the adblock-rust content type token for a request the WebView is about to make.
 *
 * ## Why this is a guess
 * `WebResourceRequest` — the Android object behind `shouldInterceptRequest` — exposes the URL, the
 * headers and whether the request is for the main frame. It does **not** expose the resource type,
 * unlike Gecko and Blink, which hand the engine an exact token. Filter lists lean on that token
 * heavily: a page's own script is not blocked by `$script` rules, its tracking pixel is, and
 * `||example.com^$image` must not stop the page itself loading.
 *
 * So Helios infers it. The two signals available are the file extension and the `Accept` header,
 * which browsers populate accurately. This is a heuristic and the results are wrong sometimes — a
 * mislabelled script is matched against script rules, and an unknown type falls back to `other`,
 * which still matches every untyped rule. That is the safe direction: over-broad types are what the
 * `$third-party` and `@@` rules in the lists exist to correct.
 *
 * ## Kept pure
 * Inputs are strings and booleans rather than `WebResourceRequest`, so this is unit testable on a
 * plain JVM. [fromRequest] is the only Android-aware wrapper.
 */
object WebRequestClassifier {

    /** Content type tokens understood by adblock-rust, plus the Android reasons for choosing them. */
    const val MAIN_FRAME = "main_frame"
    const val SUB_FRAME = "sub_frame"
    const val SCRIPT = "script"
    const val STYLESHEET = "stylesheet"
    const val IMAGE = "image"
    const val FONT = "font"
    const val MEDIA = "media"
    const val XML_HTTP_REQUEST = "xhr"
    const val BEACON = "beacon"
    const val OTHER = "other"

    private val SCRIPT_EXTENSIONS = setOf("js", "mjs", "cjs")
    private val STYLE_EXTENSIONS = setOf("css")
    private val IMAGE_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "avif", "svg", "ico", "bmp", "apng", "jxl"
    )
    private val FONT_EXTENSIONS = setOf("woff", "woff2", "ttf", "otf", "eot")
    private val MEDIA_EXTENSIONS = setOf(
        "mp4", "m4v", "webm", "ogv", "mp3", "m4a", "ogg", "oga", "wav", "flac", "aac", "avi", "mov"
    )

    /**
     * Classifies a request.
     *
     * @param url the request URL, used only for its extension.
     * @param accept the request's `Accept` header, or null.
     * @param isForMainFrame true for the top-level document. Takes priority over everything else:
     *   a `.html` main-frame request is a document no matter what the extension table says.
     * @param hasXRequestedWith true when the `X-Requested-With` header is present, which Chromium
     *   sets on every `fetch`/`XMLHttpRequest` even when the page sets its own value.
     */
    fun classify(
        url: String,
        accept: String?,
        isForMainFrame: Boolean,
        hasXRequestedWith: Boolean = false
    ): String {
        val extension = extensionOf(url)

        if (isForMainFrame) return MAIN_FRAME
        if (extension in SCRIPT_EXTENSIONS) return SCRIPT
        if (extension in STYLE_EXTENSIONS) return STYLESHEET
        if (extension in IMAGE_EXTENSIONS) return IMAGE
        if (extension in FONT_EXTENSIONS) return FONT
        if (extension in MEDIA_EXTENSIONS) return MEDIA
        if (extension == "html" || extension == "htm") return SUB_FRAME

        val header = accept?.lowercase(Locale.ROOT).orEmpty()
        return when {
            hasXRequestedWith -> XML_HTTP_REQUEST
            "text/css" in header -> STYLESHEET
            "javascript" in header || "ecmascript" in header -> SCRIPT
            header.contains("font/") -> FONT
            header.contains("image/") -> IMAGE
            header.contains("video/") || header.contains("audio/") -> MEDIA
            "text/html" in header -> SUB_FRAME
            // Chromium's `navigator.sendBeacon` sets this exact Accept.
            "application/text" in header && "text/plain" in header -> BEACON
            else -> OTHER
        }
    }

    /** Android wrapper. Keeps [classify] free of `WebResourceRequest` so it stays unit testable. */
    fun fromRequest(request: WebResourceRequest): String = classify(
        url = request.url.toString(),
        accept = request.getRequestHeader("Accept"),
        isForMainFrame = request.isForMainFrame,
        hasXRequestedWith = request.getRequestHeader("X-Requested-With") != null
    )

    /**
     * The lowercased extension after the final dot, or an empty string.
     *
     * Only the path is considered: a query string like `?file=a.js` must not classify a request as
     * a script, and a host like `example.com` must not classify as `com`.
     */
    private fun extensionOf(url: String): String {
        val withoutQuery = url.substringBefore('?').substringBefore('#')
        val lastSegment = withoutQuery.substringAfterLast('/')
        if (!lastSegment.contains('.')) return ""
        return lastSegment.substringAfterLast('.').lowercase(Locale.ROOT)
    }
}
