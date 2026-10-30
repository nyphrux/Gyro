package com.gyro.app.ui.tilt

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gyro.app.R
import com.gyro.app.core.tilt.SlopeUnit
import com.gyro.app.core.tilt.TiltMath
import com.gyro.app.core.tilt.TiltReading
import com.gyro.app.ui.common.rememberAnimatedAngle
import com.gyro.app.ui.common.rememberAnimatedOffset
import com.gyro.app.ui.common.rememberSampledState
import com.gyro.app.ui.common.slopeText
import com.gyro.app.ui.theme.GyroMotion
import com.gyro.app.ui.theme.GyroThemeColors
import com.gyro.app.ui.theme.Readout
import kotlin.math.hypot

/*
 * Both instruments are pure visualisations of the numbers shown beneath them. Their scales
 * (rings, ticks) are drawn with the same mapping the indicator uses, and the springs are
 * critically damped, so nothing overshoots or suggests a value that was not measured.
 * Screen readers get the numeric tiles instead.
 */

private val RING_DEGREES = listOf(1f, 5f, 10f)

/** Two-axis bubble vial for surface mode. The bubble rises towards the high side, as in a real vial. */
@Composable
fun BubbleLevel(reading: () -> TiltReading?, size: Dp, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val semantic = GyroThemeColors.current
    val isLevel by remember { derivedStateOf { reading()?.isLevel == true } }
    val bubbleColor by animateColorAsState(if (isLevel) semantic.success else scheme.primary, GyroMotion.effectsOrSnap(), label = "bubble")
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = Readout.dial.copy(color = scheme.onSurfaceVariant)

    val bubble = rememberAnimatedOffset {
        reading()?.let { r ->
            val magnitude = hypot(r.rollDegrees, r.pitchDegrees)
            if (magnitude < 1e-4f) {
                Offset.Zero
            } else {
                val fraction = TiltMath.bubbleFraction(magnitude)
                // Screen y grows downwards; a raised top edge moves the bubble up.
                Offset(r.rollDegrees / magnitude * fraction, -r.pitchDegrees / magnitude * fraction)
            }
        }
    }

    Box(modifier.size(size).clearAndSetSemantics { }) {
        // Static vial: measured and allocated once per size/theme; never re-recorded while the bubble moves.
        Spacer(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val radius = this.size.minDimension / 2f
                    val c = this.size.center
                    val bubbleRadius = radius * BUBBLE_RADIUS_FRACTION
                    val travel = radius - bubbleRadius - 6.dp.toPx()
                    val hairline = 1.dp.toPx()
                    val outline = Stroke(hairline)
                    val dashed = Stroke(hairline, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                    val targetRing = Stroke(2.dp.toPx())
                    // Reference rings at 1°, 5° and 10°, on the bubble's own scale.
                    val rings = RING_DEGREES.map { deg ->
                        val r = travel * TiltMath.bubbleFraction(deg)
                        val layout = textMeasurer.measure("${deg.toInt()}°", labelStyle)
                        Triple(r, layout, Offset(c.x + r + 3.dp.toPx(), c.y - layout.size.height - 2.dp.toPx()))
                    }
                    onDrawBehind {
                        drawCircle(scheme.surfaceContainerLow, radius, c)
                        drawCircle(scheme.outlineVariant, radius - 0.5.dp.toPx(), c, style = outline)
                        drawLine(scheme.outlineVariant, Offset(c.x - radius, c.y), Offset(c.x + radius, c.y), hairline)
                        drawLine(scheme.outlineVariant, Offset(c.x, c.y - radius), Offset(c.x, c.y + radius), hairline)
                        rings.forEach { (r, layout, topLeft) ->
                            drawCircle(scheme.outlineVariant, r, c, style = dashed)
                            drawText(layout, topLeft = topLeft)
                        }
                        // Target ring: the bubble sits inside it when level.
                        drawCircle(scheme.onSurface, bubbleRadius + 3.dp.toPx(), c, style = targetRing)
                    }
                },
        )
        // The bubble is drawn centred in its own layer and moved with a layer translation, so
        // following the sensor updates layer properties only; it re-records just on colour changes.
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val radius = this.size.minDimension / 2f
                    val travel = radius - radius * BUBBLE_RADIUS_FRACTION - 6.dp.toPx()
                    val offset = bubble.value * travel
                    translationX = offset.x
                    translationY = offset.y
                }
                .drawBehind {
                    val bubbleRadius = this.size.minDimension / 2f * BUBBLE_RADIUS_FRACTION
                    val c = center
                    drawCircle(bubbleColor.copy(alpha = 0.92f), bubbleRadius, c)
                    drawCircle(Color.White.copy(alpha = 0.35f), bubbleRadius * 0.32f, c + Offset(-bubbleRadius * 0.32f, -bubbleRadius * 0.32f))
                },
        )
    }
}

private const val BUBBLE_RADIUS_FRACTION = 0.15f

/**
 * Spirit level for edge mode. The fixed dashed line is the phone's edge; the solid line is
 * the true horizon. The angle between them is the measured deviation, shown in the centre and
 * turned so it stays readable whichever edge the phone stands on.
 */
@Composable
fun EdgeLevel(reading: () -> TiltReading?, size: Dp, modifier: Modifier = Modifier, unit: SlopeUnit = SlopeUnit.DEGREES) {
    val scheme = MaterialTheme.colorScheme
    val semantic = GyroThemeColors.current
    val isLevel by remember { derivedStateOf { reading()?.isLevel == true } }
    val horizonColor by animateColorAsState(if (isLevel) semantic.success else scheme.primary, GyroMotion.effectsOrSnap(), label = "horizon")
    val rotation = rememberAnimatedAngle { reading()?.edgeRotationDegrees }
    val orientation = rememberAnimatedAngle { reading()?.edgeOrientation?.toFloat() }
    val deviation by rememberSampledState { reading()?.edgeDeviationDegrees }

    Box(modifier.size(size).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        // Own layer, so redrawing the moving horizon never re-records the rest of the screen;
        // paths, strokes and dash effects are allocated once per size instead of every frame.
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawWithCache {
                    val radius = this.size.minDimension / 2f
                    val c = this.size.center
                    val circle = Path().apply { addOval(Rect(c, radius)) }
                    val outline = Stroke(1.dp.toPx())
                    val tickTop = c.y - radius + 4.dp.toPx()
                    val majorLength = 14.dp.toPx()
                    val minorLength = 7.dp.toPx()
                    val majorWidth = 2.dp.toPx()
                    val minorWidth = 1.dp.toPx()
                    val edgeWidth = 1.5.dp.toPx()
                    val edgeDash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    val horizonWidth = 3.dp.toPx()
                    onDrawBehind {
                        drawCircle(scheme.surfaceContainerLow, radius, c)
                        clipPath(circle) {
                            rotate(-rotation.value, pivot = c) {
                                // "Ground" below the true horizon.
                                drawRect(horizonColor.copy(alpha = 0.10f), topLeft = Offset(c.x - radius * 1.5f, c.y), size = Size(radius * 3f, radius * 1.5f))
                            }
                        }
                        drawCircle(scheme.outlineVariant, radius - 0.5.dp.toPx(), c, style = outline)

                        // Scale and edge reference, fixed to the phone edge that is down.
                        rotate(-orientation.value, pivot = c) {
                            for (deg in -45..45 step 5) {
                                val major = deg % 15 == 0
                                rotate(deg.toFloat(), pivot = c) {
                                    drawLine(
                                        if (major) scheme.onSurfaceVariant else scheme.outlineVariant,
                                        Offset(c.x, tickTop),
                                        Offset(c.x, tickTop + if (major) majorLength else minorLength),
                                        strokeWidth = if (major) majorWidth else minorWidth,
                                        cap = StrokeCap.Round,
                                    )
                                }
                            }
                            drawLine(
                                scheme.outline,
                                Offset(c.x - radius * 0.92f, c.y),
                                Offset(c.x + radius * 0.92f, c.y),
                                strokeWidth = edgeWidth,
                                pathEffect = edgeDash,
                            )
                        }

                        // True horizon and vertical indicator.
                        rotate(-rotation.value, pivot = c) {
                            drawLine(horizonColor, Offset(c.x - radius * 0.92f, c.y), Offset(c.x + radius * 0.92f, c.y), horizonWidth, StrokeCap.Round)
                            drawLine(horizonColor, Offset(c.x, tickTop), Offset(c.x, c.y - radius + 22.dp.toPx()), horizonWidth, StrokeCap.Round)
                        }
                    }
                },
        )

        Surface(
            shape = CircleShape,
            color = scheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier
                .graphicsLayer { rotationZ = -orientation.value }
                .clip(CircleShape),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = deviation?.let { slopeText(it, unit, signed = false) } ?: stringResource(R.string.value_unknown),
                    style = Readout.large,
                    color = if (isLevel) semantic.success else scheme.onSurface,
                )
            }
        }
    }
}
