package com.gyro.app.ui.tilt

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyro.app.R
import com.gyro.app.core.tilt.TiltAvailability
import com.gyro.app.core.tilt.TiltMode
import com.gyro.app.core.tilt.TiltReading
import com.gyro.app.ui.common.LocalGyroHaptics
import com.gyro.app.ui.common.formatDegrees
import com.gyro.app.ui.common.formatSignedDegrees
import com.gyro.app.ui.common.rememberSampledState
import com.gyro.app.ui.components.Footnote
import com.gyro.app.ui.components.GroupGap
import com.gyro.app.ui.components.GroupPosition
import com.gyro.app.ui.components.IslandStatus
import com.gyro.app.ui.components.IslandTone
import com.gyro.app.ui.components.MessagePanel
import com.gyro.app.ui.components.PanelTone
import com.gyro.app.ui.components.StatTile
import com.gyro.app.ui.components.StatusIsland
import com.gyro.app.ui.components.ToolLayout
import com.gyro.app.ui.theme.GyroMotion
import com.gyro.app.ui.theme.GyroThemeColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlin.math.abs

/** Readings within this many degrees of an axis are described as level for that axis. */
private const val AXIS_LEVEL_DEGREES = 0.05f

@Composable
fun TiltScreen(contentPadding: PaddingValues, viewModel: TiltViewModel = viewModel(factory = TiltViewModel.Factory)) {
    // High-rate state: read only through derived state, sampling and draw lambdas.
    val reading: State<TiltReading?> = viewModel.reading.collectAsStateWithLifecycle()
    val hasCustomZero by viewModel.hasCustomZero.collectAsStateWithLifecycle()
    val haptics = LocalGyroHaptics.current

    val mode by remember { derivedStateOf { reading.value?.mode } }
    val isLevel by remember { derivedStateOf { reading.value?.isLevel == true } }
    val hasReading by remember { derivedStateOf { reading.value != null } }

    // Confirm haptic when the level is found (not on first appearance).
    LaunchedEffect(haptics) {
        snapshotFlow { reading.value?.isLevel == true }
            .distinctUntilChanged()
            .drop(1)
            .filter { it }
            .collect { haptics.confirm() }
    }

    val available = viewModel.availability != TiltAvailability.UNAVAILABLE

    ToolLayout(
        contentPadding = contentPadding,
        header = { StatusIsland(tiltIsland(available, hasReading, mode, isLevel)) },
        instrument = { size ->
            if (available) {
                AnimatedContent(
                    targetState = mode ?: TiltMode.SURFACE,
                    transitionSpec = {
                        (fadeIn(GyroMotion.effects()) + scaleIn(GyroMotion.spatial(), initialScale = 0.92f)) togetherWith
                            (fadeOut(GyroMotion.fade(150)) + scaleOut(GyroMotion.spatial(), targetScale = 0.92f))
                    },
                    label = "tiltInstrument",
                ) { current ->
                    when (current) {
                        TiltMode.SURFACE -> BubbleLevel(reading = { reading.value }, size = size)
                        TiltMode.EDGE -> EdgeLevel(reading = { reading.value }, size = size)
                    }
                }
            }
        },
        details = {
            if (!available) {
                MessagePanel(
                    icon = R.drawable.ic_sensor_off,
                    title = stringResource(R.string.tilt_unavailable_title),
                    body = stringResource(R.string.tilt_unavailable_body),
                    tone = PanelTone.ERROR,
                )
            } else {
                TiltReadouts(reading = { reading.value }, mode = mode ?: TiltMode.SURFACE, isLevel = isLevel)
                ZeroControls(
                    hasCustomZero = hasCustomZero,
                    canSetZero = hasReading,
                    onSetZero = { if (viewModel.setZero()) haptics.confirm() },
                    onReset = {
                        haptics.tick()
                        viewModel.resetZero()
                    },
                )
                Footnote(
                    icon = R.drawable.ic_info,
                    text = stringResource(if (mode == TiltMode.EDGE) R.string.tilt_hint_edge else R.string.tilt_hint_surface),
                )
                if (viewModel.availability == TiltAvailability.ACCELEROMETER) {
                    Footnote(icon = R.drawable.ic_warning, text = stringResource(R.string.tilt_accelerometer_note))
                }
            }
        },
    )
}

@Composable
private fun tiltIsland(available: Boolean, hasReading: Boolean, mode: TiltMode?, isLevel: Boolean): IslandStatus = when {
    !available -> IslandStatus(stringResource(R.string.tilt_unavailable_title), IslandTone.ERROR, R.drawable.ic_sensor_off)
    !hasReading -> IslandStatus(stringResource(R.string.tilt_status_waiting), IslandTone.NEUTRAL, pulsing = true)
    isLevel -> IslandStatus(stringResource(R.string.tilt_status_level), IslandTone.SUCCESS, R.drawable.ic_check)
    mode == TiltMode.EDGE -> IslandStatus(stringResource(R.string.tilt_mode_edge), IslandTone.ACTIVE, R.drawable.ic_level)
    else -> IslandStatus(stringResource(R.string.tilt_mode_surface), IslandTone.ACTIVE, R.drawable.ic_level)
}

/** Numeric readouts, sampled at 10 Hz so the digits stay legible. */
@Composable
private fun TiltReadouts(reading: () -> TiltReading?, mode: TiltMode, isLevel: Boolean) {
    val sampled by rememberSampledState { reading() }
    val r = sampled
    val unknown = stringResource(R.string.value_unknown)
    val levelColor = GyroThemeColors.current.success
    val levelSuffix = if (isLevel) stringResource(R.string.tilt_description_level) else ""

    if (mode == TiltMode.SURFACE) {
        val roll = r?.rollDegrees
        val pitch = r?.pitchDegrees
        val description = if (roll != null && pitch != null) {
            stringResource(R.string.tilt_description_surface, formatSignedDegrees(roll), formatSignedDegrees(pitch)) + levelSuffix
        } else {
            unknown
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(GroupGap),
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = description },
        ) {
            StatTile(
                label = stringResource(R.string.tilt_stat_roll),
                value = roll?.let { stringResource(R.string.degrees_value, formatSignedDegrees(it)) } ?: unknown,
                supporting = roll?.let {
                    stringResource(
                        when {
                            abs(it) < AXIS_LEVEL_DEGREES -> R.string.tilt_axis_level
                            it > 0f -> R.string.tilt_roll_right_up
                            else -> R.string.tilt_roll_left_up
                        },
                    )
                },
                valueColor = if (isLevel) levelColor else MaterialTheme.colorScheme.onSurface,
                position = GroupPosition.FIRST,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = stringResource(R.string.tilt_stat_pitch),
                value = pitch?.let { stringResource(R.string.degrees_value, formatSignedDegrees(it)) } ?: unknown,
                supporting = pitch?.let {
                    stringResource(
                        when {
                            abs(it) < AXIS_LEVEL_DEGREES -> R.string.tilt_axis_level
                            it > 0f -> R.string.tilt_pitch_top_up
                            else -> R.string.tilt_pitch_bottom_up
                        },
                    )
                },
                valueColor = if (isLevel) levelColor else MaterialTheme.colorScheme.onSurface,
                position = GroupPosition.LAST,
                modifier = Modifier.weight(1f),
            )
        }
    } else {
        val deviation = r?.edgeDeviationDegrees
        val description = deviation?.let { stringResource(R.string.tilt_description_edge, formatSignedDegrees(it)) + levelSuffix } ?: unknown
        StatTile(
            label = stringResource(R.string.tilt_stat_angle),
            value = deviation?.let { stringResource(R.string.degrees_value, formatDegrees(it)) } ?: unknown,
            supporting = deviation?.let {
                stringResource(
                    when {
                        abs(it) < AXIS_LEVEL_DEGREES -> R.string.tilt_axis_level
                        it > 0f -> R.string.tilt_rotated_clockwise
                        else -> R.string.tilt_rotated_counter
                    },
                )
            },
            valueColor = if (isLevel) levelColor else MaterialTheme.colorScheme.onSurface,
            position = GroupPosition.SINGLE,
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = description },
        )
    }
}

@Composable
private fun ZeroControls(hasCustomZero: Boolean, canSetZero: Boolean, onSetZero: () -> Unit, onReset: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Button(onClick = onSetZero, enabled = canSetZero, modifier = Modifier.weight(1f).height(52.dp)) {
            Icon(painterResource(R.drawable.ic_target), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.tilt_set_zero))
        }
        OutlinedButton(onClick = onReset, enabled = hasCustomZero, modifier = Modifier.weight(1f).height(52.dp)) {
            Icon(painterResource(R.drawable.ic_reset), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_reset))
        }
    }
    Text(
        text = stringResource(
            R.string.tilt_reference_value,
            stringResource(if (hasCustomZero) R.string.tilt_custom_zero else R.string.tilt_true_level),
        ),
        style = MaterialTheme.typography.labelLarge,
        color = if (hasCustomZero) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
