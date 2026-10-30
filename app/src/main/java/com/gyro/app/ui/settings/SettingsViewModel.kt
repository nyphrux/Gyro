package com.gyro.app.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gyro.app.GyroApplication
import com.gyro.app.core.prefs.AccentColor
import com.gyro.app.core.prefs.NorthReference
import com.gyro.app.core.prefs.PreferencesRepository
import com.gyro.app.core.prefs.ThemeMode
import com.gyro.app.core.prefs.UserPreferences
import com.gyro.app.core.speed.SpeedUnit
import com.gyro.app.core.update.ReleaseInfo
import com.gyro.app.platform.update.GithubUpdater
import com.gyro.app.platform.update.UpdateCheck
import com.gyro.app.ui.common.STOP_TIMEOUT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Where the in-app updater is. Up-to-date and failed checks are one-off [UpdateEvent]s. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(
        val release: ReleaseInfo,
        val download: DownloadState = DownloadState.NotStarted,
        /** True after Android refused the install because "Install unknown apps" is off. */
        val needsInstallPermission: Boolean = false,
    ) : UpdateState
}

sealed interface DownloadState {
    data object NotStarted : DownloadState
    /** [progress] is 0..1, or null while the size is unknown. */
    data class InProgress(val progress: Float?) : DownloadState
    data class Done(val apk: File) : DownloadState
    data object Failed : DownloadState
}

sealed interface UpdateEvent {
    data class UpToDate(val version: String) : UpdateEvent
    data object CheckFailed : UpdateEvent
    /** An activity to start from the UI (installer, permission screen or release page). */
    data class Launch(val intent: Intent) : UpdateEvent
}

class SettingsViewModel(
    private val repository: PreferencesRepository,
    private val updater: GithubUpdater,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences?> =
        repository.preferences.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), null)

    fun setThemeMode(mode: ThemeMode) = update { repository.setThemeMode(mode) }
    fun setAccent(accent: AccentColor) = update { repository.setAccent(accent) }
    fun setSpeedUnit(unit: SpeedUnit) = update { repository.setSpeedUnit(unit) }
    fun setNorthReference(reference: NorthReference) = update { repository.setNorthReference(reference) }
    fun setHapticsEnabled(enabled: Boolean) = update { repository.setHapticsEnabled(enabled) }
    fun setKeepScreenOn(enabled: Boolean) = update { repository.setKeepScreenOn(enabled) }
    fun resetTiltZero() = update { repository.setTiltReference(null) }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    // region Updates

    val currentVersion: String get() = updater.currentVersion

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _showUpdateSheet = MutableStateFlow(false)
    val showUpdateSheet: StateFlow<Boolean> = _showUpdateSheet.asStateFlow()

    private val events = Channel<UpdateEvent>(Channel.BUFFERED)
    val updateEvents: Flow<UpdateEvent> = events.receiveAsFlow()

    private var downloadJob: Job? = null
    private var awaitingInstallPermission = false

    /** The top-bar button: checks, or reopens the sheet when an update is already known. */
    fun onUpdateButton() {
        when (_updateState.value) {
            UpdateState.Checking -> Unit
            is UpdateState.Available -> _showUpdateSheet.value = true
            UpdateState.Idle -> checkForUpdates()
        }
    }

    private fun checkForUpdates() {
        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            when (val result = updater.checkLatest()) {
                is UpdateCheck.Available -> {
                    _updateState.value = UpdateState.Available(result.release)
                    _showUpdateSheet.value = true
                }
                is UpdateCheck.UpToDate -> {
                    _updateState.value = UpdateState.Idle
                    events.send(UpdateEvent.UpToDate(result.latestVersion))
                }
                UpdateCheck.Failed -> {
                    _updateState.value = UpdateState.Idle
                    events.send(UpdateEvent.CheckFailed)
                }
            }
        }
    }

    fun dismissUpdateSheet() {
        _showUpdateSheet.value = false
    }

    /** The sheet's main button: download, install, or open the release page if there is no APK. */
    fun onPrimaryUpdateAction() {
        val state = _updateState.value as? UpdateState.Available ?: return
        when (val download = state.download) {
            is DownloadState.InProgress -> Unit
            is DownloadState.Done -> install(download.apk)
            DownloadState.NotStarted, DownloadState.Failed ->
                if (state.release.apkUrl == null) {
                    events.trySend(UpdateEvent.Launch(updater.releasePageIntent(state.release)))
                } else {
                    startDownload(state.release)
                }
        }
    }

    fun openReleasePage() {
        val release = (_updateState.value as? UpdateState.Available)?.release
        events.trySend(UpdateEvent.Launch(updater.releasePageIntent(release)))
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    private fun startDownload(release: ReleaseInfo) {
        downloadJob?.cancel()
        setDownload(DownloadState.InProgress(null))
        downloadJob = viewModelScope.launch {
            try {
                val apk = updater.download(release) { progress -> setDownload(DownloadState.InProgress(progress)) }
                setDownload(DownloadState.Done(apk))
                install(apk)
            } catch (e: CancellationException) {
                setDownload(DownloadState.NotStarted)
                throw e
            } catch (_: Exception) {
                setDownload(DownloadState.Failed)
            }
        }
    }

    private fun install(apk: File) {
        if (!apk.exists()) {
            setDownload(DownloadState.NotStarted)
            return
        }
        if (updater.canInstall()) {
            awaitingInstallPermission = false
            setNeedsPermission(false)
            events.trySend(UpdateEvent.Launch(updater.installIntent(apk)))
        } else {
            // Send the user to the "Install unknown apps" toggle; onResume() carries on from there.
            awaitingInstallPermission = true
            setNeedsPermission(true)
            events.trySend(UpdateEvent.Launch(updater.installPermissionIntent()))
        }
    }

    /** Called when Settings comes back to the foreground, e.g. from the permission screen. */
    fun onResume() {
        if (!awaitingInstallPermission) return
        awaitingInstallPermission = false
        val state = _updateState.value as? UpdateState.Available ?: return
        val apk = (state.download as? DownloadState.Done)?.apk ?: return
        if (updater.canInstall()) install(apk)
    }

    private fun setDownload(download: DownloadState) = _updateState.update { state ->
        if (state is UpdateState.Available) state.copy(download = download) else state
    }

    private fun setNeedsPermission(needed: Boolean) = _updateState.update { state ->
        if (state is UpdateState.Available) state.copy(needsInstallPermission = needed) else state
    }

    // endregion

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as GyroApplication).container
                SettingsViewModel(container.preferences, container.updater)
            }
        }
    }
}
