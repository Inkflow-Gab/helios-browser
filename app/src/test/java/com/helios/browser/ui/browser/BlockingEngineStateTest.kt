package com.helios.browser.ui.browser

import com.helios.browser.domain.model.BlockingPreset
import com.helios.browser.engine.BlockListSource
import com.helios.browser.engine.BlockingEngineSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the conversion between the engine's snapshot and the UI's value type.
 *
 * A missed field here does not fail to compile — it silently leaves a settings screen showing a
 * default forever, which is the kind of bug nobody notices until a user reports that the "Update"
 * button does nothing.
 */
class BlockingEngineStateTest {

    @Test
    fun `every field is carried across`() {
        val snapshot = BlockingEngineSnapshot(
            isAvailable = true,
            isReady = true,
            isRefreshing = true,
            source = BlockListSource.REMOTE,
            preset = BlockingPreset.UBLOCK_STRICT,
            cacheSizeBytes = 4_194_304L
        )

        assertEquals(
            BlockingEngineState(
                isAvailable = true,
                isReady = true,
                isRefreshing = true,
                source = BlockListSource.REMOTE,
                preset = BlockingPreset.UBLOCK_STRICT,
                activePreset = BlockingPreset.UBLOCK_STRICT,
                cacheSizeBytes = 4_194_304L
            ),
            snapshot.toUiState(BlockingPreset.UBLOCK_STRICT)
        )
    }

    @Test
    fun `defaults survive the round trip`() {
        assertEquals(BlockingEngineState(), BlockingEngineSnapshot().toUiState(BlockingPreset.DEFAULT))
    }

    /**
     * The chosen preset and the loaded one are deliberately separate. While a switch is rebuilding,
     * the settings say one thing and the engine contains another, and the sheet must be able to
     * show that rather than claim the new lists are live.
     */
    @Test
    fun `a preset switch in flight is reported as rebuilding`() {
        val snapshot = BlockingEngineSnapshot(
            isReady = true,
            source = BlockListSource.BUNDLED,
            preset = BlockingPreset.HELIOS_BALANCED
        )
        val chosen = BlockingPreset.UBLOCK_STRICT

        val state = snapshot.toUiState(chosen)
        assertEquals(BlockingPreset.UBLOCK_STRICT, state.preset)
        assertEquals(BlockingPreset.HELIOS_BALANCED, state.activePreset)
        assertTrue(state.isRebuilding)
    }

    @Test
    fun `a settled preset is not rebuilding`() {
        val state = BlockingEngineSnapshot(
            isReady = true,
            preset = BlockingPreset.TRACKERS_ONLY
        ).toUiState(BlockingPreset.TRACKERS_ONLY)
        assertFalse(state.isRebuilding)
        assertEquals(BlockingPreset.TRACKERS_ONLY, state.activePreset)
    }

    /**
     * Guard against a snapshot field being dropped in the conversion.
     *
     * The two types deliberately differ: the UI type carries `activePreset` *and* `preset`, because
     * one is what the running engine contains and the other is what the user asked for, and
     * comparing them is the whole point of the `isRebuilding` flag. So this does not assert that the
     * field sets are identical — that was the wrong property, and it failed for the right reason.
     *
     * What actually matters is the direction: everything the snapshot reports must survive into the
     * UI value, so a field added to `BlockingEngineSnapshot` and forgotten in `toUiState` fails here
     * instead of leaving a settings screen showing a default forever.
     *
     * Compiler-generated fields are skipped by name. `@Immutable` makes the Compose compiler add a
     * `$stable` field to the UI type, and it is not marked synthetic, so filtering on `isSynthetic`
     * does not catch it. A `$`-prefixed name is the convention every Kotlin compiler here uses for
     * generated members, and a real property can never have one.
     */
    @Test
    fun `the ui type carries every field the snapshot reports`() {
        fun declared(type: Class<*>) = type.declaredFields
            .filterNot { it.isSynthetic || it.name.startsWith("$") }
            .map { it.name }
            .toSet()

        val snapshotFields = declared(BlockingEngineSnapshot::class.java)
        val uiFields = declared(BlockingEngineState::class.java)

        assertTrue(
            "dropped by toUiState: ${snapshotFields - uiFields}",
            uiFields.containsAll(snapshotFields)
        )
        // The UI type adds exactly one field of its own: `activePreset`. Note that `preset` is *not*
        // a surplus — the snapshot has one too, holding what the running engine was built from,
        // while the UI type's `preset` holds what the user chose. The two same-named fields mean
        // different things, which is why `toUiState` takes the chosen value as a parameter rather
        // than reading it off the snapshot.
        assertEquals(setOf("activePreset"), uiFields - snapshotFields)
    }

    @Test
    fun `copy keeps unspecified fields`() {
        val original = BlockingEngineState(
            isAvailable = true,
            isReady = true,
            source = BlockListSource.BUNDLED,
            activePreset = BlockingPreset.HELIOS_MOBILE,
            cacheSizeBytes = 99L
        )
        assertEquals(original, original.copy(isRefreshing = true).copy(isRefreshing = false))
    }

    /**
     * `isRebuilding` is a computed property, so it is absent from `declaredFields` only if it is a
     * getter — which it is. This asserts the comparison is by value and not by identity, because
     * enums compare by reference and a naive implementation would be right by accident.
     */
    @Test
    fun `preset comparison is by value`() {
        val a = BlockingEngineState(preset = BlockingPreset.HELIOS_MOBILE, activePreset = BlockingPreset.HELIOS_MOBILE)
        val b = BlockingEngineState(preset = BlockingPreset.HELIOS_MOBILE, activePreset = BlockingPreset.HELIOS_MOBILE)
        assertFalse(a.isRebuilding)
        assertEquals(a, b)
    }
}