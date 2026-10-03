package com.helios.browser.engine

/**
 * The parts of [BlockListRepository] that user-facing code is allowed to depend on.
 *
 * ## Why the seam exists
 * [BlockListRepository] owns an `ApplicationContext` — it reads bundled assets and writes a cache
 * file — and `BrowserViewModel` must not hold Android objects. An interface made only of enums,
 * booleans and numbers lets the ViewModel report engine status without touching `Context`, and lets
 * a unit test stand in a fake.
 *
 * Kept deliberately narrow. Anything that needs platform types belongs behind the concrete class.
 */
interface BlockingEngineStatus {

    /**
     * True when `libhelios_adblock.so` is present for this device's ABI.
     *
     * False means the browser was built without the Rust toolchain. Blocking still works, but only
     * via the compiled-in host list, which is a small fraction of EasyList.
     */
    val isEngineAvailable: Boolean

    /** True when a filter list has been compiled into the engine and matching can be answered. */
    val isEngineReady: Boolean

    /** Where the loaded lists came from. Useful for telling the user how current they are. */
    val source: BlockListSource

    /** Size of the on-disk engine cache, or 0 when there is none. */
    val cacheSizeBytes: Long

    /**
     * Re-downloads the filter lists and rebuilds the engine.
     *
     * Suspends for several seconds and never throws: on failure the engine already in use stays in
     * use. Returns true if the engine was actually rebuilt.
     */
    suspend fun refresh(): Boolean
}
