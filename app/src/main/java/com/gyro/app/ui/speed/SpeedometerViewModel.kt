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
import com.gyro.app.ui.common.STOP_TIMEOUT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * GPS runs only while [uiState] is collected (the speedometer is on screen and the app is at
 * least STARTED) and precise location is granted. Permission state is pushed in by the UI,
 * which owns the Activity-bound permission APIs.
 */
class SpeedometerViewModel(private val model: SpeedometerModel, private val preferences: PreferencesRepository) : ViewModel() {
    private val access = MutableStateFlow(LocationAccess.UNKNOWN)

    val uiState: StateFlow<SpeedometerUiState> = model
        .uiState(access, preferences.preferences.map { it.speedUnit }.distinctUntilChanged())
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT),
            SpeedometerUiState.from(model.isAvailable, LocationAccess.UNKNOWN, null, SpeedUnit.KILOMETRES_PER_HOUR, 0f),
        )

    fun onLocationAccessChanged(newAccess: LocationAccess) {
        access.value = newAccess
    }

    fun setUnit(unit: SpeedUnit) {
        viewModelScope.launch { preferences.setSpeedUnit(unit) }
    }

    fun resetTopSpeed() = model.resetTopSpeed()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as GyroApplication).container
                SpeedometerViewModel(SpeedometerModel(container.locationSource), container.preferences)
            }
        }
    }
}
