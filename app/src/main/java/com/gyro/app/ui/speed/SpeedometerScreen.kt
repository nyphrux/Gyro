package com.gyro.app.ui.speed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyro.app.R
import com.gyro.app.core.speed.SpeedStatus
import com.gyro.app.core.speed.SpeedUnit
import com.gyro.app.core.speed.SpeedometerUiState
import com.gyro.app.ui.common.LocalGyroHaptics
import com.gyro.app.ui.common.formatAccuracy
import com.gyro.app.ui.common.formatSpeed
import com.gyro.app.ui.components.Footnote
import com.gyro.app.ui.components.GroupGap
import com.gyro.app.ui.components.GroupPosition
import com.gyro.app.ui.components.IslandStatus
import com.gyro.app.ui.components.IslandTone
import com.gyro.app.ui.components.MessagePanel
import com.gyro.app.ui.components.PanelAction
import com.gyro.app.ui.components.PanelTone
import com.gyro.app.ui.components.PillSelector
import com.gyro.app.ui.components.StatTile
import com.gyro.app.ui.components.StatusIsland
import com.gyro.app.ui.components.ToolLayout
import com.gyro.app.ui.permission.LocationPermissionState
import com.gyro.app.ui.permission.rememberLocationPermissionState

@Composable
fun SpeedometerScreen(contentPadding: PaddingValues, viewModel: SpeedometerViewModel = viewModel(factory = SpeedometerViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberLocationPermissionState(precise = true)
    LaunchedEffect(permission.access) { viewModel.onLocationAccessChanged(permission.access) }

    ToolLayout(
        contentPadding = contentPadding,
        header = { StatusIsland(speedIsland(state)) },
        instrument = { size -> SpeedGauge(state = state, size = size) },
        details = {
            PillSelector(
                options = SpeedUnit.entries,
                selected = state.unit,
                onSelect = viewModel::setUnit,
                label = { it.symbol },
                accessibilityLabel = { unitName(it) },
            )
            if (!PermissionOrServiceIssue(state.status, permission)) {
                SpeedStats(state, onResetTop = viewModel::resetTopSpeed)
            }
            Footnote(icon = R.drawable.ic_info, text = stringResource(R.string.speed_gps_note))
        },
    )
}

@Composable
fun unitName(unit: SpeedUnit): String = stringResource(
    when (unit) {
        SpeedUnit.KILOMETRES_PER_HOUR -> R.string.unit_kmh
        SpeedUnit.MILES_PER_HOUR -> R.string.unit_mph
        SpeedUnit.METRES_PER_SECOND -> R.string.unit_mps
        SpeedUnit.KNOTS -> R.string.unit_kn
    },
)

@Composable
private fun speedIsland(state: SpeedometerUiState): IslandStatus = when (state.status) {
    SpeedStatus.UNSUPPORTED -> IslandStatus(stringResource(R.string.speed_status_unsupported), IslandTone.ERROR, R.drawable.ic_sensor_off)
    SpeedStatus.CHECKING_PERMISSION -> IslandStatus(stringResource(R.string.speed_status_checking), IslandTone.NEUTRAL)
    SpeedStatus.PERMISSION_REQUIRED, SpeedStatus.PERMISSION_BLOCKED ->
        IslandStatus(stringResource(R.string.speed_status_permission), IslandTone.WARNING, R.drawable.ic_location)
    SpeedStatus.PRECISE_LOCATION_REQUIRED ->
        IslandStatus(stringResource(R.string.speed_status_precise), IslandTone.WARNING, R.drawable.ic_location)
    SpeedStatus.LOCATION_DISABLED ->
        IslandStatus(stringResource(R.string.speed_status_location_off), IslandTone.WARNING, R.drawable.ic_location_off)
    SpeedStatus.SEARCHING -> IslandStatus(stringResource(R.string.speed_status_searching), IslandTone.ACTIVE, pulsing = true)
    SpeedStatus.SIGNAL_LOST -> IslandStatus(stringResource(R.string.speed_status_signal_lost), IslandTone.WARNING, R.drawable.ic_warning, pulsing = true)
    SpeedStatus.STATIONARY -> IslandStatus(stringResource(R.string.speed_status_stationary), IslandTone.ACTIVE, R.drawable.ic_location)
    SpeedStatus.MOVING -> if (state.isLowAccuracy) {
        IslandStatus(stringResource(R.string.speed_status_weak), IslandTone.WARNING, R.drawable.ic_warning)
    } else {
        IslandStatus(stringResource(R.string.speed_status_gps), IslandTone.SUCCESS, R.drawable.ic_location)
    }
}

/**
 * Shows the panel for whatever blocks measurement (permission, location services, hardware).
 * @return true when such a panel was shown.
 */
@Composable
private fun PermissionOrServiceIssue(status: SpeedStatus, permission: LocationPermissionState): Boolean {
    when (status) {
        SpeedStatus.PERMISSION_REQUIRED -> MessagePanel(
            icon = R.drawable.ic_location,
            title = stringResource(R.string.permission_title),
            body = stringResource(R.string.permission_body),
            primaryAction = PanelAction(stringResource(R.string.action_allow_location)) { permission.request() },
        )

        SpeedStatus.PERMISSION_BLOCKED -> MessagePanel(
            icon = R.drawable.ic_location_off,
            title = stringResource(R.string.permission_blocked_title),
            body = stringResource(R.string.permission_blocked_body),
            tone = PanelTone.WARNING,
            primaryAction = PanelAction(stringResource(R.string.action_open_settings)) { permission.openAppSettings() },
        )

        SpeedStatus.PRECISE_LOCATION_REQUIRED -> MessagePanel(
            icon = R.drawable.ic_location,
            title = stringResource(R.string.precise_title),
            body = stringResource(R.string.precise_body),
            tone = PanelTone.WARNING,
            primaryAction = if (permission.isBlocked) {
                PanelAction(stringResource(R.string.action_open_settings)) { permission.openAppSettings() }
            } else {
                PanelAction(stringResource(R.string.action_allow_precise)) { permission.request() }
            },
        )

        SpeedStatus.LOCATION_DISABLED -> MessagePanel(
            icon = R.drawable.ic_location_off,
            title = stringResource(R.string.location_off_title),
            body = stringResource(R.string.location_off_body),
            tone = PanelTone.WARNING,
            primaryAction = PanelAction(stringResource(R.string.action_location_settings)) { permission.openLocationSettings() },
        )

        SpeedStatus.UNSUPPORTED -> MessagePanel(
            icon = R.drawable.ic_sensor_off,
            title = stringResource(R.string.unsupported_speed_title),
            body = stringResource(R.string.unsupported_speed_body),
            tone = PanelTone.ERROR,
        )

        else -> return false
    }
    return true
}

@Composable
private fun SpeedStats(state: SpeedometerUiState, onResetTop: () -> Unit) {
    val haptics = LocalGyroHaptics.current
    val unknown = stringResource(R.string.value_unknown)
    Row(horizontalArrangement = Arrangement.spacedBy(GroupGap), modifier = Modifier.fillMaxWidth()) {
        StatTile(
            label = stringResource(R.string.speed_stat_top),
            value = if (state.topSpeedMetersPerSecond > 0f) "${formatSpeed(state.displayTopSpeed, state.unit)} ${state.unit.symbol}" else unknown,
            position = GroupPosition.FIRST,
            modifier = Modifier.weight(1f),
            trailing = {
                IconButton(
                    onClick = {
                        haptics.tick()
                        onResetTop()
                    },
                    enabled = state.topSpeedMetersPerSecond > 0f,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_reset),
                        contentDescription = stringResource(R.string.speed_reset_top_description),
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )
        StatTile(
            label = stringResource(R.string.speed_stat_accuracy),
            value = state.displayAccuracy?.let { stringResource(R.string.speed_accuracy_value, formatAccuracy(it), state.unit.symbol) } ?: unknown,
            position = GroupPosition.LAST,
            modifier = Modifier.weight(1f),
        )
    }
}
