package com.helios.browser.engine

import android.util.Log

/**
 * Thin binding to `libhelios_adblock.so`, which wraps [adblock-rust] — the engine that powers
 * Brave's native adblocker.
 *
 * ## What this buys
 * Full Adblock Plus syntax: `$third-party`, `$domain=`, `@@` exceptions, `$badfilter`, site-specific
 * `##` cosmetic rules, generic hiding and uBlock Origin `##+js()` scriptlets. Helios ships the same
 * filter lists Brave and uBlock Origin use, so the rules behave the way their authors intended.
 *
 * ## Availability is a runtime fact, not a compile-time one
 * [isAvailable] is resolved once by actually loading the library. A debug build made without the
 * Rust toolchain has no `.so` at all, and on an emulator or device with an unexpected ABI the load
 * can fail too. Every method here is therefore a no-op when the library is missing, and callers
 * fall back to the much cruder host matcher in [AdBlockEngine]. Blocking degrades; it never throws.
 *
 * [adblock-rust]: https://github.com/brave/adblock-rust
 */
object NativeAdBlock {

    private const val TAG = "NativeAdBlock"
    private const val LIBRARY = "helios_adblock"

    /**
     * True if the native library loaded and the engine is installed.
     *
     * Reading [System.loadLibrary] into a `val` matters: doing it lazily per call would repeat a
     * failing load on every intercepted request.
     *
     * The catch list is deliberately wider than "the library is missing". This is an `object`, so a
     * throwable that escapes the initializer is *fatal and permanent* — the JVM marks the class as
     * failed and every later access raises `NoClassDefFoundError`. A single uncaught throwable here
     * would therefore take the browser down on the first intercepted request rather than degrading
     * to the host list.
     *
     * - `UnsatisfiedLinkError` — no `.so` for this ABI, or a missing dependency inside it.
     * - `LinkageError` — covers `ExceptionInInitializerError` and the `NoClassDefFoundError` family,
     *   which is what a half-initialised library produces.
     * - `SecurityException` — blocked by device policy.
     * - `RuntimeException` — a broken loader on some OEM builds.
     *
     * `Error` is *not* caught wholesale: swallowing `OutOfMemoryError` here would be worse than
     * crashing. `LinkageError` is the specific one that matters.
     */
    val isAvailable: Boolean = try {
        System.loadLibrary(LIBRARY)
        nativeAvailable()
    } catch (error: UnsatisfiedLinkError) {
        Log.w(TAG, "Native adblock unavailable; using the built-in host matcher only", error)
        false
    } catch (error: LinkageError) {
        Log.e(TAG, "Native adblock failed to initialise", error)
        false
    } catch (error: SecurityException) {
        Log.w(TAG, "Native adblock blocked by device policy", error)
        false
    } catch (error: RuntimeException) {
        Log.e(TAG, "Native adblock loader threw", error)
        false
    }

    /**
     * True once a filter list has been compiled into an engine.
     *
     * False for the first few seconds of a cold start while [BlockListRepository] parses the
     * bundled lists. Requests that arrive in that window are served unblocked rather than delayed.
     */
    @Volatile
    var isReady: Boolean = false
        private set

    /** Should this request be blocked? Always false when unavailable, so this can be called freely. */
    fun shouldBlock(url: String, sourceUrl: String, requestType: String, method: String): Boolean {
        if (!isAvailable || !isReady) return false
        return try {
            nativeCheck(url, sourceUrl, requestType, method)
        } catch (error: UnsatisfiedLinkError) {
            // The library went away underneath us, which should be impossible, but failing open is
            // still the right answer to any error raised from inside a JNI boundary.
            Log.w(TAG, "nativeCheck failed; allowing the request", error)
            false
        }
    }

    /** Newline-separated CSS selectors that hide ad containers on [url]. Empty when none apply. */
    fun hideSelectors(url: String): String =
        if (!isAvailable || !isReady) "" else runCatching { nativeHideSelectors(url) }
            .getOrDefault("")

    /**
     * True when the page opted out of generic hiding via `$generichide`.
     *
     * True means "do not run the generic-hide round trip for this page", which is also the answer
     * when nothing is known, so a null or empty result should be treated as opted out.
     */
    fun isGenericHideDisabled(url: String): Boolean =
        !isAvailable || !isReady || runCatching { nativeGenericHideDisabled(url) }.getOrDefault(true)

    /** Generic-hide selectors for the class and id names actually present on the page. */
    fun genericHideSelectors(url: String, classes: String, ids: String): String {
        if (!isAvailable || !isReady) return ""
        return runCatching { nativeHiddenClassIdSelectors(url, classes, ids) }.getOrDefault("")
    }

    /** Scriptlet JavaScript to inject into [url] (`##+js(...)` rules). Empty when none apply. */
    fun injectedScript(url: String): String =
        if (!isAvailable || !isReady) "" else runCatching { nativeInjectedScript(url) }.getOrDefault("")

    /**
     * Compiles [listText] into the engine. Several seconds of work over a few megabytes.
     *
     * One string holding every list in the chosen preset, joined with newlines. That is equivalent to
     * passing them separately — the engine concatenates its sources anyway — and it avoids a second
     * multi-megabyte JNI string. List headers inside the text are still parsed, so metadata
     * survives.
     *
     * Empty text is refused rather than accepted: an engine with no rules would report "ready" while
     * blocking nothing, and the shields sheet would repeat that claim.
     */
    fun load(listText: String): Boolean {
        if (!isAvailable || listText.isEmpty()) return false
        return try {
            nativeLoad(listText).also { isReady = it }
        } catch (error: UnsatisfiedLinkError) {
            Log.e(TAG, "nativeLoad failed", error)
            isReady = false
            false
        }
    }

    /** Restores an engine previously produced by [serialize]. False on version or checksum mismatch. */
    fun deserialize(bytes: ByteArray): Boolean {
        if (!isAvailable) return false
        return try {
            nativeDeserialize(bytes).also { isReady = it }
        } catch (error: UnsatisfiedLinkError) {
            Log.e(TAG, "nativeDeserialize failed", error)
            isReady = false
            false
        }
    }

    /** Serialises the engine for on-disk caching, so the next cold start skips the parse. */
    fun serialize(): ByteArray? {
        if (!isAvailable || !isReady) return null
        return try {
            nativeSerialize()?.takeIf { it.isNotEmpty() }
        } catch (error: UnsatisfiedLinkError) {
            Log.e(TAG, "nativeSerialize failed", error)
            null
        }
    }

    /** Frees the engine. Blocking is off until the next [load] or [deserialize]. */
    fun release() {
        if (!isAvailable) return
        runCatching { nativeRelease() }
        isReady = false
    }

    private external fun nativeAvailable(): Boolean
    private external fun nativeLoad(listText: String): Boolean
    private external fun nativeSerialize(): ByteArray?
    private external fun nativeDeserialize(data: ByteArray): Boolean
    private external fun nativeRelease()
    private external fun nativeCheck(
        url: String,
        sourceUrl: String,
        requestType: String,
        method: String
    ): Boolean

    private external fun nativeHideSelectors(url: String): String
    private external fun nativeGenericHideDisabled(url: String): Boolean
    private external fun nativeHiddenClassIdSelectors(
        url: String,
        classesCsv: String,
        idsCsv: String
    ): String

    private external fun nativeInjectedScript(url: String): String
}
