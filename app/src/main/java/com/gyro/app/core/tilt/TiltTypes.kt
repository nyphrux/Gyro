package com.gyro.app.core.tilt

import com.gyro.app.core.math.ScreenRotation
import com.gyro.app.core.math.Vec3
import kotlinx.coroutines.flow.Flow

enum class TiltAvailability {
    /** Platform-fused gravity sensor (accelerometer + gyroscope): stable and responsive. */
    GRAVITY,

    /** Raw accelerometer, low-pass filtered in-app: fine when the phone is held still. */
    ACCELEROMETER,

    UNAVAILABLE,
}

/**
 * Gravity in the device frame, following Android's convention: lying flat face-up it reads
 * `(0, 0, +9.81)`, i.e. the vector points *up*.
 */
data class GravitySample(
    val gravity: Vec3,
    val timestampNanos: Long,
    val screenRotation: ScreenRotation,
)

interface TiltSensorSource {
    val availability: TiltAvailability

    /** Registers the sensor on collection, unregisters on cancellation. */
    fun samples(): Flow<GravitySample>
}

enum class TiltMode {
    /** Phone lying on a surface: two-axis bubble level. */
    SURFACE,

    /** Phone standing on an edge or held against a wall: single-axis spirit level. */
    EDGE,
}

data class TiltReading(
    val mode: TiltMode,
    /** Surface: inclination of the screen's left→right axis. Positive = right edge raised. */
    val rollDegrees: Float,
    /** Surface: inclination of the screen's bottom→top axis. Positive = top edge raised. */
    val pitchDegrees: Float,
    /** Surface: total angle away from flat, ≥ 0. */
    val surfaceTiltDegrees: Float,
    /** Edge: clockwise rotation of the screen in its own plane from upright, `[-180, 180)`. */
    val edgeRotationDegrees: Float,
    /** Edge: which edge is down, as the nearest multiple of 90 (0 = upright portrait). */
    val edgeOrientation: Int,
    /** Edge: deviation of that edge from level, `[-45, 45]`. Positive = rotated clockwise. */
    val edgeDeviationDegrees: Float,
    val isLevel: Boolean,
    /** Filtered gravity in the device frame before any reference correction (used to set zero). */
    val deviceGravity: Vec3,
    val isRelativeToReference: Boolean,
) {
    /** The single error figure used for "is it level?" in the current mode. */
    val levelError: Float
        get() = if (mode == TiltMode.SURFACE) surfaceTiltDegrees else kotlin.math.abs(edgeDeviationDegrees)
}
