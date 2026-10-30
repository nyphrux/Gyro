package com.gyro.app.core.speed

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update

/**
 * Speedometer state machine, independent of Android so it can be unit-tested.
 *
 * Location updates run only while the returned flow is collected *and* precise location is
 * granted; the ViewModel collects it with `WhileSubscribed`, so leaving the screen or
 * backgrounding the app stops GPS.
 */
class SpeedometerModel(private val source: LocationSource, private val staleTimeoutMillis: Long = DEFAULT_STALE_TIMEOUT_MILLIS) {
    private val topSpeed = MutableStateFlow(0f)
    val topSpeedMetersPerSecond: StateFlow<Float> = topSpeed.asStateFlow()

    private val tripStats = MutableStateFlow(TripStats())
    val trip: StateFlow<TripStats> = tripStats.asStateFlow()

    /** The persistable part of the state; emits whenever the trip or top speed changes. */
    val records: Flow<TripRecord> = combine(tripStats, topSpeed) { trip, top -> TripRecord(trip, top) }.distinctUntilChanged()

    private var restored = false

    val isAvailable: Boolean get() = source.isAvailable

    fun resetTopSpeed() {
        topSpeed.value = 0f
    }

    fun resetTrip() {
        tripStats.value = TripStats()
    }

    /**
     * Brings back a saved trip. Anything measured before the saved copy finished loading is
     * added on top rather than overwritten. Only the first call has an effect.
     */
    fun restore(record: TripRecord) {
        if (restored) return
        restored = true
        tripStats.update { record.trip + it }
        topSpeed.update { maxOf(it, record.topSpeedMetersPerSecond) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun uiState(
        access: Flow<LocationAccess>,
        unit: Flow<SpeedUnit>,
        alertMetersPerSecond: Flow<Float?> = flowOf(null),
    ): Flow<SpeedometerUiState> {
        val distinctAccess = access.distinctUntilChanged()
        val signals: Flow<SpeedSignal?> = distinctAccess.flatMapLatest { current ->
            if (current == LocationAccess.GRANTED && source.isAvailable) {
                signals().onStart { emit(SpeedSignal.Searching) }
            } else {
                flowOf(null)
            }
        }
        val totals = combine(topSpeed, tripStats, alertMetersPerSecond.distinctUntilChanged()) { top, trip, alert -> Triple(top, trip, alert) }
        return combine(distinctAccess, signals, unit, totals) { a, s, u, (top, trip, alert) ->
            SpeedometerUiState.from(source.isAvailable, a, s, u, top, trip, alert)
        }.withRunningState().distinctUntilChanged()
    }

    /**
     * Adds the parts of the state that depend on history:
     * - the gauge range, carried from one state to the next and held while there is no reading,
     *   so a brief signal drop at speed does not collapse and re-expand the dial;
     * - the over-speed flag, whose hysteresis needs the previous state.
     */
    private fun Flow<SpeedometerUiState>.withRunningState(): Flow<SpeedometerUiState> = flow {
        var scale: Float? = null
        var scaleUnit: SpeedUnit? = null
        val alert = SpeedAlertDetector()
        collect { state ->
            if (state.unit != scaleUnit) {
                scale = null
                scaleUnit = state.unit
            }
            val speed = state.displaySpeed
            val next = when {
                speed != null -> GaugeScale.select(scale, speed)
                else -> scale ?: GaugeScale.minimum
            }
            scale = next
            val over = if (state.isTracking) alert.update(state.speedMetersPerSecond, state.alertMetersPerSecond) else alert.update(null, null)
            emit(state.copy(gaugeScale = next, isOverAlert = over))
        }
    }

    /**
     * Raw signal stream. A fix older than [staleTimeoutMillis] with no successor becomes
     * [SpeedSignal.SignalLost] so the UI never keeps showing an out-of-date speed.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun signals(): Flow<SpeedSignal> = flow {
        val processor = SpeedProcessor()
        emitAll(
            source.updates().transformLatest { event ->
                when (event) {
                    is LocationEvent.ProviderStatus -> {
                        if (event.enabled) {
                            emit(SpeedSignal.Searching)
                        } else {
                            processor.reset()
                            emit(SpeedSignal.LocationDisabled)
                        }
                    }

                    is LocationEvent.Fix -> {
                        val measurement = processor.process(event.sample)
                        if (measurement == null) {
                            emit(SpeedSignal.Searching)
                        } else {
                            emit(SpeedSignal.Measured(measurement))
                        }
                        // Cancelled by the next event (transformLatest) if one arrives in time.
                        delay(staleTimeoutMillis)
                        processor.reset()
                        emit(SpeedSignal.SignalLost)
                    }
                }
            },
        )
    }.withTripAccounting()

    private fun Flow<SpeedSignal>.withTripAccounting(): Flow<SpeedSignal> = flow {
        // One meter per subscription: a stretch never joins across leaving and re-opening the screen.
        val meter = TripMeter()
        collect { signal ->
            if (signal is SpeedSignal.Measured) {
                val m = signal.measurement
                // Unreliable fixes are shown (flagged) but never allowed to set a record.
                if (!m.isStationary && !m.isLowAccuracy) {
                    topSpeed.update { maxOf(it, m.speedMetersPerSecond) }
                }
                val increment = meter.add(m)
                if (!increment.isEmpty) tripStats.update { it + increment }
            } else {
                meter.interrupt()
            }
            emit(signal)
        }
    }

    companion object {
        const val DEFAULT_STALE_TIMEOUT_MILLIS = 5_000L
    }
}
