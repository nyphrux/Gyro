package com.gyro.app.core.speed

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripTest {

    private fun m(speed: Float, seconds: Double, stationary: Boolean = false) =
        SpeedMeasurement(speed, 0.2f, isStationary = stationary, isLowAccuracy = false, elapsedRealtimeNanos = (seconds * 1e9).toLong())

    @Test
    fun meter_integratesSpeedOverTime() {
        val meter = TripMeter()
        var trip = TripStats()
        trip += meter.add(m(10f, 0.0)) // first fix: nothing to join yet
        trip += meter.add(m(10f, 1.0))
        trip += meter.add(m(20f, 2.0)) // trapezoid: (10 + 20) / 2 × 1 s
        assertEquals(25.0, trip.distanceMeters, 1e-6)
        assertEquals(2.0, trip.movingSeconds, 1e-9)
    }

    @Test
    fun meter_stopsDoNotCountAsMovingTime() {
        val meter = TripMeter()
        var trip = TripStats()
        trip += meter.add(m(0f, 0.0, stationary = true))
        trip += meter.add(m(0f, 1.0, stationary = true))
        trip += meter.add(m(0f, 2.0, stationary = true))
        assertEquals(0.0, trip.distanceMeters)
        assertEquals(0.0, trip.movingSeconds)
        assertNull(trip.averageMetersPerSecond)
    }

    @Test
    fun meter_doesNotJoinAcrossGapsOrInterruptions() {
        val meter = TripMeter(maxGapSeconds = 5.0)
        var trip = TripStats()
        trip += meter.add(m(10f, 0.0))
        trip += meter.add(m(10f, 10.0)) // 10 s gap: not joined
        assertEquals(0.0, trip.distanceMeters)
        trip += meter.add(m(10f, 11.0))
        assertEquals(10.0, trip.distanceMeters, 1e-6)
        meter.interrupt()
        trip += meter.add(m(10f, 12.0))
        assertEquals(10.0, trip.distanceMeters, 1e-6)
    }

    @Test
    fun averageIsDistanceOverMovingTime() {
        assertEquals(12.5f, TripStats(distanceMeters = 125.0, movingSeconds = 10.0).averageMetersPerSecond!!, 1e-5f)
        assertNull(TripStats(distanceMeters = 5.0, movingSeconds = 1.0).averageMetersPerSecond) // too little to average
    }

    @Test
    fun alert_firesAboveLimitAndRearmsBelowMargin() {
        val alert = SpeedAlertDetector(marginFraction = 0.03f, minMarginMetersPerSecond = 0.5f)
        val limit = 30f // margin = 0.9 m/s
        assertFalse(alert.update(29.9f, limit))
        assertFalse(alert.update(30f, limit)) // at the limit is not over it
        assertTrue(alert.update(30.2f, limit))
        assertTrue(alert.update(29.5f, limit)) // hovering just under: still over (no re-trigger)
        assertTrue(alert.update(null, limit)) // no reading: state held
        assertFalse(alert.update(29.0f, limit)) // clearly below: re-armed
        assertTrue(alert.update(31f, limit))
        assertFalse(alert.update(31f, null)) // alert switched off
    }

    @Test
    fun distanceUnits() {
        assertEquals(1.0, SpeedUnit.KILOMETRES_PER_HOUR.distanceFromMeters(1000.0), 1e-12)
        assertEquals(1.0, SpeedUnit.MILES_PER_HOUR.distanceFromMeters(1609.344), 1e-12)
        assertEquals("km", SpeedUnit.METRES_PER_SECOND.distanceSymbol)
    }

    @Test
    fun processor_passesCourseOnlyWhenMovingAndAccurate() {
        fun course(speed: Float, bearing: Float?, accuracy: Float? = 5f): Float? =
            SpeedProcessor().process(LocationSample(speed, 0.2f, 4f, 1L, bearing, accuracy))?.courseDegrees
        assertEquals(87f, course(10f, 87f))
        assertNull(course(0.2f, 87f)) // stationary
        assertNull(course(0.8f, 87f)) // walking-pace noise
        assertNull(course(10f, 87f, accuracy = 45f)) // poor bearing accuracy
        assertNull(course(10f, null))
        assertEquals(87f, course(10f, 87f, accuracy = null)) // accuracy unknown: trusted
    }

    private class ScriptedSource : LocationSource {
        val events = Channel<LocationEvent>(Channel.UNLIMITED)
        override val isAvailable = true
        override fun updates(): Flow<LocationEvent> = events.receiveAsFlow()
    }

    private fun fix(speed: Float, seconds: Double, bearing: Float? = null) =
        LocationEvent.Fix(LocationSample(speed, 0.2f, 4f, (seconds * 1e9).toLong(), bearing, 5f))

    @Test
    fun model_accumulatesTripRestoresAndResets() = runBlocking {
        val source = ScriptedSource()
        val model = SpeedometerModel(source)
        val latest = MutableStateFlow<SpeedometerUiState?>(null)
        val job = launch {
            model.uiState(MutableStateFlow(LocationAccess.GRANTED), MutableStateFlow(SpeedUnit.KILOMETRES_PER_HOUR)).collect { latest.value = it }
        }
        source.events.send(fix(10f, 1.0, bearing = 90f))
        source.events.send(fix(10f, 2.0, bearing = 90f))
        source.events.send(fix(10f, 3.0, bearing = 90f))
        val moving = withTimeout(2_000) { latest.first { (it?.trip?.distanceMeters ?: 0.0) >= 20.0 }!! }
        assertEquals(20.0, moving.trip.distanceMeters, 1e-3)
        assertEquals(90f, moving.courseDegrees)
        assertEquals(0.02, moving.displayDistance, 1e-6) // 20 m in km

        // A saved trip loading late is added to what was measured meanwhile, not overwritten.
        model.restore(TripRecord(TripStats(1000.0, 100.0), topSpeedMetersPerSecond = 40f))
        val restored = withTimeout(2_000) { latest.first { (it?.trip?.distanceMeters ?: 0.0) > 1000.0 }!! }
        assertEquals(1020.0, restored.trip.distanceMeters, 1e-3)
        assertEquals(40f, restored.topSpeedMetersPerSecond)
        model.restore(TripRecord(TripStats(5000.0, 1.0))) // only the first restore counts
        assertEquals(1020.0, model.trip.value.distanceMeters, 1e-3)

        model.resetTrip()
        withTimeout(2_000) { latest.first { it?.trip?.isEmpty == true } }
        assertEquals(40f, model.topSpeedMetersPerSecond.value, "trip reset leaves the top speed alone")
        job.cancel()
    }

    @Test
    fun model_flagsOverSpeed() = runBlocking {
        val source = ScriptedSource()
        val model = SpeedometerModel(source)
        val alert = MutableStateFlow<Float?>(20f)
        val latest = MutableStateFlow<SpeedometerUiState?>(null)
        val job = launch {
            model.uiState(MutableStateFlow(LocationAccess.GRANTED), MutableStateFlow(SpeedUnit.KILOMETRES_PER_HOUR), alert).collect { latest.value = it }
        }
        source.events.send(fix(25f, 1.0))
        val over = withTimeout(2_000) { latest.first { it?.status == SpeedStatus.MOVING }!! }
        assertTrue(over.isOverAlert)
        assertEquals(72f, over.displayAlert!!, 1e-3f)
        alert.value = null
        withTimeout(2_000) { latest.first { it != null && it.alertMetersPerSecond == null && !it.isOverAlert } }
        job.cancel()
    }
}
