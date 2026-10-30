package com.gyro.app.core.speed

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeedTest {

    @Test
    fun units_convertFromMetresPerSecond() {
        assertEquals(36f, SpeedUnit.KILOMETRES_PER_HOUR.fromMetersPerSecond(10f), 1e-4f)
        assertEquals(22.369363f, SpeedUnit.MILES_PER_HOUR.fromMetersPerSecond(10f), 1e-4f)
        assertEquals(10f, SpeedUnit.METRES_PER_SECOND.fromMetersPerSecond(10f), 1e-6f)
        assertEquals(19.438445f, SpeedUnit.KNOTS.fromMetersPerSecond(10f), 1e-4f)
        // Exact definitions: 100 km/h, 60 mph, 1 knot.
        assertEquals(27.777779f, SpeedUnit.KILOMETRES_PER_HOUR.toMetersPerSecond(100f), 1e-4f)
        assertEquals(26.8224f, SpeedUnit.MILES_PER_HOUR.toMetersPerSecond(60f), 1e-4f)
        assertEquals(0.514444f, SpeedUnit.KNOTS.toMetersPerSecond(1f), 1e-5f)
    }

    @Test
    fun units_roundTrip() {
        for (unit in SpeedUnit.entries) {
            assertEquals(12.34f, unit.toMetersPerSecond(unit.fromMetersPerSecond(12.34f)), 1e-4f, unit.name)
        }
    }

    @Test
    fun units_defaultByRegion() {
        assertEquals(SpeedUnit.MILES_PER_HOUR, SpeedUnit.defaultForRegion("GB"))
        assertEquals(SpeedUnit.MILES_PER_HOUR, SpeedUnit.defaultForRegion("us"))
        assertEquals(SpeedUnit.KILOMETRES_PER_HOUR, SpeedUnit.defaultForRegion("DE"))
        assertEquals(SpeedUnit.KILOMETRES_PER_HOUR, SpeedUnit.defaultForRegion(null))
        assertEquals(SpeedUnit.KNOTS, SpeedUnit.fromName("KNOTS"))
        assertNull(SpeedUnit.fromName("bogus"))
    }

    private fun sample(speed: Float?, seconds: Double, accuracy: Float? = 0.3f, horizontal: Float? = 5f) =
        LocationSample(speed, accuracy, horizontal, (seconds * 1e9).toLong())

    @Test
    fun processor_rejectsMissingOrInvalidSpeed() {
        val p = SpeedProcessor()
        assertNull(p.process(sample(null, 1.0)))
        assertNull(p.process(sample(Float.NaN, 2.0)))
        assertNull(p.process(sample(-1f, 3.0)))
    }

    @Test
    fun processor_snapsGnssNoiseAtRestToStationary() {
        val p = SpeedProcessor()
        val m = p.process(sample(0.3f, 1.0))!!
        assertTrue(m.isStationary)
        assertEquals(0f, m.speedMetersPerSecond)
    }

    @Test
    fun processor_smoothsButFollowsChanges() {
        val p = SpeedProcessor()
        p.process(sample(10f, 1.0))
        val next = p.process(sample(11f, 2.0))!!
        assertTrue(next.speedMetersPerSecond > 10.5f && next.speedMetersPerSecond < 11f, "got ${next.speedMetersPerSecond}")
        // A big jump (e.g. hard braking) is passed through without smoothing.
        val brake = p.process(sample(3f, 3.0))!!
        assertEquals(3f, brake.speedMetersPerSecond)
    }

    @Test
    fun processor_flagsLowAccuracy() {
        val p = SpeedProcessor()
        assertTrue(p.process(sample(5f, 1.0, accuracy = 3f))!!.isLowAccuracy)
        assertTrue(p.process(sample(5f, 2.0, horizontal = 80f))!!.isLowAccuracy)
        assertFalse(p.process(sample(5f, 3.0))!!.isLowAccuracy)
    }

    @Test
    fun gaugeScale_growsImmediatelyAndShrinksWithHysteresis() {
        assertEquals(10f, GaugeScale.select(null, 0f))
        assertEquals(10f, GaugeScale.select(null, 8f))
        assertEquals(20f, GaugeScale.select(10f, 9f)) // 9 > 0.85 × 10
        assertEquals(150f, GaugeScale.select(40f, 120f))
        // Dropping from 100 to 30: candidate 40 but 30 is not well inside it yet.
        assertEquals(100f, GaugeScale.select(100f, 30f))
        assertEquals(40f, GaugeScale.select(100f, 25f))
        // Beyond the table (aircraft): extends in round steps.
        assertEquals(500f, GaugeScale.select(300f, 400f))
        assertEquals(1000f, GaugeScale.select(500f, 800f))
    }

    @Test
    fun gaugeScale_tickStepDividesScale() {
        val scales = listOf(10f, 20f, 40f, 60f, 100f, 150f, 200f, 300f, 500f, 1000f, 1500f, 2000f)
        for (s in scales) {
            val step = GaugeScale.majorTickStep(s)
            val intervals = s / step
            assertEquals(Math.round(intervals).toFloat(), intervals, 1e-3f, "scale $s step $step")
            assertTrue(intervals in 3f..8f, "scale $s gives $intervals intervals")
        }
    }

    @Test
    fun uiState_mapsPermissionAndSignalStates() {
        fun state(supported: Boolean = true, access: LocationAccess = LocationAccess.GRANTED, signal: SpeedSignal? = null) =
            SpeedometerUiState.from(supported, access, signal, SpeedUnit.KILOMETRES_PER_HOUR, 0f)

        assertEquals(SpeedStatus.UNSUPPORTED, state(supported = false).status)
        assertEquals(SpeedStatus.CHECKING_PERMISSION, state(access = LocationAccess.UNKNOWN).status)
        assertEquals(SpeedStatus.PERMISSION_REQUIRED, state(access = LocationAccess.DENIED).status)
        assertEquals(SpeedStatus.PERMISSION_BLOCKED, state(access = LocationAccess.PERMANENTLY_DENIED).status)
        assertEquals(SpeedStatus.PRECISE_LOCATION_REQUIRED, state(access = LocationAccess.APPROXIMATE_ONLY).status)
        assertEquals(SpeedStatus.SEARCHING, state(signal = SpeedSignal.Searching).status)
        assertEquals(SpeedStatus.LOCATION_DISABLED, state(signal = SpeedSignal.LocationDisabled).status)
        assertEquals(SpeedStatus.SIGNAL_LOST, state(signal = SpeedSignal.SignalLost).status)
        assertNull(state(signal = SpeedSignal.SignalLost).speedMetersPerSecond)

        val moving = state(signal = SpeedSignal.Measured(SpeedMeasurement(10f, 0.2f, isStationary = false, isLowAccuracy = false)))
        assertEquals(SpeedStatus.MOVING, moving.status)
        assertEquals(36f, moving.displaySpeed!!, 1e-4f)
        assertNotNull(moving.displayAccuracy)

        // A measured value must never leak into a state where permission is missing.
        val denied = state(
            access = LocationAccess.DENIED,
            signal = SpeedSignal.Measured(SpeedMeasurement(10f, 0.2f, isStationary = false, isLowAccuracy = false)),
        )
        assertNull(denied.speedMetersPerSecond)
    }
}
