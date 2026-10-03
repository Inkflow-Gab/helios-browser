package com.helios.browser.engine

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Hands non-web URLs to whichever installed app claims them.
 *
 * Uses the `<queries>` block in the manifest so `resolveActivity` can still see candidates on
 * API 30+. Always returns a boolean so the caller can surface a "no app can open this" message
 * instead of failing silently.
 */
object ExternalUrlLauncher {

    fun launch(context: Context, url: String): Boolean {
        val uri = try {
            Uri.parse(url)
        } catch (_: Exception) {
            return false
        }
        if (uri.scheme.isNullOrBlank()) return false

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (context !is android.app.Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            return try {
                context.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
        return false
    }

    /** Builds the share sheet for a page. Returns null when nothing can handle text/plain. */
    fun buildShareIntent(context: Context, url: String, title: String?): Intent? {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title?.takeIf { it.isNotBlank() } ?: url)
            putExtra(Intent.EXTRA_TEXT, buildString {
                if (!title.isNullOrBlank() && title != url) {
                    append(title).append('\n')
                }
                append(url)
            })
        }
        val chooser = Intent.createChooser(send, "Share page").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (chooser.resolveActivity(context.packageManager) != null) chooser else null
    }
}