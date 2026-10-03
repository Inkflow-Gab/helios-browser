package com.helios.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [CookieJar] against the real format.
 *
 * The examples are lifted from an actual browser export rather than invented, because the shape of a
 * Netscape jar is not something to guess at: a mis-parsed cookie is a silent login failure that looks
 * like the site rejecting a good session.
 */
class CookieJarTest {

    private val realExport = """
        cursor.com	FALSE	/	TRUE	1819202162	cursor_anonymous_id	ad6a7a72-5051-4cdb-9f2b-1406238b95d3
        cursor.com	FALSE	/	TRUE	1790857802	logoCountry	IN
        cursor.com	FALSE	/	FALSE	1819801596	_dd_s	aid=d19d60be&rum=2&id=cc24da15&created=1788265596181&expire=1788266496181
        #HttpOnly_cursor.com	FALSE	/	TRUE	1792850268	session_token	eyJhbGciOiJIUzI1NiJ9.abc.def
    """.trimIndent()

    @Test
    fun `parses a real seven column export`() {
        val result = CookieJar.parse(realExport)
        val entries = result.entries

        assertEquals(4, entries.size)
        assertTrue("nothing should be rejected: ${result.rejected}", result.rejected.isEmpty())

        val first = entries[0]
        assertEquals("cursor.com", first.domain)
        assertFalse(first.includeSubdomains)
        assertEquals("/", first.path)
        assertTrue(first.secure)
        assertEquals(1_819_202_162L, first.expiresAtEpochSeconds)
        assertEquals("cursor_anonymous_id", first.name)
        assertEquals("ad6a7a72-5051-4cdb-9f2b-1406238b95d3", first.value)
    }

    /**
     * A value containing `=` is extremely common — base64, JSON, signed tokens — and splitting on
     * every `=` would truncate it into something the site rejects.
     */
    @Test
    fun `keeps everything after the first equals sign in the value`() {
        val entries = CookieJar.parse(
            "x.test\tFALSE\t/\tTRUE\t1819202162\ttoken\taGVsbG8=extra=more"
        ).entries

        assertEquals(1, entries.size)
        assertEquals("token", entries[0].name)
        assertEquals("aGVsbG8=extra=more", entries[0].value)
    }

    /** A real exported value. Splitting it would produce a token the server does not recognise. */
    @Test
    fun `keeps a base64 url encoded jwt intact`() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyXzAxIn0.xTcv0B7KppM2qLHBUgIAcxV"
        val entries = CookieJar.parse(
            "x.test\tFALSE\t/\tTRUE\t1819202162\tsession\t$jwt"
        ).entries

        assertEquals(jwt, entries.single().value)
    }

    /** `#HttpOnly_` is a marker, not a comment, and must not be skipped as one. */
    @Test
    fun `httponly marker is read as a flag not a comment`() {
        val entries = CookieJar.parse(
            "#HttpOnly_x.test\tFALSE\t/\tTRUE\t1819202162\tsecret\topaque"
        ).entries

        val cookie = entries.single()
        assertEquals("x.test", cookie.domain)
        assertTrue(cookie.httpOnly)
        assertEquals("secret", cookie.name)
        assertEquals("opaque", cookie.value)
    }

    /** A comment is a comment, and dropping it is not the same as keeping a cookie. */
    @Test
    fun `plain comments are ignored`() {
        val result = CookieJar.parse(
            """
            # Netscape HTTP Cookie File
            ! this is a note

            x.test	FALSE	/	FALSE	0	s	a=b
            """.trimIndent()
        )

        assertEquals(1, result.entries.size)
        assertTrue(result.rejected.isEmpty())
    }

    /**
     * The older six-column export packs name=value into the last field. A user pasting one of those
     * should not get nothing.
     */
    @Test
    fun `parses the legacy six column format`() {
        val entries = CookieJar.parse(
            "x.test\tFALSE\t/\tFALSE\t1819202162\tsession=abc123"
        ).entries

        val cookie = entries.single()
        assertEquals("session", cookie.name)
        assertEquals("abc123", cookie.value)
    }

    @Test
    fun `accepts space separated exports`() {
        val entries = CookieJar.parse("x.test FALSE / FALSE 1819202162 name value").entries
        assertEquals("name", entries.single().name)
        assertEquals("value", entries.single().value)
    }

    /**
     * A blank or garbage expiry means "session", not "1970". Reading it as zero would make every
     * such cookie a deletion.
     */
    @Test
    fun `a non numeric expiry means session cookie`() {
        val entries = CookieJar.parse(
            "x.test\tFALSE\t/\tTRUE\tnot-a-number\tname\tvalue"
        ).entries
        assertFalse(entries.single().hasExpiry)
    }

    /**
     * A truncated line is rejected, not guessed at. Half a line would set a cookie on the wrong
     * domain or with the wrong value, which is worse than setting none.
     */
    @Test
    fun `truncated lines are rejected and reported`() {
        val result = CookieJar.parse(
            """
            x.test	FALSE	/	TRUE	1819202162	good	value
            x.test	FALSE	/	TRUE
            """.trimIndent()
        )

        assertEquals(1, result.entries.size)
        assertEquals(1, result.rejected.size)
        assertTrue(result.rejected.single().contains("x.test"))
    }

    @Test
    fun `a line with no dot in the domain is rejected`() {
        // Without a dot this could not be distinguished from a bare word, and `localhost` is a
        // special case the cookie store handles itself.
        val result = CookieJar.parse("notahost\tFALSE\t/\tFALSE\t0\tn\tv")
        assertTrue(result.entries.isEmpty())
        assertEquals(1, result.rejected.size)
    }

    @Test
    fun `a name containing an equals sign is rejected`() {
        // It would be ambiguous with the packed legacy format and cannot be represented in a header.
        val result = CookieJar.parse("x.test\tFALSE\t/\tFALSE\t0\tna=me\tvalue")
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `a value containing a semicolon is rejected`() {
        // A semicolon would terminate the attribute list and let the rest be read as an attribute.
        val result = CookieJar.parse("x.test\tFALSE\t/\tFALSE\t0\tname\tva;lue")
        assertTrue(result.entries.isEmpty())
    }

    @Test
    fun `blank input is parsed not thrown`() {
        val result = CookieJar.parse("")
        assertTrue(result.entries.isEmpty())
        assertTrue(result.rejected.isEmpty())
    }

    @Test
    fun `empty input is Parsed rather than Partial`() {
        // The distinction drives the UI message: "nothing to apply" versus "these lines failed".
        assertTrue(CookieJar.parse("") is CookieJar.Result.Parsed)
        assertTrue(CookieJar.parse("garbage") is CookieJar.Result.Partial)
    }

    @Test
    fun `multi host jars have no single host`() {
        val entries = CookieJar.parse(
            """
            a.test	FALSE	/	FALSE	0	n1	v1
            b.test	FALSE	/	FALSE	0	n2	v2
            """.trimIndent()
        ).entries

        assertNull(CookieJar.singleHostOf(entries))
    }

    @Test
    fun `single host jars report that host case insensitively`() {
        val entries = CookieJar.parse(
            """
            Cursor.com	FALSE	/	FALSE	0	n1	v1
            cursor.com	FALSE	/	FALSE	0	n2	v2
            """.trimIndent()
        ).entries

        assertEquals("cursor.com", CookieJar.singleHostOf(entries))
    }

    @Test
    fun `leading dots on the domain are tolerated`() {
        val entries = CookieJar.parse(".x.test\tFALSE\t/\tFALSE\t0\tn\tv").entries
        assertEquals("x.test", entries.single().domain)
    }

    // --- header generation ------------------------------------------------------------------------

    @Test
    fun `a future expiry becomes a positive max age`() {
        val now = 1_000_000_000L
        val cookie = CookieEntry("x.test", name = "n", value = "v", expiresAtEpochSeconds = now + 3600)
        assertTrue(cookie.toSetCookieHeader(now).contains("Max-Age=3600"))
    }

    @Test
    fun `a past expiry becomes max age zero which deletes`() {
        val now = 1_000_000_000L
        val cookie = CookieEntry("x.test", name = "n", value = "v", expiresAtEpochSeconds = now - 60)
        assertTrue(cookie.isDeletion(now))
        assertTrue(cookie.toSetCookieHeader(now).contains("Max-Age=0"))
    }

    @Test
    fun `a deletion header omits the original value`() {
        // Sending a token the caller asked to destroy would be a bug in the other direction.
        val header = CookieEntry("x.test", name = "n", value = "secret").deletionHeaderFor()
        assertFalse(header.contains("secret"))
        assertTrue(header.contains("Max-Age=0"))
        assertTrue(header.startsWith("n=;"))
    }

    @Test
    fun `a session cookie gets a long max age not zero`() {
        // The store cannot express "no expiry" through setCookie, and zero would delete it
        // immediately instead of setting it.
        val header = CookieEntry("x.test", name = "n", value = "v").toSetCookieHeader(1_000_000_000L)
        val maxAge = header.substringAfter("Max-Age=").substringBefore(';').toLong()
        assertTrue("expected a positive max age, got $maxAge", maxAge > 0)
    }

    @Test
    fun `secure and httponly are emitted as attributes`() {
        val header = CookieEntry(
            "x.test", secure = true, httpOnly = true, name = "n", value = "v"
        ).toSetCookieHeader()
        assertTrue(header.contains("; Secure"))
        assertTrue(header.contains("; HttpOnly"))
    }

    @Test
    fun `include subdomains becomes a leading dot`() {
        assertEquals(".x.test", CookieEntry("x.test", includeSubdomains = true, name = "n", value = "v").domainForHeader)
        assertEquals("x.test", CookieEntry("x.test", includeSubdomains = false, name = "n", value = "v").domainForHeader)
        // A dot already on the input must not become two.
        assertEquals(".x.test", CookieEntry(".x.test", includeSubdomains = true, name = "n", value = "v").domainForHeader)
    }

    // --- round trip -------------------------------------------------------------------------------

    /**
     * Copy out, paste back in, same cookies. This is the feature working or it is not: the two
     * halves have to agree.
     */
    @Test
    fun `format round trips through parse`() {
        val original = CookieJar.parse(realExport).entries
        val reparsed = CookieJar.parse(CookieJar.format(original)).entries

        assertEquals(original.size, reparsed.size)
        original.forEachIndexed { index, before ->
            val after = reparsed[index]
            assertEquals("name at $index", before.name, after.name)
            assertEquals("value at $index", before.value, after.value)
            assertEquals("domain at $index", before.domain, after.domain)
            assertEquals("expiry at $index", before.expiresAtEpochSeconds, after.expiresAtEpochSeconds)
            assertEquals("secure at $index", before.secure, after.secure)
            assertEquals("path at $index", before.path, after.path)
            assertEquals("httponly at $index", before.httpOnly, after.httpOnly)
        }
    }
}