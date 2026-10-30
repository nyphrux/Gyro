package com.gyro.app.core.speed

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the speedometer state machine end to end with a scripted location source.
 * Uses real (short) time for the staleness timeout so it needs only kotlinx-coroutines-core.
 */
class SpeedometerModelTest {

    private class FakeLocationSource(override val isAvailable: Boolean = true) : LocationSource {
        val events = Channel<LocationEvent>(Channel.UNLIMITED)
        var activeCollectors = 0
            private set
        var totalSubscriptions = 0
            private set

        override fun updates(): Flow<LocationEvent> = events.receiveAsFlow()
            .onStart { activeCollectors++; totalSubscriptions++ }
            .onCompletion { activeCollectors-- }
    }

    private fun fix(speed: Float, seconds: Double, accuracy: Float = 0.2f) =
        LocationEvent.Fix(LocationSample(speed, accuracy, 4f, (seconds * 1e9).toLong()))

    private suspend fun Flow<SpeedometerUiState>.awaitStatus(status: SpeedStatus): SpeedometerUiState =
        withTimeout(2_000) { first { it.status == status } }

    @Test
    fun noLocationUpdatesWithoutPermission() = runBlocking {
        val source = FakeLocationSource()
        val model = SpeedometerModel(source)
        val access = MutableStateFlow(LocationAccess.DENIED)
        val state = model.uiState(access, MutableStateFlow(SpeedUnit.KILOMETRES_PER_HOUR)).awaitStatus(SpeedStatus.PERMISSION_REQUIRED)
        assertNull(state.speedMetersPerSecond)
        assertEquals(0, source.totalSubscriptions, "GPS must not start without permission")
    }

    @Test
    fun grantedFlowsThroughSearchingMovingAndSignalLost() = runBlocking {
        val source = FakeLocationSource()
        val model = SpeedometerModel(source, staleTimeoutMillis = 150)
        val latest = MutableStateFlow<SpeedometerUiState?>(null)
        // One long-lived collector, as the ViewModel would have.
        val job = launch {
            model.uiState(MutableStateFlow(LocationAccess.GRANTED), MutableStateFlow(SpeedUnit.KILOMETRES_PER_HOUR))
                .collect { latest.value = it }
        }
        suspend fun await(status: SpeedStatus) = withTimeout(2_000) { latest.first { it?.status == status }!! }

        source.events.send(LocationEvent.ProviderStatus(true))
        await(SpeedStatus.SEARCHING)

        source.events.send(fix(10f, 1.0))
        val moving = await(SpeedStatus.MOVING)
        assertEquals(36f, moving.displaySpeed!!, 1e-3f)
        assertEquals(10f, moving.topSpeedMetersPerSecond, 1e-4f)

        // No further fixes: after the timeout the reading is withdrawn, not frozen.
        val lost = await(SpeedStatus.SIGNAL_LOST)
        assertNull(lost.speedMetersPerSecond)
        assertEquals(10f, lost.topSpeedMetersPerSecond, 1e-4f)

        // The gauge range is held while there is no reading (36 km/h needs the 60 scale).
        assertEquals(60f, lost.gaugeScale)

        // A fresh fix recovers.
        source.events.send(fix(8f, 9.0))
        val recovered = await(SpeedStatus.MOVING)
        assertEquals(8f, recovered.speedMetersPerSecond!!, 1e-4f)
        assertEquals(60f, recovered.gaugeScale) // 28.8 km/h is not yet "well inside" 40
        job.cancel()
    }

    @Test
    fun locationServicesSwitchedOffIsReported() = runBlocking {
        val source = FakeLocationSource()
        val model = SpeedometerModel(source)
        val states = model.uiState(MutableStateFlow(LocationAccess.GRANTED), MutableStateFlow(SpeedUnit.METRES_PER_SECOND))
        source.events.send(LocationEvent.ProviderStatus(false))
        val state = states.awaitStatus(SpeedStatus.LOCATION_DISABLED)
        assertNull(state.speedMetersPerSecond)
    }

    @Test
    fun stationaryAndLowAccuracyDoNotSetTopSpeed() = runBlocking {
        val source = FakeLocationSource()
        val model = SpeedometerModel(source)
        val states = model.uiState(MutableStateFlow(LocationAccess.GRANTED), MutableStateFlow(SpeedUnit.METRES_PER_SECOND))
        source.events.send(fix(0.2f, 1.0))
        val still = states.awaitStatus(SpeedStatus.STATIONARY)
        assertEquals(0f, still.speedMetersPerSecond)

        source.events.send(fix(30f, 2.0, accuracy = 6f)) // an implausible, low-accuracy spike
        val spike = states.awaitStatus(SpeedStatus.MOVING)
        assertTrue(spike.isLowAccuracy)
        assertEquals(0f, model.topSpeedMetersPerSecond.value, "low-accuracy readings must not set a record")
    }

    @Test
    fun revokingPermissionStopsUpdates() = runBlocking {
        val source = FakeLocationSource()
        val model = SpeedometerModel(source)
        val access = MutableStateFlow(LocationAccess.GRANTED)
        val states = model.uiState(access, MutableStateFlow(SpeedUnit.KILOMETRES_PER_HOUR))
        val job = launch { states.collect { } }
        withTimeout(2_000) { while (source.activeCollectors == 0) delay(5) }
        access.value = LocationAccess.APPROXIMATE_ONLY
        withTimeout(2_000) { while (source.activeCollectors != 0) delay(5) }
        job.cancel()
        assertEquals(SpeedStatus.PRECISE_LOCATION_REQUIRED, states.first().status)
    }

    @Test
    fun unitChangeReformatsWithoutRestartingGps() = runBlocking {
        val source = FakeLocationSource()
        val model = SpeedometerModel(source)
        val unit = MutableStateFlow(SpeedUnit.KILOMETRES_PER_HOUR)
        val states = model.uiState(MutableStateFlow(LocationAccess.GRANTED), unit)
        val job = launch { states.collect { } }
        source.events.send(fix(10f, 1.0))
        withTimeout(2_000) { while (source.activeCollectors == 0) delay(5) }
        unit.value = SpeedUnit.MILES_PER_HOUR
        delay(50)
        job.cancel()
        assertEquals(1, source.totalSubscriptions)
    }

    @Test
    fun resetTopSpeed() = runBlocking {
        val source = FakeLocationSource()
        val model = SpeedometerModel(source)
        val states = model.uiState(MutableStateFlow(LocationAccess.GRANTED), MutableStateFlow(SpeedUnit.KILOMETRES_PER_HOUR))
        source.events.send(fix(12f, 1.0))
        states.awaitStatus(SpeedStatus.MOVING)
        model.resetTopSpeed()
        assertEquals(0f, model.topSpeedMetersPerSecond.value)
    }
}
