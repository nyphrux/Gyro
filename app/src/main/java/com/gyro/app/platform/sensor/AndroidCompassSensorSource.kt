package com.gyro.app.platform.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.gyro.app.core.compass.CompassAvailability
import com.gyro.app.core.compass.CompassSensorEvent
import com.gyro.app.core.compass.CompassSensorSource
import com.gyro.app.core.compass.SensorAccuracy
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.sqrt

/**
 * Compass sensors, best available first:
 * 1. TYPE_ROTATION_VECTOR (accelerometer + magnetometer + gyroscope, fused by the platform)
 * 2. TYPE_GEOMAGNETIC_ROTATION_VECTOR (no gyroscope)
 * 3. Raw accelerometer + magnetometer via [SensorManager.getRotationMatrix]
 *
 * The magnetometer is always registered as well (at UI rate when not otherwise needed) because its
 * accuracy callback and field strength drive calibration and interference warnings.
 *
 * Callbacks run on a dedicated HandlerThread so sensor work never touches the main thread.
 */
class AndroidCompassSensorSource(private val sensorManager: SensorManager, private val displayManager: DisplayManager) : CompassSensorSource {
    private val rotationVector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val geomagneticRotationVector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    override val availability: CompassAvailability = when {
        magnetometer == null -> CompassAvailability.UNAVAILABLE
        rotationVector != null -> CompassAvailability.ROTATION_VECTOR
        geomagneticRotationVector != null -> CompassAvailability.GEOMAGNETIC_ROTATION_VECTOR
        accelerometer != null -> CompassAvailability.ACCELEROMETER_MAGNETOMETER
        else -> CompassAvailability.UNAVAILABLE
    }

    override fun events(): Flow<CompassSensorEvent> = callbackFlow {
        val mode = availability
        val magnet = magnetometer
        if (mode == CompassAvailability.UNAVAILABLE || magnet == null) {
            close()
            return@callbackFlow
        }

        val thread = HandlerThread("GyroCompassSensors").apply { start() }
        val handler = Handler(thread.looper)
        val display = DisplayRotationTracker(displayManager).also { it.start(handler) }

        val rotationMatrix = FloatArray(9)
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false
        var lastAccuracy: Int? = null
        var lastFieldReportMillis = 0L

        fun reportAccuracy(status: Int) {
            if (status == lastAccuracy) return
            lastAccuracy = status
            trySend(CompassSensorEvent.Accuracy(status.toSensorAccuracy()))
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        trySend(CompassSensorEvent.Orientation(rotationMatrix.copyOf(), event.timestamp, display.rotation))
                    }

                    Sensor.TYPE_ACCELEROMETER -> {
                        lowPass(event.values, gravity, if (hasGravity) ACCEL_ALPHA else 1f)
                        hasGravity = true
                        emitFromRawSensors(event.timestamp)
                    }

                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        reportAccuracy(event.accuracy)
                        val x = event.values[0]
                        val y = event.values[1]
                        val z = event.values[2]
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastFieldReportMillis >= FIELD_REPORT_INTERVAL_MILLIS) {
                            lastFieldReportMillis = now
                            trySend(CompassSensorEvent.MagneticField(sqrt(x * x + y * y + z * z)))
                        }
                        if (mode == CompassAvailability.ACCELEROMETER_MAGNETOMETER) {
                            lowPass(event.values, geomagnetic, if (hasGeomagnetic) MAG_ALPHA else 1f)
                            hasGeomagnetic = true
                            emitFromRawSensors(event.timestamp)
                        }
                    }
                }
            }

            private fun emitFromRawSensors(timestamp: Long) {
                if (mode != CompassAvailability.ACCELEROMETER_MAGNETOMETER || !hasGravity || !hasGeomagnetic) return
                if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                    trySend(CompassSensorEvent.Orientation(rotationMatrix.copyOf(), timestamp, display.rotation))
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) reportAccuracy(accuracy)
            }
        }

        val primary: Sensor? = when (mode) {
            CompassAvailability.ROTATION_VECTOR -> rotationVector
            CompassAvailability.GEOMAGNETIC_ROTATION_VECTOR -> geomagneticRotationVector
            CompassAvailability.ACCELEROMETER_MAGNETOMETER -> accelerometer
            CompassAvailability.UNAVAILABLE -> null
        }
        primary?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        val magnetRate = if (mode == CompassAvailability.ACCELEROMETER_MAGNETOMETER) {
            SensorManager.SENSOR_DELAY_GAME
        } else {
            SensorManager.SENSOR_DELAY_UI
        }
        sensorManager.registerListener(listener, magnet, magnetRate, handler)

        awaitClose {
            sensorManager.unregisterListener(listener)
            display.stop()
            thread.quitSafely()
        }
    }

    private companion object {
        const val ACCEL_ALPHA = 0.2f
        const val MAG_ALPHA = 0.3f
        const val FIELD_REPORT_INTERVAL_MILLIS = 250L

        fun lowPass(input: FloatArray, output: FloatArray, alpha: Float) {
            for (i in 0 until 3) output[i] += alpha * (input[i] - output[i])
        }

        fun Int.toSensorAccuracy(): SensorAccuracy = when (this) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> SensorAccuracy.HIGH
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> SensorAccuracy.MEDIUM
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> SensorAccuracy.LOW
            else -> SensorAccuracy.UNRELIABLE // includes SENSOR_STATUS_NO_CONTACT
        }
    }
}
