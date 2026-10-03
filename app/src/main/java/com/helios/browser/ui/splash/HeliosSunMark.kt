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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Helios mark: a sun sitting on a horizon.
 *
 * ## The shape
 * A disc whose lower edge is cut by a straight chord, with short tapered corona spikes rising from
 * the lit half. That is the whole idea: a sun partly below a line, which reads as *the light being
 * held back* — the browser's actual job — without resorting to a shield or a padlock that every
 * privacy browser already uses.
 *
 * ## Why it is drawn flat
 * The previous version had a radial-gradient halo, which meant a shader had to be evaluated for
 * every pixel of it on every frame. That is the single most expensive thing in this file, and it was
 * paying that cost on a mark that is 76dp across on the start page. It is now three concentric
 * translucent discs: visually near-identical at these sizes, and each one is a flat fill.
 *
 * The chord is part of the disc's own path rather than a subtraction, so there is no `saveLayer`
 * and no blend mode. That matters: a subtraction would need an offscreen buffer, which on a budget
 * GPU is exactly the allocation that causes stutter, and it would also break on any background that
 * is not solid black.
 *
 * ## Why it animates smoothly
 * Rotation is applied with [Modifier.graphicsLayer], so it is a compositor transform and the draw
 * lambda below never re-runs because of it. The geometry is built once by [drawWithCache] and the
 * paths are cached, so a frame costs one matrix and a handful of flat fills.
 *
 * ## Pass an angle, do not start a clock
 * [spinDegrees] defaults to null, which starts an animation clock — but callers should almost always
 * pass one instead. Every `rememberInfiniteTransition` is its own ticker with its own frame
 * callbacks, so a screen showing the mark, a counter-rotating ring and a pulsing halo was running
 * three of them. The splash now creates one and threads the angle through.
 */
@Composable
fun HeliosSunMark(
    modifier: Modifier = Modifier,
    spinDegrees: Float? = null,
    coreColor: Color = HeliosSun,
    spikeColor: Color = HeliosSunLight,
    highlightColor: Color = HeliosSunPale,
    haloColor: Color = HeliosSun,
    animate: Boolean = true
) {
    val spin = spinDegrees ?: if (animate) rememberSteadySpin() else 0f
    val geometry = remember(spikeColor) { SunGeometry() }

    Canvas(
        modifier = modifier.graphicsLayer {
            // A compositor transform. Reading `spin` here is what invalidates this block per frame,
            // and nothing below it — the draw lambda closes over `geometry`, which never changes.
            rotationZ = spin
        }
    ) {
        val side = minOf(size.width, size.height)
        val factor = side / DESIGN_SIZE
        val origin = Offset(size.width / 2f, size.height / 2f)

        translate(origin.x - DESIGN_CENTRE * factor, origin.y - DESIGN_CENTRE * factor) {
            scale(factor, factor, pivot = Offset(DESIGN_CENTRE, DESIGN_CENTRE)) {
                drawHalo(haloColor)
                geometry.spikes.forEach { drawPath(it, spikeColor) }
                drawSunBody(geometry, coreColor)
                drawCoreHighlight(highlightColor)
            }
        }
    }
}

/**
 * The sun's geometry, in a 100-unit square centred on [DESIGN_CENTRE].
 *
 * Built once and shared. [Path] is mutable, so handing the same instances to every frame is safe as
 * long as nothing writes to them — and nothing does, because `drawPath` only reads.
 */
private class SunGeometry(
    /** The disc with its lower edge cut by the horizon chord, as one closed path. */
    val body: Path,
    /** Short corona spikes, rising only from the lit half. */
    val spikes: List<Path>
) {
    companion object {
        fun build(): SunGeometry {
            val chord = CHORD_FRACTION * SUN_RADIUS
            // Where the chord meets the circle. sqrt(1 - k^2) with k the chord's offset in radii.
            const val k = CHORD_FRACTION
            val halfSpan = SUN_RADIUS * sqrt(1f - k * k)
            val chordY = DESIGN_CENTRE + chord

            val left = Offset(DESIGN_CENTRE - halfSpan, chordY)
            val right = Offset(DESIGN_CENTRE + halfSpan, chordY)

            // Screen-space angles: 0 is 3 o'clock and they increase clockwise, so the arc that goes
            // over the top from the left intersection to the right one is the sweep below.
            val startAngle = Math.toDegrees(atan2(k.toDouble(), -halfSpan.toDouble())).toFloat()
            val endAngle = Math.toDegrees(atan2(k.toDouble(), halfSpan.toDouble())).toFloat()
            val sweep = (endAngle - startAngle + 360f) % 360f

            val body = Path().apply {
                moveTo(left.x, left.y)
                arcTo(
                    rect = Rect(
                        offset = Offset(DESIGN_CENTRE - SUN_RADIUS, DESIGN_CENTRE - SUN_RADIUS),
                        size = Size(SUN_RADIUS * 2f, SUN_RADIUS * 2f)
                    ),
                    startAngleDegrees = startAngle,
                    sweepAngleDegrees = sweep,
                    forceMoveTo = false
                )
                close()
            }

            val spikes = buildList {
                // Eight spikes on a 45 degree step, only across the upper 180 degrees so they read as
                // rays coming off a sun rather than as a full ring.
                val step = 45f
                for (degree in -180f..180f step step) {
                    val radians = Math.toRadians(degree.toDouble())
                    val cosR = cos(radians).toFloat()
                    val sinR = sin(radians).toFloat()

                    fun corner(x: Float, y: Float) = Offset(
                        DESIGN_CENTRE + x * cosR - y * sinR,
                        DESIGN_CENTRE + x * sinR + y * cosR
                    )

                    val base = corner(-SPIKE_HALF_WIDTH, -SPIKE_INNER_RADIUS)
                    val tip = corner(0f, -SPIKE_OUTER_RADIUS)
                    val tipRight = corner(SPIKE_HALF_WIDTH, -SPIKE_INNER_RADIUS)
                    add(
                        Path().apply {
                            moveTo(base.x, base.y)
                            lineTo(tip.x, tip.y)
                            lineTo(tipRight.x, tipRight.y)
                            close()
                        }
                    )
                }
            }

            return SunGeometry(body = body, spikes = spikes)
        }
    }
}

/**
 * Three flat translucent discs standing in for a gradient.
 *
 * A [androidx.compose.ui.graphics.Brush] radial gradient has to be evaluated per pixel per frame;
 * three flat fills do not, and at 76dp you cannot see the difference.
 */
private fun DrawScope.drawHalo(color: Color) {
    val centre = Offset(DESIGN_CENTRE, DESIGN_CENTRE)
    drawCircle(color.copy(alpha = 0.07f), SUN_RADIUS * 1.62f, centre)
    drawCircle(color.copy(alpha = 0.07f), SUN_RADIUS * 1.40f, centre)
    drawCircle(color.copy(alpha = 0.07f), SUN_RADIUS * 1.20f, centre)
}

/** The sun itself: one closed path, so the horizon is a real edge and not a subtraction. */
private fun DrawScope.drawSunBody(geometry: SunGeometry, coreColor: Color) {
    drawPath(geometry.body, coreColor)
}

/** A brighter disc inside the body, offset towards the light. */
private fun DrawScope.drawCoreHighlight(color: Color) {
    drawCircle(
        color = color,
        radius = HIGHLIGHT_RADIUS,
        center = Offset(DESIGN_CENTRE - HIGHLIGHT_OFFSET_X, DESIGN_CENTRE - HIGHLIGHT_OFFSET_Y)
    )
}

/**
 * A hairline ring, for the decorative layers behind the mark.
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
 * A ring that turns against the mark.
 *
 * Takes the angle as a parameter rather than running its own clock, for the reason in
 * [HeliosSunMark]'s docs: every infinite transition is a separate ticker.
 */
@Composable
fun HeliosCounterRing(
    spinDegrees: Float,
    modifier: Modifier = Modifier,
    color: Color = HeliosSun,
    strokeWidth: Dp = 1.dp,
    radiusFraction: Float = 0.5f,
    reverse: Boolean = true
) {
    Canvas(
        modifier = modifier.graphicsLayer {
            // Counter-rotation is applied as a second matrix rather than by negating the angle, so
            // the caller can pass a single shared angle and both rings follow one clock.
            rotationZ = if (reverse) -spinDegrees * COUNTER_RATIO else spinDegrees * COUNTER_RATIO
        }
    ) {
        val unit = minOf(size.width, size.height)
        drawCircle(
            color = color,
            radius = unit * radiusFraction,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * The shared rotation.
 *
 * 24 seconds: at 16 the mark came round often enough that the eye read it as a spinner rather than
 * something turning on its own.
 *
 * Only the splash should call this. The start page deliberately does not — a front page that spins
 * forever costs two animation clocks and a redraw per frame for as long as it is open, which is
 * both a battery cost and the most likely cause of stutter on a budget phone.
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
private const val SUN_RADIUS = 24f

/** Where the horizon cuts the disc, as a fraction of the radius below centre. 0.34 of it. */
private const val CHORD_FRACTION = 0.34f
private const val SPIKE_INNER_RADIUS = SUN_RADIUS * 0.94f
private const val SPIKE_OUTER_RADIUS = SUN_RADIUS * 1.52f
private const val SPIKE_HALF_WIDTH = 2.6f

/** The highlight disc inside the sun: offset up and left, so the disc has a light source. */
private const val HIGHLIGHT_RADIUS = SUN_RADIUS * 0.46f
private const val HIGHLIGHT_OFFSET_X = SUN_RADIUS * 0.14f
private const val HIGHLIGHT_OFFSET_Y = SUN_RADIUS * 0.26f

/**
 * The counter-ring turns at this multiple of the mark's angle.
 *
 * Not 1: a different speed is what makes two rings read as separate planes instead of one spinning
 * sticker. 0.6 rather than something coprime-looking because a ratio near 1 still reads as one
 * object.
 */
private const val COUNTER_RATIO = 0.6f

@Preview(showBackground = true, backgroundColor = 0xFF000000L)
@Composable
private fun HeliosSunMarkPreview() {
    HeliosSunMark(modifier = Modifier.size(240.dp), animate = false)
}