package com.gyro.app.core.tilt

import com.gyro.app.core.math.AngleMath
import com.gyro.app.core.math.ScreenRotation
import com.gyro.app.core.math.Vec3
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TiltTest {

    private val g = 9.81f

    /** Up-pointing gravity for a phone lying flat with its right edge raised by [roll] degrees. */
    private fun rightEdgeRaised(roll: Float): Vec3 {
        val r = AngleMath.toRadians(roll)
        return Vec3(sin(r), 0f, cos(r)) * g
    }

    /** Up-pointing gravity for a phone lying flat with its top edge raised by [pitch] degrees. */
    private fun topEdgeRaised(pitch: Float): Vec3 {
        val p = AngleMath.toRadians(pitch)
        return Vec3(0f, sin(p), cos(p)) * g
    }

    /** Upright phone (screen vertical) turned clockwise in its plane by [degrees]. */
    private fun uprightRotatedClockwise(degrees: Float): Vec3 {
        val a = AngleMath.toRadians(degrees)
        return Vec3(-sin(a), cos(a), 0f) * g
    }

    @Test
    fun surfaceAngles_flatIsZero() {
        val a = TiltMath.surfaceAngles(Vec3(0f, 0f, g))
        assertEquals(0f, a.roll, 1e-4f)
        assertEquals(0f, a.pitch, 1e-4f)
        assertEquals(0f, a.total, 1e-2f)
    }

    @Test
    fun surfaceAngles_signsMatchRaisedEdge() {
        assertEquals(10f, TiltMath.surfaceAngles(rightEdgeRaised(10f)).roll, 1e-3f)
        assertEquals(-4.5f, TiltMath.surfaceAngles(rightEdgeRaised(-4.5f)).roll, 1e-3f)
        assertEquals(7f, TiltMath.surfaceAngles(topEdgeRaised(7f)).pitch, 1e-3f)
        assertEquals(7f, TiltMath.surfaceAngles(topEdgeRaised(7f)).total, 1e-2f)
    }

    @Test
    fun edgeAngles_measureInPlaneRotation() {
        val upright = TiltMath.edgeAngles(uprightRotatedClockwise(0f))
        assertEquals(0, upright.orientation)
        assertEquals(0f, upright.deviation, 1e-3f)

        val tilted = TiltMath.edgeAngles(uprightRotatedClockwise(3f))
        assertEquals(3f, tilted.rotation, 1e-3f)
        assertEquals(3f, tilted.deviation, 1e-3f)

        // Standing on its long edge (landscape), slightly off level.
        val landscape = TiltMath.edgeAngles(uprightRotatedClockwise(88f))
        assertEquals(90, landscape.orientation)
        assertEquals(-2f, landscape.deviation, 1e-3f)

        val otherWay = TiltMath.edgeAngles(uprightRotatedClockwise(-91f))
        assertEquals(270, otherWay.orientation)
        assertEquals(-1f, otherWay.deviation, 1e-3f)

        val upsideDown = TiltMath.edgeAngles(uprightRotatedClockwise(179f))
        assertEquals(180, upsideDown.orientation)
        assertEquals(-1f, upsideDown.deviation, 1e-3f)
    }

    @Test
    fun detectMode_hasHysteresis() {
        val flat = Vec3(0f, 0f, 1f)
        val fortyFive = Vec3(0f, 0.7071f, 0.7071f)
        val upright = Vec3(0f, 1f, 0f)
        assertEquals(TiltMode.SURFACE, TiltMath.detectMode(flat, null))
        assertEquals(TiltMode.EDGE, TiltMath.detectMode(upright, null))
        // At 45° we keep whatever mode we were in.
        assertEquals(TiltMode.SURFACE, TiltMath.detectMode(fortyFive, TiltMode.SURFACE))
        assertEquals(TiltMode.EDGE, TiltMath.detectMode(fortyFive, TiltMode.EDGE))
    }

    @Test
    fun reference_zeroesTheStoredOrientation() {
        val stored = rightEdgeRaised(3f) + Vec3(0f, 0.2f, 0f) // slightly off on both axes
        val ref = TiltReference.from(stored)!!
        val zero = TiltMath.surfaceAngles(ref.apply(stored))
        assertEquals(0f, zero.roll, 1e-3f)
        assertEquals(0f, zero.pitch, 1e-3f)
    }

    @Test
    fun reference_measuresChangesRelativeToZero() {
        val ref = TiltReference.from(rightEdgeRaised(5f))!!
        // Raise the right edge a further 2°: relative reading is +2°.
        val relative = TiltMath.surfaceAngles(ref.apply(rightEdgeRaised(7f)))
        assertEquals(2f, relative.roll, 1e-3f)
        // Back to true level reads -5° relative to the zero.
        assertEquals(-5f, TiltMath.surfaceAngles(ref.apply(rightEdgeRaised(0f))).roll, 1e-3f)
    }

    @Test
    fun reference_worksInEdgeMode() {
        val ref = TiltReference.from(uprightRotatedClockwise(1.5f))!!
        val atZero = TiltMath.edgeAngles(ref.apply(uprightRotatedClockwise(1.5f)))
        assertEquals(0f, atZero.deviation, 1e-3f)
        assertEquals(1f, TiltMath.edgeAngles(ref.apply(uprightRotatedClockwise(2.5f))).deviation, 1e-3f)
    }

    @Test
    fun reference_rejectsZeroVector() {
        assertNull(TiltReference.from(Vec3.ZERO))
    }

    @Test
    fun bubbleFraction_isMonotonicAndClamped() {
        assertEquals(0f, TiltMath.bubbleFraction(0f))
        assertEquals(1f, TiltMath.bubbleFraction(15f), 1e-6f)
        assertEquals(1f, TiltMath.bubbleFraction(40f), 1e-6f)
        assertEquals(TiltMath.bubbleFraction(3f), TiltMath.bubbleFraction(-3f))
        var previous = -1f
        for (tenths in 0..150) {
            val f = TiltMath.bubbleFraction(tenths / 10f)
            assertTrue(f >= previous)
            previous = f
        }
        // Finer than linear near level: 1° moves the bubble well over 1/15 of its travel.
        assertTrue(TiltMath.bubbleFraction(1f) > 2f / 15f)
    }

    @Test
    fun levelDetector_usesHysteresis() {
        val d = LevelDetector(enterDegrees = 0.35f, exitDegrees = 0.7f)
        assertFalse(d.update(0.5f))
        assertTrue(d.update(0.3f))
        assertTrue(d.update(0.6f)) // still level inside the exit band
        assertFalse(d.update(0.8f))
    }

    private class FakeTiltSource(
        override val availability: TiltAvailability,
        private val samples: List<GravitySample>,
    ) : TiltSensorSource {
        override fun samples(): Flow<GravitySample> = samples.asFlow()
    }

    @Test
    fun model_producesLevelSurfaceReading() = runBlocking {
        val samples = (0 until 30).map { GravitySample(Vec3(0f, 0f, g), it * 20_000_000L, ScreenRotation.ROTATION_0) }
        val readings = TiltModel(FakeTiltSource(TiltAvailability.GRAVITY, samples)).readings(flowOf(null)).toList()
        val last = readings.last()
        assertEquals(TiltMode.SURFACE, last.mode)
        assertTrue(last.isLevel)
        assertFalse(last.isRelativeToReference)
    }

    @Test
    fun model_appliesReferenceAndScreenRotation() = runBlocking {
        val tilted = rightEdgeRaised(4f)
        val samples = (0 until 30).map { GravitySample(tilted, it * 20_000_000L, ScreenRotation.ROTATION_0) }
        val source = FakeTiltSource(TiltAvailability.GRAVITY, samples)
        val zeroed = TiltModel(source).readings(flowOf(tilted)).toList().last()
        assertTrue(zeroed.isRelativeToReference)
        assertEquals(0f, zeroed.rollDegrees, 1e-3f)
        assertTrue(zeroed.isLevel)

        // In landscape (ROTATION_90) the device's right edge is the top of the screen.
        val landscape = (0 until 30).map { GravitySample(tilted, it * 20_000_000L, ScreenRotation.ROTATION_90) }
        val reading = TiltModel(FakeTiltSource(TiltAvailability.GRAVITY, landscape)).readings(flowOf(null)).toList().last()
        assertEquals(0f, reading.rollDegrees, 1e-3f)
        assertEquals(4f, reading.pitchDegrees, 1e-3f)
    }

    @Test
    fun model_unavailableEmitsNothing() = runBlocking {
        val readings = TiltModel(FakeTiltSource(TiltAvailability.UNAVAILABLE, emptyList())).readings(flowOf(null)).toList()
        assertTrue(readings.isEmpty())
    }
}
