package com.helios.browser.engine

import com.helios.browser.domain.model.BlockingPreset
import kotlinx.coroutines.flow.StateFlow

/**
 * The parts of [BlockListRepository] that user-facing code is allowed to depend on.
 *
 * ## Why the seam exists
 * [BlockListRepository] owns an `ApplicationContext` — it reads bundled assets and writes a cache
 * file — and `BrowserViewModel` must not hold Android objects. An interface made only of enums,
 * booleans and numbers lets the ViewModel report engine status without touching `Context`, and lets
 * a unit test stand in a fake.
 *
 * ## Why a Flow and not four getters
 * Engine status changes at unpredictable times: the bundled lists finish parsing a few seconds after
 * launch, and a filter-list refresh swaps the engine whenever the network allows. The obvious way to
 * reflect that in the UI is to poll, but polling means re-reading [cacheSizeBytes] — which stats a
 * file — on whatever thread the collector runs on, and emitting a new state on a timer whether or
 * anything changed. That is main-thread file I/O and a full recomposition of the browser screen once
 * a second, forever.
 *
 * A `StateFlow` makes the repository push on real transitions only, so the file is touched when the
 * answer actually changes.
 *
 * Kept deliberately narrow. Anything that needs platform types belongs behind the concrete class.
 */
interface BlockingEngineStatus {

    /**
     * Engine state, updated on real transitions only.
     *
     * The value is a plain immutable snapshot, so it is safe to hold in `BrowserState`.
     */
    val status: StateFlow<BlockingEngineSnapshot>

    /**
     * Re-downloads the filter lists for the current preset and rebuilds the engine.
     *
     * Emits into [status] while it runs, so the UI can show progress. Suspends for several seconds
     * and never throws: on failure the engine already in use stays in use. Returns true if the
     * engine was actually rebuilt.
     */
    suspend fun refresh(): Boolean

    /**
     * Switches the filter preset and rebuilds the engine from the new lists.
     *
     * Takes effect immediately rather than at the next launch, because a settings toggle that
     * requires a restart reads as broken. Clears the on-disk cache first, since a serialised engine
     * is the compiled form of the old rules.
     *
     * Suspends for several seconds. Returns true if the new preset's engine loaded.
     */
    suspend fun setPreset(preset: BlockingPreset): Boolean
}

/**
 * An immutable read of the adblock engine's state.
 *
 * Lives in `engine` rather than `ui.browser` because the repository produces it and the ViewModel
 * only re-exposes it. Keeping it out of Compose entirely is what lets it be a [StateFlow] value with
 * no recomposition risk of its own.
 */
data class BlockingEngineSnapshot(
    /**
     * True when `libhelios_adblock.so` is present for this device's ABI.
     *
     * False means the browser was built without the Rust toolchain. Blocking still works, but only
     * via the compiled-in host list, which is a small fraction of EasyList.
     */
    val isAvailable: Boolean = false,

    /** True when a filter list has been compiled into the engine and matching can be answered. */
    val isReady: Boolean = false,

    /** True while [refresh] is running. */
    val isRefreshing: Boolean = false,

    /** Where the loaded lists came from. Useful for telling the user how current they are. */
    val source: BlockListSource = BlockListSource.NONE,

    /**
     * Which filter lists the loaded engine was built from.
     *
     * Reported separately from the settings value because the two can legitimately differ: the
     * setting is what the user chose, this is what the running engine actually contains. They
     * diverge while a preset switch is still rebuilding, and during that window the shields sheet
     * should say so rather than claiming the new lists are active.
     */
    val preset: BlockingPreset = BlockingPreset.DEFAULT,

    /** Size of the on-disk engine cache, or 0 when there is none. */
    val cacheSizeBytes: Long = 0L
)