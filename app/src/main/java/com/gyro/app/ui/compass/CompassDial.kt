package com.gyro.app.ui.compass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gyro.app.ui.common.rememberAnimatedAngle
import com.gyro.app.ui.theme.Readout
import kotlin.math.cos
import kotlin.math.sin

private val CARDINALS = mapOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")
private val INTERCARDINALS = mapOf(45 to "NE", 135 to "SE", 225 to "SW", 315 to "NW")

/**
 * The compass card. The rose is drawn once into a cached layer; rotation is applied with
 * `graphicsLayer`, so following the heading costs a layer transform per frame, not a redraw
 * and never a recomposition.
 *
 * The fixed lubber mark at the top shows where the phone is pointing; the card turns
 * underneath it, so the mark reads against the card just like a marine compass.
 */
@Composable
fun CompassDial(heading: () -> Float?, size: Dp, modifier: Modifier = Modifier) {
    val rotation = rememberAnimatedAngle(heading)
    val hasHeading by remember { derivedStateOf { heading() != null } }
    val textMeasurer = rememberTextMeasurer(cacheSize = 32)
    val scheme = MaterialTheme.colorScheme
    val numberStyle = Readout.dial.copy(color = scheme.onSurfaceVariant)
    val intercardinalStyle = Readout.dial.copy(color = scheme.onSurface, fontWeight = FontWeight.Bold)
    val cardinalStyle = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
    val northStyle = cardinalStyle.copy(color = scheme.tertiary)

    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { }, // The heading readout above carries the accessible description.
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(LubberSpace)
                .graphicsLayer {
                    rotationZ = -rotation.value
                    alpha = if (hasHeading) 1f else 0.45f
                }
                .drawWithCache {
                    val radius = this.size.minDimension / 2f
                    val pivot = this.size.center
                    val labels = buildList {
                        for (deg in 30 until 360 step 30) {
                            if (deg % 90 != 0) add(Label(deg, textMeasurer.measure(deg.toString(), numberStyle)))
                        }
                        INTERCARDINALS.forEach { (deg, text) -> add(Label(deg, textMeasurer.measure(text, intercardinalStyle))) }
                        CARDINALS.forEach { (deg, text) ->
                            add(Label(deg, textMeasurer.measure(text, if (deg == 0) northStyle else cardinalStyle)))
                        }
                    }
                    val northMarker = Path().apply {
                        moveTo(pivot.x, pivot.y - radius + 2.dp.toPx())
                        lineTo(pivot.x - 7.dp.toPx(), pivot.y - radius + 14.dp.toPx())
                        lineTo(pivot.x + 7.dp.toPx(), pivot.y - radius + 14.dp.toPx())
                        close()
                    }
                    onDrawBehind {
                        drawRose(radius, labels, scheme.surfaceContainerLow, scheme.surfaceContainer, scheme.outlineVariant, scheme.onSurface, scheme.onSurfaceVariant, scheme.tertiary)
                        drawPath(northMarker, scheme.tertiary)
                    }
                },
        )
        // Fixed overlay: lubber mark above the card, heading line and centre pivot.
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val cardRadius = this.size.minDimension / 2f - LubberSpace.toPx()
            val lubber = Path().apply {
                moveTo(c.x, LubberSpace.toPx() + 2.dp.toPx())
                lineTo(c.x - 8.dp.toPx(), 0f)
                lineTo(c.x + 8.dp.toPx(), 0f)
                close()
            }
            drawPath(lubber, scheme.primary)
            drawLine(
                color = scheme.primary.copy(alpha = 0.6f),
                start = Offset(c.x, c.y - 9.dp.toPx()),
                end = Offset(c.x, c.y - (cardRadius - 80.dp.toPx()).coerceAtLeast(16.dp.toPx())),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(scheme.primary, radius = 5.dp.toPx(), center = c)
            drawCircle(scheme.surface, radius = 2.dp.toPx(), center = c)
        }
    }
}

/** Space reserved around the card for the fixed lubber mark. */
private val LubberSpace = 14.dp

private class Label(val degrees: Int, val layout: TextLayoutResult)

private fun DrawScope.drawRose(
    radius: Float,
    labels: List<Label>,
    face: Color,
    innerFace: Color,
    ring: Color,
    majorTick: Color,
    minorTick: Color,
    north: Color,
) {
    val c = center
    drawCircle(face, radius, c)
    drawCircle(ring, radius - 0.5.dp.toPx(), c, style = Stroke(1.dp.toPx()))

    val outer = radius - 16.dp.toPx()
    for (deg in 0 until 360 step 2) {
        val (length, width, color) = when {
            deg == 0 -> Triple(16.dp.toPx(), 3.dp.toPx(), north)
            deg % 30 == 0 -> Triple(14.dp.toPx(), 2.dp.toPx(), majorTick)
            deg % 10 == 0 -> Triple(9.dp.toPx(), 1.5.dp.toPx(), minorTick)
            else -> Triple(5.dp.toPx(), 1.dp.toPx(), minorTick.copy(alpha = 0.5f))
        }
        val a = Math.toRadians(deg.toDouble())
        val s = sin(a).toFloat()
        val co = cos(a).toFloat()
        drawLine(
            color = color,
            start = Offset(c.x + s * outer, c.y - co * outer),
            end = Offset(c.x + s * (outer - length), c.y - co * (outer - length)),
            strokeWidth = width,
            cap = StrokeCap.Round,
        )
    }

    // Labels sit on one band and turn with the card, reading outward like a printed rose.
    val band = outer - 34.dp.toPx()
    labels.forEach { label ->
        val w = label.layout.size.width
        val h = label.layout.size.height
        rotate(label.degrees.toFloat(), pivot = c) {
            drawText(label.layout, topLeft = Offset(c.x - w / 2f, c.y - band - h / 2f))
        }
    }

    // Inner face with a subtle cross.
    val inner = band - 30.dp.toPx()
    drawCircle(innerFace, inner, c)
    drawLine(ring, Offset(c.x - inner, c.y), Offset(c.x + inner, c.y), strokeWidth = 1.dp.toPx())
    drawLine(ring, Offset(c.x, c.y - inner), Offset(c.x, c.y + inner), strokeWidth = 1.dp.toPx())
}
