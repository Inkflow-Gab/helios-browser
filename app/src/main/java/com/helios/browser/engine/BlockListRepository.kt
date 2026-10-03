package com.helios.browser.engine

import android.app.ActivityManager
import android.content.Context
import android.util.Log
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
 *    reason cold start does not stall.
 * 2. **Bundled.** The APK ships EasyList and EasyPrivacy gzipped in `assets/blocklists`. Parsing
 *    them takes a few seconds, but they are always present, so blocking is functional offline and
 *    on first run with no network at all.
 * 3. **Remote.** A refresh afterwards pulls the publishers' current versions. Failure here changes
 *    nothing about the first two steps, which is the point: the network is an optimisation, never a
 *    dependency.
 *
 * ## Why the cache is safe to trust
 * The serialised engine embeds a checksum, and adblock-rust refuses a file written by a different
 * crate version. A failed restore therefore costs one wasted parse, never a wrong engine.
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
            cacheSizeBytes = cacheFile.takeIf { it.exists() }?.length() ?: 0L
        )
    }

    /**
     * Loads an engine, reusing the cache when it is still valid.
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
     * Pulls the publishers' current lists and rebuilds the engine, then rewrites the cache.
     *
     * Network and parse failures are swallowed on purpose. The engine already in use came from the
     * cache or the APK, and replacing it with nothing would make things strictly worse.
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
        val lists = REMOTE_SOURCES.mapNotNull { url -> runCatching { download(url) }.getOrNull() }
        val easyList = lists.firstOrNull { it.first == EASY_LIST_URL }?.second
        val easyPrivacy = lists.firstOrNull { it.first == EASY_PRIVACY_URL }?.second

        if (easyList.isNullOrEmpty() || easyPrivacy.isNullOrEmpty()) {
            Log.w(TAG, "Filter list refresh incomplete; keeping the current engine")
            return@withContext false
        }

        val rebuilt = NativeAdBlock.load(easyList, easyPrivacy)
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
     * The serialised engine for EasyList plus EasyPrivacy runs to tens of megabytes. Reading it
     * costs one `ByteArray` of that size, and handing it to `nativeDeserialize` costs a second copy
     * inside Rust — so restoring a 40MB cache peaks at roughly 80MB of heap before a single rule is
     * matched. On a low-RAM phone that is an `OutOfMemoryError` on the launch path, which is exactly
     * the "it will not open after a few launches" report: the first launch has no cache and works,
     * and every launch after the cache exists does not.
     *
     * So the cache is treated as an optimisation with a budget, not as the source of truth. Over
     * [MAX_CACHE_BYTES], or on a device the platform reports as low-RAM, it is skipped entirely and
     * the engine is rebuilt from the bundled lists. That costs a few seconds of parse and removes
     * the failure mode, which is the right trade for a browser.
     *
     * An oversized cache is deleted rather than left in place, because the gate is a length check
     * and a file that is never read would otherwise occupy storage forever.
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

    private fun loadBundled(): Boolean {
        val easyList = readBundled(EASY_LIST_ASSET)
        val easyPrivacy = readBundled(EASY_PRIVACY_ASSET)
        if (easyList.isEmpty() && easyPrivacy.isEmpty()) return false

        val loaded = NativeAdBlock.load(easyList, easyPrivacy)
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
     * Stored gzipped because the raw text is around 3.6MB and aapt will not compress an asset
     * further. Returning an empty string on failure is intentional: [loadBundled] can work from
     * either list alone.
     */
    private fun readBundled(assetPath: String): String = runCatching {
        context.assets.open(assetPath).use { raw ->
            GZIPInputStream(raw.buffered()).bufferedReader().use { it.readText() }
        }
    }.onFailure { Log.w(TAG, "Bundled list $assetPath unreadable", it) }.getOrDefault("")

    private fun download(url: String): Pair<String, String> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            // Some CDNs serve an empty or HTML body to unrecognised clients, and the engine would
            // happily compile that into an engine that blocks nothing.
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP ${connection.responseCode} from $url")
                return url to ""
            }
            return url to connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Writes the serialised engine next to the app's files, atomically.
     *
     * The temp-then-rename matters: a half-written cache file would be rejected on the next start,
     * and if the process died during the write the failure would look like a corrupt cache rather
     * than an interrupted one.
     */
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
        val temp = File(target.parentFile, "${target.name}.tmp")
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
        const val EASY_LIST_ASSET = "blocklists/easylist.txt.gz"
        const val EASY_PRIVACY_ASSET = "blocklists/easyprivacy.txt.gz"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000

        /** Some CDNs serve an empty or HTML body to unrecognised clients. */
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36 Helios/0.2"

        const val EASY_LIST_URL = "https://easylist.to/easylist/easylist.txt"
        const val EASY_PRIVACY_URL = "https://easylist.to/easylist/easyprivacy.txt"

        /**
         * The same two lists Brave and uBlock Origin ship by default, in Adblock Plus syntax —
         * which is the only format the engine reads. Filter lists published in hosts format would
         * need `ParseOptions.format = FilterFormat::Hosts`; none are configured here.
         */
        val REMOTE_SOURCES = listOf(EASY_LIST_URL, EASY_PRIVACY_URL)
    }
}
