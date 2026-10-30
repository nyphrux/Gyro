package com.gyro.app.core.math

import kotlin.math.abs
import kotlin.math.sqrt

/** Minimal immutable 3-vector used for gravity and orientation maths. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)

    operator fun times(scale: Float) = Vec3(x * scale, y * scale, z * scale)

    infix fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    infix fun cross(other: Vec3) = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    val length: Float get() = sqrt(x * x + y * y + z * z)

    fun normalized(): Vec3 {
        val l = length
        return if (l < 1e-9f || l.isNaN()) ZERO else Vec3(x / l, y / l, z / l)
    }

    /** The signed unit axis (±X, ±Y or ±Z) this vector is closest to. */
    fun nearestAxis(): Vec3 {
        val ax = abs(x)
        val ay = abs(y)
        val az = abs(z)
        return when {
            ax >= ay && ax >= az -> if (x >= 0f) X else -X
            ay >= az -> if (y >= 0f) Y else -Y
            else -> if (z >= 0f) Z else -Z
        }
    }

    operator fun unaryMinus() = Vec3(-x, -y, -z)

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val X = Vec3(1f, 0f, 0f)
        val Y = Vec3(0f, 1f, 0f)
        val Z = Vec3(0f, 0f, 1f)
    }
}

/** Row-major 3×3 matrix. Only what the orientation maths needs. */
class Matrix3(values: FloatArray) {
    private val m: FloatArray = values.copyOf()

    init {
        require(values.size == 9) { "Matrix3 needs 9 values, got ${values.size}" }
    }

    operator fun get(row: Int, column: Int): Float = m[row * 3 + column]

    operator fun times(v: Vec3) = Vec3(
        m[0] * v.x + m[1] * v.y + m[2] * v.z,
        m[3] * v.x + m[4] * v.y + m[5] * v.z,
        m[6] * v.x + m[7] * v.y + m[8] * v.z,
    )

    companion object {
        val IDENTITY = Matrix3(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))

        /**
         * The minimal rotation taking direction [from] onto direction [to] (Rodrigues' formula).
         * Handles the parallel and anti-parallel cases explicitly.
         */
        fun rotationBetween(from: Vec3, to: Vec3): Matrix3 {
            val a = from.normalized()
            val b = to.normalized()
            if (a == Vec3.ZERO || b == Vec3.ZERO) return IDENTITY
            val v = a cross b
            val c = a dot b
            val s2 = v dot v
            if (s2 < 1e-12f) {
                if (c > 0f) return IDENTITY
                // 180° turn about any axis perpendicular to `a`: R = 2uuᵀ − I.
                val helper = if (abs(a.x) < 0.9f) Vec3.X else Vec3.Y
                val u = (a cross helper).normalized()
                return Matrix3(
                    floatArrayOf(
                        2 * u.x * u.x - 1, 2 * u.x * u.y, 2 * u.x * u.z,
                        2 * u.y * u.x, 2 * u.y * u.y - 1, 2 * u.y * u.z,
                        2 * u.z * u.x, 2 * u.z * u.y, 2 * u.z * u.z - 1,
                    ),
                )
            }
            // R = I + [v]× + [v]×² · (1 − c) / s², with [v]×² = vvᵀ − s²I.
            val k = (1f - c) / s2
            val diagonal = 1f - k * s2
            return Matrix3(
                floatArrayOf(
                    diagonal + k * v.x * v.x, -v.z + k * v.x * v.y, v.y + k * v.x * v.z,
                    v.z + k * v.y * v.x, diagonal + k * v.y * v.y, -v.x + k * v.y * v.z,
                    -v.y + k * v.z * v.x, v.x + k * v.z * v.y, diagonal + k * v.z * v.z,
                ),
            )
        }
    }
}
