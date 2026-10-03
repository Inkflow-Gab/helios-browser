package com.helios.browser.ui.adaptive

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/**
 * Unwraps the Activity behind a Compose [Context].
 *
 * Used by the theme to reach the window for the system bar appearance. Returns null rather than
 * throwing when there is no Activity — previews and instrumented hosts both hit that.
 */
fun Context.findActivity(): Activity? {
    var candidate: Context? = this
    while (candidate is ContextWrapper) {
        if (candidate is Activity) return candidate
        candidate = candidate.baseContext
    }
    return null
}

/**
 * Resolves the current window shape into a [HeliosLayout].
 *
 * ## Why `LocalConfiguration` and not `material3-window-size-class`
 * `WindowSizeClass` is the more precise API — it measures the window's decor view — but it is
 * `@ExperimentalMaterial3WindowSizeClassApi` and requires an `Activity`, which a composable does not
 * reliably have. Since Android 7.0 `Configuration.screenWidthDp` and `screenHeightDp` describe the
 * bounds of the window the activity occupies rather than the physical display, so in split-screen
 * and freeform it already reports the window, not the screen. That makes it the same number for
 * this purpose, with no experimental opt-in and no Activity unwrapping.
 *
 * Keyed on the `Configuration`, so a rotation, a fold, a resize or a multi-window change recomposes
 * and reclassifies.
 *
 * The fallback when there is no Activity is a 360x780 phone rather than an exception: a missing
 * Activity should degrade to a sensible single-column layout, never crash the browser on launch.
 *
 * @param preferTopOmnibox the user's omnibox setting, which the rail layout is allowed to override.
 */
@Composable
fun rememberHeliosLayout(preferTopOmnibox: Boolean): HeliosLayout {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current

    return remember(configuration, preferTopOmnibox, context) {
        val widthDp = configuration.screenWidthDp.takeIf { it > 0 } ?: PHONE_FALLBACK_WIDTH_DP
        val heightDp = configuration.screenHeightDp.takeIf { it > 0 } ?: PHONE_FALLBACK_HEIGHT_DP
        HeliosLayout.from(widthDp, heightDp, preferTopOmnibox)
    }
}

private const val PHONE_FALLBACK_WIDTH_DP = 360
private const val PHONE_FALLBACK_HEIGHT_DP = 780