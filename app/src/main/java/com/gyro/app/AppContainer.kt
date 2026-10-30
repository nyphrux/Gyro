package com.gyro.app

import android.content.Context
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import com.gyro.app.core.compass.CompassSensorSource
import com.gyro.app.core.compass.DeclinationProvider
import com.gyro.app.core.prefs.PreferencesRepository
import com.gyro.app.core.speed.LocationSource
import com.gyro.app.core.speed.SpeedUnit
import com.gyro.app.core.tilt.TiltSensorSource
import com.gyro.app.platform.location.AndroidDeclinationProvider
import com.gyro.app.platform.location.AndroidLocationSource
import com.gyro.app.platform.prefs.DataStorePreferencesRepository
import com.gyro.app.platform.sensor.AndroidCompassSensorSource
import com.gyro.app.platform.sensor.AndroidTiltSensorSource
import com.gyro.app.platform.update.GithubUpdater
import java.util.Locale

/**
 * Manual, constructor-based dependency wiring. The app is small enough that a DI framework
 * would add more ceremony than value; ViewModel factories pull what they need from here.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val sensorManager = checkNotNull(appContext.getSystemService(SensorManager::class.java))
    private val displayManager = checkNotNull(appContext.getSystemService(DisplayManager::class.java))
    val preferences: PreferencesRepository = DataStorePreferencesRepository(appContext, SpeedUnit.defaultForRegion(Locale.getDefault().country))
    val compassSensors: CompassSensorSource by lazy { AndroidCompassSensorSource(sensorManager, displayManager) }
    val tiltSensors: TiltSensorSource by lazy { AndroidTiltSensorSource(sensorManager, displayManager) }
    val locationSource: LocationSource by lazy { AndroidLocationSource(appContext) }
    val declinationProvider: DeclinationProvider by lazy { AndroidDeclinationProvider(appContext) }
    val updater: GithubUpdater by lazy { GithubUpdater(appContext) }
}
