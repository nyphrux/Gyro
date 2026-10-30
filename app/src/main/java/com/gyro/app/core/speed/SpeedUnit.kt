package com.gyro.app.core.speed

/**
 * Display units for speed. Internally everything is metres per second, exactly as
 * `android.location.Location.getSpeed()` reports it.
 */
enum class SpeedUnit(val symbol: String, private val perMetrePerSecond: Float, val readoutDecimals: Int) {
    KILOMETRES_PER_HOUR("km/h", 3.6f, 0),
    MILES_PER_HOUR("mph", 1f / 0.44704f, 0), // 1 mph is exactly 0.44704 m/s
    METRES_PER_SECOND("m/s", 1f, 1),
    KNOTS("kn", 3600f / 1852f, 0), // 1 knot is exactly 1852 m per hour
    ;

    fun fromMetersPerSecond(metersPerSecond: Float): Float = metersPerSecond * perMetrePerSecond

    fun toMetersPerSecond(value: Float): Float = value / perMetrePerSecond

    companion object {
        /** Regions where road speed is posted in miles per hour. */
        private val MPH_REGIONS = setOf("US", "GB", "LR", "MM", "PR", "VG", "VI", "GU", "AS", "MP", "KY", "FK")

        fun defaultForRegion(countryCode: String?): SpeedUnit =
            if (countryCode?.uppercase() in MPH_REGIONS) MILES_PER_HOUR else KILOMETRES_PER_HOUR

        fun fromName(name: String?): SpeedUnit? = entries.firstOrNull { it.name == name }
    }
}
