package com.helios.browser.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.helios.browser.R

/*
 * Three bundled families, all OFL 1.1, licences copied into `app/src/main/assets/licenses`.
 *
 * Each ships as a single variable font rather than one file per weight. That is roughly a third of
 * the APK size versus static weights, and it means a new weight is a new line here instead of a new
 * binary. The cost is that each weight needs explicit `variationSettings`, because Compose will not
 * interpolate along the axis on its own — see the helpers below.
 *
 * Do not replace these with downloadable fonts (Google Fonts provider): that needs network on first
 * run and Play Services, both of which this app is trying not to depend on.
 */

/** UI and body text. Inter, chosen for tall x-height and unambiguous l/1/I at small sizes. */
private fun interFont(weight: FontWeight): Font = Font(
    resId = R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        // Optical size axis. Pinning it to the largest step keeps small text from looking spindly
        // on a high-density screen without shipping a second file.
        FontVariation.opticalSizing(32f)
    )
)

/** Numerals and short labels: the wordmark, the blocked counter, tab counts. */
private fun displayFont(weight: FontWeight): Font = Font(
    resId = R.font.space_grotesk_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

/** URLs, hosts and anything where character-by-character alignment is visible. */
private fun monoFont(weight: FontWeight): Font = Font(
    resId = R.font.jetbrains_mono_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

val InterFamily = FontFamily(
    interFont(FontWeight.Normal),
    interFont(FontWeight.Medium),
    interFont(FontWeight.SemiBold),
    interFont(FontWeight.Bold)
)

val DisplayFamily = FontFamily(
    displayFont(FontWeight.Normal),
    displayFont(FontWeight.Medium),
    displayFont(FontWeight.SemiBold),
    displayFont(FontWeight.Bold)
)

val MonoFamily = FontFamily(
    monoFont(FontWeight.Normal),
    monoFont(FontWeight.Medium)
)

/**
 * Material3 trims the first line's top by default, which is right for web text and wrong for the
 * compact UI here — it makes single-line labels sit visibly high in their touch targets. Trim only
 * the last line's bottom instead.
 */
private val TrimBottomOnly = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.LastLineBottom
)

private fun style(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    tracking: Double = 0.0
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = TrimBottomOnly
)

/**
 * Type scale.
 *
 * Display styles use Space Grotesk so the wordmark and big headings are visibly a different voice
 * from body copy; everything functional is Inter.
 */
val Typography = Typography(
    displayLarge = style(DisplayFamily, FontWeight.Bold, 44, 50, (-1.5)),
    displayMedium = style(DisplayFamily, FontWeight.Bold, 34, 40, (-1.0)),
    displaySmall = style(DisplayFamily, FontWeight.SemiBold, 28, 34, (-0.6)),

    headlineLarge = style(DisplayFamily, FontWeight.SemiBold, 26, 32, (-0.5)),
    headlineMedium = style(DisplayFamily, FontWeight.SemiBold, 24, 30, (-0.5)),
    headlineSmall = style(InterFamily, FontWeight.Bold, 20, 26, (-0.3)),

    titleLarge = style(InterFamily, FontWeight.SemiBold, 19, 25, (-0.3)),
    titleMedium = style(InterFamily, FontWeight.Medium, 16, 22, (-0.2)),
    titleSmall = style(InterFamily, FontWeight.SemiBold, 14, 19, (-0.1)),

    bodyLarge = style(InterFamily, FontWeight.Normal, 15, 21, 0.0),
    bodyMedium = style(InterFamily, FontWeight.Normal, 13, 18, 0.0),
    bodySmall = style(InterFamily, FontWeight.Normal, 12, 16, 0.1),

    labelLarge = style(InterFamily, FontWeight.SemiBold, 14, 18, 0.1),
    labelMedium = style(InterFamily, FontWeight.Medium, 12, 16, 0.3),
    labelSmall = style(InterFamily, FontWeight.SemiBold, 11, 14, 0.5)
)