package com.gyro.app.core.compass

import com.gyro.app.core.math.AngleMath
import com.gyro.app.core.math.ScreenRotation
import kotlin.math.atan2
import kotlin.math.hypot

object CompassMath {
    /** Below this horizontal component the pointing direction is too vertical to be meaningful. */
    private const val MIN_HORIZONTAL = 0.2f

    /**
     * Magnetic heading of the direction the user is "looking along" with the phone.
     *
     * The classic `getOrientation()` azimuth breaks down (gimbal lock) when the phone is held
     * upright. Instead we use the device vector `screenUp − screenNormal`: lying flat its
     * horizontal part is the top of the screen; held upright it is the direction the back
     * camera faces; in between both terms point the same way, so the horizontal projection
     * never vanishes across the whole 0–90° range of natural holding angles.
     *
     * @param rotationMatrix device→world, row-major, world axes (East, North, Up).
     * @return heading in `[0, 360)`, or null when the phone points (almost) straight up or down.
     */
    fun heading(rotationMatrix: FloatArray, screenRotation: ScreenRotation): Float? {
        require(rotationMatrix.size >= 9) { "Expected a 3x3 rotation matrix" }
        val up = screenRotation.screenUp
        val fx = up.x
        val fy = up.y
        val fz = up.z - 1f
        val r = rotationMatrix
        val east = r[0] * fx + r[1] * fy + r[2] * fz
        val north = r[3] * fx + r[4] * fy + r[5] * fz
        if (hypot(east, north) < MIN_HORIZONTAL) return null
        return AngleMath.normalize(AngleMath.toDegrees(atan2(east, north)))
    }
}
