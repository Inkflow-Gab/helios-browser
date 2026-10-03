package com.helios.browser.ui.adaptive

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity

/**
 * Unwraps the Activity behind a Compose [Context].
 *
 * `calculateWindowSizeClass` needs a real Activity because it measures the window's decor view, and
 * Compose only hands out a `ContextWrapper` chain from `LocalContext`. Returns null rather than
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
 * The fallback is a 360x780 phone rather than an exception: a missing Activity should degrade to a
 * sensible single-column phone layout, never crash the browser on launch.
 *
 * @param preferTopOmnibox the user's omnibox setting, which the rail layout is allowed to override.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberHeliosLayout(preferTopOmnibox: Boolean): HeliosLayout {
    val context = LocalContext.current
    val density = LocalDensity.current
    val activity = remember(context) { context.findActivity() }

    return if (activity == null) {
        HeliosLayout.from(PHONE_FALLBACK_WIDTH_DP, PHONE_FALLBACK_HEIGHT_DP, preferTopOmnibox)
    } else {
        val windowSizeClass = calculateWindowSizeClass(activity)
        // Recomputing from px rather than reusing `minWidthDp` keeps the classification honest when
        // the window is mid-resize, and lets the density change (external display) matter.
        remember(windowSizeClass, density.density, preferTopOmnibox) {
            HeliosLayout.from(
                widthDp = with(density) { windowSizeClass.widthDp.minWidthDp.toDp().toInt() },
                heightDp = with(density) { windowSizeClass.heightDp.minHeightDp.toDp().toInt() },
                preferTopOmnibox = preferTopOmnibox
            )
        }
    }
}

private const val PHONE_FALLBACK_WIDTH_DP = 360
private const val PHONE_FALLBACK_HEIGHT_DP = 780