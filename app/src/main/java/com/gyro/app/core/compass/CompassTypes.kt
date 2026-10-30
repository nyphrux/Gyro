package com.gyro.app.core.compass

import com.gyro.app.core.math.AngleMath
import com.gyro.app.core.math.ScreenRotation
import com.gyro.app.core.prefs.NorthReference
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

/** Which sensor pipeline drives the compass, best first. */
enum class CompassAvailability {
    /** Fused accelerometer + magnetometer + gyroscope (TYPE_ROTATION_VECTOR). */
    ROTATION_VECTOR,

    /** Accelerometer + magnetometer fused by the platform, no gyroscope. */
    GEOMAGNETIC_ROTATION_VECTOR,

    /** Raw accelerometer + magnetometer combined in-app. */
    ACCELEROMETER_MAGNETOMETER,

    /** No magnetometer: a compass is impossible on this device. */
    UNAVAILABLE,
}

/** Mirrors `SensorManager.SENSOR_STATUS_*` for the magnetometer. */
enum class SensorAccuracy {
    UNRELIABLE, LOW, MEDIUM, HIGH;

    val needsCalibration: Boolean get() = this == UNRELIABLE || this == LOW
}

sealed interface CompassSensorEvent {
    /**
     * Device→world rotation matrix (row-major, world axes East/North/Up) as produced by
     * `SensorManager.getRotationMatrixFromVector`, plus the screen rotation at that moment.
     */
    class Orientation(
        val rotationMatrix: FloatArray,
        val timestampNanos: Long,
        val screenRotation: ScreenRotation,
    ) : CompassSensorEvent

    data class Accuracy(val accuracy: SensorAccuracy) : CompassSensorEvent

    /** Total magnetic field strength, used to detect local interference. */
    data class MagneticField(val strengthMicroTesla: Float) : CompassSensorEvent
}

interface CompassSensorSource {
    val availability: CompassAvailability

    /** Registers sensors on collection, unregisters on cancellation. */
    fun events(): Flow<CompassSensorEvent>
}

/** Earth's surface field is roughly 25–65 µT; well outside that means nearby metal or magnets. */
object EarthMagneticField {
    const val MIN_MICROTESLA = 20f
    const val MAX_MICROTESLA = 70f
}

/** One processed compass update. [magneticHeading] is null until the first usable sample. */
data class CompassFrame(
    val magneticHeading: Float?,
    val accuracy: SensorAccuracy?,
    val fieldStrengthMicroTesla: Float?,
) {
    val magneticInterference: Boolean
        get() = fieldStrengthMicroTesla?.let {
            it < EarthMagneticField.MIN_MICROTESLA || it > EarthMagneticField.MAX_MICROTESLA
        } ?: false

    /** The slow-changing part of a frame, so the UI only recomposes when status changes. */
    fun toSignal() = CompassSignal(
        hasHeading = magneticHeading != null,
        accuracy = accuracy,
        interference = magneticInterference,
        fieldStrengthMicroTesla = fieldStrengthMicroTesla?.roundToInt(),
    )
}

data class CompassSignal(
    val hasHeading: Boolean,
    val accuracy: SensorAccuracy?,
    val interference: Boolean,
    val fieldStrengthMicroTesla: Int?,
)

sealed interface DeclinationResult {
    data object Loading : DeclinationResult
    data object PermissionRequired : DeclinationResult
    data object LocationUnavailable : DeclinationResult

    /** Magnetic declination in degrees, east positive (true = magnetic + declination). */
    data class Available(val degrees: Float) : DeclinationResult
}

/** A position on Earth. Approximate is fine for everything the compass derives from it. */
data class GeoPoint(val latitude: Double, val longitude: Double, val altitudeMeters: Float = 0f)

/** The outcome of asking for the user's approximate position. */
sealed interface PositionResult {
    data object Loading : PositionResult
    data object PermissionRequired : PositionResult
    data object Unavailable : PositionResult
    data class Available(val point: GeoPoint) : PositionResult
}

/** Supplies a one-off approximate position (no continuous updates). */
fun interface PositionProvider {
    suspend fun position(): PositionResult
}

/** Magnetic declination at a place and time (the platform's World Magnetic Model on Android). */
fun interface MagneticModel {
    /** Degrees, east positive (true = magnetic + declination). */
    fun declinationDegrees(point: GeoPoint, epochMillis: Long): Float
}

/** Declination for the compass, derived from the position lookup. Only needed for true north. */
fun declinationResult(
    northReference: NorthReference,
    position: PositionResult?,
    epochMillis: Long,
    model: MagneticModel,
): DeclinationResult? {
    if (northReference != NorthReference.TRUE) return null
    return when (position) {
        null, PositionResult.Loading -> DeclinationResult.Loading
        PositionResult.PermissionRequired -> DeclinationResult.PermissionRequired
        PositionResult.Unavailable -> DeclinationResult.LocationUnavailable
        is PositionResult.Available -> DeclinationResult.Available(model.declinationDegrees(position.point, epochMillis))
    }
}

/** What the compass shows about the sun. */
sealed interface SunState {
    /** The sun marker is switched off in settings. */
    data object Hidden : SunState
    data object Locating : SunState
    data object PermissionRequired : SunState
    data object Unavailable : SunState

    /**
     * @property bearingDegrees the sun's bearing in the compass's *displayed* reference (true or
     *   magnetic), so it can be drawn on the same card as the heading.
     */
    data class Visible(val bearingDegrees: Float, val elevationDegrees: Float) : SunState {
        val isUp: Boolean get() = elevationDegrees > -0.833f
    }

    companion object {
        /**
         * The solar azimuth is a true bearing. When the card is showing magnetic north it is
         * converted using the declination at the same position, which is always known here.
         */
        fun from(
            showSun: Boolean,
            position: PositionResult?,
            displaysTrueNorth: Boolean,
            epochMillis: Long,
            model: MagneticModel,
        ): SunState {
            if (!showSun) return Hidden
            return when (position) {
                null, PositionResult.Loading -> Locating
                PositionResult.PermissionRequired -> PermissionRequired
                PositionResult.Unavailable -> Unavailable
                is PositionResult.Available -> {
                    val point = position.point
                    val sun = SolarPosition.compute(point.latitude, point.longitude, epochMillis)
                    val bearing = if (displaysTrueNorth) {
                        sun.azimuthDegrees
                    } else {
                        AngleMath.normalize(sun.azimuthDegrees - model.declinationDegrees(point, epochMillis))
                    }
                    Visible(bearing, sun.elevationDegrees)
                }
            }
        }
    }
}

enum class CalibrationStatus { GOOD, NEEDS_CALIBRATION, INTERFERENCE }

data class CompassUiState(
    val availability: CompassAvailability,
    val hasHeading: Boolean = false,
    val calibration: CalibrationStatus = CalibrationStatus.GOOD,
    val accuracy: SensorAccuracy? = null,
    val fieldStrengthMicroTesla: Int? = null,
    val northReference: NorthReference = NorthReference.MAGNETIC,
    /** Only populated when [northReference] is TRUE. */
    val declination: DeclinationResult? = null,
) {
    val isAvailable: Boolean get() = availability != CompassAvailability.UNAVAILABLE

    /** True only when the displayed heading really is referenced to true north. */
    val showsTrueNorth: Boolean
        get() = northReference == NorthReference.TRUE && declination is DeclinationResult.Available

    companion object {
        fun from(
            availability: CompassAvailability,
            signal: CompassSignal?,
            northReference: NorthReference,
            declination: DeclinationResult?,
        ) = CompassUiState(
            availability = availability,
            hasHeading = signal?.hasHeading == true,
            calibration = when {
                signal == null -> CalibrationStatus.GOOD
                signal.interference -> CalibrationStatus.INTERFERENCE
                signal.accuracy?.needsCalibration == true -> CalibrationStatus.NEEDS_CALIBRATION
                else -> CalibrationStatus.GOOD
            },
            accuracy = signal?.accuracy,
            fieldStrengthMicroTesla = signal?.fieldStrengthMicroTesla,
            northReference = northReference,
            declination = if (northReference == NorthReference.TRUE) declination else null,
        )
    }
}

/**
 * Converts a magnetic heading into the heading shown to the user. Falls back to magnetic
 * (and the UI says so) whenever declination is not known.
 */
fun displayedHeading(
    magneticHeading: Float,
    northReference: NorthReference,
    declination: DeclinationResult?,
): Float = if (northReference == NorthReference.TRUE && declination is DeclinationResult.Available) {
    AngleMath.normalize(magneticHeading + declination.degrees)
} else {
    magneticHeading
}
