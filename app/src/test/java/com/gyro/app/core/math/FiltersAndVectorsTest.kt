package com.gyro.app.core.math

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FiltersAndVectorsTest {

    private val ms = 1_000_000L

    @Test
    fun angleFilter_firstSamplePassesThrough() {
        val filter = AngleLowPassFilter(0.1f)
        assertEquals(42f, filter.update(42f, 0L))
    }

    @Test
    fun angleFilter_averagesAcrossNorthNotSouth() {
        val filter = AngleLowPassFilter(timeConstantSeconds = 0.1f, catchUpDegrees = 0f)
        filter.update(355f, 0L)
        var t = 0L
        repeat(50) {
            t += 20 * ms
            val out = filter.update(if (it % 2 == 0) 5f else 355f, t)
            // Readings either side of north must stay near north, never near 180°.
            assertTrue(out > 340f || out < 20f, "filtered heading drifted to $out")
        }
    }

    @Test
    fun angleFilter_convergesToSteadyInput() {
        val filter = AngleLowPassFilter(0.08f)
        filter.update(0f, 0L)
        var t = 0L
        var out = 0f
        repeat(100) {
            t += 20 * ms
            out = filter.update(120f, t)
        }
        assertEquals(120f, out, 0.01f)
    }

    @Test
    fun angleFilter_catchUpReducesLagForLargeTurns() {
        val plain = AngleLowPassFilter(0.2f, catchUpDegrees = 0f)
        val adaptive = AngleLowPassFilter(0.2f, catchUpDegrees = 25f)
        plain.update(0f, 0L)
        adaptive.update(0f, 0L)
        val p = plain.update(90f, 20 * ms)
        val a = adaptive.update(90f, 20 * ms)
        assertTrue(a > p, "adaptive ($a) should follow a 90° turn faster than plain ($p)")
    }

    @Test
    fun vectorFilter_isTimeBased() {
        val filter = VectorLowPassFilter(timeConstantSeconds = 1f)
        filter.update(Vec3.ZERO, 0L)
        val out = filter.update(Vec3(0f, 0f, 10f), 1_000 * ms) // dt == tau → alpha = 0.5
        assertEquals(5f, out.z, 1e-4f)
    }

    @Test
    fun rotationBetween_mapsSourceOntoTarget() {
        val cases = listOf(
            Vec3(0.1f, 0.2f, 0.97f) to Vec3.Z,
            Vec3(1f, 0f, 0f) to Vec3(0f, 1f, 0f),
            Vec3(0.3f, -0.8f, 0.2f) to -Vec3.Y,
            Vec3(0f, 0f, 1f) to Vec3(0f, 0f, 1f), // parallel
            Vec3(0f, 0f, 1f) to Vec3(0f, 0f, -1f), // anti-parallel
            Vec3(1f, 0f, 0f) to Vec3(-1f, 0f, 0f), // anti-parallel, different helper axis
        )
        for ((from, to) in cases) {
            val result = Matrix3.rotationBetween(from, to) * from.normalized()
            val expected = to.normalized()
            assertEquals(expected.x, result.x, 1e-5f, "x for $from→$to")
            assertEquals(expected.y, result.y, 1e-5f, "y for $from→$to")
            assertEquals(expected.z, result.z, 1e-5f, "z for $from→$to")
        }
    }

    @Test
    fun rotationBetween_preservesLength() {
        val r = Matrix3.rotationBetween(Vec3(0.2f, 0.1f, 1f), Vec3.Z)
        val v = Vec3(3f, -4f, 12f)
        assertEquals(v.length, (r * v).length, 1e-4f)
    }

    @Test
    fun screenRotation_mapsAxes() {
        // Landscape, device turned counter-clockwise: device +X is screen up.
        assertVec(Vec3(0f, 1f, 0f), ScreenRotation.ROTATION_90.toScreen(Vec3.X))
        // ...and device +Y now points to the screen's left.
        assertVec(Vec3(-1f, 0f, 0f), ScreenRotation.ROTATION_90.toScreen(Vec3.Y))
        assertVec(Vec3(1f, 0f, 0f), ScreenRotation.ROTATION_270.toScreen(Vec3.Y))
        assertVec(Vec3(0f, -1f, 0f), ScreenRotation.ROTATION_180.toScreen(Vec3.Y))
        assertEquals(ScreenRotation.ROTATION_270, ScreenRotation.fromSurfaceRotation(3))
        // Screen axes stay orthonormal and right-handed for every rotation.
        for (r in ScreenRotation.entries) {
            assertVec(Vec3.Z, r.screenRight cross r.screenUp, "right × up must be the screen normal for $r")
        }
    }

    @Test
    fun nearestAxis_picksDominantSignedAxis() {
        assertEquals(Vec3.Z, Vec3(0.1f, 0.2f, 0.9f).nearestAxis())
        assertEquals(-Vec3.Y, Vec3(0.1f, -0.9f, 0.3f).nearestAxis())
        assertEquals(-Vec3.X, Vec3(-0.7f, 0.6f, 0.1f).nearestAxis())
    }

    /** Compares numerically, so 0.0 and -0.0 are equal (data-class equals treats them as different). */
    private fun assertVec(expected: Vec3, actual: Vec3, message: String? = null) {
        assertEquals(expected.x, actual.x, 1e-6f, message)
        assertEquals(expected.y, actual.y, 1e-6f, message)
        assertEquals(expected.z, actual.z, 1e-6f, message)
    }
}
