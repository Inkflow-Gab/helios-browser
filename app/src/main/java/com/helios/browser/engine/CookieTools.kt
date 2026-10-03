package com.helios.browser.engine

import android.webkit.CookieManager
import android.webkit.ValueCallback
import com.helios.browser.core.url.UrlNormalizer

/**
 * Applies cookie changes to the WebView's cookie store.
 *
 * ## Where this sits
 * [CookieJar] does the parsing and is pure, so it is unit tested on a JVM. This is the Android-facing
 * half: it talks to [CookieManager] and is the only part that cannot be tested without a device.
 *
 * ## What the platform will not let us do
 * `CookieManager.getCookie` returns name/value pairs and **nothing else** — no domain, no expiry, no
 * flags. So an export from here is a reconstruction: the values are real, the attributes are
 * defaults chosen to match what the site most likely set. [exportFor] is honest about that in its
 * own documentation, because a jar that looks authoritative but is partly invented is worse than one
 * that admits the gap.
 *
 * ## Incognito
 * [BrowserViewModel] sets `setAcceptCookie(false)` for private tabs. Writing cookies while that is
 * in force does nothing, so [apply] refuses rather than reporting success. Silently accepting a
 * paste and dropping it is the one behaviour that would make this tool untrustworthy.
 */
class CookieTools(private val cookieManager: CookieManager = CookieManager.getInstance()) {

    /** Why a change could not be made. */
    enum class Refusal {
        /** A private tab has cookies switched off, so nothing written would stick. */
        PRIVATE_MODE,

        /** The current page is not a web URL, so there is nothing to scope a cookie to. */
        NOT_A_PAGE,

        /** The pasted text contained no readable lines. */
        NOTHING_PARSED,

        /**
         * The jar's cookies belong to a different site than the one open.
         *
         * A distinct case from [NOT_A_PAGE] because the message is different: there is a page, it is
         * just the wrong one. Reporting this as "open a page first" sends the user to do something
         * that will not help.
         */
        HOST_MISMATCH,

        /**
         * The jar spans several hosts, so there is no page it can be scoped to.
         *
         * Refused rather than applied against whichever site happened to be open. Applying it would
         * set a session cookie for one site on another, which at best logs the user out somewhere
         * and at worst hands a valid token to a domain that should never see it.
         */
        MIXED_HOSTS
    }

    /** Outcome of applying a jar. */
    data class ApplyResult(
        val applied: Int,
        val removed: Int,
        val rejectedLines: List<String>,
        val refusal: Refusal? = null
    ) {
        val ok: Boolean get() = refusal == null
    }

    /**
     * Applies a pasted Netscape jar to the cookie store.
     *
     * @param text the pasted content.
     * @param pageUrl the page to scope against, normally `webView.url`. Used to reject a jar whose
     *   cookies belong to a different host — see [CookieJar.singleHostOf].
     * @param isPrivate whether a private tab is in front.
     * @param onDone called on the main thread once the store has acknowledged, because
     *   `setCookie` is asynchronous and reporting completion before the write lands would make the
     *   UI lie.
     */
    fun apply(
        text: String,
        pageUrl: String?,
        isPrivate: Boolean,
        onDone: (ApplyResult) -> Unit
    ) {
        if (isPrivate) {
            onDone(ApplyResult(0, 0, emptyList(), Refusal.PRIVATE_MODE))
            return
        }
        if (pageUrl == null || !UrlNormalizer.isWebUrl(pageUrl)) {
            onDone(ApplyResult(0, 0, emptyList(), Refusal.NOT_A_PAGE))
            return
        }

        val parsed = CookieJar.parse(text)
        val entries = parsed.entries
        if (entries.isEmpty()) {
            onDone(ApplyResult(0, 0, parsed.rejected, Refusal.NOTHING_PARSED))
            return
        }

        // Whether this jar belongs to the open page. The decision lives in CookieJar so it is unit
        // tested: as an inline `if` it once let a multi-host jar through, because the check only
        // fired when a single host could be identified, while the comment above it promised the
        // opposite.
        val refusal = when (CookieJar.hostFit(entries, UrlNormalizer.hostOf(pageUrl))) {
            CookieJar.HostFit.MATCHES -> null
            CookieJar.HostFit.MIXED_HOSTS -> Refusal.MIXED_HOSTS
            CookieJar.HostFit.DIFFERENT_HOST -> Refusal.HOST_MISMATCH
        }
        if (refusal != null) {
            onDone(ApplyResult(0, 0, parsed.rejected, refusal))
            return
        }

        var applied = 0
        var removed = 0
        // Chain the writes: setCookie is asynchronous, and firing 30 of them concurrently against a
        // store that serialises internally is how you get a partial apply with no way to tell.
        fun writeNext(index: Int) {
            if (index >= entries.size) {
                // Durability. Without this the change lives only in memory and is lost when the
                // WebView process is killed, which is exactly when a user would have expected it to
                // have stuck.
                cookieManager.flush()
                onDone(ApplyResult(applied, removed, parsed.rejected))
                return
            }
            val entry = entries[index]
            val header = if (entry.isDeletion()) entry.deletionHeaderFor() else entry.toSetCookieHeader()
            cookieManager.setCookie(pageUrl, header, ValueCallback<Boolean> { success ->
                if (success == true) {
                    if (entry.isDeletion()) removed++ else applied++
                }
                writeNext(index + 1)
            })
        }
        writeNext(0)
    }

    /**
     * Sets a single cookie, for the "add cookie" case where pasting a whole jar is overkill.
     *
     * @param expiryEpochSeconds [CookieEntry.SESSION] for a session cookie, or an absolute time.
     * @return false if the entry was rejected or the tab is private.
     */
    fun addSingle(
        entry: CookieEntry,
        pageUrl: String?,
        isPrivate: Boolean,
        onDone: (Boolean) -> Unit
    ) {
        if (isPrivate || pageUrl == null || !UrlNormalizer.isWebUrl(pageUrl)) {
            onDone(false)
            return
        }
        // Refuse the same shapes the parser would refuse, so the two paths cannot disagree.
        if (entry.name.isBlank() || entry.name.any { it == '=' || it == ';' }) {
            onDone(false)
            return
        }
        if (entry.value.any { it == ';' || it == '\n' || it == '\r' }) {
            onDone(false)
            return
        }
        cookieManager.setCookie(pageUrl, entry.toSetCookieHeader(), ValueCallback<Boolean> { ok ->
            cookieManager.flush()
            onDone(ok == true)
        })
    }

    /**
     * Deletes the cookies the store will actually hand back for [pageUrl], ignoring everything else.
     *
     * Only reachable for the current host, because `CookieManager` gives no way to enumerate the
     * whole jar with its attributes. [removeAll] is the blunt version.
     */
    fun clearFor(pageUrl: String, onDone: (Int) -> Unit) {
        val header = cookieManager.getCookie(pageUrl) ?: return onDone(0)
        val names = header.split(';')
            .mapNotNull { pair ->
                val name = pair.substringBefore('=').trim()
                name.takeIf { it.isNotEmpty() }
            }
        if (names.isEmpty()) return onDone(0)

        var cleared = 0
        fun step(index: Int) {
            if (index >= names.size) {
                cookieManager.flush()
                onDone(cleared)
                return
            }
            // Path is left at the document root on purpose: the store matches a deletion on domain
            // and path, and a cookie set on `/app` would survive a deletion aimed at `/`.
            val header = "${names[index]}=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT"
            cookieManager.setCookie(pageUrl, header, ValueCallback<Boolean> { ok ->
                if (ok == true) cleared++
                step(index + 1)
            })
        }
        step(0)
    }

    /** Deletes every cookie the app's WebView holds, then reports how many writes succeeded. */
    fun removeAll(onDone: (Boolean) -> Unit) {
        cookieManager.removeAllCookies { success ->
            cookieManager.flush()
            onDone(success == true)
        }
    }

    /**
     * The cookies currently held for [pageUrl], reconstructed as a Netscape jar.
     *
     * ## What is real and what is reconstructed
     * The names and values are exactly what the store holds. The domain is the host asked about, and
     * the path, secure flag and expiry are **not available from the platform** and are set to
     * permissive defaults — path `/`, secure false, session lifetime. That is enough to paste the
     * jar back in and get the same cookies, but it is not a faithful export of what the site
     * originally set.
     *
     * This is a platform limit, not a shortcut: `CookieManager` exposes no API to read a cookie's
     * attributes, only the `name=value` string it will hand to a request.
     */
    fun exportFor(pageUrl: String): String {
        val host = UrlNormalizer.hostOf(pageUrl) ?: return ""
        val header = cookieManager.getCookie(pageUrl) ?: return ""
        val entries = header.split(';')
            .mapNotNull { pair ->
                val name = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=', "").trim()
                if (name.isEmpty()) null
                else CookieEntry(domain = host, name = name, value = value)
            }
        return CookieJar.format(entries)
    }
}