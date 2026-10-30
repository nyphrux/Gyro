package com.gyro.app.platform.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import androidx.core.content.ContextCompat
import com.gyro.app.core.speed.LocationEvent
import com.gyro.app.core.speed.LocationSample
import com.gyro.app.core.speed.LocationSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Foreground GNSS updates from the platform [LocationManager].
 *
 * The GPS provider is used directly because speed must come from the satellite receiver's
 * Doppler measurement anyway; this avoids a Google Play services dependency and works on
 * devices without it. Devices with no GPS fall back to the platform fused provider, whose fixes
 * may or may not carry speed (handled downstream as "searching").
 */
class AndroidLocationSource(private val context: Context) : LocationSource {
    private val locationManager: LocationManager = checkNotNull(context.getSystemService(LocationManager::class.java))

    private val provider: String? = run {
        val providers = locationManager.allProviders
        val hasGps = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
        when {
            hasGps && LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            LocationManager.FUSED_PROVIDER in providers -> LocationManager.FUSED_PROVIDER
            else -> null
        }
    }

    override val isAvailable: Boolean get() = provider != null

    @SuppressLint("MissingPermission") // Checked explicitly below; SecurityException is also caught.
    override fun updates(): Flow<LocationEvent> = callbackFlow {
        val provider = provider
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (provider == null || !granted) {
            close()
            return@callbackFlow
        }

        trySend(LocationEvent.ProviderStatus(locationManager.isProviderEnabled(provider)))

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(LocationEvent.Fix(location.toSample()))
            }

            override fun onProviderEnabled(provider: String) {
                trySend(LocationEvent.ProviderStatus(true))
            }

            override fun onProviderDisabled(provider: String) {
                trySend(LocationEvent.ProviderStatus(false))
            }
        }

        val request = LocationRequest.Builder(UPDATE_INTERVAL_MILLIS)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MILLIS)
            .build()
        try {
            locationManager.requestLocationUpdates(provider, request, ContextCompat.getMainExecutor(context), listener)
        } catch (e: SecurityException) {
            close()
            return@callbackFlow
        }

        awaitClose { locationManager.removeUpdates(listener) }
    }

    private fun Location.toSample() = LocationSample(
        speedMetersPerSecond = if (hasSpeed()) speed else null,
        speedAccuracyMetersPerSecond = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
        horizontalAccuracyMeters = if (hasAccuracy()) accuracy else null,
        elapsedRealtimeNanos = elapsedRealtimeNanos,
        bearingDegrees = if (hasBearing()) bearing else null,
        bearingAccuracyDegrees = if (hasBearingAccuracy()) bearingAccuracyDegrees else null,
    )

    private companion object {
        const val UPDATE_INTERVAL_MILLIS = 1_000L
        const val MIN_UPDATE_INTERVAL_MILLIS = 500L
    }
}
