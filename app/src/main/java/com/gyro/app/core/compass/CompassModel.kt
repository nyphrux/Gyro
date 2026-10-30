package com.gyro.app.core.compass

import com.gyro.app.core.math.AngleLowPassFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

/**
 * Turns raw compass sensor events into filtered [CompassFrame]s.
 *
 * The rotation vector is already fused by the platform, so only a short (~80 ms) adaptive
 * circular filter is applied: enough to remove visible jitter without making the needle lag.
 * The accelerometer+magnetometer fallback is noisier and gets a longer time constant.
 */
class CompassModel(private val source: CompassSensorSource) {

    val availability: CompassAvailability get() = source.availability

    private val timeConstantSeconds: Float
        get() = when (availability) {
            CompassAvailability.ROTATION_VECTOR -> 0.08f
            CompassAvailability.GEOMAGNETIC_ROTATION_VECTOR -> 0.15f
            else -> 0.25f
        }

    fun frames(): Flow<CompassFrame> {
        if (availability == CompassAvailability.UNAVAILABLE) return emptyFlow()
        return flow {
            val filter = AngleLowPassFilter(timeConstantSeconds)
            var heading: Float? = null
            var accuracy: SensorAccuracy? = null
            var field: Float? = null
            source.events().collect { event ->
                when (event) {
                    is CompassSensorEvent.Orientation -> {
                        val raw = CompassMath.heading(event.rotationMatrix, event.screenRotation)
                        // Pointing straight up/down: hold the last good heading.
                        if (raw != null) heading = filter.update(raw, event.timestampNanos)
                    }

                    is CompassSensorEvent.Accuracy -> accuracy = event.accuracy
                    is CompassSensorEvent.MagneticField -> field = event.strengthMicroTesla
                }
                emit(CompassFrame(heading, accuracy, field))
            }
        }
    }
}
