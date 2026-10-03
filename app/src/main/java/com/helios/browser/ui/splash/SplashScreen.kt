package com.helios.browser.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(durationMillis = 900, easing = LinearOutSlowInEasing))
    }

    // A slower, separate loop so the rings keep breathing after the entrance has settled. If this
    // shared the entrance tween the whole thing would visibly stop dead at 900ms.
    val breath = rememberInfiniteTransition(label = "splash-pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(320)),
        exit = fadeOut(tween(420))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HeliosOledBackground),
            contentAlignment = Alignment.Center
        ) {
            // Two rings at half a cycle apart, expanding and fading. They sit behind the mark and
            // never stop, so the screen does not look dead while a slow disk read finishes.
            repeat(2) { ring ->
                val phase = (breath.value + ring * 0.5f) % 1f
                HeliosOrbitRing(
                    modifier = Modifier
                        .size(320.dp)
                        .alpha((1f - phase) * 0.30f)
                        .scale(0.55f + phase * 0.75f),
                    color = HeliosSun
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                HeliosSunMark(
                    modifier = Modifier
                        .size(148.dp)
                        .scale(0.72f + entrance.value * 0.28f)
                        .alpha(entrance.value)
                )

                Spacer(Modifier.height(28.dp))

                Text(
                    text = stringResource(R.string.app_name).uppercase(),
                    color = HeliosSun,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Light,
                    // Tracking collapses as the mark settles, which reads as the wordmark pulling
                    // itself together.
                    letterSpacing = (14f - entrance.value * 9f).sp,
                    modifier = Modifier.alpha(stagger(entrance.value, 0.35f))
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = tagline,
                    color = HeliosTextSecondary,
                    fontSize = 13.sp,
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

@Preview(widthDp = 360, heightDp = 720, showBackground = true, backgroundColor = 0xFF000000.toInt())
@Composable
private fun SplashScreenPreview() {
    HeliosTheme {
        SplashScreen(visible = true)
    }
}