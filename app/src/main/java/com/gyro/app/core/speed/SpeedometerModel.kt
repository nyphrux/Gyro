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
import kotlinx.coroutines.flow.onEach
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

    val isAvailable: Boolean get() = source.isAvailable

    fun resetTopSpeed() {
        topSpeed.value = 0f
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun uiState(access: Flow<LocationAccess>, unit: Flow<SpeedUnit>): Flow<SpeedometerUiState> {
        val distinctAccess = access.distinctUntilChanged()
        val signals: Flow<SpeedSignal?> = distinctAccess.flatMapLatest { current ->
            if (current == LocationAccess.GRANTED && source.isAvailable) {
                signals().onStart { emit(SpeedSignal.Searching) }
            } else {
                flowOf(null)
            }
        }
        return combine(distinctAccess, signals, unit, topSpeed) { a, s, u, top ->
            SpeedometerUiState.from(source.isAvailable, a, s, u, top)
        }.withAutoRangedGauge().distinctUntilChanged()
    }

    /**
     * Carries the gauge range from one state to the next. The range is held while there is no
     * reading, so a brief signal drop at speed does not collapse and re-expand the dial.
     */
    private fun Flow<SpeedometerUiState>.withAutoRangedGauge(): Flow<SpeedometerUiState> = flow {
        var scale: Float? = null
        var scaleUnit: SpeedUnit? = null
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
            emit(state.copy(gaugeScale = next))
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
    }.onEach { signal ->
        if (signal is SpeedSignal.Measured) {
            val m = signal.measurement
            // Unreliable fixes are shown (flagged) but never allowed to set a record.
            if (!m.isStationary && !m.isLowAccuracy) {
                topSpeed.update { maxOf(it, m.speedMetersPerSecond) }
            }
        }
    }

    companion object {
        const val DEFAULT_STALE_TIMEOUT_MILLIS = 5_000L
    }
}
