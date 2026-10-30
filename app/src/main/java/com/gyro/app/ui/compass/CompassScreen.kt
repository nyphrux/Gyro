package com.gyro.app.ui.compass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyro.app.R
import com.gyro.app.core.compass.CalibrationStatus
import com.gyro.app.core.compass.CompassUiState
import com.gyro.app.core.compass.DeclinationResult
import com.gyro.app.core.compass.SensorAccuracy
import com.gyro.app.core.math.CardinalDetent
import com.gyro.app.core.math.CardinalDirection
import com.gyro.app.core.prefs.NorthReference
import com.gyro.app.ui.common.LocalGyroHaptics
import com.gyro.app.ui.common.formatSignedDegrees
import com.gyro.app.ui.common.rememberSampledState
import com.gyro.app.ui.components.GroupGap
import com.gyro.app.ui.components.GroupPosition
import com.gyro.app.ui.components.IslandStatus
import com.gyro.app.ui.components.IslandTone
import com.gyro.app.ui.components.MessagePanel
import com.gyro.app.ui.components.PanelAction
import com.gyro.app.ui.components.PanelTone
import com.gyro.app.ui.components.StatTile
import com.gyro.app.ui.components.StatusIsland
import com.gyro.app.ui.components.ToolLayout
import com.gyro.app.ui.permission.rememberLocationPermissionState
import com.gyro.app.ui.theme.GyroMotion
import com.gyro.app.ui.theme.LocalReducedMotion
import com.gyro.app.ui.theme.Readout
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun CompassScreen(contentPadding: PaddingValues, viewModel: CompassViewModel = viewModel(factory = CompassViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Kept as State (not delegated) so only draw lambdas and derived state read it.
    val heading: State<Float?> = viewModel.heading.collectAsStateWithLifecycle()
    val haptics = LocalGyroHaptics.current

    val permission = rememberLocationPermissionState(precise = false, onResult = { viewModel.refreshDeclination() })
    val latestState by rememberUpdatedState(state)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Retry when coming back (e.g. from settings) unless true north is already resolved.
        val s = latestState
        if (s.northReference == NorthReference.TRUE && s.declination !is DeclinationResult.Available) {
            viewModel.refreshDeclination()
        }
    }

    // A light detent each time the heading passes N, E, S or W.
    LaunchedEffect(haptics) {
        val detent = CardinalDetent()
        snapshotFlow { heading.value }.filterNotNull().collect { if (detent.update(it)) haptics.tick() }
    }

    ToolLayout(
        contentPadding = contentPadding,
        header = {
            StatusIsland(compassIsland(state))
            Spacer(Modifier.height(16.dp))
            if (state.isAvailable) HeadingReadout(heading = { heading.value }, state = state)
        },
        instrument = { size ->
            if (state.isAvailable) CompassDial(heading = { heading.value }, size = size)
        },
        details = {
            if (!state.isAvailable) {
                MessagePanel(
                    icon = R.drawable.ic_sensor_off,
                    title = stringResource(R.string.compass_unavailable_title),
                    body = stringResource(R.string.compass_unavailable_body),
                    tone = PanelTone.ERROR,
                )
            } else {
                CompassStats(state)
                CalibrationGuidance(state.calibration)
                TrueNorthPrompt(
                    state = state,
                    blocked = permission.isBlocked,
                    onAllow = { permission.request() },
                    onOpenSettings = { permission.openAppSettings() },
                    onUseMagnetic = { viewModel.setNorthReference(NorthReference.MAGNETIC) },
                )
            }
        },
    )
}

@Composable
private fun compassIsland(state: CompassUiState): IslandStatus = when {
    !state.isAvailable -> IslandStatus(stringResource(R.string.compass_unavailable_title), IslandTone.ERROR, R.drawable.ic_sensor_off)
    state.calibration == CalibrationStatus.INTERFERENCE ->
        IslandStatus(stringResource(R.string.compass_interference), IslandTone.WARNING, R.drawable.ic_magnet)
    state.calibration == CalibrationStatus.NEEDS_CALIBRATION ->
        IslandStatus(stringResource(R.string.compass_calibrate), IslandTone.WARNING, R.drawable.ic_warning, pulsing = true)
    !state.hasHeading -> IslandStatus(stringResource(R.string.compass_waiting), IslandTone.NEUTRAL, pulsing = true)
    state.northReference == NorthReference.TRUE -> when (state.declination) {
        is DeclinationResult.Available -> IslandStatus(stringResource(R.string.compass_true_north), IslandTone.ACTIVE, R.drawable.ic_north)
        DeclinationResult.Loading, null -> IslandStatus(stringResource(R.string.compass_true_north_locating), IslandTone.NEUTRAL, pulsing = true)
        else -> IslandStatus(stringResource(R.string.compass_true_north_fallback), IslandTone.WARNING, R.drawable.ic_north)
    }
    else -> IslandStatus(stringResource(R.string.compass_magnetic_north), IslandTone.ACTIVE, R.drawable.ic_north)
}

@Composable
private fun HeadingReadout(heading: () -> Float?, state: CompassUiState) {
    // Sampled at 10 Hz (like the other readouts): following the raw heading re-laid out the 76 sp
    // text and rebuilt the accessibility description on almost every sensor sample while turning.
    // The sector is derived from the displayed number so the two can never disagree.
    val degrees by rememberSampledState { heading()?.roundToInt()?.mod(360) }
    val cardinal = degrees?.let { CardinalDirection.fromHeading(it.toFloat()) }
    val names = stringArrayResource(R.array.cardinal_names)
    val reference = stringResource(if (state.showsTrueNorth) R.string.compass_true_north else R.string.compass_magnetic_north)
    val description = degrees?.let { d -> stringResource(R.string.compass_heading_description, d, cardinal?.let { names[it.ordinal] } ?: "", reference) }
        ?: stringResource(R.string.compass_waiting)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = degrees?.let { "$it°" } ?: stringResource(R.string.value_unknown),
            style = Readout.hero,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = cardinal?.name ?: stringResource(R.string.value_unknown),
                style = MaterialTheme.typography.headlineMedium,
                color = if (cardinal == CardinalDirection.N) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            )
            Text(
                text = cardinal?.let { names[it.ordinal] } ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CompassStats(state: CompassUiState) {
    val unknown = stringResource(R.string.value_unknown)
    Row(horizontalArrangement = Arrangement.spacedBy(GroupGap), modifier = Modifier.fillMaxWidth()) {
        StatTile(
            label = stringResource(R.string.compass_stat_field),
            value = state.fieldStrengthMicroTesla?.let { stringResource(R.string.compass_stat_field_value, it) } ?: unknown,
            position = GroupPosition.FIRST,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(R.string.compass_stat_accuracy),
            value = when (state.accuracy) {
                SensorAccuracy.HIGH -> stringResource(R.string.accuracy_high)
                SensorAccuracy.MEDIUM -> stringResource(R.string.accuracy_medium)
                SensorAccuracy.LOW -> stringResource(R.string.accuracy_low)
                SensorAccuracy.UNRELIABLE -> stringResource(R.string.accuracy_unreliable)
                null -> unknown
            },
            position = GroupPosition.MIDDLE,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = stringResource(R.string.compass_stat_declination),
            value = (state.declination as? DeclinationResult.Available)
                ?.let { stringResource(R.string.degrees_value, formatSignedDegrees(it.degrees)) }
                ?: stringResource(if (state.northReference == NorthReference.MAGNETIC) R.string.north_magnetic else R.string.value_unknown),
            position = GroupPosition.LAST,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CalibrationGuidance(calibration: CalibrationStatus) {
    AnimatedVisibility(
        visible = calibration != CalibrationStatus.GOOD,
        enter = fadeIn(GyroMotion.effects()) + expandVertically(GyroMotion.spatial()),
        exit = fadeOut(GyroMotion.effects()) + shrinkVertically(GyroMotion.spatial()),
    ) {
        val interference = calibration == CalibrationStatus.INTERFERENCE
        MessagePanel(
            icon = if (interference) R.drawable.ic_magnet else R.drawable.ic_compass,
            title = stringResource(if (interference) R.string.interference_title else R.string.calibration_title),
            body = stringResource(if (interference) R.string.interference_body else R.string.calibration_body),
            tone = PanelTone.WARNING,
            extra = { FigureEight(Modifier.align(Alignment.CenterHorizontally)) },
        )
    }
}

/**
 * Shows the calibration motion. Only composed while guidance is visible.
 *
 * The animated progress is read only in the draw phase and the curve is built once per size,
 * so the loop costs a redraw per frame rather than a recomposition plus a 96-point path rebuild.
 */
@Composable
private fun FigureEight(modifier: Modifier = Modifier) {
    val reducedMotion = LocalReducedMotion.current
    val progress: State<Float> = if (reducedMotion) {
        remember { mutableFloatStateOf(0.125f) }
    } else {
        rememberInfiniteTransition(label = "figureEight")
            .animateFloat(0f, 1f, infiniteRepeatable(tween(2800, easing = LinearEasing)), label = "figureEightProgress")
    }
    val scheme = MaterialTheme.colorScheme
    val description = stringResource(R.string.calibration_animation_description)
    Spacer(
        modifier
            .size(width = 140.dp, height = 64.dp)
            .semantics { contentDescription = description }
            .drawWithCache {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val ax = size.width / 2f - 8.dp.toPx()
                val ay = size.height / 2f - 6.dp.toPx()
                // Lemniscate of Gerono: x = sin t, y = sin t · cos t.
                fun point(t: Float) = Offset(cx + ax * sin(t), cy + ay * 2f * sin(t) * cos(t))
                val path = Path()
                val steps = 96
                for (i in 0..steps) {
                    val p = point((i / steps.toFloat()) * 2f * PI.toFloat())
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                val halo = scheme.primary.copy(alpha = 0.25f)
                val haloRadius = 12.dp.toPx()
                val dotRadius = 6.dp.toPx()
                onDrawBehind {
                    drawPath(path, scheme.outlineVariant, style = stroke)
                    val dot = point(progress.value * 2f * PI.toFloat())
                    drawCircle(halo, radius = haloRadius, center = dot)
                    drawCircle(scheme.primary, radius = dotRadius, center = dot)
                }
            },
    )
}

@Composable
private fun TrueNorthPrompt(
    state: CompassUiState,
    blocked: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onUseMagnetic: () -> Unit,
) {
    val needsPermission = state.northReference == NorthReference.TRUE && state.declination == DeclinationResult.PermissionRequired
    AnimatedVisibility(
        visible = needsPermission,
        enter = fadeIn(GyroMotion.effects()) + expandVertically(GyroMotion.spatial()),
        exit = fadeOut(GyroMotion.effects()) + shrinkVertically(GyroMotion.spatial()),
    ) {
        MessagePanel(
            icon = R.drawable.ic_location,
            title = stringResource(R.string.true_north_permission_title),
            body = stringResource(if (blocked) R.string.true_north_blocked_body else R.string.true_north_permission_body),
            primaryAction = if (blocked) {
                PanelAction(stringResource(R.string.action_open_settings), onOpenSettings)
            } else {
                PanelAction(stringResource(R.string.action_allow_location), onAllow)
            },
            secondaryAction = PanelAction(stringResource(R.string.action_use_magnetic), onUseMagnetic),
        )
    }
}
