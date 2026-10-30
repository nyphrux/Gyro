package com.gyro.app.ui.speed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gyro.app.GyroApplication
import com.gyro.app.core.prefs.PreferencesRepository
import com.gyro.app.core.speed.LocationAccess
import com.gyro.app.core.speed.SpeedUnit
import com.gyro.app.core.speed.SpeedometerModel
import com.gyro.app.core.speed.SpeedometerUiState
import com.gyro.app.core.speed.TripRecord
import com.gyro.app.core.speed.TripRepository
import com.gyro.app.ui.common.STOP_TIMEOUT
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * GPS runs only while [uiState] is collected (the speedometer is on screen and the app is at
 * least STARTED) and precise location is granted. Permission state is pushed in by the UI,
 * which owns the Activity-bound permission APIs.
 *
 * The trip and top speed are loaded once and then saved at most every few seconds while they
 * change, so they carry on across app restarts without a disk write per GPS fix.
 */
class SpeedometerViewModel(
    private val model: SpeedometerModel,
    private val preferences: PreferencesRepository,
    private val tripRepository: TripRepository,
) : ViewModel() {
    private val access = MutableStateFlow(LocationAccess.UNKNOWN)

    init {
        viewModelScope.launch {
            model.restore(tripRepository.load())
            // The first record is the one just restored; save only changes after it.
            model.records.drop(1).conflate().collect { record ->
                tripRepository.save(record)
                delay(SAVE_INTERVAL_MILLIS)
            }
        }
    }

    val uiState: StateFlow<SpeedometerUiState> = model
        .uiState(
            access = access,
            unit = preferences.preferences.map { it.speedUnit }.distinctUntilChanged(),
            alertMetersPerSecond = preferences.preferences.map { it.speedAlertMetersPerSecond }.distinctUntilChanged(),
        )
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT),
            SpeedometerUiState.from(model.isAvailable, LocationAccess.UNKNOWN, null, SpeedUnit.KILOMETRES_PER_HOUR, 0f),
        )

    val hudMirrored: StateFlow<Boolean> = preferences.preferences
        .map { it.hudMirrored }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), true)

    fun onLocationAccessChanged(newAccess: LocationAccess) {
        access.value = newAccess
    }

    fun setUnit(unit: SpeedUnit) {
        viewModelScope.launch { preferences.setSpeedUnit(unit) }
    }

    /** @param metersPerSecond the new threshold, or null to switch the alert off. */
    fun setSpeedAlert(metersPerSecond: Float?) {
        viewModelScope.launch { preferences.setSpeedAlert(metersPerSecond) }
    }

    fun setHudMirrored(mirrored: Boolean) {
        viewModelScope.launch { preferences.setHudMirrored(mirrored) }
    }

    fun resetTopSpeed() {
        model.resetTopSpeed()
        saveNow()
    }

    fun resetTrip() {
        model.resetTrip()
        saveNow()
    }

    /** Resets are saved straight away so they stick even if the app closes right after. */
    private fun saveNow() {
        val record = TripRecord(model.trip.value, model.topSpeedMetersPerSecond.value)
        viewModelScope.launch { tripRepository.save(record) }
    }

    companion object {
        private const val SAVE_INTERVAL_MILLIS = 3_000L

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as GyroApplication).container
                SpeedometerViewModel(SpeedometerModel(container.locationSource), container.preferences, container.tripRepository)
            }
        }
    }
}
