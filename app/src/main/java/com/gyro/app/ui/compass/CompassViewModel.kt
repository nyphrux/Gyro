package com.gyro.app.ui.compass

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gyro.app.GyroApplication
import com.gyro.app.core.compass.CompassFrame
import com.gyro.app.core.compass.CompassModel
import com.gyro.app.core.compass.CompassSignal
import com.gyro.app.core.compass.CompassUiState
import com.gyro.app.core.compass.DeclinationProvider
import com.gyro.app.core.compass.DeclinationResult
import com.gyro.app.core.compass.displayedHeading
import com.gyro.app.core.prefs.NorthReference
import com.gyro.app.core.prefs.PreferencesRepository
import com.gyro.app.ui.common.STOP_TIMEOUT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Exposes two streams with very different rates:
 * - [heading]: every filtered sensor sample (~50 Hz). The UI reads it only inside draw /
 *   graphicsLayer lambdas and derived state, so it never recomposes the whole screen.
 * - [uiState]: availability, calibration and north reference, which change rarely.
 *
 * Both share one upstream sensor subscription that starts when the screen collects and stops
 * [STOP_TIMEOUT] after it leaves (long enough to survive a configuration change).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompassViewModel(private val model: CompassModel, private val preferences: PreferencesRepository, private val declinationProvider: DeclinationProvider) : ViewModel() {
    private val refreshDeclination = MutableStateFlow(0)

    private val northReference: Flow<NorthReference> =
        preferences.preferences.map { it.northReference }.distinctUntilChanged()

    private val declination: Flow<DeclinationResult?> =
        combine(northReference, refreshDeclination) { reference, _ -> reference }
            .flatMapLatest { reference ->
                if (reference == NorthReference.TRUE) {
                    flow { emit(declinationProvider.declination()) }.onStart { emit(DeclinationResult.Loading) }
                } else {
                    flowOf(null)
                }
            }
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

    /** Re-query declination, e.g. after the user grants location or returns to the screen. */
    fun refreshDeclination() {
        refreshDeclination.value++
    }

    fun setNorthReference(reference: NorthReference) {
        viewModelScope.launch { preferences.setNorthReference(reference) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as GyroApplication).container
                CompassViewModel(
                    model = CompassModel(container.compassSensors),
                    preferences = container.preferences,
                    declinationProvider = container.declinationProvider,
                )
            }
        }
    }
}
