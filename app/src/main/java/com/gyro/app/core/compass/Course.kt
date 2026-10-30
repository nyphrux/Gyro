package com.gyro.app.core.compass

import com.gyro.app.core.math.AngleMath
import kotlin.math.abs

/**
 * Where a locked bearing is relative to the current heading.
 *
 * @property targetDegrees the locked bearing, `[0, 360)`, in the same north reference as the heading.
 * @property offsetDegrees how far to turn to face it, `(-180, 180]`: positive = turn right (clockwise).
 * @property onCourse true while the heading is within the on-course band (with hysteresis).
 */
data class CourseGuidance(
    val targetDegrees: Float,
    val offsetDegrees: Float,
    val onCourse: Boolean,
)

/**
 * Bearing lock ("follow this bearing"). Hysteresis keeps the on-course state, and the haptic
 * that marks entering it, from chattering while the user wobbles around the line.
 */
class CourseTracker(
    private val enterDegrees: Float = 3f,
    private val exitDegrees: Float = 6f,
) {
    var onCourse: Boolean = false
        private set

    fun update(heading: Float, target: Float): CourseGuidance {
        val offset = AngleMath.shortestDelta(heading, target)
        val distance = abs(offset)
        onCourse = if (onCourse) distance <= exitDegrees else distance <= enterDegrees
        return CourseGuidance(AngleMath.normalize(target), offset, onCourse)
    }

    fun reset() {
        onCourse = false
    }
}
