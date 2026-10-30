package com.gyro.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import com.gyro.app.core.speed.SpeedUnit
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
