package com.gyro.app.core.tilt

import com.gyro.app.core.math.AngleMath
import com.gyro.app.core.math.Matrix3
import com.gyro.app.core.math.Vec3
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

data class SurfaceAngles(val roll: Float, val pitch: Float, val total: Float)

data class EdgeAngles(val rotation: Float, val orientation: Int, val deviation: Float)

object TiltMath {
    /** Mode switch thresholds on |up·screenNormal| (cosine of the angle from flat). */
    private const val ENTER_SURFACE = 0.8f // within ~37° of flat
    private const val LEAVE_SURFACE = 0.55f // beyond ~57° from flat
    private const val INITIAL_SURFACE = 0.7f

    /**
     * Inclination of each screen axis above the horizontal, from an up-pointing gravity vector
     * in screen axes. These are exactly what a two-axis spirit level reads, and stay
     * well-defined at any orientation (no Euler-angle gimbal lock).
     */
    fun surfaceAngles(upScreen: Vec3): SurfaceAngles {
        val n = upScreen.normalized()
        val roll = AngleMath.toDegrees(atan2(n.x, sqrt(n.y * n.y + n.z * n.z)))
        val pitch = AngleMath.toDegrees(atan2(n.y, sqrt(n.x * n.x + n.z * n.z)))
        val total = AngleMath.toDegrees(acos(abs(n.z).coerceIn(0f, 1f)))
        return SurfaceAngles(roll, pitch, total)
    }

    /** Rotation of the screen within its own plane, for edge (spirit-level) use. */
    fun edgeAngles(upScreen: Vec3): EdgeAngles {
        val n = upScreen.normalized()
        // Up vector leaning towards −x means the device was turned clockwise.
        val rotation = wrapSigned(-AngleMath.toDegrees(atan2(n.x, n.y)))
        val nearest = round(rotation / 90f) * 90f
        val deviation = rotation - nearest
        val orientation = AngleMath.normalize(nearest).let { round(it).toInt() % 360 }
        return EdgeAngles(rotation, orientation, deviation)
    }

    /** Picks surface or edge mode with hysteresis so it does not flicker near 45°. */
    fun detectMode(upScreen: Vec3, previous: TiltMode?): TiltMode {
        val flatness = abs(upScreen.normalized().z)
        return when (previous) {
            TiltMode.SURFACE -> if (flatness < LEAVE_SURFACE) TiltMode.EDGE else TiltMode.SURFACE
            TiltMode.EDGE -> if (flatness > ENTER_SURFACE) TiltMode.SURFACE else TiltMode.EDGE
            null -> if (flatness >= INITIAL_SURFACE) TiltMode.SURFACE else TiltMode.EDGE
        }
    }

    /**
     * Radial position of the level bubble for a tilt, as a fraction of its maximum travel.
     * A power curve (< 1) gives fine resolution near level while still showing ±[fullScale]°;
     * the dial's reference rings are drawn with the same function, so the scale stays honest.
     */
    fun bubbleFraction(degrees: Float, fullScale: Float = BUBBLE_FULL_SCALE, exponent: Float = BUBBLE_EXPONENT): Float {
        val magnitude = (abs(degrees) / fullScale).coerceIn(0f, 1f)
        return magnitude.pow(exponent)
    }

    const val BUBBLE_FULL_SCALE = 15f
    private const val BUBBLE_EXPONENT = 0.6f

    private fun wrapSigned(degrees: Float): Float {
        val n = AngleMath.normalize(degrees)
        return if (n >= 180f) n - 360f else n
    }
}

/**
 * A user-defined zero. "Set zero" means *treat the current orientation as perfectly level*:
 * we store the gravity vector at that moment and compute the minimal rotation that maps it
 * onto the device axis it is closest to (flat, portrait-upright, landscape...). Every later
 * gravity sample is rotated by the same correction before angles are computed, so readings
 * are genuinely relative to the stored orientation in both surface and edge modes.
 */
class TiltReference private constructor(
    val referenceGravity: Vec3,
    private val correction: Matrix3,
) {
    fun apply(deviceGravity: Vec3): Vec3 = correction * deviceGravity

    companion object {
        fun from(referenceGravity: Vec3): TiltReference? {
            val n = referenceGravity.normalized()
            if (n == Vec3.ZERO) return null
            return TiltReference(referenceGravity, Matrix3.rotationBetween(n, n.nearestAxis()))
        }
    }
}

/** Level detection with hysteresis so the "Level" state (and its haptic) does not chatter. */
class LevelDetector(
    private val enterDegrees: Float = 0.35f,
    private val exitDegrees: Float = 0.7f,
) {
    var isLevel: Boolean = false
        private set

    fun update(errorDegrees: Float): Boolean {
        isLevel = if (isLevel) errorDegrees <= exitDegrees else errorDegrees <= enterDegrees
        return isLevel
    }

    fun reset() {
        isLevel = false
    }
}
