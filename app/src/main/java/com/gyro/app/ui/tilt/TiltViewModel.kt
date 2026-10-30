package com.gyro.app.ui.tilt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gyro.app.GyroApplication
import com.gyro.app.core.math.Vec3
import com.gyro.app.core.prefs.PreferencesRepository
import com.gyro.app.core.tilt.SlopeUnit
import com.gyro.app.core.tilt.TiltAvailability
import com.gyro.app.core.tilt.TiltModel
import com.gyro.app.core.tilt.TiltReading
import com.gyro.app.ui.common.STOP_TIMEOUT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TiltViewModel(model: TiltModel, private val preferences: PreferencesRepository) : ViewModel() {

    val availability: TiltAvailability = model.availability

    private val reference: Flow<Vec3?> = preferences.preferences.map { it.tiltReference }.distinctUntilChanged()

    private val live: StateFlow<TiltReading?> = model.readings(reference)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT, replayExpirationMillis = 0), null)

    private val held = MutableStateFlow<TiltReading?>(null)

    /** True while a reading is frozen on screen. */
    val isHeld: StateFlow<Boolean> = held.map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /**
     * What the instruments show: the held reading while one is held, otherwise the live one.
     * High-rate; read by the UI only in derived state and draw lambdas.
     */
    val reading: StateFlow<TiltReading?> = combine(live, held) { current, frozen -> frozen ?: current }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT, replayExpirationMillis = 0), null)

    val hasCustomZero: StateFlow<Boolean> = reference.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), false)

    val slopeUnit: StateFlow<SlopeUnit> = preferences.preferences.map { it.slopeUnit }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), SlopeUnit.DEGREES)

    val levelTone: StateFlow<Boolean> = preferences.preferences.map { it.levelTone }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), false)

    /**
     * Freezes the current reading (to measure somewhere the screen can't be seen), or releases a
     * held one. Returns the new held state, or null when there is no reading to hold yet.
     */
    fun toggleHold(): Boolean? {
        if (held.value != null) {
            held.value = null
            return false
        }
        val current = live.value ?: return null
        held.value = current
        return true
    }

    /** Treats the current orientation as level from now on. Returns false if no reading yet. */
    fun setZero(): Boolean {
        val gravity = live.value?.deviceGravity ?: return false
        held.value = null // a held reading was measured against the old zero
        viewModelScope.launch { preferences.setTiltReference(gravity) }
        return true
    }

    fun resetZero() {
        held.value = null
        viewModelScope.launch { preferences.setTiltReference(null) }
    }

    fun setSlopeUnit(unit: SlopeUnit) {
        viewModelScope.launch { preferences.setSlopeUnit(unit) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as GyroApplication).container
                TiltViewModel(TiltModel(container.tiltSensors), container.preferences)
            }
        }
    }
}
