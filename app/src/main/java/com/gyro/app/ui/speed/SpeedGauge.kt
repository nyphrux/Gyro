package com.gyro.app.ui.speed

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gyro.app.R
import com.gyro.app.core.speed.GaugeScale
import com.gyro.app.core.speed.SpeedStatus
import com.gyro.app.core.speed.SpeedometerUiState
import com.gyro.app.ui.common.formatSpeed
import com.gyro.app.ui.common.rememberAnimatedValue
import com.gyro.app.ui.theme.GyroMotion
import com.gyro.app.ui.theme.GyroThemeColors
import com.gyro.app.ui.theme.Readout
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f

/**
 * Auto-ranging arc gauge. The arc and tick labels are drawn in the draw phase from animated
 * values, so smooth motion between 1 Hz GPS fixes never recomposes. The number in the centre
 * is the measured (smoothed) value only — never an interpolated in-between figure.
 */
@Composable
fun SpeedGauge(state: SpeedometerUiState, size: Dp, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val semantic = GyroThemeColors.current
    val speed = rememberAnimatedValue { state.displaySpeed ?: 0f }
    val scale = rememberAnimatedValue(initial = state.gaugeScale) { state.gaugeScale }
    val arcColor by animateColorAsState(
        when {
            state.displaySpeed == null -> scheme.outline
            state.isOverAlert -> scheme.error
            state.isLowAccuracy -> semantic.warning
            else -> scheme.primary
        },
        GyroMotion.effectsOrSnap(),
        label = "gaugeArc",
    )
    val textMeasurer = rememberTextMeasurer(cacheSize = 48)
    val tickLabelStyle = Readout.dial.copy(color = scheme.onSurfaceVariant)
    val majorStep = GaugeScale.majorTickStep(state.gaugeScale)
    val alertValue = state.displayAlert
    val alertColor = scheme.error

    val reading = state.displaySpeed
    val readingText = reading?.let { formatSpeed(it, state.unit) } ?: stringResource(R.string.value_unknown)
    val caption = when {
        state.status == SpeedStatus.STATIONARY -> stringResource(R.string.speed_caption_stationary)
        state.status == SpeedStatus.SIGNAL_LOST -> stringResource(R.string.speed_caption_no_signal)
        state.status == SpeedStatus.SEARCHING -> stringResource(R.string.speed_caption_waiting)
        state.isOverAlert -> stringResource(R.string.speed_caption_over_alert)
        state.isLowAccuracy -> stringResource(R.string.speed_caption_low_accuracy)
        else -> null
    }
    val description = if (reading != null) {
        stringResource(R.string.speed_description, readingText, unitName(state.unit))
    } else {
        stringResource(R.string.speed_description_none)
    }

    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val radius = this.size.minDimension / 2f - stroke / 2f - 2.dp.toPx()
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            drawArc(
                color = scheme.surfaceContainerHighest,
                startAngle = START_ANGLE,
                sweepAngle = SWEEP_ANGLE,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )

            val currentScale = scale.value.coerceAtLeast(1f)
            val fraction = (speed.value / currentScale).coerceIn(0f, 1f)
            if (fraction > 0.002f) {
                drawArc(
                    color = arcColor,
                    startAngle = START_ANGLE,
                    sweepAngle = SWEEP_ANGLE * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            // Speed alert threshold: a short bar across the track, when it is on the dial.
            if (alertValue != null && alertValue <= currentScale) {
                val a = Math.toRadians((START_ANGLE + SWEEP_ANGLE * (alertValue / currentScale)).toDouble())
                val ca = cos(a).toFloat()
                val sa = sin(a).toFloat()
                val half = stroke / 2f + 4.dp.toPx()
                drawLine(
                    color = alertColor,
                    start = Offset(center.x + ca * (radius - half), center.y + sa * (radius - half)),
                    end = Offset(center.x + ca * (radius + half), center.y + sa * (radius + half)),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            // Knob at the tip marks the exact reading.
            val tip = Math.toRadians((START_ANGLE + SWEEP_ANGLE * fraction).toDouble())
            val tipCenter = Offset(center.x + radius * cos(tip).toFloat(), center.y + radius * sin(tip).toFloat())
            drawCircle(scheme.surface, radius = stroke * 0.32f, center = tipCenter)

            // Ticks and labels inside the arc. Positions use the animated scale, so they glide
            // when the gauge re-ranges.
            val tickOuter = radius - stroke / 2f - 8.dp.toPx()
            val minorStep = majorStep / 5f
            var value = 0f
            var index = 0
            while (value <= currentScale + 0.001f) {
                val isMajor = index % 5 == 0
                val angle = Math.toRadians((START_ANGLE + SWEEP_ANGLE * (value / currentScale)).toDouble())
                val c = cos(angle).toFloat()
                val s = sin(angle).toFloat()
                val length = if (isMajor) 10.dp.toPx() else 5.dp.toPx()
                drawLine(
                    color = if (isMajor) scheme.onSurfaceVariant else scheme.outlineVariant,
                    start = Offset(center.x + c * tickOuter, center.y + s * tickOuter),
                    end = Offset(center.x + c * (tickOuter - length), center.y + s * (tickOuter - length)),
                    strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                if (isMajor) {
                    val layout = textMeasurer.measure(value.roundToInt().toString(), tickLabelStyle)
                    val labelRadius = tickOuter - length - 14.dp.toPx()
                    drawText(
                        layout,
                        topLeft = Offset(
                            center.x + c * labelRadius - layout.size.width / 2f,
                            center.y + s * labelRadius - layout.size.height / 2f,
                        ),
                    )
                }
                index++
                value = index * minorStep
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = readingText,
                style = Readout.hero,
                color = if (reading == null) scheme.onSurfaceVariant else scheme.onSurface,
            )
            Text(
                text = state.unit.symbol,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurfaceVariant,
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        state.isOverAlert -> scheme.error
                        state.isLowAccuracy -> semantic.warning
                        else -> scheme.primary
                    },
                )
            }
        }
    }
}
