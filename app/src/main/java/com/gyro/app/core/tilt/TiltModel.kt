package com.gyro.app.core.tilt

import com.gyro.app.core.math.Vec3
import com.gyro.app.core.math.VectorLowPassFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Converts gravity samples into [TiltReading]s.
 *
 * Filtering is applied to the raw gravity vector (not to angles), which keeps the maths
 * consistent across axes. The fused gravity sensor needs very little smoothing; the raw
 * accelerometer fallback needs more to suppress hand tremor and vibration.
 */
class TiltModel(private val source: TiltSensorSource) {
    val availability: TiltAvailability get() = source.availability

    private val timeConstantSeconds: Float
        get() = if (availability == TiltAvailability.GRAVITY) 0.12f else 0.3f

    fun readings(reference: Flow<Vec3?>): Flow<TiltReading> {
        if (availability == TiltAvailability.UNAVAILABLE) return emptyFlow()
        return flow {
            val filter = VectorLowPassFilter(timeConstantSeconds)
            val level = LevelDetector()
            var mode: TiltMode? = null
            val references = reference.distinctUntilChanged().map { it?.let(TiltReference::from) }

            combine(source.samples(), references) { sample, ref -> sample to ref }
                .collect { (sample, ref) ->
                    val filtered = filter.update(sample.gravity, sample.timestampNanos)
                    val corrected = ref?.apply(filtered) ?: filtered
                    val upScreen = sample.screenRotation.toScreen(corrected).normalized()
                    if (upScreen == Vec3.ZERO) return@collect // free fall / bogus sample

                    val currentMode = TiltMath.detectMode(upScreen, mode)
                    if (currentMode != mode) level.reset()
                    mode = currentMode

                    val surface = TiltMath.surfaceAngles(upScreen)
                    val edge = TiltMath.edgeAngles(upScreen)
                    val error = if (currentMode == TiltMode.SURFACE) surface.total else kotlin.math.abs(edge.deviation)
                    emit(
                        TiltReading(
                            mode = currentMode,
                            rollDegrees = surface.roll,
                            pitchDegrees = surface.pitch,
                            surfaceTiltDegrees = surface.total,
                            edgeRotationDegrees = edge.rotation,
                            edgeOrientation = edge.orientation,
                            edgeDeviationDegrees = edge.deviation,
                            isLevel = level.update(error),
                            deviceGravity = filtered,
                            isRelativeToReference = ref != null,
                        ),
                    )
                }
        }
    }
}
