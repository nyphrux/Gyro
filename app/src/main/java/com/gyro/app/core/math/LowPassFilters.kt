package com.gyro.app.core.math

import kotlin.math.abs

/**
 * Time-constant based smoothing factor for an exponential low-pass filter. Using the real
 * time between samples (rather than a fixed alpha) keeps behaviour identical whatever rate
 * the hardware actually delivers.
 */
internal fun smoothingFactor(dtSeconds: Float, timeConstantSeconds: Float): Float =
    if (timeConstantSeconds <= 0f) 1f else (dtSeconds / (timeConstantSeconds + dtSeconds)).coerceIn(0f, 1f)

private const val NANOS_PER_SECOND = 1_000_000_000f

/**
 * Circular low-pass filter for headings. It always moves along the shortest arc, so readings
 * either side of north are averaged around 0°, never around 180°.
 *
 * Adaptive: small changes (sensor noise) are smoothed with [timeConstantSeconds]; large,
 * deliberate turns raise the factor so the instrument does not lag behind the user.
 */
class AngleLowPassFilter(
    private val timeConstantSeconds: Float,
    private val catchUpDegrees: Float = 25f,
) {
    private var value = Float.NaN
    private var lastTimestampNanos = 0L

    val current: Float? get() = value.takeUnless { it.isNaN() }

    fun update(angle: Float, timestampNanos: Long): Float {
        if (value.isNaN()) {
            value = AngleMath.normalize(angle)
            lastTimestampNanos = timestampNanos
            return value
        }
        val dt = (timestampNanos - lastTimestampNanos).coerceAtLeast(0L) / NANOS_PER_SECOND
        lastTimestampNanos = timestampNanos
        val delta = AngleMath.shortestDelta(value, angle)
        val base = smoothingFactor(dt, timeConstantSeconds)
        val alpha = if (catchUpDegrees > 0f) {
            (base * (1f + abs(delta) / catchUpDegrees)).coerceAtMost(1f)
        } else {
            base
        }
        value = AngleMath.normalize(value + delta * alpha)
        return value
    }

    fun reset() {
        value = Float.NaN
    }
}

/** Component-wise exponential low-pass filter for 3-vectors (gravity / acceleration). */
class VectorLowPassFilter(private val timeConstantSeconds: Float) {
    private var value: Vec3? = null
    private var lastTimestampNanos = 0L

    fun update(sample: Vec3, timestampNanos: Long): Vec3 {
        val previous = value
        if (previous == null) {
            value = sample
            lastTimestampNanos = timestampNanos
            return sample
        }
        val dt = (timestampNanos - lastTimestampNanos).coerceAtLeast(0L) / NANOS_PER_SECOND
        lastTimestampNanos = timestampNanos
        val alpha = smoothingFactor(dt, timeConstantSeconds)
        val next = previous + (sample - previous) * alpha
        value = next
        return next
    }

    fun reset() {
        value = null
    }
}
