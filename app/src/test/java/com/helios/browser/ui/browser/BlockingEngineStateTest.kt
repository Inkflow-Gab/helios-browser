package com.helios.browser.ui.browser

import com.helios.browser.engine.BlockListSource
import com.helios.browser.engine.BlockingEngineSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the one conversion between the engine's snapshot and the UI's value type.
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
            cacheSizeBytes = 4_194_304L
        )

        assertEquals(
            BlockingEngineState(
                isAvailable = true,
                isReady = true,
                isRefreshing = true,
                source = BlockListSource.REMOTE,
                cacheSizeBytes = 4_194_304L
            ),
            snapshot.toUiState()
        )
    }

    @Test
    fun `defaults survive the round trip`() {
        val snapshot = BlockingEngineSnapshot()
        assertEquals(BlockingEngineState(), snapshot.toUiState())
    }

    /**
     * Guard against the two drifting apart: adding a field to one and not the other must fail here,
     * not silently in production.
     */
    @Test
    fun `the two types have the same fields`() {
        val uiFields = BlockingEngineState::class.java.declaredFields.map { it.name }.toSet()
        val snapshotFields = BlockingEngineSnapshot::class.java.declaredFields.map { it.name }.toSet()
        assertEquals(uiFields, snapshotFields)
    }

    /**
     * The fields are read positionally nowhere, but `copy` with defaults is how a new field gets
     * silently dropped, so pin that a partially-specified copy keeps the rest.
     */
    @Test
    fun `copy keeps unspecified fields`() {
        val original = BlockingEngineState(
            isAvailable = true,
            isReady = true,
            source = BlockListSource.BUNDLED,
            cacheSizeBytes = 99L
        )
        assertEquals(original, original.copy(isRefreshing = true).copy(isRefreshing = false))
    }
}