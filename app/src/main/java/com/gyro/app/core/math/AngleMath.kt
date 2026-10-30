package com.gyro.app.core.math

import kotlin.math.PI

/** Angle helpers. All angles are in degrees unless a name says otherwise. */
object AngleMath {

    /** Normalises any angle into `[0, 360)`. */
    fun normalize(degrees: Float): Float {
        if (degrees.isNaN() || degrees.isInfinite()) return degrees
        val remainder = degrees % 360f
        val positive = if (remainder < 0f) remainder + 360f else remainder
        // Float rounding can turn e.g. -1e-7 + 360 into exactly 360.
        return if (positive >= 360f) 0f else positive
    }

    /**
     * Signed shortest rotation from [from] to [to], in `(-180, 180]`.
     * Crossing north is handled: `shortestDelta(350, 10) == 20`.
     */
    fun shortestDelta(from: Float, to: Float): Float {
        val delta = normalize(to - from)
        return if (delta > 180f) delta - 360f else delta
    }

    /**
     * Converts a wrapped angle into a continuous one next to [previousContinuous], so an
     * animation from 359° to 1° moves +2° instead of spinning -358° backwards.
     */
    fun unwrap(previousContinuous: Float, target: Float): Float =
        previousContinuous + shortestDelta(normalize(previousContinuous), target)

    fun toDegrees(radians: Float): Float = (radians * 180.0 / PI).toFloat()

    fun toRadians(degrees: Float): Float = (degrees * PI / 180.0).toFloat()
}

/** The eight compass points, each covering a 45° sector centred on its bearing. */
enum class CardinalDirection(val bearing: Float) {
    N(0f), NE(45f), E(90f), SE(135f), S(180f), SW(225f), W(270f), NW(315f);

    companion object {
        fun fromHeading(heading: Float): CardinalDirection {
            val index = ((AngleMath.normalize(heading) + 22.5f) / 45f).toInt() % entries.size
            return entries[index]
        }
    }
}

/**
 * Reports when a heading moves decisively across one of the four cardinal points (N, E, S, W).
 * A small hysteresis band stops sensor noise around a boundary from firing repeatedly,
 * which matters because every report triggers a haptic tick.
 */
class CardinalDetent(private val hysteresisDegrees: Float = 2f) {
    private var quadrant: Int? = null

    /** @return true when a cardinal point was crossed since the previous update. */
    fun update(heading: Float): Boolean {
        val normalized = AngleMath.normalize(heading)
        if (normalized.isNaN()) return false
        val candidate = (normalized / 90f).toInt().coerceIn(0, 3)
        val current = quadrant
        if (current == null) {
            quadrant = candidate
            return false
        }
        if (candidate == current) return false
        val offsetIntoQuadrant = normalized - candidate * 90f
        val distanceFromBoundary = minOf(offsetIntoQuadrant, 90f - offsetIntoQuadrant)
        if (distanceFromBoundary < hysteresisDegrees) return false
        quadrant = candidate
        return true
    }

    fun reset() {
        quadrant = null
    }
}
