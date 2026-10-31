package com.gyro.app.core.speed

/**
 * Display units for speed. Internally everything is metres per second, exactly as
 * `android.location.Location.getSpeed()` reports it.
 *
 * Each speed unit also names the distance unit that goes with it for trip distance
 * (km with km/h and m/s, miles with mph). A saved unit that no longer exists (knots, in
 * older versions) falls back to the regional default via [fromName].
 */
enum class SpeedUnit(
    val symbol: String,
    private val perMetrePerSecond: Float,
    val readoutDecimals: Int,
    val distanceSymbol: String,
    private val metresPerDistanceUnit: Double,
) {
    KILOMETRES_PER_HOUR("km/h", 3.6f, 0, "km", 1000.0),
    MILES_PER_HOUR("mph", 1f / 0.44704f, 0, "mi", 1609.344), // 1 mph is exactly 0.44704 m/s
    METRES_PER_SECOND("m/s", 1f, 1, "km", 1000.0),
    ;

    fun fromMetersPerSecond(metersPerSecond: Float): Float = metersPerSecond * perMetrePerSecond

    fun toMetersPerSecond(value: Float): Float = value / perMetrePerSecond

    fun distanceFromMeters(meters: Double): Double = meters / metresPerDistanceUnit

    companion object {
        /** Regions where road speed is posted in miles per hour. */
        private val MPH_REGIONS = setOf("US", "GB", "LR", "MM", "PR", "VG", "VI", "GU", "AS", "MP", "KY", "FK")

        fun defaultForRegion(countryCode: String?): SpeedUnit =
            if (countryCode?.uppercase() in MPH_REGIONS) MILES_PER_HOUR else KILOMETRES_PER_HOUR

        fun fromName(name: String?): SpeedUnit? = entries.firstOrNull { it.name == name }
    }
}
