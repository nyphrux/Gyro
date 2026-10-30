package com.gyro.app.platform.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.gyro.app.core.compass.GeoPoint
import com.gyro.app.core.compass.MagneticModel
import com.gyro.app.core.compass.PositionProvider
import com.gyro.app.core.compass.PositionResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Magnetic declination from the World Magnetic Model built into Android ([GeomagneticField]).
 */
object AndroidMagneticModel : MagneticModel {
    override fun declinationDegrees(point: GeoPoint, epochMillis: Long): Float =
        GeomagneticField(point.latitude.toFloat(), point.longitude.toFloat(), point.altitudeMeters, epochMillis).declination
}

/**
 * A one-off approximate position for the compass (true-north declination and the sun marker).
 *
 * Both vary slowly with position, so an approximate location is plenty: we use the most recent
 * last-known fix if it is reasonably fresh, otherwise request a single current fix.
 * No continuous location updates are started for the compass.
 */
class AndroidPositionProvider(private val context: Context) : PositionProvider {
    private val locationManager: LocationManager = checkNotNull(context.getSystemService(LocationManager::class.java))

    override suspend fun position(): PositionResult {
        if (!hasAnyLocationPermission()) return PositionResult.PermissionRequired
        val location = freshLastKnownLocation() ?: currentLocation() ?: lastKnownLocation()
            ?: return PositionResult.Unavailable
        return PositionResult.Available(
            GeoPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                altitudeMeters = if (location.hasAltitude()) location.altitude.toFloat() else 0f,
            ),
        )
    }

    private fun hasAnyLocationPermission(): Boolean =
        listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun freshLastKnownLocation(): Location? = lastKnownLocation()?.takeIf {
        val ageMillis = (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000
        ageMillis < MAX_LAST_KNOWN_AGE_MILLIS
    }

    @SuppressLint("MissingPermission") // Guarded by hasAnyLocationPermission().
    private fun lastKnownLocation(): Location? = try {
        locationManager.getProviders(true)
            .mapNotNull { locationManager.getLastKnownLocation(it) }
            .maxByOrNull { it.elapsedRealtimeNanos }
    } catch (e: SecurityException) {
        null
    }

    @SuppressLint("MissingPermission") // Guarded by hasAnyLocationPermission().
    private suspend fun currentLocation(): Location? {
        val enabled = locationManager.getProviders(true)
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { it in enabled } ?: return null
        return withTimeoutOrNull(CURRENT_LOCATION_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    locationManager.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                } catch (e: SecurityException) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        }
    }

    private companion object {
        const val MAX_LAST_KNOWN_AGE_MILLIS = 6 * 60 * 60 * 1000L
        const val CURRENT_LOCATION_TIMEOUT_MILLIS = 15_000L
    }
}
