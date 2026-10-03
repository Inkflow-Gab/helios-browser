package com.helios.browser.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import com.helios.browser.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures uncaught exceptions to a file and shows them in-app.
 *
 * ## Why this exists
 * Helios is built and installed from CI, on a phone with no debugger attached. When it crashed, the
 * only evidence was the system dialog, which says what happened and nothing about why. That is not
 * enough to work from: the crash could have been anywhere in a codebase that had never been
 * compiled before.
 *
 * So the handler writes the full stack trace to a file and the app surfaces it on the next launch.
 * This is the difference between "it crashes" and a `NullPointerException` with a line number.
 *
 * ## What it deliberately does not do
 * - **No network.** Traces stay on the device. Nothing here uploads anything.
 * - **Not every throwable.** Catching `Throwable` would swallow `OutOfMemoryError` and
 *   `StackOverflowError`, which the runtime needs to see in order to recover. Only `Exception` is
 *   recorded, and the handler always delegates to the previous one so the normal crash dialog and
 *   logcat still happen.
 * - **Bounded.** Keeps the newest [MAX_TRACES] files and deletes older ones, so a crash loop cannot
 *   fill the user's storage.
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val DIRECTORY = "crashes"
    private const val PREFIX = "crash-"
    private const val MAX_TRACES = 10

    /** Guards against the handler re-entering itself while recording. */
    private val recording = ThreadLocal.withInitial { false }

    @Volatile
    private var installed: Thread.UncaughtExceptionHandler? = null

    /**
     * Installs the handler. Safe to call more than once; only the first call takes effect.
     *
     * @param context used only to resolve the files directory. Held as a [Context], not an Activity.
     */
    fun install(context: Context) {
        if (installed != null) return
        val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // A throwable that escapes while we are recording must not recurse.
            if (!recording.get()) {
                recording.set(true)
                runCatching { record(directory, thread, error) }
                    .onFailure { Log.e(TAG, "Could not record the crash", it) }
            }
            // Always hand back to the platform so the usual dialog and logcat entry still happen.
            previous?.uncaughtException(thread, error)
        }
        installed = previous
        prune(directory)
    }

    /**
     * Every recorded crash, newest first.
     *
     * Returns file names rather than file contents so the caller can decide whether it wants to
     * read a potentially large trace off the main thread.
     */
    fun recordedCrashes(context: Context): List<String> =
        File(context.filesDir, DIRECTORY).listFiles { file ->
            file.isFile && file.name.startsWith(PREFIX)
        }?.sortedByDescending { it.lastModified() }?.map { it.name }.orEmpty()

    /** The full text of one recorded crash, or null if it is missing. */
    fun readCrash(context: Context, name: String): String? {
        // Reject anything that is not a plain file name in the crash directory: this value reaches
        // here from a list the app generated, but a traversal here would be a file-read primitive.
        if (name.contains('/') || name.contains('\\') || name.contains("..")) return null
        val file = File(File(context.filesDir, DIRECTORY), name)
        return if (file.isFile) file.readText() else null
    }

    /** Deletes every recorded crash. Returns how many files went away. */
    fun clear(context: Context): Int {
        val files = File(context.filesDir, DIRECTORY).listFiles() ?: return 0
        var removed = 0
        files.forEach { if (it.delete()) removed++ }
        return removed
    }

    /**
     * Writes the trace.
     *
     * The header deliberately carries device and app identity, because "it crashes on my phone" is
     * only diagnosable if we know which phone and which build.
     */
    private fun record(directory: File, thread: Thread, error: Throwable) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(directory, "$PREFIX$stamp.txt")

        val body = buildString {
            appendLine("When:  $stamp")
            appendLine("Build: ${BuildConfig.BUILD_TYPE} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
            appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("Abi: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append("Stack trace:")
            appendLine()
            append(StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString())

            // `causedBy` is already inside printStackTrace, but the suppressed list is not, and a
            // suppressed exception is often the interesting one.
            val suppressed = error.suppressed
            if (suppressed.isNotEmpty()) {
                appendLine()
                appendLine("Suppressed:")
                suppressed.forEach { appendLine(it.stackTraceToString()) }
            }
        }

        file.writeText(body)
        Log.e(TAG, "Recorded crash to ${file.name}", error)
    }

    /** Keeps only the newest [MAX_TRACES] files. */
    private fun prune(directory: File) {
        val files = directory.listFiles { file -> file.name.startsWith(PREFIX) }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(MAX_TRACES).forEach { it.delete() }
    }
}