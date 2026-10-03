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
     * Guard against the two types drifting apart: adding a field to one and not the other must fail
     * here, not silently in production.
     *
     * Synthetic fields are excluded because `@Immutable` makes the Compose compiler add a `$stable`
     * field to the UI type that the engine type has no reason to have. Comparing raw reflection
     * output would report a difference that does not exist.
     */
    @Test
    fun `the two types have the same fields`() {
        fun declared(type: Class<*>) = type.declaredFields
            .filterNot { it.isSynthetic }
            .map { it.name }
            .toSet()

        assertEquals(
            declared(BlockingEngineSnapshot::class.java),
            declared(BlockingEngineState::class.java)
        )
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