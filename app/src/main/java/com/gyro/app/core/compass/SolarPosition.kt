package com.gyro.app.core.compass

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Where the sun is in the sky.
 *
 * @property azimuthDegrees bearing from true north, clockwise, `[0, 360)`.
 * @property elevationDegrees apparent angle above the horizon (refraction included); negative = below.
 */
data class SunPosition(val azimuthDegrees: Float, val elevationDegrees: Float) {
    /** The upper limb clears the horizon at about −0.833° (refraction plus the sun's radius). */
    val isUp: Boolean get() = elevationDegrees > -0.833f
}

/**
 * Solar position from the NOAA solar calculator equations (after Meeus, "Astronomical
 * Algorithms"). Accurate to well under a degree between 1800 and 2100, far finer than a phone
 * compass can show, and needs nothing but a position and the time.
 */
object SolarPosition {
    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val UNIX_EPOCH_JULIAN_DAY = 2_440_587.5
    private const val J2000 = 2_451_545.0

    /**
     * @param latitude degrees, north positive.
     * @param longitude degrees, east positive.
     * @param epochMillis UTC time, milliseconds since 1970-01-01.
     */
    fun compute(latitude: Double, longitude: Double, epochMillis: Long): SunPosition {
        val julianDay = epochMillis / MILLIS_PER_DAY + UNIX_EPOCH_JULIAN_DAY
        val t = (julianDay - J2000) / 36_525.0 // Julian centuries since J2000.0

        val meanLongitude = mod(280.46646 + t * (36_000.76983 + t * 0.0003032), 360.0)
        val meanAnomaly = 357.52911 + t * (35_999.05029 - 0.0001537 * t)
        val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val m = rad(meanAnomaly)
        val centre = sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * m) * (0.019993 - 0.000101 * t) +
            sin(3 * m) * 0.000289
        val trueLongitude = meanLongitude + centre
        val omega = rad(125.04 - 1934.136 * t)
        val apparentLongitude = trueLongitude - 0.00569 - 0.00478 * sin(omega)

        val meanObliquity = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val obliquity = meanObliquity + 0.00256 * cos(omega)
        val declination = asin(sin(rad(obliquity)) * sin(rad(apparentLongitude)))

        // Equation of time, in minutes.
        val y = tan(rad(obliquity) / 2).let { it * it }
        val l0 = rad(meanLongitude)
        val equationOfTime = 4 * deg(
            y * sin(2 * l0) -
                2 * eccentricity * sin(m) +
                4 * eccentricity * y * sin(m) * cos(2 * l0) -
                0.5 * y * y * sin(4 * l0) -
                1.25 * eccentricity * eccentricity * sin(2 * m),
        )

        val utcMinutes = mod(epochMillis.toDouble(), MILLIS_PER_DAY) / 60_000.0
        val trueSolarMinutes = mod(utcMinutes + equationOfTime + 4 * longitude, 1440.0)
        val hourAngle = if (trueSolarMinutes / 4 < 0) trueSolarMinutes / 4 + 180 else trueSolarMinutes / 4 - 180

        val phi = rad(latitude)
        val cosZenith = (sin(phi) * sin(declination) + cos(phi) * cos(declination) * cos(rad(hourAngle))).coerceIn(-1.0, 1.0)
        val zenith = acos(cosZenith)

        val denominator = cos(phi) * sin(zenith)
        val azimuth = if (abs(denominator) < 1e-9) {
            // At a pole or with the sun overhead the azimuth is undefined; due south/north is as good as any.
            if (latitude >= 0) 180.0 else 0.0
        } else {
            val cosAzimuth = ((sin(phi) * cos(zenith) - sin(declination)) / denominator).coerceIn(-1.0, 1.0)
            val a = deg(acos(cosAzimuth))
            if (hourAngle > 0) mod(a + 180, 360.0) else mod(540 - a, 360.0)
        }

        val elevation = 90 - deg(zenith)
        return SunPosition(
            azimuthDegrees = azimuth.toFloat(),
            elevationDegrees = (elevation + refraction(elevation)).toFloat(),
        )
    }

    /** NOAA's approximate atmospheric refraction, in degrees, for a geometric elevation. */
    private fun refraction(elevation: Double): Double {
        if (elevation > 85) return 0.0
        val te = tan(rad(elevation))
        val arcSeconds = when {
            elevation > 5 -> 58.1 / te - 0.07 / (te * te * te) + 0.000086 / (te * te * te * te * te)
            elevation > -0.575 -> 1735 + elevation * (-518.2 + elevation * (103.4 + elevation * (-12.79 + elevation * 0.711)))
            else -> -20.772 / te
        }
        return arcSeconds / 3600
    }

    private fun rad(degrees: Double) = degrees * PI / 180
    private fun deg(radians: Double) = radians * 180 / PI
    private fun mod(value: Double, by: Double): Double = ((value % by) + by) % by
}
