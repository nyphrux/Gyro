package com.gyro.app.core.compass

import com.gyro.app.core.math.AngleMath
import com.gyro.app.core.math.ScreenRotation
import com.gyro.app.core.prefs.NorthReference
import kotlinx.coroutines.flow.Flow
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

class CompassTest {

    /**
     * Builds a device→world matrix (row-major, rows = East/North/Up) from where the device's
     * X, Y and Z axes point in world coordinates.
     */
    private fun matrix(x: FloatArray, y: FloatArray, z: FloatArray) = floatArrayOf(
        x[0], y[0], z[0],
        x[1], y[1], z[1],
        x[2], y[2], z[2],
    )

    /** Phone lying flat, top of the phone pointing at [heading] degrees. */
    private fun flat(heading: Float): FloatArray {
        val h = AngleMath.toRadians(heading)
        val y = floatArrayOf(sin(h), cos(h), 0f) // forward
        val x = floatArrayOf(cos(h), -sin(h), 0f) // right
        return matrix(x, y, floatArrayOf(0f, 0f, 1f))
    }

    /**
     * Phone held upright in front of the user, back camera pointing at [heading], with its top
     * leaning away from the user by [tiltFromVertical] (screen tipping up towards the face).
     */
    private fun upright(heading: Float, tiltFromVertical: Float = 0f): FloatArray {
        val h = AngleMath.toRadians(heading)
        val t = AngleMath.toRadians(tiltFromVertical)
        val forward = floatArrayOf(sin(h), cos(h), 0f)
        val right = floatArrayOf(cos(h), -sin(h), 0f)
        // Screen-up leans forward by t; the screen normal (right × up) points at the user and upwards.
        val y = floatArrayOf(forward[0] * sin(t), forward[1] * sin(t), cos(t))
        val z = floatArrayOf(-forward[0] * cos(t), -forward[1] * cos(t), sin(t))
        return matrix(right, y, z)
    }

    @Test
    fun heading_flatPhone() {
        for (h in listOf(0f, 45f, 90f, 180f, 271f, 359f)) {
            assertEquals(h, CompassMath.heading(flat(h), ScreenRotation.ROTATION_0)!!, 1e-3f, "flat $h")
        }
    }

    @Test
    fun heading_uprightPhoneUsesCameraDirection() {
        for (h in listOf(0f, 90f, 200f)) {
            assertEquals(h, CompassMath.heading(upright(h), ScreenRotation.ROTATION_0)!!, 1e-3f, "upright $h")
            // Partly tilted back is the awkward case for Euler azimuth; ours is unaffected.
            assertEquals(h, CompassMath.heading(upright(h, 40f), ScreenRotation.ROTATION_0)!!, 1e-3f, "tilted $h")
        }
    }

    @Test
    fun heading_followsScreenRotation() {
        // Device top points north, but the screen is in landscape (device +X is screen-up).
        // Device +X points east here, so the user is looking east.
        assertEquals(90f, CompassMath.heading(flat(0f), ScreenRotation.ROTATION_90)!!, 1e-3f)
        assertEquals(270f, CompassMath.heading(flat(0f), ScreenRotation.ROTATION_270)!!, 1e-3f)
        assertEquals(180f, CompassMath.heading(flat(0f), ScreenRotation.ROTATION_180)!!, 1e-3f)
    }

    @Test
    fun heading_nullWhenPointingStraightUp() {
        // Not a physical rotation: it only exercises the guard for a vertical pointing direction.
        val degenerate = matrix(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, 1f))
        assertNull(CompassMath.heading(degenerate, ScreenRotation.ROTATION_0))
    }

    @Test
    fun displayedHeading_appliesDeclinationWithWrap() {
        val east = DeclinationResult.Available(3f)
        assertEquals(2f, displayedHeading(359f, NorthReference.TRUE, east), 1e-4f)
        assertEquals(359f, displayedHeading(359f, NorthReference.MAGNETIC, east), 1e-4f)
        // No declination known yet: fall back to magnetic rather than guess.
        assertEquals(10f, displayedHeading(10f, NorthReference.TRUE, DeclinationResult.PermissionRequired))
        assertEquals(358f, displayedHeading(1f, NorthReference.TRUE, DeclinationResult.Available(-3f)), 1e-4f)
    }

    @Test
    fun uiState_prioritisesInterferenceThenAccuracy() {
        val base = CompassSignal(hasHeading = true, accuracy = SensorAccuracy.HIGH, interference = false, fieldStrengthMicroTesla = 48)
        fun state(s: CompassSignal?) = CompassUiState.from(CompassAvailability.ROTATION_VECTOR, s, NorthReference.MAGNETIC, null)
        assertEquals(CalibrationStatus.GOOD, state(base).calibration)
        assertEquals(CalibrationStatus.NEEDS_CALIBRATION, state(base.copy(accuracy = SensorAccuracy.LOW)).calibration)
        assertEquals(CalibrationStatus.NEEDS_CALIBRATION, state(base.copy(accuracy = SensorAccuracy.UNRELIABLE)).calibration)
        assertEquals(CalibrationStatus.INTERFERENCE, state(base.copy(accuracy = SensorAccuracy.LOW, interference = true)).calibration)
        assertFalse(state(null).hasHeading)
    }

    @Test
    fun uiState_trueNorthOnlyWhenDeclinationKnown() {
        fun state(ref: NorthReference, d: DeclinationResult?) = CompassUiState.from(CompassAvailability.ROTATION_VECTOR, null, ref, d)
        assertTrue(state(NorthReference.TRUE, DeclinationResult.Available(1f)).showsTrueNorth)
        assertFalse(state(NorthReference.TRUE, DeclinationResult.Loading).showsTrueNorth)
        assertFalse(state(NorthReference.MAGNETIC, DeclinationResult.Available(1f)).showsTrueNorth)
        assertNull(state(NorthReference.MAGNETIC, DeclinationResult.Available(1f)).declination)
    }

    @Test
    fun frame_detectsMagneticInterference() {
        assertFalse(CompassFrame(0f, SensorAccuracy.HIGH, 50f).magneticInterference)
        assertTrue(CompassFrame(0f, SensorAccuracy.HIGH, 140f).magneticInterference)
        assertTrue(CompassFrame(0f, SensorAccuracy.HIGH, 8f).magneticInterference)
        assertFalse(CompassFrame(0f, SensorAccuracy.HIGH, null).magneticInterference)
    }

    private class FakeCompassSource(
        override val availability: CompassAvailability,
        private val events: List<CompassSensorEvent>,
    ) : CompassSensorSource {
        override fun events(): Flow<CompassSensorEvent> = flowOf(*events.toTypedArray())
    }

    @Test
    fun model_emitsFilteredHeadingAndStatus() = runBlocking {
        val ms = 1_000_000L
        val events = buildList {
            add(CompassSensorEvent.Accuracy(SensorAccuracy.LOW))
            add(CompassSensorEvent.MagneticField(45f))
            for (i in 0 until 60) add(CompassSensorEvent.Orientation(flat(350f), i * 20 * ms, ScreenRotation.ROTATION_0))
            add(CompassSensorEvent.Accuracy(SensorAccuracy.HIGH))
        }
        val frames = CompassModel(FakeCompassSource(CompassAvailability.ROTATION_VECTOR, events)).frames().toList()
        assertNull(frames.first().magneticHeading)
        val last = frames.last()
        assertEquals(350f, last.magneticHeading!!, 0.01f)
        assertEquals(SensorAccuracy.HIGH, last.accuracy)
        assertEquals(45f, last.fieldStrengthMicroTesla)
    }

    @Test
    fun model_unavailableEmitsNothing() = runBlocking {
        val frames = CompassModel(FakeCompassSource(CompassAvailability.UNAVAILABLE, emptyList())).frames().toList()
        assertTrue(frames.isEmpty())
    }
}
