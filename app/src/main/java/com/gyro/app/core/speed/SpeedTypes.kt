package com.gyro.app.core.speed

import kotlinx.coroutines.flow.Flow

/** The parts of an `android.location.Location` the speedometer needs, free of Android types. */
data class LocationSample(
    /** GNSS (Doppler) speed, or null when the fix carried no speed. */
    val speedMetersPerSecond: Float?,
    /** 68% confidence speed accuracy, when the provider reports one. */
    val speedAccuracyMetersPerSecond: Float?,
    val horizontalAccuracyMeters: Float?,
    val elapsedRealtimeNanos: Long,
    /** Direction of travel from GNSS, degrees clockwise from true north, when the fix carries one. */
    val bearingDegrees: Float? = null,
    /** 68% confidence bearing accuracy in degrees, when reported. */
    val bearingAccuracyDegrees: Float? = null,
)

sealed interface LocationEvent {
    /** The location provider was switched on or off (system location toggle). */
    data class ProviderStatus(val enabled: Boolean) : LocationEvent

    data class Fix(val sample: LocationSample) : LocationEvent
}

/** Source of location fixes. Collecting [updates] starts updates; cancelling stops them. */
interface LocationSource {
    /** False when the device has no suitable location provider at all. */
    val isAvailable: Boolean

    /** Must only be collected while precise location permission is granted. */
    fun updates(): Flow<LocationEvent>
}

/** The app's view of the location permission, derived in the UI layer. */
enum class LocationAccess {
    /** Not evaluated yet (first frame). */
    UNKNOWN,

    /** No location permission; asking is still possible. */
    DENIED,

    /** Denied with "don't ask again" semantics; only system settings can change it. */
    PERMANENTLY_DENIED,

    /** Only approximate location granted: no usable GPS speed. */
    APPROXIMATE_ONLY,

    /** Precise location granted. */
    GRANTED,
}

data class SpeedMeasurement(
    val speedMetersPerSecond: Float,
    val accuracyMetersPerSecond: Float?,
    val isStationary: Boolean,
    val isLowAccuracy: Boolean,
    /** GNSS direction of travel (true north), or null when not moving fast enough to trust it. */
    val courseDegrees: Float? = null,
    /** When the underlying fix was taken (monotonic clock), for integrating distance. */
    val elapsedRealtimeNanos: Long = 0L,
)

sealed interface SpeedSignal {
    data object Searching : SpeedSignal
    data object LocationDisabled : SpeedSignal
    data object SignalLost : SpeedSignal
    data class Measured(val measurement: SpeedMeasurement) : SpeedSignal
}

enum class SpeedStatus {
    UNSUPPORTED,
    CHECKING_PERMISSION,
    PERMISSION_REQUIRED,
    PERMISSION_BLOCKED,
    PRECISE_LOCATION_REQUIRED,
    LOCATION_DISABLED,
    SEARCHING,
    SIGNAL_LOST,
    STATIONARY,
    MOVING,
}

data class SpeedometerUiState(
    val status: SpeedStatus,
    /** Null whenever there is no valid, current reading: never an invented value. */
    val speedMetersPerSecond: Float? = null,
    val accuracyMetersPerSecond: Float? = null,
    val isLowAccuracy: Boolean = false,
    val topSpeedMetersPerSecond: Float = 0f,
    val unit: SpeedUnit = SpeedUnit.KILOMETRES_PER_HOUR,
    /** Full-scale value of the gauge in [unit]; auto-ranged by [GaugeScale]. */
    val gaugeScale: Float = GaugeScale.minimum,
    /** GNSS direction of travel (true north) while moving; null otherwise. */
    val courseDegrees: Float? = null,
    val trip: TripStats = TripStats(),
    /** Speed alert threshold, or null when the alert is off. */
    val alertMetersPerSecond: Float? = null,
    /** True while the current speed is above [alertMetersPerSecond] (with hysteresis). */
    val isOverAlert: Boolean = false,
) {
    val displaySpeed: Float? get() = speedMetersPerSecond?.let(unit::fromMetersPerSecond)
    val displayTopSpeed: Float get() = unit.fromMetersPerSecond(topSpeedMetersPerSecond)
    val displayAccuracy: Float? get() = accuracyMetersPerSecond?.let(unit::fromMetersPerSecond)
    val displayAlert: Float? get() = alertMetersPerSecond?.let(unit::fromMetersPerSecond)
    val displayDistance: Double get() = unit.distanceFromMeters(trip.distanceMeters)
    val displayAverageSpeed: Float? get() = trip.averageMetersPerSecond?.let(unit::fromMetersPerSecond)

    /** True for the states where the location pipeline is running. */
    val isTracking: Boolean
        get() = status in setOf(SpeedStatus.SEARCHING, SpeedStatus.SIGNAL_LOST, SpeedStatus.STATIONARY, SpeedStatus.MOVING)

    companion object {
        fun from(
            supported: Boolean,
            access: LocationAccess,
            signal: SpeedSignal?,
            unit: SpeedUnit,
            topSpeedMetersPerSecond: Float,
            trip: TripStats = TripStats(),
            alertMetersPerSecond: Float? = null,
        ): SpeedometerUiState {
            val status = when {
                !supported -> SpeedStatus.UNSUPPORTED
                access == LocationAccess.UNKNOWN -> SpeedStatus.CHECKING_PERMISSION
                access == LocationAccess.DENIED -> SpeedStatus.PERMISSION_REQUIRED
                access == LocationAccess.PERMANENTLY_DENIED -> SpeedStatus.PERMISSION_BLOCKED
                access == LocationAccess.APPROXIMATE_ONLY -> SpeedStatus.PRECISE_LOCATION_REQUIRED
                signal == null || signal == SpeedSignal.Searching -> SpeedStatus.SEARCHING
                signal == SpeedSignal.LocationDisabled -> SpeedStatus.LOCATION_DISABLED
                signal == SpeedSignal.SignalLost -> SpeedStatus.SIGNAL_LOST
                signal is SpeedSignal.Measured && signal.measurement.isStationary -> SpeedStatus.STATIONARY
                else -> SpeedStatus.MOVING
            }
            val measurement = (signal as? SpeedSignal.Measured)?.measurement
                ?.takeIf { status == SpeedStatus.MOVING || status == SpeedStatus.STATIONARY }
            return SpeedometerUiState(
                status = status,
                speedMetersPerSecond = measurement?.speedMetersPerSecond,
                accuracyMetersPerSecond = measurement?.accuracyMetersPerSecond,
                isLowAccuracy = measurement?.isLowAccuracy ?: false,
                topSpeedMetersPerSecond = topSpeedMetersPerSecond,
                unit = unit,
                courseDegrees = measurement?.courseDegrees?.takeIf { status == SpeedStatus.MOVING },
                trip = trip,
                alertMetersPerSecond = alertMetersPerSecond,
            )
        }
    }
}
