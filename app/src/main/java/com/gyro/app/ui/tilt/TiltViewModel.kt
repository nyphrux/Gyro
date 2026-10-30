package com.gyro.app.ui.tilt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gyro.app.GyroApplication
import com.gyro.app.core.math.Vec3
import com.gyro.app.core.prefs.PreferencesRepository
import com.gyro.app.core.tilt.TiltAvailability
import com.gyro.app.core.tilt.TiltModel
import com.gyro.app.core.tilt.TiltReading
import com.gyro.app.ui.common.STOP_TIMEOUT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TiltViewModel(model: TiltModel, private val preferences: PreferencesRepository) : ViewModel() {

    val availability: TiltAvailability = model.availability

    private val reference: Flow<Vec3?> = preferences.preferences.map { it.tiltReference }.distinctUntilChanged()

    /** High-rate readings; read by the UI only in derived state and draw lambdas. */
    val reading: StateFlow<TiltReading?> = model.readings(reference)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT, replayExpirationMillis = 0), null)

    val hasCustomZero: StateFlow<Boolean> = reference.map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), false)

    /** Treats the current orientation as level from now on. Returns false if no reading yet. */
    fun setZero(): Boolean {
        val gravity = reading.value?.deviceGravity ?: return false
        viewModelScope.launch { preferences.setTiltReference(gravity) }
        return true
    }

    fun resetZero() {
        viewModelScope.launch { preferences.setTiltReference(null) }
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
