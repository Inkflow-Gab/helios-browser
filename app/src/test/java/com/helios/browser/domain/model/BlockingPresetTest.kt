package com.helios.browser.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [BlockingPreset], which decides what the adblock engine is actually built from.
 *
 * The properties pinned here are the ones that would be wrong in a way nobody would notice: a preset
 * whose list set silently changed, or whose stored name stopped parsing.
 */
class BlockingPresetTest {

    @Test
    fun `fromName round trips every preset`() {
        BlockingPreset.entries.forEach { preset ->
            assertEquals(preset, BlockingPreset.fromName(preset.name))
        }
    }

    /**
     * A stored name that no longer exists — an app downgrade, or a hand-edited preference file —
     * must fall back to the default rather than throwing on the launch path.
     */
    @Test
    fun `fromName falls back for unknown values`() {
        assertEquals(BlockingPreset.DEFAULT, BlockingPreset.fromName(null))
        assertEquals(BlockingPreset.DEFAULT, BlockingPreset.fromName(""))
        assertEquals(BlockingPreset.DEFAULT, BlockingPreset.fromName("SOME_REMOVED_PRESET"))
        // Case matters: stored names come from `name`, which is exact.
        assertEquals(BlockingPreset.DEFAULT, BlockingPreset.fromName("helios_mobile"))
    }

    /**
     * Names are persisted, so they must not change when the enum is reordered. If they did, every
     * existing install would silently switch presets on update.
     */
    @Test
    fun `names are stable and match what is persisted`() {
        assertEquals("HELIOS_BALANCED", BlockingPreset.DEFAULT.name)
        assertEquals("HELIOS_MOBILE", BlockingPreset.HELIOS_MOBILE.name)
        assertEquals("UBLOCK_STRICT", BlockingPreset.UBLOCK_STRICT.name)
        assertEquals("TRACKERS_ONLY", BlockingPreset.TRACKERS_ONLY.name)
    }

    @Test
    fun `every preset has at least one list and a size hint`() {
        BlockingPreset.entries.forEach { preset ->
            assertTrue("${preset.name} has no lists", preset.lists.isNotEmpty())
            assertTrue("${preset.name} has no summary", preset.summary.isNotBlank())
            assertTrue("${preset.name} has no size hint", preset.sizeHint.isNotBlank())
        }
    }

    /**
     * No list may appear twice: a duplicate would be parsed twice for no benefit and, worse, the
     * `$badfilter` and exception interactions between two copies of the same list are not something
     * to reason about.
     */
    @Test
    fun `no preset lists the same list twice`() {
        BlockingPreset.entries.forEach { preset ->
            val names = preset.lists.map { it.name }
            assertEquals("${preset.name} has duplicates: $names", names.size, names.distinct().size)
        }
    }

    /** Every preset must keep the tracker lists, or "blocking on" means something different. */
    @Test
    fun `every preset blocks trackers`() {
        BlockingPreset.entries.forEach { preset ->
            assertTrue(
                "${preset.name} has no tracker list",
                preset.lists.any { it == FilterList.EASYPRIVACY || it == FilterList.UBLOCK_PRIVACY }
            )
        }
    }

    /** The annoyances lists are what make UBLOCK_STRICT the most likely to hide something needed. */
    @Test
    fun `only the strict preset hides site annoyances`() {
        val hiding = BlockingPreset.entries.filter { preset ->
            preset.lists.any { it.name.startsWith("UBLOCK_ANNOYANCES") }
        }
        assertEquals(listOf(BlockingPreset.UBLOCK_STRICT), hiding)
    }

    /**
     * The bundled asset path is what makes blocking work offline on first run, so a typo here is a
     * silent loss of protection rather than an error.
     *
     * The path is relative to the assets root, because that is what `AssetManager.open` takes — it
     * is not prefixed with `assets/`.
     */
    @Test
    fun `every list has an asset path and a remote url`() {
        FilterList.entries.forEach { list ->
            assertTrue(
                "${list.name} asset path should be gzipped under blocklists/",
                list.assetPath.startsWith("blocklists/") && list.assetPath.endsWith(".gz")
            )
            assertTrue(
                "${list.name} should have an https source",
                list.remoteUrl.startsWith("https://")
            )
            assertTrue("${list.name} has no display name", list.displayName.isNotBlank())
            assertTrue("${list.name} has no licence note", list.licenceNote.isNotBlank())
        }
    }

    /** Distinct lists must not share an asset, or one would overwrite the other in the APK. */
    @Test
    fun `asset paths are unique`() {
        val paths = FilterList.entries.map { it.assetPath }
        assertEquals(paths.size, paths.distinct().size)
        val urls = FilterList.entries.map { it.remoteUrl }
        assertEquals(urls.size, urls.distinct().size)
    }

    /**
     * What actually distinguishes the presets is *which kind* of list is in them, not how many.
     *
     * An earlier version of this asserted `TRACKERS_ONLY.lists.size < BALANCED.lists.size`, which is
     * false — both are two lists. List count was never a proxy for engine size: EasyPrivacy is a
     * third smaller than EasyList, so trackers-only is genuinely lighter with the same number of
     * entries. Asserting the property that matters instead.
     */
    @Test
    fun `presets differ by which lists they contain, not how many`() {
        val adLists = setOf(
            FilterList.EASYLIST,
            FilterList.UBLOCK_FILTERS,
            FilterList.UBLOCK_MOBILE
        )

        assertNotEquals(
            BlockingPreset.TRACKERS_ONLY.listNames,
            BlockingPreset.HELIOS_BALANCED.listNames
        )
        // Trackers only blocks no ad list at all. That is the whole point of it.
        assertTrue(
            "TRACKERS_ONLY should contain no ad list",
            BlockingPreset.TRACKERS_ONLY.lists.none { it in adLists }
        )
        assertTrue(
            "HELIOS_BALANCED should contain an ad list",
            BlockingPreset.HELIOS_BALANCED.lists.any { it in adLists }
        )
        assertTrue(
            "HELIOS_BALANCED should contain no annoyances list",
            BlockingPreset.HELIOS_BALANCED.lists.none { it.name.startsWith("UBLOCK_ANNOYANCES") }
        )
        assertTrue(
            "Strict is a superset of balanced",
            BlockingPreset.HELIOS_BALANCED.lists.all { it in BlockingPreset.UBLOCK_STRICT.lists }
        )
    }

    @Test
    fun `list names are human readable`() {
        assertEquals(
            listOf("EasyList", "EasyPrivacy"),
            BlockingPreset.HELIOS_BALANCED.listNames
        )
    }
}