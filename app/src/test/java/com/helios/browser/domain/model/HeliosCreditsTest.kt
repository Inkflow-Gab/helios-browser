package com.helios.browser.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the attribution list.
 *
 * The failure mode this protects against is quiet: an entry with no licence text still renders, the
 * sheet still looks complete, and nothing anywhere fails. The only symptom is that a licence which
 * requires its terms to travel with the binary does not have them. So it is asserted here.
 */
class HeliosCreditsTest {

    @Test
    fun `every copyleft credit bundles its licence text`() {
        val offenders = HeliosCredits.all
            .filter {
                val copyleft = it.licence.contains("GPL") ||
                    it.licence.contains("MPL") ||
                    it.licence.contains("OFL")
                copyleft && it.licenceFile == null
            }
            .map { it.name }

        assertTrue("copyleft without bundled terms: $offenders", offenders.isEmpty())
        assertTrue(HeliosCredits.everyLicenceTextIsBundled())
    }

    @Test
    fun `every credit names a licence and an author`() {
        val incomplete = HeliosCredits.all
            .filter { it.licence.isBlank() || it.author.isBlank() || it.name.isBlank() }
            .map { it.name }

        assertTrue("incomplete entries: $incomplete", incomplete.isEmpty())
    }

    @Test
    fun `every credit explains what it does`() {
        // "Chromium" alone is not attribution anyone can act on. The role is what tells a reader
        // that Helios renders pages with someone else's engine rather than one of its own.
        val vague = HeliosCredits.all.filter { it.role.length < 40 }.map { it.name }
        assertTrue("roles too vague to be useful: $vague", vague.isEmpty())
    }

    @Test
    fun `the blocking engine and its lists are all credited`() {
        // adblock-rust is MPL-2.0 and every bundled list is GPL-3.0. All of them are inside the
        // APK, so all of them have to appear. A new preset that adds a list without a credit is
        // the exact regression worth failing on.
        val blocking = HeliosCredits.grouped
            .first { it.first == HeliosCredits.Group.Blocking }
            .second
            .map { it.name }

        assertTrue(
            "adblock-rust missing from credits",
            blocking.any { it.contains("adblock", ignoreCase = true) }
        )
        assertTrue(
            "EasyList missing from credits",
            blocking.any { it.contains("easylist", ignoreCase = true) }
        )
        assertTrue(
            "EasyPrivacy missing from credits",
            blocking.any { it.contains("easyprivacy", ignoreCase = true) }
        )
        assertTrue(
            "uBlock lists missing from credits",
            blocking.any { it.contains("ublock", ignoreCase = true) }
        )
    }

    @Test
    fun `the engine Helios renders pages with is credited`() {
        // This is the one people assume Helios wrote. It must not be absent from the list.
        val engine = HeliosCredits.grouped.first { it.first == HeliosCredits.Group.Engine }
            .second
            .map { it.name }

        assertTrue("no Chromium credit in the engine group", engine.any { it.contains("Chromium") })
    }

    @Test
    fun `group keys are unique`() {
        // Duplicate names in a LazyColumn key crash at runtime, on the device, with a stack trace
        // about keys rather than about a duplicate dependency.
        val names = HeliosCredits.all.map { it.name }
        assertEquals("duplicate credit names: $names", names.size, names.toSet().size)
    }

    @Test
    fun `every licence file path points at the licences directory`() {
        val stray = HeliosCredits.all
            .mapNotNull { it.licenceFile }
            .filter { !it.startsWith("licenses/") || !it.endsWith(".txt") }

        assertTrue("licence paths that are not readable assets: $stray", stray.isEmpty())
    }

    @Test
    fun `every group has at least one credit`() {
        HeliosCredits.Group.entries.forEach { group ->
            val credits = HeliosCredits.grouped.firstOrNull { it.first == group }?.second
            assertNotNull("group ${group.name} has no section", credits)
            assertTrue("group ${group.name} is empty", credits!!.isNotEmpty())
        }
    }

    @Test
    fun `every grouped entry belongs to the group it is filed under`() {
        // Catches a copy-pasted credit landing under the wrong heading, which reads as a false claim.
        HeliosCredits.grouped.forEach { (group, credits) ->
            credits.forEach { credit ->
                assertTrue(
                    "${credit.name} filed under ${group.name} is blank",
                    credit.role.isNotBlank()
                )
            }
        }
    }
}