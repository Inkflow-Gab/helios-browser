package com.helios.browser.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helios.browser.R
import com.helios.browser.ui.theme.HeliosOledBackground
import com.helios.browser.ui.theme.HeliosSun
import com.helios.browser.ui.theme.HeliosTextSecondary
import com.helios.browser.ui.theme.HeliosTheme

/**
 * How long the splash is held open even if settings arrive sooner.
 *
 * Without a floor, a warm DataStore read would make this flash for 120ms and read as a glitch
 * rather than as an animation. Lives here so the number is next to the thing it governs; the host
 * in `MainActivity` is the only caller.
 */
const val SPLASH_MIN_VISIBLE_MILLIS = 900L

/**
 * Cold-start animation shown while DataStore is still being read.
 *
 * Two rules this composable depends on and should not lose:
 *  - It never blocks on anything. The host decides when to drop [visible] and fade out; this screen
 *    does not know whether setup or the browser is coming next, and must not grow that knowledge.
 *  - The minimum hold lives in [SPLASH_MIN_VISIBLE_MILLIS] and is the host's job, so a slow disk
 *    and a fast one both produce the same entrance.
 *
 * @param visible when false the mark fades and scales away.
 * @param tagline shown under the wordmark. Hardcoded per the project's literal-copy convention.
 */
@Composable
fun SplashScreen(
    visible: Boolean,
    modifier: Modifier = Modifier,
    tagline: String = "Private by default"
) {
    // One shared entrance progress rather than a per-child animation, so the wordmark cannot fade
    // in before the mark it belongs to. Staggering happens via thresholds on this single value.
    //
    // 650ms, not 900: the splash is held for at least SPLASH_MIN_VISIBLE_MILLIS (900ms), so a 900ms
    // entrance finished at the exact moment the fade-out began and the settled state was never
    // actually on screen. 650 leaves a beat where the mark is at rest.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(durationMillis = 650, easing = LinearOutSlowInEasing))
    }

    // One clock for the whole splash. The mark, the counter ring and the halo all read from this
    // single angle, because each `rememberInfiniteTransition` is its own ticker with its own frame
    // callbacks — three of them meant three sets of per-frame work for one screen.
    val spin = rememberSteadySpin()

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(280)),
        exit = fadeOut(tween(420))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HeliosOledBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    // A hairline ring, static. It was previously two 320dp rings expanding and
                    // fading: a large alpha-blended shape being scaled on the CPU every frame for
                    // the whole time the splash was up, and it read as a generic loading spinner.
                    HeliosOrbitRing(
                        modifier = Modifier.size(200.dp),
                        color = HeliosSun.copy(alpha = 0.16f)
                    )
                    // Counter-rotating off the same angle as the mark, so the two read as separate
                    // planes rather than one spinning sticker, at the cost of no extra ticker.
                    HeliosCounterRing(
                        spinDegrees = spin,
                        modifier = Modifier.size(152.dp),
                        color = HeliosSun.copy(alpha = 0.42f),
                        strokeWidth = 1.dp
                    )
                    HeliosSunMark(
                        spinDegrees = spin,
                        modifier = Modifier
                            .size(148.dp)
                            .scale(0.78f + entrance.value * 0.22f)
                            .alpha(entrance.value)
                    )
                }

                Spacer(Modifier.height(30.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    color = HeliosSun,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.SemiBold,
                    // Tracking opens up and then settles, which reads as the wordmark taking a
                    // breath. The previous version went to 14sp at 30sp of text, which was wide
                    // enough to look stretched rather than deliberate.
                    letterSpacing = (7f + (1f - entrance.value) * 5f).sp,
                    modifier = Modifier.alpha(stagger(entrance.value, 0.35f))
                )

                Spacer(Modifier.height(9.dp))

                Text(
                    text = tagline,
                    color = HeliosTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp)
                        .alpha(stagger(entrance.value, 0.6f))
                )
            }
        }
    }
}

/**
 * Maps shared entrance progress onto a delayed 0..1 fade.
 *
 * @param progress the entrance value, 0..1.
 * @param startAt the fraction of the entrance at which this element begins appearing.
 */
private fun stagger(progress: Float, startAt: Float): Float =
    ((progress - startAt) / (1f - startAt)).coerceIn(0f, 1f)

// backgroundColor is a Long, so the literal needs the L suffix.
@Preview(widthDp = 360, heightDp = 720, showBackground = true, backgroundColor = 0xFF000000L)
@Composable
private fun SplashScreenPreview() {
    HeliosTheme {
        SplashScreen(visible = true)
    }
}