package com.gyro.app.platform.sensor

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import com.gyro.app.core.math.Vec3
import com.gyro.app.core.tilt.GravitySample
import com.gyro.app.core.tilt.TiltAvailability
import com.gyro.app.core.tilt.TiltSensorSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Gravity for the tilt meter. Prefers TYPE_GRAVITY, which the platform derives from the
 * accelerometer and gyroscope and is therefore free of most hand-motion acceleration;
 * falls back to the raw accelerometer (smoothed more heavily in [com.gyro.app.core.tilt.TiltModel]).
 */
class AndroidTiltSensorSource(private val sensorManager: SensorManager, private val displayManager: DisplayManager) : TiltSensorSource {
    private val gravitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    override val availability: TiltAvailability = when {
        gravitySensor != null -> TiltAvailability.GRAVITY
        accelerometer != null -> TiltAvailability.ACCELEROMETER
        else -> TiltAvailability.UNAVAILABLE
    }

    override fun samples(): Flow<GravitySample> = callbackFlow {
        val sensor = gravitySensor ?: accelerometer
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val thread = HandlerThread("GyroTiltSensors").apply { start() }
        val handler = Handler(thread.looper)
        val display = DisplayRotationTracker(displayManager).also { it.start(handler) }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val v = event.values
                trySend(GravitySample(Vec3(v[0], v[1], v[2]), event.timestamp, display.rotation))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME, handler)

        awaitClose {
            sensorManager.unregisterListener(listener)
            display.stop()
            thread.quitSafely()
        }
    }
}
