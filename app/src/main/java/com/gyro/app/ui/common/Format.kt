package com.gyro.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.res.stringResource
import com.gyro.app.R
import com.gyro.app.core.speed.SpeedUnit
import com.gyro.app.core.tilt.Slope
import com.gyro.app.core.tilt.SlopeUnit
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

/** One decimal, with an explicit sign, and never "-0.0". */
fun formatSignedDegrees(value: Float): String {
    val rounded = round(value * 10f) / 10f
    return if (abs(rounded) < 0.05f) String.format(Locale.getDefault(), "%.1f", 0f) else String.format(Locale.getDefault(), "%+.1f", rounded)
}

fun formatDegrees(value: Float): String = String.format(Locale.getDefault(), "%.1f", round(abs(value) * 10f) / 10f)

fun formatSpeed(value: Float, unit: SpeedUnit): String =
    String.format(Locale.getDefault(), "%.${unit.readoutDecimals}f", value)

/** Trip distance with resolution that suits its size: 0.42, 12.4, 316. */
fun formatDistance(value: Double): String = when {
    value < 10.0 -> String.format(Locale.getDefault(), "%.2f", value)
    value < 100.0 -> String.format(Locale.getDefault(), "%.1f", value)
    else -> String.format(Locale.getDefault(), "%.0f", value)
}

/** Elapsed time as m:ss, or h:mm:ss from an hour up. */
fun formatDuration(totalSeconds: Double): String {
    val seconds = totalSeconds.toLong().coerceAtLeast(0L)
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s) else String.format(Locale.getDefault(), "%d:%02d", m, s)
}

/**
 * An inclination in the chosen [unit]. Degrees and percent can carry a sign (which side is
 * high is also spelled out next to the number); a ratio is always unsigned, and near level it is
 * shown as 1:∞.
 */
@Composable
fun slopeText(degrees: Float, unit: SlopeUnit, signed: Boolean): String = when (unit) {
    SlopeUnit.DEGREES -> stringResource(R.string.degrees_value, if (signed) formatSignedDegrees(degrees) else formatDegrees(degrees))
    SlopeUnit.PERCENT -> Slope.percent(degrees)?.let { pct ->
        val rounded = round(pct * 10f) / 10f
        val text = when {
            abs(rounded) < 0.05f -> String.format(Locale.getDefault(), "%.1f", 0f)
            signed -> String.format(Locale.getDefault(), "%+.1f", rounded)
            else -> String.format(Locale.getDefault(), "%.1f", abs(rounded))
        }
        stringResource(R.string.slope_percent_value, text)
    } ?: stringResource(R.string.value_unknown)
    SlopeUnit.RATIO -> Slope.ratioRun(degrees)?.let { run ->
        val text = if (run >= 100f) String.format(Locale.getDefault(), "%.0f", run) else String.format(Locale.getDefault(), "%.1f", run)
        stringResource(R.string.slope_ratio_value, text)
    } ?: stringResource(R.string.slope_ratio_level)
}

fun formatAccuracy(value: Float): String =
    if (value < 10f) String.format(Locale.getDefault(), "%.1f", value) else String.format(Locale.getDefault(), "%.0f", value)

/**
 * Samples a fast-changing value for text display at most every [intervalMillis] (10 Hz by
 * default), like a real digital instrument. Draw-phase visuals still update every frame; this
 * keeps numeric text readable and limits recomposition to the text that shows it.
 */
@Composable
fun <T> rememberSampledState(intervalMillis: Long = 100L, read: () -> T): State<T> {
    val currentRead = rememberUpdatedState(read)
    val state = remember { mutableStateOf(Snapshot.withoutReadObservation { read() }) }
    LaunchedEffect(intervalMillis) {
        // snapshotFlow re-reads the latest value after each delay, so this is a conflating throttle.
        snapshotFlow { currentRead.value() }.collect { value ->
            state.value = value
            delay(intervalMillis)
        }
    }
    return state
}
