package com.gyro.app.core.speed

import kotlin.math.max

/**
 * A running trip, like a car's trip computer.
 *
 * @property distanceMeters distance travelled while the speedometer was open.
 * @property movingSeconds time spent moving (stops don't count, so the average is a moving average).
 */
data class TripStats(
    val distanceMeters: Double = 0.0,
    val movingSeconds: Double = 0.0,
) {
    /** Average moving speed, or null until there has been enough motion to average. */
    val averageMetersPerSecond: Float?
        get() = if (movingSeconds >= MIN_SECONDS_FOR_AVERAGE) (distanceMeters / movingSeconds).toFloat() else null

    val isEmpty: Boolean get() = distanceMeters == 0.0 && movingSeconds == 0.0

    operator fun plus(other: TripStats) =
        TripStats(distanceMeters + other.distanceMeters, movingSeconds + other.movingSeconds)

    private companion object {
        const val MIN_SECONDS_FOR_AVERAGE = 5.0
    }
}

/** What survives between sessions: the trip and the top speed. */
data class TripRecord(
    val trip: TripStats = TripStats(),
    val topSpeedMetersPerSecond: Float = 0f,
)

/** Persists the trip so it carries on across app restarts. */
interface TripRepository {
    suspend fun load(): TripRecord

    suspend fun save(record: TripRecord)
}

/**
 * Integrates GNSS (Doppler) speed over time into distance. This is steadier than summing the
 * distance between successive positions, which drifts at rest and cuts corners when moving.
 *
 * Only consecutive fixes no more than [maxGapSeconds] apart are joined: across a signal loss
 * nothing is invented, so the trip slightly under-counts rather than guessing.
 */
class TripMeter(private val maxGapSeconds: Double = 5.0) {
    private var lastNanos: Long? = null
    private var lastSpeed = 0f

    /** Adds the stretch since the previous measurement; returns the increment (possibly empty). */
    fun add(measurement: SpeedMeasurement): TripStats {
        val previousNanos = lastNanos
        val previousSpeed = lastSpeed
        val speed = if (measurement.isStationary) 0f else measurement.speedMetersPerSecond.coerceAtLeast(0f)
        lastNanos = measurement.elapsedRealtimeNanos
        lastSpeed = speed
        if (previousNanos == null) return TripStats()

        val dt = (measurement.elapsedRealtimeNanos - previousNanos) / 1e9
        if (dt <= 0.0 || dt > maxGapSeconds) return TripStats()

        // Trapezoid rule: the average of the two speeds over the interval.
        val distance = (previousSpeed + speed) / 2.0 * dt
        val moving = if (max(previousSpeed, speed) > 0f) dt else 0.0
        return TripStats(distance, moving)
    }

    /** The signal dropped: the next measurement starts a new stretch. */
    fun interrupt() {
        lastNanos = null
        lastSpeed = 0f
    }
}

/**
 * Over-speed detection with hysteresis: once over the limit, the speed must drop clearly below
 * it before the alert re-arms, so hovering around the limit does not beep every second.
 */
class SpeedAlertDetector(
    private val marginFraction: Float = 0.03f,
    private val minMarginMetersPerSecond: Float = 0.5f,
) {
    var isOver: Boolean = false
        private set

    /**
     * @param speedMetersPerSecond current reading, or null when there is none (the state is held,
     *   so a brief signal drop while speeding does not re-trigger the alert).
     * @param limitMetersPerSecond the alert threshold, or null when the alert is off.
     */
    fun update(speedMetersPerSecond: Float?, limitMetersPerSecond: Float?): Boolean {
        if (limitMetersPerSecond == null || limitMetersPerSecond <= 0f) {
            isOver = false
            return false
        }
        if (speedMetersPerSecond == null) return isOver
        val margin = max(minMarginMetersPerSecond, limitMetersPerSecond * marginFraction)
        isOver = if (isOver) {
            speedMetersPerSecond > limitMetersPerSecond - margin
        } else {
            speedMetersPerSecond > limitMetersPerSecond
        }
        return isOver
    }
}
