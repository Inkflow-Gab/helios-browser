package com.helios.browser.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.helios.browser.ui.theme.HeliosSun
import com.helios.browser.ui.theme.HeliosSunLight
import com.helios.browser.ui.theme.HeliosSunPale
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Helios mark: a warm core inside eight alternating-length rays, slowly rotating.
 *
 * Drawn with Canvas rather than shipped as an `ImageVector` because the rotation is the point. The
 * geometry deliberately mirrors `res/drawable/ic_launcher_foreground.xml` so the thing that
 * appears on the splash is the thing the user tapped on the home screen.
 *
 * @param modifier size the mark; the drawing scales to whatever box it is given.
 * @param spinDegrees current rotation. Pass a value animated by the caller, or `null` to run the
 *   built-in 16-second spin.
 * @param coreColor centre disc colour.
 * @param rayLightColour long rays, which read brighter as they sweep past.
 * @param rayColor short rays.
 * @param coreHighlight inner highlight, the "lit from within" bit.
 */
@Composable
fun HeliosSunMark(
    modifier: Modifier = Modifier,
    spinDegrees: Float? = null,
    coreColor: Color = HeliosSun,
    rayLightColour: Color = HeliosSunLight,
    rayColor: Color = HeliosSun,
    coreHighlight: Color = HeliosSunPale
) {
    val spin = spinDegrees ?: rememberSteadySpin()
    Canvas(modifier = modifier) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        // Everything is expressed as a fraction of the shorter edge so the mark stays circular in
        // a non-square box.
        val unit = minOf(size.width, size.height)

        val rotation = Math.toRadians(spin.toDouble())
        val cosR = cos(rotation).toFloat()
        val sinR = sin(rotation).toFloat()

        fun point(radius: Float, degrees: Double): Offset {
            val angle = Math.toRadians(degrees)
            val x = cos(angle).toFloat() * radius
            val y = sin(angle).toFloat() * radius
            return Offset(centre.x + x * cosR - y * sinR, centre.y + x * sinR + y * cosR)
        }

        // Rays first, so the core sits on top of their inner ends.
        repeat(RAY_COUNT) { index ->
            val isLong = index % 2 == 0
            val startRadius = unit * if (isLong) 0.17f else 0.18f
            val endRadius = unit * if (isLong) 0.44f else 0.36f
            val degrees = index * (360.0 / RAY_COUNT)
            drawLine(
                color = if (isLong) rayLightColour else rayColor,
                start = point(startRadius, degrees),
                end = point(endRadius, degrees),
                strokeWidth = unit * if (isLong) 0.055f else 0.045f,
                cap = StrokeCap.Round
            )
        }

        val coreRadius = unit * 0.155f
        drawCircle(color = coreColor.copy(alpha = 0.22f), radius = coreRadius * 1.45f, center = centre)
        drawCircle(color = coreColor, radius = coreRadius, center = centre)
        drawCircle(color = coreHighlight, radius = coreRadius * 0.6f, center = centre)
    }
}

/**
 * An orbit ring at [radiusFraction] of the mark's size, for the decorative layers behind it.
 *
 * @param color ring colour; callers usually pass it at low alpha.
 * @param widthFraction stroke width as a fraction of the mark size.
 */
@Composable
fun HeliosOrbitRing(
    modifier: Modifier = Modifier,
    color: Color = HeliosSun,
    widthFraction: Float = 0.012f,
    radiusFraction: Float = 0.48f
) {
    Canvas(modifier = modifier) {
        val unit = minOf(size.width, size.height)
        drawCircle(
            color = color,
            radius = unit * radiusFraction,
            style = Stroke(width = unit * widthFraction)
        )
    }
}

/** The shared 16-second rotation used by the splash and the onboarding welcome page. */
@Composable
fun rememberSteadySpin(durationMillis: Int = 16_000): Float {
    val transition = rememberInfiniteTransition(label = "helios-sun-spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )
    return angle
}

private const val RAY_COUNT = 8

// backgroundColor is a Long, so the literal needs the L suffix; `.toInt()` would not help.
@Preview(showBackground = true, backgroundColor = 0xFF000000L)
@Composable
private fun HeliosSunMarkPreview() {
    HeliosSunMark(modifier = Modifier.size(240.dp))
}