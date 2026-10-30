package com.gyro.app.core.tilt

import com.gyro.app.core.math.AngleMath
import kotlin.math.abs
import kotlin.math.tan

/**
 * How an inclination is shown. Builders and ramp specifications use gradient (%) or a rise:run
 * ratio ("1:12") rather than degrees, so the tilt meter can show any of the three.
 */
enum class SlopeUnit {
    /** Angle from level, in degrees. */
    DEGREES,

    /** Gradient: rise over run × 100. 45° is 100 %. */
    PERCENT,

    /** Rise to run, written 1:n. 45° is 1:1; level is 1:∞. */
    RATIO,
    ;

    companion object {
        fun fromName(name: String?): SlopeUnit? = entries.firstOrNull { it.name == name }
    }
}

object Slope {
    /** Below this the run is effectively infinite: shown as level rather than as 1:100000. */
    private const val MIN_RATIO_DEGREES = 0.05f

    /** At or beyond this the gradient is effectively infinite (vertical). */
    private const val MAX_GRADIENT_DEGREES = 89.9f

    /** Signed gradient in percent, or null when the slope is (near) vertical. */
    fun percent(degrees: Float): Float? {
        if (abs(degrees) >= MAX_GRADIENT_DEGREES) return null
        return tan(AngleMath.toRadians(degrees)) * 100f
    }

    /**
     * The run for a rise of 1 (the n in "1:n"), always positive; null when (near) level, where
     * the run is unbounded, or vertical, where it is zero.
     */
    fun ratioRun(degrees: Float): Float? {
        val magnitude = abs(degrees)
        if (magnitude < MIN_RATIO_DEGREES || magnitude >= MAX_GRADIENT_DEGREES) return null
        return 1f / tan(AngleMath.toRadians(magnitude))
    }
}
