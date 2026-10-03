package com.helios.browser.ui.adaptive

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Width buckets, using the same dp breakpoints as Material 3's window size classes. */
enum class HeliosWidthClass { COMPACT, MEDIUM, EXPANDED }

/** Height buckets, same breakpoints as Material 3's window size classes. */
enum class HeliosHeightClass { COMPACT, MEDIUM, EXPANDED }

/**
 * Everything the browser UI needs to know about the shape of the window it was given.
 *
 * This exists because "where do the buttons go" was previously answered by hardcoded constants in
 * whichever composable happened to need them, so a tablet rendered a two-column tab grid stretched
 * across 1200dp and put the omnibox under the user's left hand. Every breakpoint decision now
 * lives in [from] and nowhere else.
 *
 * @param widthClass width bucket.
 * @param heightClass height bucket.
 * @param isLandscape true when width exceeds height.
 * @param useNavigationRail true on wide screens, where a permanent left-hand rail beats a
 *   floating bar and frees the full width for the page.
 * @param omniboxAtTop resolved omnibox edge. This already accounts for the user's preference:
 *   [com.helios.browser.domain.model.OmniboxPosition] wins everywhere except on a rail layout,
 *   where the bar belongs at the top by construction.
 * @param omniboxAtTopForced true when the rail layout overrode the user's preference.
 * @param tabGridColumns columns in the tab switcher card grid.
 * @param startPageColumns columns for the start page's shortcut grid.
 * @param maxContentWidthDp cap on reading width; 0 means unconstrained.
 * @param cardMinHeightDp minimum card height, raised on touch-only layouts.
 * @param compactTouchTargets true when every hit target must be at least 48dp, because there is no
 *   pointer to fall back on.
 */
@Immutable
data class HeliosLayout(
    val widthClass: HeliosWidthClass,
    val heightClass: HeliosHeightClass,
    val isLandscape: Boolean,
    val useNavigationRail: Boolean,
    val omniboxAtTop: Boolean,
    val omniboxAtTopForced: Boolean,
    val tabGridColumns: Int,
    val startPageColumns: Int,
    val maxContentWidthDp: Int,
    val cardMinHeightDp: Int,
    val compactTouchTargets: Boolean
) {
    /** Reading-width cap, or [Dp.Unspecified] when the layout is unconstrained. */
    val maxContentWidth: Dp
        get() = if (maxContentWidthDp <= 0) Dp.Unspecified else maxContentWidthDp.dp

    companion object {
        private const val WIDTH_MEDIUM = 600
        private const val WIDTH_EXPANDED = 840
        private const val HEIGHT_MEDIUM = 480
        private const val HEIGHT_EXPANDED = 900

        /**
         * The one place breakpoints are decided.
         *
         * Deliberately takes plain ints and touches no Android or Compose class beyond [Dp], so the
         * whole classifier is exercised by `HeliosLayoutTest` on a plain JVM. See `core/url` and
         * `AdBlockEngine` for the same rule elsewhere in the project.
         *
         * @param widthDp window width in dp.
         * @param heightDp window height in dp.
         * @param preferTopOmnibox the user's omnibox setting.
         */
        fun from(
            widthDp: Int,
            heightDp: Int,
            preferTopOmnibox: Boolean
        ): HeliosLayout {
            val widthClass = when {
                widthDp < WIDTH_MEDIUM -> HeliosWidthClass.COMPACT
                widthDp < WIDTH_EXPANDED -> HeliosWidthClass.MEDIUM
                else -> HeliosWidthClass.EXPANDED
            }
            val heightClass = when {
                heightDp < HEIGHT_MEDIUM -> HeliosHeightClass.COMPACT
                heightDp < HEIGHT_EXPANDED -> HeliosHeightClass.MEDIUM
                else -> HeliosHeightClass.EXPANDED
            }
            val isLandscape = widthDp > heightDp

            // A rail only earns its keep once there is genuine side room. On a landscape phone the
            // width qualifies as MEDIUM, and a rail there would steal height from the page.
            val useRail = widthClass == HeliosWidthClass.EXPANDED

            return HeliosLayout(
                widthClass = widthClass,
                heightClass = heightClass,
                isLandscape = isLandscape,
                useNavigationRail = useRail,
                omniboxAtTop = useRail || preferTopOmnibox,
                omniboxAtTopForced = useRail && !preferTopOmnibox,
                tabGridColumns = when (widthClass) {
                    HeliosWidthClass.COMPACT -> 2
                    HeliosWidthClass.MEDIUM -> 3
                    HeliosWidthClass.EXPANDED -> 4
                },
                startPageColumns = when (widthClass) {
                    HeliosWidthClass.COMPACT -> 1
                    HeliosWidthClass.MEDIUM -> 2
                    HeliosWidthClass.EXPANDED -> 2
                },
                // Line length stops being readable well before the window stops growing, so on
                // anything medium or wider the page is centred in a capped column instead.
                maxContentWidthDp = when (widthClass) {
                    HeliosWidthClass.COMPACT -> 0
                    HeliosWidthClass.MEDIUM -> 720
                    HeliosWidthClass.EXPANDED -> 920
                },
                cardMinHeightDp = if (heightClass == HeliosHeightClass.EXPANDED) 148 else 104,
                compactTouchTargets = true
            )
        }
    }
}