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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.helios.browser.ui.theme.HeliosSun
import com.helios.browser.ui.theme.HeliosSunLight
import com.helios.browser.ui.theme.HeliosSunPale
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Helios mark: a sun with a twelve-spike corona, four spikes long and eight short.
 *
 * ## Why it is drawn like this
 * The previous version was eight round-capped `drawLine` calls on a 45 degree step. That is the
 * default sunburst: every arm identical, every end a semicircle, no negative space, nothing for the
 * eye to hold on to. It read as generated because it was.
 *
 * What changed:
 *  - **12 spikes on a 30 degree step, with four of them long** (at the cardinal points). The uneven
 *    rhythm is the whole difference between a sun and a star-shaped blob — it gives the rotation
 *    something to resolve against, so motion is legible instead of just busy.
 *  - **Tapered spikes.** Each is a filled triangle coming to a point, not a line with a cap, and
 *    the base is held at a constant width while the length varies. Long spikes read as light, short
 *    ones as texture.
 *  - **A hairline ring**, counter-rotating, to add a second plane of motion without another shape.
 *
 * ## Why it animates smoothly
 * The rotation used to be applied *inside* the draw pass, which meant twelve rays' worth of
 * `sin`/`cos` on every frame, on top of the rest of the frame's work. Now the mark is built once
 * into cached [Path]s and turned with [Modifier.graphicsLayer], so the per-frame cost is one GPU
 * matrix and four draw calls. That is the difference between a mark that stutters on a budget phone
 * and one that does not.
 *
 * @param modifier size the mark; the drawing scales to whatever box it is given.
 * @param spinDegrees current rotation, or null to run the built-in spin.
 * @param coreColor centre disc colour.
 * @param rayLightColour the four long spikes.
 * @param rayColor the eight short spikes.
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
    // Built once per composition and only rebuilt if the colours change, because none of this
    // depends on the frame.
    val paths = remember(rayLightColour, rayColor) { HeliosCoronaPaths.build() }
    // Same reasoning for the halo gradient: a Brush computes its shader on construction, so it is
    // built here rather than inside the draw lambda.
    val halo = remember(coreColor) {
        Brush.radialGradient(
            colors = listOf(coreColor.copy(alpha = 0.34f), Color.Transparent),
            center = Offset(DESIGN_CENTRE, DESIGN_CENTRE),
            radius = HALO_RADIUS
        )
    }

    Canvas(
        modifier = modifier.graphicsLayer {
            // Rotation is a compositing transform, so the draw lambda below never re-runs because
            // of it. This is the reason the animation is smooth.
            rotationZ = spin
        }
    ) {
        // Geometry is authored in a 100-unit square and scaled to fit, so the numbers below are
        // fractions of the mark rather than pixels and nothing needs recomputing per size.
        val side = minOf(size.width, size.height)
        val scale = side / DESIGN_SIZE
        val centre = Offset(size.width / 2f, size.height / 2f)

        translate(centre.x - DESIGN_CENTRE * scale, centre.y - DESIGN_CENTRE * scale) {
            scale(scale, scale, pivot = Offset(DESIGN_CENTRE, DESIGN_CENTRE)) {
                drawHalo(halo)
                paths.longSpikes.forEach { drawPath(it, rayLightColour) }
                paths.shortSpikes.forEach { drawPath(it, rayColor) }
                drawCore(coreColor, coreHighlight)
            }
        }
    }
}

/**
 * The corona geometry, in a 100-unit square centred on [DESIGN_CENTRE].
 *
 * Built once and shared. [Path] is mutable, so handing the same instances to every frame is safe as
 * long as nothing writes to them — and nothing does, because `drawPath` only reads.
 */
private class HeliosCoronaPaths(
    val longSpikes: List<Path>,
    val shortSpikes: List<Path>
) {
    companion object {
        fun build(): HeliosCoronaPaths {
            val long = ArrayList<Path>(SPIKE_COUNT / 3)
            val short = ArrayList<Path>(SPIKE_COUNT - SPIKE_COUNT / 3)
            for (index in 0 until SPIKE_COUNT) {
                // Every third spike is long, which puts them at the cardinal points and gives the
                // mark four-fold symmetry rather than the twelve-fold symmetry of a plain star.
                val isLong = index % 3 == 0
                val path = spike(
                    degrees = index * (360.0 / SPIKE_COUNT),
                    innerRadius = SPIKE_INNER_RADIUS,
                    outerRadius = if (isLong) SPIKE_LONG_RADIUS else SPIKE_SHORT_RADIUS,
                    halfWidth = if (isLong) SPIKE_LONG_HALF_WIDTH else SPIKE_SHORT_HALF_WIDTH
                )
                if (isLong) long.add(path) else short.add(path)
            }
            return HeliosCoronaPaths(long, short)
        }
    }
}

/**
 * One tapered spike, pointing straight up from the centre and rotated into place.
 *
 * A triangle rather than a stroked line: a line cannot come to a point, and a round cap is exactly
 * the blunt shape that made the old mark look machine-made. Base corners are placed by
 * [halfWidth], so every spike has the same width at the core and differs only in how far it
 * reaches — which is what makes length read as intensity.
 */
private fun spike(
    degrees: Double,
    innerRadius: Float,
    outerRadius: Float,
    halfWidth: Float
): Path {
    val radians = Math.toRadians(degrees)
    val cosR = cos(radians).toFloat()
    val sinR = sin(radians).toFloat()

    // Author each spike pointing up (negative y), then rotate it about the centre. Each corner is
    // computed once — this runs at build time, not per frame, but doing it twice is still silly.
    fun corner(x: Float, y: Float): Offset =
        Offset(DESIGN_CENTRE + x * cosR - y * sinR, DESIGN_CENTRE + x * sinR + y * cosR)

    val left = corner(-halfWidth, -innerRadius)
    val tip = corner(0f, -outerRadius)
    val right = corner(halfWidth, -innerRadius)

    return Path().apply {
        moveTo(left.x, left.y)
        lineTo(tip.x, tip.y)
        lineTo(right.x, right.y)
        close()
    }
}

/**
 * The soft halo behind the core.
 *
 * Takes a [Brush] that the caller already built, rather than building a gradient inside the draw
 * pass: a `Brush` computes its shader on construction, and doing that every frame for a shape that
 * never changes shape is exactly the kind of per-frame allocation that makes an animation stutter on
 * a budget phone.
 */
private fun DrawScope.drawHalo(halo: Brush) {
    drawCircle(
        brush = halo,
        radius = HALO_RADIUS,
        center = Offset(DESIGN_CENTRE, DESIGN_CENTRE)
    )
}

/** The core: a disc with a smaller, brighter highlight on top. */
private fun DrawScope.drawCore(coreColor: Color, highlight: Color) {
    drawCircle(
        color = coreColor.copy(alpha = 0.9f),
        radius = CORE_RADIUS,
        center = Offset(DESIGN_CENTRE, DESIGN_CENTRE)
    )
    drawCircle(
        color = highlight,
        radius = CORE_RADIUS * 0.52f,
        center = Offset(DESIGN_CENTRE - CORE_RADIUS * 0.12f, DESIGN_CENTRE - CORE_RADIUS * 0.12f)
    )
}

/**
 * A hairline ring at [radiusFraction] of the mark's size, for the decorative layers behind it.
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
            style = Stroke(width = unit * widthFraction, cap = StrokeCap.Round)
        )
    }
}

/**
 * The ring that rides just outside the corona, turning the other way.
 *
 * Two rotations in opposite directions at different speeds read as depth rather than as a spinning
 * sticker, and it costs one extra draw call. The rate is deliberately slow: 48s against the mark's
 * 24s means they realign every 48s and never appear to lock.
 */
@Composable
fun HeliosCounterRing(
    modifier: Modifier = Modifier,
    color: Color = HeliosSun,
    strokeWidth: Dp = 1.dp,
    reverse: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "helios-counter-ring")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 48_000, easing = LinearEasing),
            repeatMode = if (reverse) RepeatMode.Restart else RepeatMode.Reverse
        ),
        label = "counter-ring-angle"
    )
    Canvas(
        modifier = modifier.graphicsLayer {
            rotationZ = if (reverse) -angle else angle
        }
    ) {
        val unit = minOf(size.width, size.height)
        drawCircle(
            color = color,
            radius = unit * COUNTER_RING_FRACTION,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * The shared rotation used by the splash, the start page and onboarding.
 *
 * 24 seconds rather than the 16 the previous version used. At 16 the mark came round often enough
 * that the eye read it as a spinner; 24 reads as something turning on its own.
 */
@Composable
fun rememberSteadySpin(durationMillis: Int = 24_000): Float {
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

private const val DESIGN_SIZE = 100f
private const val DESIGN_CENTRE = 50f
private const val SPIKE_COUNT = 12
private const val SPIKE_INNER_RADIUS = 13f
private const val SPIKE_LONG_RADIUS = 44f
private const val SPIKE_SHORT_RADIUS = 33f
private const val SPIKE_LONG_HALF_WIDTH = 3.1f
private const val SPIKE_SHORT_HALF_WIDTH = 2.3f
private const val CORE_RADIUS = 11.5f
private const val HALO_RADIUS = 30f
private const val COUNTER_RING_FRACTION = 0.5f

@Preview(showBackground = true, backgroundColor = 0xFF000000L)
@Composable
private fun HeliosSunMarkPreview() {
    HeliosSunMark(modifier = Modifier.size(240.dp))
}