package com.gyro.app.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gyro.app.R
import com.gyro.app.core.prefs.AccentColor
import com.gyro.app.core.prefs.ThemeMode
import com.gyro.app.ui.common.LocalGyroHaptics
import com.gyro.app.ui.theme.GyroMotion
import com.gyro.app.ui.theme.accentSwatch
import com.gyro.app.ui.theme.gyroColorScheme
import com.gyro.app.ui.theme.isDark

@StringRes
internal fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.BLACK -> R.string.theme_black
    ThemeMode.NIGHT -> R.string.theme_night
    ThemeMode.DIM -> R.string.theme_dim
    ThemeMode.MIDNIGHT -> R.string.theme_midnight
    ThemeMode.PAPER -> R.string.theme_paper
    ThemeMode.CONTRAST -> R.string.theme_contrast
    ThemeMode.TERMINAL -> R.string.theme_terminal
}

@StringRes
internal fun ThemeMode.summaryRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system_summary
    ThemeMode.LIGHT -> R.string.theme_light_summary
    ThemeMode.DARK -> R.string.theme_dark_summary
    ThemeMode.BLACK -> R.string.theme_black_summary
    ThemeMode.NIGHT -> R.string.theme_night_summary
    ThemeMode.DIM -> R.string.theme_dim_summary
    ThemeMode.MIDNIGHT -> R.string.theme_midnight_summary
    ThemeMode.PAPER -> R.string.theme_paper_summary
    ThemeMode.CONTRAST -> R.string.theme_contrast_summary
    ThemeMode.TERMINAL -> R.string.theme_terminal_summary
}

@StringRes
internal fun AccentColor.labelRes(): Int = when (this) {
    AccentColor.TEAL -> R.string.accent_teal
    AccentColor.BLUE -> R.string.accent_blue
    AccentColor.INDIGO -> R.string.accent_indigo
    AccentColor.VIOLET -> R.string.accent_violet
    AccentColor.FUCHSIA -> R.string.accent_fuchsia
    AccentColor.ROSE -> R.string.accent_rose
    AccentColor.AMBER -> R.string.accent_amber
    AccentColor.LIME -> R.string.accent_lime
    AccentColor.GREEN -> R.string.accent_green
    AccentColor.MOCHA -> R.string.accent_mocha
    AccentColor.GRAPHITE -> R.string.accent_graphite
    AccentColor.RED -> R.string.accent_red
    AccentColor.ORANGE -> R.string.accent_orange
    AccentColor.GOLD -> R.string.accent_gold
    AccentColor.OLIVE -> R.string.accent_olive
    AccentColor.MINT -> R.string.accent_mint
    AccentColor.CYAN -> R.string.accent_cyan
    AccentColor.SKY -> R.string.accent_sky
    AccentColor.NAVY -> R.string.accent_navy
    AccentColor.LAVENDER -> R.string.accent_lavender
    AccentColor.PINK -> R.string.accent_pink
    AccentColor.SLATE -> R.string.accent_slate
    AccentColor.WALLPAPER -> R.string.accent_wallpaper
    AccentColor.CUSTOM -> R.string.accent_custom
}

/** Themes per row in the theme grid. */
private const val THEME_COLUMNS = 5
private val SwatchSize = 48.dp
private val SwatchGap = 8.dp

/**
 * A grid that, collapsed, shows only one row: the one holding the current choice, so what is
 * selected is always visible. Expanding reveals the other rows around it in place.
 */
@Composable
private fun <T> CollapsibleGrid(
    items: List<T>,
    columns: Int,
    selected: T,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    rowSpacing: Dp = SwatchGap,
    cell: @Composable (T) -> Unit,
) {
    val rows = items.chunked(columns.coerceAtLeast(1))
    val selectedRow = rows.indexOfFirst { selected in it }.coerceAtLeast(0)
    Column(modifier.fillMaxWidth().selectableGroup()) {
        rows.forEachIndexed { index, row ->
            AnimatedVisibility(
                visible = expanded || index == selectedRow,
                enter = fadeIn(GyroMotion.effects()) + expandVertically(GyroMotion.spatial()),
                exit = fadeOut(GyroMotion.fade(120)) + shrinkVertically(GyroMotion.spatial()),
            ) {
                // The gap sits above every row but the first visible one.
                val firstVisible = if (expanded) index == 0 else true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (firstVisible) 0.dp else rowSpacing),
                    horizontalArrangement = Arrangement.spacedBy(SwatchGap),
                ) {
                    row.forEach { item -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { cell(item) } }
                    // Keep a short last row on the same column grid.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Chevron for a collapsible section header; turns to point up when open. */
@Composable
internal fun ExpandChevron(expanded: Boolean, modifier: Modifier = Modifier) {
    val angle by animateFloatAsState(if (expanded) 180f else 0f, GyroMotion.spatialOrSnap(), label = "chevron")
    Icon(
        painterResource(R.drawable.ic_expand),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .size(24.dp)
            .rotate(angle),
    )
}

/** One tile per theme, each a miniature of that theme in the current accent. */
@Composable
internal fun ThemePicker(selected: ThemeMode, accent: AccentColor, customAccent: Int, expanded: Boolean, onSelect: (ThemeMode) -> Unit) {
    val haptics = LocalGyroHaptics.current
    CollapsibleGrid(items = ThemeMode.entries, columns = THEME_COLUMNS, selected = selected, expanded = expanded) { mode ->
        val isSelected = mode == selected
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .selectable(
                    selected = isSelected,
                    role = Role.RadioButton,
                    onClick = {
                        if (!isSelected) {
                            haptics.tick()
                            onSelect(mode)
                        }
                    },
                )
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ThemePreview(mode, accent, customAccent, isSelected)
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(mode.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ThemePreview(mode: ThemeMode, accent: AccentColor, customAccent: Int, selected: Boolean) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    // System shows its light and dark halves side by side.
    val schemes = remember(context, mode, accent, customAccent, systemDark) {
        if (mode == ThemeMode.SYSTEM) {
            listOf(
                gyroColorScheme(context, ThemeMode.LIGHT, accent, dark = false, custom = customAccent),
                gyroColorScheme(context, ThemeMode.DARK, accent, dark = true, custom = customAccent),
            )
        } else {
            listOf(gyroColorScheme(context, mode, accent, dark = mode.isDark(systemDark), custom = customAccent))
        }
    }
    val ring by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        GyroMotion.effectsOrSnap(),
        label = "themeRing",
    )
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .border(if (selected) 2.dp else 1.dp, ring, shape),
    ) {
        Row(Modifier.fillMaxSize()) {
            schemes.forEach { scheme ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(scheme.surface)
                        .padding(6.dp),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(scheme.primary),
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(scheme.surfaceContainerHigh),
                    )
                }
            }
        }
        if (selected) {
            // A check as well as the ring, so the choice is not shown by colour alone.
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

/**
 * Round colour swatches, as many per row as fit. Wallpaper shows the wallpaper's own three
 * accent colours; Custom shows the chosen colour inside a rainbow ring and opens a picker.
 */
@Composable
internal fun AccentPicker(
    selected: AccentColor,
    customAccent: Int,
    enabled: Boolean,
    expanded: Boolean,
    onSelect: (AccentColor) -> Unit,
    onEditCustom: () -> Unit,
) {
    val haptics = LocalGyroHaptics.current
    BoxWithConstraints(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f)) {
        val columns = ((maxWidth + SwatchGap) / (SwatchSize + SwatchGap)).toInt().coerceAtLeast(1)
        CollapsibleGrid(items = AccentColor.entries, columns = columns, selected = selected, expanded = expanded) { accent ->
            AccentSwatch(
                accent = accent,
                customAccent = customAccent,
                selected = accent == selected,
                enabled = enabled,
                onClick = {
                    when {
                        accent == AccentColor.CUSTOM -> {
                            haptics.tick()
                            onEditCustom()
                        }
                        accent != selected -> {
                            haptics.tick()
                            onSelect(accent)
                        }
                    }
                },
            )
        }
    }
}

private val Rainbow = listOf(
    Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFCC00), Color(0xFF34C759),
    Color(0xFF00C7BE), Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF3B30),
)

@Composable
private fun AccentSwatch(accent: AccentColor, customAccent: Int, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val colors: List<Color> = remember(context, accent, customAccent) {
        if (accent == AccentColor.WALLPAPER) {
            val scheme = gyroColorScheme(context, ThemeMode.LIGHT, AccentColor.WALLPAPER, dark = false)
            listOf(scheme.primary, scheme.secondary, scheme.tertiary)
        } else {
            listOf(accentSwatch(context, accent, customAccent))
        }
    }
    val ringColor = MaterialTheme.colorScheme.onSurface
    val name = stringResource(accent.labelRes())
    val isCustom = accent == AccentColor.CUSTOM
    Box(
        modifier = Modifier
            .size(SwatchSize)
            .clip(CircleShape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(44.dp)) {
            val outer = size.minDimension / 2f
            val fill = outer - 5.dp.toPx()
            if (selected) drawCircle(ringColor, radius = outer - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            if (isCustom) {
                // The rainbow band marks "any colour"; the chosen colour sits inside it.
                drawCircle(Brush.sweepGradient(Rainbow, center), radius = fill)
                drawCircle(colors[0], radius = fill - 4.dp.toPx())
            } else if (colors.size == 1) {
                drawCircle(colors[0], radius = fill)
            } else {
                val sweep = 360f / colors.size
                val topLeft = Offset(center.x - fill, center.y - fill)
                val arcSize = Size(fill * 2f, fill * 2f)
                colors.forEachIndexed { i, color ->
                    drawArc(color, startAngle = -90f + i * sweep, sweepAngle = sweep, useCenter = true, topLeft = topLeft, size = arcSize)
                }
            }
        }
        if (selected) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = if (colors[0].luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Picks the custom accent: hue, saturation and brightness sliders over live gradients, plus a
 * hex field for an exact colour. Gyro builds the full light and dark scheme from whatever is
 * picked, keeping text contrast, so no choice can make the app unreadable.
 */
@Composable
internal fun CustomAccentDialog(initial: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val start = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by rememberSaveable { mutableFloatStateOf(start[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(start[1]) }
    var brightness by rememberSaveable { mutableFloatStateOf(start[2]) }
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
    val hex = "%06X".format(argb and 0xFFFFFF)
    // The field keeps what is typed until it is a valid colour; sliders overwrite it.
    var typed by rememberSaveable { mutableStateOf<String?>(null) }

    fun hsv(h: Float = hue, s: Float = saturation, v: Float = brightness) = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_accent_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                    Spacer(Modifier.width(16.dp))
                    OutlinedTextField(
                        value = typed ?: hex,
                        onValueChange = { input ->
                            val clean = input.removePrefix("#").filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }.take(6).uppercase()
                            typed = clean
                            if (clean.length == 6) {
                                val out = FloatArray(3)
                                android.graphics.Color.colorToHSV(clean.toInt(16) or 0xFF000000.toInt(), out)
                                hue = out[0]
                                saturation = out[1]
                                brightness = out[2]
                            }
                        },
                        prefix = { Text("#") },
                        label = { Text(stringResource(R.string.custom_accent_hex)) },
                        singleLine = true,
                        isError = typed != null && typed?.length != 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                GradientSlider(
                    label = stringResource(R.string.custom_accent_hue),
                    value = hue / 360f,
                    onValueChange = {
                        hue = it * 360f
                        typed = null
                    },
                    brush = Brush.horizontalGradient((0..6).map { hsv(h = it * 60f, s = 1f, v = 1f) }),
                )
                GradientSlider(
                    label = stringResource(R.string.custom_accent_saturation),
                    value = saturation,
                    onValueChange = {
                        saturation = it
                        typed = null
                    },
                    brush = Brush.horizontalGradient(listOf(hsv(s = 0f), hsv(s = 1f))),
                )
                GradientSlider(
                    label = stringResource(R.string.custom_accent_brightness),
                    value = brightness,
                    onValueChange = {
                        brightness = it
                        typed = null
                    },
                    brush = Brush.horizontalGradient(listOf(hsv(v = 0f), hsv(v = 1f))),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(Color(argb).toArgb()) }) { Text(stringResource(R.string.custom_accent_use)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_action_cancel)) }
        },
    )
}

@Composable
private fun GradientSlider(label: String, value: Float, onValueChange: (Float) -> Unit, brush: Brush) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .background(brush),
        )
        Slider(value = value.coerceIn(0f, 1f), onValueChange = onValueChange, modifier = Modifier.semantics { contentDescription = label })
    }
}
