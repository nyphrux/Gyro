package com.gyro.app.core.speed

import kotlin.math.abs

/**
 * Turns raw location fixes into displayable speed.
 *
 * - Uses the provider's own speed (GNSS Doppler), never position differencing.
 * - Light exponential smoothing with a short time constant: at 1 Hz fixes each new reading
 *   carries ~60% weight, so real acceleration and braking show up within a second or two.
 *   Large jumps bypass smoothing entirely.
 * - GNSS speed hovers around a few tenths of a m/s at rest, so readings under
 *   [stationaryThresholdMetersPerSecond] are reported as stationary (0) rather than as motion.
 * - The GNSS direction of travel is passed on only when moving briskly enough for it to mean
 *   something and its reported accuracy is reasonable.
 */
class SpeedProcessor(
    private val stationaryThresholdMetersPerSecond: Float = 0.5f,
    private val timeConstantSeconds: Float = 0.6f,
    private val jumpThresholdMetersPerSecond: Float = 4f,
    private val lowAccuracySpeedThreshold: Float = 1.5f,
    private val lowAccuracyHorizontalThreshold: Float = 30f,
    private val courseMinSpeedMetersPerSecond: Float = 1f,
    private val courseMaxAccuracyDegrees: Float = 30f,
) {
    private var smoothed: Float? = null
    private var lastNanos = 0L

    /** @return a measurement, or null when the fix carried no usable speed. */
    fun process(sample: LocationSample): SpeedMeasurement? {
        val raw = sample.speedMetersPerSecond ?: return null
        if (raw.isNaN() || raw.isInfinite() || raw < 0f) return null

        val previous = smoothed
        val dtSeconds = (sample.elapsedRealtimeNanos - lastNanos) / 1_000_000_000f
        lastNanos = sample.elapsedRealtimeNanos
        val next = when {
            previous == null -> raw
            dtSeconds <= 0f || dtSeconds > 5f -> raw
            abs(raw - previous) >= jumpThresholdMetersPerSecond -> raw
            else -> previous + (raw - previous) * (dtSeconds / (timeConstantSeconds + dtSeconds))
        }
        smoothed = next

        val stationary = next < stationaryThresholdMetersPerSecond
        val lowAccuracy = (sample.speedAccuracyMetersPerSecond ?: 0f) > lowAccuracySpeedThreshold ||
            (sample.horizontalAccuracyMeters ?: 0f) > lowAccuracyHorizontalThreshold
        // GNSS bearing is noise at walking pace and meaningless at rest.
        val course = sample.bearingDegrees?.takeIf {
            !stationary && next >= courseMinSpeedMetersPerSecond && !it.isNaN() &&
                (sample.bearingAccuracyDegrees ?: 0f) <= courseMaxAccuracyDegrees
        }
        return SpeedMeasurement(
            speedMetersPerSecond = if (stationary) 0f else next,
            accuracyMetersPerSecond = sample.speedAccuracyMetersPerSecond,
            isStationary = stationary,
            isLowAccuracy = lowAccuracy,
            courseDegrees = course,
            elapsedRealtimeNanos = sample.elapsedRealtimeNanos,
        )
    }

    fun reset() {
        smoothed = null
        lastNanos = 0L
    }
}
