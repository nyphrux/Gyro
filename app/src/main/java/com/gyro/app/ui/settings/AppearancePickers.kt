package com.gyro.app.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gyro.app.R
import com.gyro.app.core.prefs.AccentColor
import com.gyro.app.core.prefs.ThemeMode
import com.gyro.app.ui.common.LocalGyroHaptics
import com.gyro.app.ui.theme.GyroMotion
import com.gyro.app.ui.theme.accentSwatch
import com.gyro.app.ui.theme.gyroColorScheme

@StringRes
internal fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.BLACK -> R.string.theme_black
    ThemeMode.NIGHT -> R.string.theme_night
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
    AccentColor.WALLPAPER -> R.string.accent_wallpaper
}

/** One tile per theme, each a miniature of that theme in the current accent. */
@Composable
internal fun ThemePicker(selected: ThemeMode, accent: AccentColor, onSelect: (ThemeMode) -> Unit) {
    val haptics = LocalGyroHaptics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Column(
                modifier = Modifier
                    .weight(1f)
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
                ThemePreview(mode, accent, isSelected)
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
}

@Composable
private fun ThemePreview(mode: ThemeMode, accent: AccentColor, selected: Boolean) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    // System shows its light and dark halves side by side.
    val schemes = remember(context, mode, accent, systemDark) {
        if (mode == ThemeMode.SYSTEM) {
            listOf(
                gyroColorScheme(context, ThemeMode.LIGHT, accent, dark = false),
                gyroColorScheme(context, ThemeMode.DARK, accent, dark = true),
            )
        } else {
            listOf(gyroColorScheme(context, mode, accent, dark = mode != ThemeMode.LIGHT))
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

/** Round colour swatches; Wallpaper shows the wallpaper's own three accent colours. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccentPicker(selected: AccentColor, enabled: Boolean, onSelect: (AccentColor) -> Unit) {
    val haptics = LocalGyroHaptics.current
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .alpha(if (enabled) 1f else 0.38f),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccentColor.entries.forEach { accent ->
            AccentSwatch(
                accent = accent,
                selected = accent == selected,
                enabled = enabled,
                onClick = {
                    if (accent != selected) {
                        haptics.tick()
                        onSelect(accent)
                    }
                },
            )
        }
    }
}

@Composable
private fun AccentSwatch(accent: AccentColor, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val colors: List<Color> = remember(context, accent) {
        if (accent == AccentColor.WALLPAPER) {
            val scheme = gyroColorScheme(context, ThemeMode.LIGHT, AccentColor.WALLPAPER, dark = false)
            listOf(scheme.primary, scheme.secondary, scheme.tertiary)
        } else {
            listOf(accentSwatch(context, accent))
        }
    }
    val ringColor = MaterialTheme.colorScheme.onSurface
    val name = stringResource(accent.labelRes())
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(44.dp)) {
            val outer = size.minDimension / 2f
            val fill = outer - 5.dp.toPx()
            if (selected) drawCircle(ringColor, radius = outer - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
            if (colors.size == 1) {
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
