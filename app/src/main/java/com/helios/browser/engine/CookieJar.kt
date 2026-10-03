package com.helios.browser.engine

import java.util.Locale

/**
 * One cookie, in the terms the WebView cookie store uses.
 *
 * @param domain the host the cookie belongs to.
 * @param includeSubdomains whether it also applies to subdomains.
 * @param path the path prefix it is sent on.
 * @param secure whether it is sent over HTTPS only.
 * @param httpOnly whether the page's own script can read it. Tracked because a Netscape export
 *   carries this as a `#HttpOnly_` marker on the domain column, and dropping it would silently
 *   downgrade a cookie the site deliberately made unreadable.
 * @param expiresAtEpochSeconds when it expires. [SESSION] means "until the WebView's cookie jar
 *   goes away". A value in the past is how a deletion is expressed — see [deletionHeaderFor].
 * @param name the cookie name.
 * @param value the value, as-is. Not decoded: a Netscape export is already percent-encoded, and
 *   decoding would corrupt values that legitimately contain `%`.
 */
data class CookieEntry(
    val domain: String,
    val includeSubdomains: Boolean = false,
    val path: String = "/",
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
    val expiresAtEpochSeconds: Long = SESSION,
    val name: String,
    val value: String
) {
    /** True when the cookie has a fixed expiry rather than living for the session. */
    val hasExpiry: Boolean get() = expiresAtEpochSeconds != SESSION

    /** True when this entry names something to delete rather than a cookie to set. */
    fun isDeletion(nowEpochSeconds: Long = CookieJar.nowEpochSeconds()): Boolean =
        hasExpiry && expiresAtEpochSeconds in 1 until nowEpochSeconds

    /**
     * The `Set-Cookie` form, which is what `CookieManager.setCookie` accepts.
     *
     * `Max-Age` is preferred over `Expires` because it is relative and immune to clock skew between
     * this device and whatever minted the token. A past expiry becomes `Max-Age=0`, which is how the
     * cookie store is told to delete rather than store.
     */
    fun toSetCookieHeader(nowEpochSeconds: Long = CookieJar.nowEpochSeconds()): String =
        buildString {
            append(name).append('=').append(value)
            if (domain.isNotBlank()) append("; Domain=").append(domainForHeader)
            if (path.isNotBlank()) append("; Path=").append(path)
            if (secure) append("; Secure")
            if (httpOnly) append("; HttpOnly")
            append("; Max-Age=").append(
                if (hasExpiry) (expiresAtEpochSeconds - nowEpochSeconds).coerceAtLeast(0) else SESSION_MAX_AGE
            )
        }

    /**
     * The `Set-Cookie` header that removes this cookie.
     *
     * A deletion only needs the name, domain and path to identify the cookie; the value, expiry and
     * flags are ignored by the store. Setting them to the deletion defaults rather than echoing the
     * original avoids sending a token the caller asked to destroy.
     */
    fun deletionHeaderFor(): String = buildString {
        append(name).append("=;")
        if (domain.isNotBlank()) append(" Domain=").append(domainForHeader)
        if (path.isNotBlank()) append("; Path=").append(path)
        append("; Max-Age=0")
        append("; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
    }

    /**
     * The domain as a cookie header wants it.
     *
     * A Netscape export writes `includeSubdomains` as its own boolean column, but the header encodes
     * the same thing by prefixing the domain with a dot. The dot is deprecated but still the only
     * way to express it and is accepted by every current browser.
     */
    val domainForHeader: String
        get() {
            val trimmed = domain.trim().removePrefix(".")
            return if (includeSubdomains) ".$trimmed" else trimmed
        }

    companion object {
        /** Not persisted: lives as long as the WebView's cookie jar. */
        const val SESSION = 0L

        /**
         * `Max-Age` for a session cookie.
         *
         * A large relative lifetime rather than `Session`, because the WebView cookie store has no
         * way to express "no expiry" through `setCookie` and would otherwise fall back to its own
         * default. Two years is the same bound Chrome uses for session cookies.
         */
        const val SESSION_MAX_AGE = 63_072_000L
    }
}

/**
 * Parsing and formatting for Netscape-format cookie jars — the format every browser's "copy
 * cookies" button produces.
 *
 * ```
 * cursor.com   FALSE   /   TRUE   1819202162   cursor_anonymous_id   ad6a7a72-…
 * <domain>     <sub>   <path>   <secure>   <expiry>   <name>   <value>
 * ```
 *
 * ## Pure on purpose
 * Nothing here touches `CookieManager` or `android.webkit`, so all of it is unit tested on a plain
 * JVM. That matters more than usual here: a mis-parsed cookie is a silent authentication failure
 * that looks like a site rejecting a perfectly good session.
 *
 * ## Two shapes are accepted
 * The modern seven-column export, and the older six-column one where the last field packs
 * `name=value`. Anything shorter is rejected rather than guessed at — a half-read line would set a
 * cookie on the wrong domain, which is worse than setting none.
 */
object CookieJar {

    /** Marker Netscape uses in the domain column for an HttpOnly cookie. */
    private const val HTTP_ONLY_PREFIX = "#HttpOnly_"

    /** Modern format column count. */
    private const val FIELDS_MODERN = 7

    /** Packed legacy format column count. */
    private const val FIELDS_PACKED = 6

    private const val MAX_REJECTED_ECHO = 80

    /** What [parse] produced, including what it could not read. */
    sealed interface Result {
        /** Every line parsed. */
        data class Parsed(val entries: List<CookieEntry>) : Result

        /**
         * Some lines parsed and some did not.
         *
         * @param rejected the unreadable lines, truncated. They are reported rather than dropped:
         *   a user who pastes a jar and sees no error will assume it all applied, and a cookie that
         *   silently did not is an authentication failure with no cause.
         */
        data class Partial(val entries: List<CookieEntry>, val rejected: List<String>) : Result
    }

    /** The parsed entries, whatever else happened. */
    val Result.entries: List<CookieEntry>
        get() = when (this) {
            is Result.Parsed -> entries
            is Result.Partial -> entries
        }

    /** The unreadable lines, or empty. */
    val Result.rejected: List<String>
        get() = (this as? Result.Partial)?.rejected.orEmpty()

    fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1000

    /**
     * Parses a pasted cookie jar.
     *
     * Blank lines and `#` comments are ignored. `#HttpOnly_` is a marker rather than a comment and
     * is handled before the comment check, so an HttpOnly cookie is not mistaken for a note.
     */
    fun parse(text: String): Result {
        val entries = ArrayList<CookieEntry>()
        val rejected = ArrayList<String>()

        text.lineSequence().forEach { raw ->
            val line = raw.trim('\n', '\r')
            if (line.isBlank()) return@forEach

            val httpOnly = line.startsWith(HTTP_ONLY_PREFIX)
            val body = if (httpOnly) line.removePrefix(HTTP_ONLY_PREFIX) else line
            if (!httpOnly && body.trimStart().startsWith("#")) return@forEach

            val entry = parseLine(body, httpOnly)
            if (entry != null) entries.add(entry) else rejected.add(line.take(MAX_REJECTED_ECHO))
        }

        return if (rejected.isEmpty()) Result.Parsed(entries) else Result.Partial(entries, rejected)
    }

    /** One line to one entry, or null when the line cannot be trusted. */
    private fun parseLine(line: String, httpOnly: Boolean): CookieEntry? {
        // Tabs are the real separator, but plenty of exporters substitute spaces, so fall back to
        // whitespace runs. The name and value are the last two fields either way, which is what
        // makes the fallback safe for a value containing spaces.
        val tabbed = line.split('\t').map { it.trim() }.filter { it.isNotEmpty() }
        val fields = if (tabbed.size >= FIELDS_MODERN) {
            tabbed
        } else {
            val spaced = line.trim().split(Regex("\\s+"))
            if (spaced.size >= FIELDS_PACKED) spaced else return null
        }
        if (fields.size < FIELDS_PACKED) return null

        val domain = fields[0]
        if (!looksLikeHost(domain)) return null

        val name: String
        val value: String
        if (fields.size >= FIELDS_MODERN) {
            name = fields[5]
            value = fields[6]
        } else {
            // Legacy: the last field packs name=value. Split on the first `=` so a value containing
            // further `=` survives intact.
            val packed = fields[5]
            val split = packed.indexOf('=')
            if (split <= 0) return null
            name = packed.substring(0, split)
            value = packed.substring(split + 1)
        }

        // A cookie name may not contain `=` or a separator. One that does is a malformed export
        // rather than a cookie.
        if (name.isBlank() || name.any { it == '=' || it == ';' || it.isISOControl() }) return null
        if (value.any { it == ';' || it == '\n' || it == '\r' }) return null

        return CookieEntry(
            domain = domain,
            includeSubdomains = fields[1].equals("TRUE", ignoreCase = true),
            path = fields[2].ifBlank { "/" },
            secure = fields[3].equals("TRUE", ignoreCase = true),
            httpOnly = httpOnly,
            // A non-numeric expiry means "session", which is what a blank or garbage column should
            // mean rather than an epoch of zero being read as 1970.
            expiresAtEpochSeconds = fields[4].toLongOrNull() ?: CookieEntry.SESSION,
            name = name,
            value = value
        )
    }

    /**
     * Renders cookies back into the Netscape format.
     *
     * Round-tripping matters: "copy cookies" and "paste cookies" are two halves of one feature, and
     * an export that cannot be read back means the tool is not finished.
     */
    fun format(cookies: List<CookieEntry>): String = buildString {
        cookies.forEach { cookie ->
            val domainColumn = if (cookie.httpOnly) {
                HTTP_ONLY_PREFIX + cookie.domain.removePrefix(".")
            } else {
                cookie.domain.removePrefix(".")
            }
            append(domainColumn).append('\t')
            append(if (cookie.includeSubdomains) "TRUE" else "FALSE").append('\t')
            append(cookie.path).append('\t')
            append(if (cookie.secure) "TRUE" else "FALSE").append('\t')
            append(cookie.expiresAtEpochSeconds).append('\t')
            append(cookie.name).append('\t')
            append(cookie.value).append('\n')
        }
    }

    /**
     * The single host a jar's cookies apply to, when they all agree.
     *
     * Null otherwise. A jar spanning several hosts cannot be applied against one URL, and guessing
     * which host was meant would set someone's session cookie on the wrong site.
     */
    fun singleHostOf(entries: List<CookieEntry>): String? =
        entries.map { it.domain.removePrefix(".").lowercase(Locale.ROOT) }.distinct().singleOrNull()

    /** A host is plausible if it has a dot, no scheme or port, and only host-legal characters. */
    private fun looksLikeHost(candidate: String): Boolean {
        val host = candidate.trim().removePrefix(".")
        if (host.isEmpty() || !host.contains('.')) return false
        return host.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
    }
}