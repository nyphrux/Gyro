package com.gyro.app.ui.compass

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gyro.app.GyroApplication
import com.gyro.app.core.compass.CompassFrame
import com.gyro.app.core.compass.CompassModel
import com.gyro.app.core.compass.CompassSignal
import com.gyro.app.core.compass.CompassUiState
import com.gyro.app.core.compass.CourseGuidance
import com.gyro.app.core.compass.CourseTracker
import com.gyro.app.core.compass.DeclinationResult
import com.gyro.app.core.compass.MagneticModel
import com.gyro.app.core.compass.PositionProvider
import com.gyro.app.core.compass.PositionResult
import com.gyro.app.core.compass.SunState
import com.gyro.app.core.compass.declinationResult
import com.gyro.app.core.compass.displayedHeading
import com.gyro.app.core.math.AngleMath
import com.gyro.app.core.prefs.NorthReference
import com.gyro.app.core.prefs.PreferencesRepository
import com.gyro.app.ui.common.STOP_TIMEOUT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Exposes streams with very different rates:
 * - [heading] and [course]: every filtered sensor sample (~50 Hz). The UI reads them only inside
 *   draw / graphicsLayer lambdas and derived or sampled state, so they never recompose the screen.
 * - [uiState], [sun] and [lockedBearing]: availability, calibration, north reference, the sun
 *   marker and the bearing lock, which change rarely.
 *
 * All share one upstream sensor subscription that starts when the screen collects and stops
 * [STOP_TIMEOUT] after it leaves (long enough to survive a configuration change).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompassViewModel(
    private val model: CompassModel,
    private val preferences: PreferencesRepository,
    private val positionProvider: PositionProvider,
    private val magneticModel: MagneticModel,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val refreshPosition = MutableStateFlow(0)

    private val northReference: Flow<NorthReference> =
        preferences.preferences.map { it.northReference }.distinctUntilChanged()

    private val showSun: Flow<Boolean> =
        preferences.preferences.map { it.showSun }.distinctUntilChanged()

    /**
     * One-off approximate position, looked up only while something needs it (true north or the
     * sun marker) and again on [refreshPosition]. Switching between those two needs does not
     * trigger a second lookup.
     */
    private val position: Flow<PositionResult?> =
        combine(
            combine(northReference, showSun) { reference, sun -> reference == NorthReference.TRUE || sun }.distinctUntilChanged(),
            refreshPosition,
        ) { needed, _ -> needed }
            .flatMapLatest { needed ->
                if (needed) {
                    flow { emit(positionProvider.position()) }.onStart { emit(PositionResult.Loading) }
                } else {
                    flowOf(null)
                }
            }
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), replay = 1)

    private val declination: Flow<DeclinationResult?> =
        combine(northReference, position) { reference, pos ->
            declinationResult(reference, pos, System.currentTimeMillis(), magneticModel)
        }
            .distinctUntilChanged()
            .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), replay = 1)

    private val frames: Flow<CompassFrame> = model.frames()
        .flowOn(Dispatchers.Default)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT, replayExpirationMillis = 0), replay = 1)

    val heading: StateFlow<Float?> =
        combine(frames, northReference, declination) { frame, reference, decl ->
            frame.magneticHeading?.let { displayedHeading(it, reference, decl) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT, replayExpirationMillis = 0), null)

    val uiState: StateFlow<CompassUiState> =
        combine(
            frames.map { it.toSignal() }.distinctUntilChanged().onStart<CompassSignal?> { emit(null) },
            northReference,
            declination.onStart { emit(null) },
        ) { signal, reference, decl ->
            CompassUiState.from(model.availability, signal, reference, decl)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT),
            CompassUiState(availability = model.availability),
        )

    /** Recomputed every minute while shown: the sun moves about a quarter of a degree a minute. */
    val sun: StateFlow<SunState> = showSun
        .flatMapLatest { show ->
            if (!show) {
                flowOf(SunState.Hidden)
            } else {
                combine(position.onStart { emit(null) }, declination.onStart { emit(null) }, minuteTicks()) { pos, decl, now ->
                    SunState.from(true, pos, displaysTrueNorth = decl is DeclinationResult.Available, epochMillis = now, model = magneticModel)
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), SunState.Hidden)

    /** The locked bearing (in the displayed north reference), kept across process death. */
    val lockedBearing: StateFlow<Float?> = savedState.getStateFlow<Float?>(KEY_LOCKED_BEARING, null)

    /** Where the locked bearing is relative to the heading; null when no bearing is locked. */
    val course: StateFlow<CourseGuidance?> = lockedBearing
        .flatMapLatest { target ->
            if (target == null) {
                flowOf(null)
            } else {
                val tracker = CourseTracker()
                heading.map { h -> h?.let { tracker.update(it, target) } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT, replayExpirationMillis = 0), null)

    /** Locks the current heading as the bearing to follow. Returns false while there is no heading. */
    fun lockBearing(): Boolean {
        val current = heading.value ?: return false
        savedState[KEY_LOCKED_BEARING] = AngleMath.normalize(Math.round(current).toFloat())
        return true
    }

    fun clearBearing() {
        savedState[KEY_LOCKED_BEARING] = null
    }

    /** Re-query the position, e.g. after the user grants location or returns to the screen. */
    fun refreshPosition() {
        refreshPosition.value++
    }

    fun setNorthReference(reference: NorthReference) {
        viewModelScope.launch { preferences.setNorthReference(reference) }
    }

    fun setShowSun(enabled: Boolean) {
        viewModelScope.launch { preferences.setShowSun(enabled) }
    }

    private fun minuteTicks(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }

    companion object {
        private const val KEY_LOCKED_BEARING = "locked_bearing"

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as GyroApplication).container
                CompassViewModel(
                    model = CompassModel(container.compassSensors),
                    preferences = container.preferences,
                    positionProvider = container.positionProvider,
                    magneticModel = container.magneticModel,
                    savedState = createSavedStateHandle(),
                )
            }
        }
    }
}
