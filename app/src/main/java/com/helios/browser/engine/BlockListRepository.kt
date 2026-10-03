package com.helios.browser.engine

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.helios.browser.domain.model.BlockingPreset
import com.helios.browser.domain.model.FilterList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** How the filter lists currently in the engine got there. */
enum class BlockListSource {
    /** Nothing loaded yet. */
    NONE,

    /** Restored from the on-disk cache written by a previous run. */
    CACHE,

    /** Parsed from the copies bundled in the APK. */
    BUNDLED,

    /** Downloaded from the filter list publishers. */
    REMOTE
}

/**
 * Owns the adblock engine's lifecycle: which filter lists are loaded, and where they came from.
 *
 * ## Startup order, and why
 * 1. **Cache.** [NativeAdBlock.deserialize] on the file a previous run wrote. Restoring a compiled
 *    engine is a memory copy; rebuilding one is seconds of parsing. This is the single biggest
 *    reason cold start does not stall. Bounded by [MAX_CACHE_BYTES] — see [restoreFromCache].
 * 2. **Bundled.** The APK ships the lists for [preset] gzipped in `assets/blocklists`. Parsing
 *    them takes a few seconds, but they are always present, so blocking is functional offline and on
 *    first run with no network at all.
 * 3. **Remote.** A refresh afterwards pulls the publishers' current versions. Failure here changes
 *    nothing about the first two steps, which is the point: the network is an optimisation, never a
 *    dependency.
 *
 * ## The cache is per-preset
 * A serialised engine is the compiled form of one specific set of rules. Restoring a cache built
 * from EasyList after the user has switched to uBlock would silently keep applying the old rules, so
 * [setPreset] deletes the cache whenever the preset actually changes.
 *
 * ## Threading
 * [initialize] is idempotent and guarded by a coroutine [Mutex], so the application class and the
 * first WebView can both call it without racing. Everything expensive runs on [Dispatchers.IO].
 */
@Singleton
class BlockListRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : BlockingEngineStatus {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startLock = Mutex()

    private val _status = MutableStateFlow(BlockingEngineSnapshot())

    override val status: StateFlow<BlockingEngineSnapshot> = _status.asStateFlow()

    /**
     * The preset the engine is currently built from.
     *
     * Kept here rather than read from [com.helios.browser.data.repository.SettingsRepository] on
     * demand because the repository must be able to act on a preset change the moment it is told,
     * not the next time a caller happens to ask.
     */
    @Volatile
    private var preset: BlockingPreset = BlockingPreset.DEFAULT

    private var source: BlockListSource = BlockListSource.NONE
        private set

    private var lastRefreshAt: Long = 0L
        private set

    /**
     * Republishes [BlockingEngineSnapshot] after any change to the engine.
     *
     * Reads the cache size here rather than in the flow's consumers, so `File.exists()` runs on
     * whichever thread made the change — in practice always IO, because every mutation happens
     * inside a `withContext(Dispatchers.IO)` in this class.
     */
    private fun publish(isRefreshing: Boolean = _status.value.isRefreshing) {
        _status.value = BlockingEngineSnapshot(
            isAvailable = NativeAdBlock.isAvailable,
            isReady = NativeAdBlock.isReady,
            isRefreshing = isRefreshing,
            source = source,
            preset = preset,
            cacheSizeBytes = cacheFile.takeIf { it.exists() }?.length() ?: 0L
        )
    }

    /**
     * Switches the filter preset and rebuilds the engine from the new lists.
     *
     * Rebuilds rather than waiting for the next launch, because a settings toggle that does not take
     * effect until you restart the browser reads as broken. The rebuild is the same few seconds of
     * parse the first launch pays, and it happens on IO.
     *
     * The cache is deleted first, because it is the compiled form of the *old* rules and would
     * otherwise be restored over the top.
     */
    suspend fun setPreset(next: BlockingPreset) = withContext(Dispatchers.IO) {
        if (next == preset) return@withContext
        startLock.withLock {
            Log.i(TAG, "Switching blocking preset ${preset.name} -> ${next.name}")
            preset = next
            runCatching { cacheFile.delete() }
            runCatching { cacheFile.temp().delete() }

            // Release the old engine before parsing the new one, so peak memory is the larger of the
            // two rather than their sum. A failed rebuild then leaves no engine, which the Kotlin
            // fallback covers, rather than an engine built from rules the user has moved away from.
            NativeAdBlock.release()
            source = BlockListSource.NONE
            publish()

            val loaded = loadBundled()
            if (!loaded) {
                Log.e(TAG, "Could not build an engine for ${next.name}")
            } else {
                writeCache()
            }
            publish()
            loaded
        }
    }

    /**
     * Loads an engine for the current preset, reusing the cache when it is still valid.
     *
     * Safe to call from anywhere and as often as you like; only the first caller does work. Returns
     * true if an engine is loaded when it finishes.
     *
     * @param onReady invoked on the IO dispatcher once loading settles. Not dispatched to the main
     *   thread for you, because callers use it for logging and state that is already off-main.
     */
    suspend fun initialize(onReady: (BlockListSource) -> Unit = {}): Boolean = startLock.withLock {
        if (NativeAdBlock.isReady) {
            publish()
            onReady(source)
            return@withLock true
        }

        withContext(Dispatchers.IO) {
            val restored = restoreFromCache()
            val loaded = restored || loadBundled()
            if (!loaded) {
                Log.e(TAG, "No filter lists could be loaded; ad blocking is inert")
            }
            publish()
            onReady(source)
            loaded
        }
    }

    /**
     * Pulls the publishers' current lists for every list in the preset and rebuilds the engine.
     *
     * Partial results are refused: a preset means a specific set of lists, and quietly building an
     * engine from two of its six is worse than keeping the one already in use. Network and parse
     * failures are swallowed on purpose — the engine already in use came from the cache or the APK.
     *
     * @return true if the engine was rebuilt from fresh lists.
     */
    override suspend fun refresh(): Boolean = startLock.withLock {
        publish(isRefreshing = true)
        val rebuilt = refreshLocked()
        publish(isRefreshing = false)
        rebuilt
    }

    /**
     * The body of [refresh], called with [startLock] held.
     *
     * The lock matters here for the same reason it matters in [initialize]: two concurrent engine
     * builds would each parse several megabytes and then race to install, so the second one's work
     * is thrown away. A user tapping "Update" during the first cold start would otherwise trigger
     * exactly that.
     */
    private suspend fun refreshLocked(): Boolean = withContext(Dispatchers.IO) {
        val wanted = preset.lists
        val downloaded = wanted.mapNotNull { list ->
            runCatching { list to download(list) }.getOrNull()
        }.filter { it.second.isNotEmpty() }.toMap()

        val missing = wanted.filterNot { downloaded.containsKey(it) }
        if (missing.isNotEmpty()) {
            Log.w(TAG, "Could not fetch ${missing.map { it.displayName }}; keeping the current engine")
            return@withContext false
        }

        // Merged in one call so the engine is built from all of the preset's lists at once rather
        // than replacing itself per list.
        val rebuilt = NativeAdBlock.load(
            downloaded.values.joinToString("\n") { it }
        )
        if (!rebuilt) {
            Log.w(TAG, "Engine rebuild from downloaded lists failed; keeping the current engine")
            return@withContext false
        }

        source = BlockListSource.REMOTE
        lastRefreshAt = System.currentTimeMillis()
        // Cache only what actually loaded. Writing a cache the restore path then rejects would
        // waste the next cold start.
        writeCache()
        true
    }

    /** Starts a refresh without waiting for it. Safe to call repeatedly. */
    fun refreshInBackground() {
        scope.launch { refresh() }
    }

    /**
     * Restores a cached engine, if there is one this device can afford to load.
     *
     * ## Why there is a size gate here
     * A serialised engine runs to tens of megabytes. Reading it costs one `ByteArray` of that size,
     * and handing it to `nativeDeserialize` costs a second copy inside Rust — so restoring a 40MB
     * cache peaks at roughly 80MB of heap before a single rule is matched. On a low-RAM phone that
     * is an `OutOfMemoryError` on the launch path, which is exactly the "it will not open after a
     * few launches" report: the first launch has no cache and works, every launch after does not.
     *
     * So the cache is an optimisation with a budget, not the source of truth. Over
     * [MAX_CACHE_BYTES], or on a device the platform reports as low-RAM, it is skipped and the engine
     * is rebuilt from the bundled lists. That costs a few seconds of parse and removes the failure
     * mode, which is the right trade for a browser.
     *
     * An oversized cache is deleted rather than left in place: the gate is a length check, so a file
     * that is never read would otherwise occupy storage forever.
     */
    private fun restoreFromCache(): Boolean {
        val file = cacheFile
        if (!file.exists() || file.length() == 0L) return false

        val size = file.length()
        if (size > MAX_CACHE_BYTES) {
            Log.w(TAG, "Engine cache is ${size / 1024 / 1024}MB, over the budget; deleting it")
            runCatching { file.delete() }
            return false
        }
        if (isLowRamDevice) {
            Log.i(TAG, "Skipping the ${size / 1024}KB engine cache on a low-RAM device")
            return false
        }

        return runCatching { NativeAdBlock.deserialize(file.readBytes()) }
            .onFailure { Log.w(TAG, "Engine cache rejected; rebuilding from bundled lists", it) }
            .getOrDefault(false)
            .also { if (it) source = BlockListSource.CACHE }
    }

    /** Parses every list in the current preset out of the APK. */
    private fun loadBundled(): Boolean {
        val texts = preset.lists.map { readBundled(it.assetPath) }
        if (texts.none { it.isNotEmpty() }) return false

        val loaded = NativeAdBlock.load(texts.joinToString("\n") { it })
        if (loaded) {
            source = BlockListSource.BUNDLED
            // Seeding the cache here is what makes the *next* start fast. The lists in the APK
            // change only when the app updates, so this stays valid until then.
            writeCache()
        }
        return loaded
    }

    /**
     * Decompresses one bundled list.
     *
     * Stored gzipped because the raw text runs to megabytes and aapt will not compress an asset
     * further. Returning an empty string on failure is intentional: the engine can be built from
     * whichever lists did load, though [refreshLocked] is stricter and refuses a partial fetch.
     */
    private fun readBundled(assetPath: String): String = runCatching {
        context.assets.open(assetPath).use { raw ->
            GZIPInputStream(raw.buffered()).bufferedReader().use { it.readText() }
        }
    }.onFailure { Log.w(TAG, "Bundled list $assetPath unreadable", it) }.getOrDefault("")

    private fun download(list: FilterList): String {
        val connection = (URL(list.remoteUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            // Some CDNs serve an empty or HTML body to unrecognised clients, and the engine would
            // happily compile that into an engine that blocks nothing.
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP ${connection.responseCode} from ${list.remoteUrl}")
                return ""
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Writes the serialised engine, if it is small enough to be worth restoring later.
     *
     * Serialising already costs a copy in Rust and a copy in the Java array, so the size is checked
     * before it is written rather than after: an oversized cache is not merely useless, it is the
     * thing that makes the next launch fail. See [restoreFromCache].
     */
    private fun writeCache() {
        if (isLowRamDevice) return
        val bytes = NativeAdBlock.serialize() ?: return
        if (bytes.size > MAX_CACHE_BYTES) {
            Log.w(TAG, "Engine is ${bytes.size / 1024 / 1024}MB; not caching it")
            return
        }
        val target = cacheFile
        val temp = target.temp()
        runCatching {
            target.parentFile?.mkdirs()
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IOException("Could not replace $target")
            }
        }.onFailure { Log.w(TAG, "Could not write engine cache", it) }
    }

    /**
     * True when the platform considers this device memory-constrained.
     *
     * `isLowRamDevice` is the honest signal the platform gives us; the memory class is a second
     * opinion. Either one disqualifies the device from a cache that costs tens of megabytes.
     *
     * Read once and cached, because it cannot change during the process's life and
     * `ActivityManager` is not free to look up repeatedly.
     */
    private val isLowRamDevice: Boolean by lazy {
        runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            manager.isLowRamDevice || manager.memoryClass < MIN_MEMORY_CLASS_MB
        }.getOrDefault(false)
    }

    private val cacheFile: File
        get() = File(File(context.filesDir, CACHE_DIR), CACHE_FILE)

    /** The in-progress file [writeCache] writes to before renaming over the real one. */
    private fun File.temp(): File = File(parentFile, "$name.tmp")

    private companion object {
        const val TAG = "BlockListRepository"
        const val CACHE_DIR = "blocklist"
        const val CACHE_FILE = "engine.bin"

        /**
         * Largest serialised engine worth caching, in bytes.
         *
         * Restoring costs two copies — the Java `ByteArray` and the Rust `Vec` behind
         * `convert_byte_array` — so this is really a heap budget rather than a disk budget. 32MB of
         * cache is roughly 64MB of transient heap, which is a lot on a 128MB-class device but
         * unremarkable on a modern one, and the gate exists to stop it being a launch-path OOM on
         * the phone this was first reported from.
         */
        const val MAX_CACHE_BYTES = 32L * 1024 * 1024

        /**
         * Below this `memoryClass`, the platform is telling us there is not much to play with and
         * the engine cache is skipped entirely. 192MB is roughly where a WebView plus a browser
         * process starts fighting for room.
         */
        const val MIN_MEMORY_CLASS_MB = 192

        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 Helios/0.2"
    }
}