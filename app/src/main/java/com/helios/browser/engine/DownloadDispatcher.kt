package com.helios.browser.engine

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of trying to queue a download. */
sealed interface DownloadResult {
    data class Queued(val id: Long, val fileName: String, val isPublicDestination: Boolean) : DownloadResult
    data class NeedsPermission(val permissions: List<String>) : DownloadResult
    data object Failed : DownloadResult
}

/**
 * Queues downloads with the platform [DownloadManager].
 *
 * Destination rules, which differ by API level:
 *  - API 29+ (Q): the public Downloads collection. Scoped storage means no permission is needed.
 *  - API 26-28: the public Downloads folder needs WRITE_EXTERNAL_STORAGE, so the permission is
 *    requested at runtime first; without it the file lands in the app-scoped external files
 *    directory, which always works but is only visible to Helios.
 */
@Singleton
class DownloadDispatcher @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun hasStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun dispatch(request: DownloadRequest): DownloadResult {
        val manager = context.getSystemService(DownloadManager::class.java) ?: return DownloadResult.Failed
        val fileName = AdBlockEngine.sanitizeFileName(request.contentDisposition, request.url)
        val canUsePublicFolder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        if (!canUsePublicFolder && !hasStoragePermission()) {
            return DownloadResult.NeedsPermission(listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
        }

        val parsed = runCatching { Uri.parse(request.url) }.getOrNull()
            ?: return DownloadResult.Failed

        val enqueue = DownloadManager.Request(parsed).apply {
            request.userAgent?.takeIf { it.isNotBlank() }?.let { addRequestHeader("User-Agent", it) }
            request.mimeType?.takeIf { it.isNotBlank() }?.let { setMimeType(it) }
            setTitle(fileName)
            setDescription(request.url)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }

        val usedPublicDestination = if (canUsePublicFolder) {
            runCatching {
                enqueue.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                true
            }.getOrDefault(false)
        } else {
            false
        }

        if (!usedPublicDestination) {
            enqueue.setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                fileName
            )
        }

        val id = runCatching { manager.enqueue(enqueue) }.getOrNull() ?: return DownloadResult.Failed
        return DownloadResult.Queued(id, fileName, usedPublicDestination)
    }

    /** Best-effort notification permission request for API 33+, used when downloads are started. */
    fun notificationPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }
}