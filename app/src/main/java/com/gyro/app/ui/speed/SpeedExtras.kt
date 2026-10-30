package com.gyro.app.ui.speed

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.gyro.app.R
import com.gyro.app.core.speed.SpeedUnit
import com.gyro.app.core.speed.SpeedometerUiState
import com.gyro.app.ui.common.formatSpeed
import com.gyro.app.ui.theme.Readout

/** Largest threshold the alert accepts, in display units (well above any road speed in any unit). */
private const val MAX_ALERT = 999f

/**
 * Sets or clears the speed alert. The value is typed in the current display unit and stored in
 * m/s, so switching units later keeps the same real-world threshold.
 *
 * @param onSet receives the new threshold in m/s, or null to switch the alert off.
 */
@Composable
fun SpeedAlertDialog(currentMetersPerSecond: Float?, unit: SpeedUnit, onSet: (Float?) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable {
        mutableStateOf(currentMetersPerSecond?.let { formatSpeed(unit.fromMetersPerSecond(it), unit) } ?: "")
    }
    // Accept a decimal comma as well as a point, whatever the locale's keyboard offers.
    val value = text.replace(',', '.').toFloatOrNull()
    val valid = value != null && value > 0f && value <= MAX_ALERT
    val submit = { if (valid && value != null) onSet(unit.toMetersPerSecond(value)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_bell), contentDescription = null) },
        title = { Text(stringResource(R.string.alert_dialog_title)) },
        text = {
            Column {
                Text(stringResource(R.string.alert_dialog_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() || it == '.' || it == ',' }.take(6) },
                    label = { Text(stringResource(R.string.alert_dialog_field)) },
                    suffix = { Text(unit.symbol) },
                    singleLine = true,
                    isError = text.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = valid) { Text(stringResource(R.string.alert_dialog_set)) }
        },
        dismissButton = {
            Row {
                if (currentMetersPerSecond != null) {
                    TextButton(onClick = { onSet(null) }) { Text(stringResource(R.string.alert_dialog_off)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_action_cancel)) }
            }
        },
    )
}

/**
 * Full-screen head-up display: the speed in huge digits on black, at full brightness, meant to
 * be reflected in a windscreen at night. Mirrored by default, because a reflection reverses
 * left and right when the phone lies flat on the dashboard with its top edge towards the driver.
 *
 * The speed value is the same honest reading as the gauge: a dash when there is no fix.
 */
@Composable
fun SpeedHud(state: SpeedometerUiState, mirrored: Boolean, onToggleMirror: () -> Unit, onClose: () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        HudWindowEffects()
        val scheme = MaterialTheme.colorScheme
        val reading = state.displaySpeed
        val text = reading?.let { formatSpeed(it, state.unit) } ?: stringResource(R.string.value_unknown)
        val color = when {
            reading == null -> scheme.onSurfaceVariant
            state.isOverAlert -> scheme.error
            else -> scheme.primary
        }
        val description = if (reading != null) {
            stringResource(R.string.speed_description, text, unitName(state.unit))
        } else {
            stringResource(R.string.speed_description_none)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(bottom = 72.dp)
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                // Three digits fill most of the width in portrait.
                val digitHeight = min(maxWidth / 2.2f, maxHeight / 1.6f)
                val fontSize = with(LocalDensity.current) { digitHeight.toSp() }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer { scaleX = if (mirrored) -1f else 1f },
                ) {
                    Text(text = text, style = Readout.hero.copy(fontSize = fontSize, lineHeight = fontSize), color = color, maxLines = 1)
                    Text(text = state.unit.symbol, style = MaterialTheme.typography.headlineLarge, color = color.copy(alpha = 0.8f))
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(16.dp),
            ) {
                val dim = Color.White.copy(alpha = 0.55f)
                TextButton(onClick = onToggleMirror) {
                    Text(stringResource(if (mirrored) R.string.hud_unmirror else R.string.hud_mirror), color = dim)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = null, tint = dim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.hud_close), color = dim)
                }
            }
        }
    }
}

/** Full brightness, screen kept on and system bars hidden while the HUD is up; all undone on close. */
@Composable
private fun HudWindowEffects() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window == null) {
            onDispose { }
        } else {
            val previousBrightness = window.attributes.screenBrightness
            window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val insets = WindowCompat.getInsetsController(window, view)
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                insets.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.attributes = window.attributes.apply { screenBrightness = previousBrightness }
            }
        }
    }
}
