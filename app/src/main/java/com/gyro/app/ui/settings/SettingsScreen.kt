package com.gyro.app.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gyro.app.BuildConfig
import com.gyro.app.R
import com.gyro.app.core.prefs.HudOrientation
import com.gyro.app.core.prefs.NorthReference
import com.gyro.app.core.prefs.SoundSettings
import com.gyro.app.core.prefs.UserPreferences
import com.gyro.app.core.speed.SpeedUnit
import com.gyro.app.core.tilt.SlopeUnit
import com.gyro.app.ui.common.LocalGyroHaptics
import com.gyro.app.ui.common.rememberGyroCues
import com.gyro.app.ui.components.GroupGap
import com.gyro.app.ui.components.GroupPosition
import com.gyro.app.ui.components.GroupedSurface
import com.gyro.app.ui.components.GyroTopBar
import com.gyro.app.ui.components.PillSelector
import com.gyro.app.ui.components.SectionHeader
import com.gyro.app.ui.components.TopBarIconButton
import com.gyro.app.ui.speed.unitName
import com.gyro.app.ui.tilt.slopeUnitName
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val showUpdateSheet by viewModel.showUpdateSheet.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val upToDateMessage = stringResource(R.string.update_up_to_date, viewModel.currentVersion)
    val checkFailedMessage = stringResource(R.string.update_check_failed)

    LaunchedEffect(viewModel) {
        viewModel.updateEvents.collect { event ->
            when (event) {
                // Snackbars suspend until dismissed, so they get their own coroutine.
                is UpdateEvent.UpToDate -> launch { snackbarHostState.showSnackbar(upToDateMessage) }
                UpdateEvent.CheckFailed -> launch { snackbarHostState.showSnackbar(checkFailedMessage) }
                is UpdateEvent.Launch -> try {
                    context.startActivity(event.intent)
                } catch (_: ActivityNotFoundException) {
                    launch { snackbarHostState.showSnackbar(checkFailedMessage) }
                }
            }
        }
    }
    // Picks the install back up after a trip to the "Install unknown apps" setting.
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            GyroTopBar(
                title = stringResource(R.string.settings_title),
                modifier = Modifier.statusBarsPadding(),
                navigation = { TopBarIconButton(R.drawable.ic_back, stringResource(R.string.nav_back), onBack) },
                actions = { UpdateButton(updateState, onClick = viewModel::onUpdateButton) },
            )
            val prefs = preferences
            if (prefs != null) Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
                    SettingsContent(prefs, viewModel)
                    Spacer(Modifier.height(24.dp))
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
    }

    val available = updateState as? UpdateState.Available
    if (showUpdateSheet && available != null) {
        UpdateSheet(
            state = available,
            currentVersion = viewModel.currentVersion,
            onDismiss = viewModel::dismissUpdateSheet,
            onPrimary = viewModel::onPrimaryUpdateAction,
            onCancelDownload = viewModel::cancelDownload,
            onOpenReleasePage = viewModel::openReleasePage,
        )
    }
}

@Composable
private fun SettingsContent(prefs: UserPreferences, viewModel: SettingsViewModel) {
    SectionHeader(stringResource(R.string.settings_appearance))
    // Both pickers start collapsed to one row; the row shown is the one holding the choice.
    var themesExpanded by rememberSaveable { mutableStateOf(false) }
    var accentsExpanded by rememberSaveable { mutableStateOf(false) }
    var editingCustom by rememberSaveable { mutableStateOf(false) }
    val accentsPaused = prefs.themeMode.ignoresAccent
    Group {
        CollapsibleSelectorItem(
            position = GroupPosition.FIRST,
            title = stringResource(R.string.settings_theme),
            summary = stringResource(prefs.themeMode.summaryRes()),
            expanded = themesExpanded,
            onToggle = { themesExpanded = !themesExpanded },
        ) {
            ThemePicker(
                selected = prefs.themeMode,
                accent = prefs.accent,
                customAccent = prefs.customAccent,
                expanded = themesExpanded,
                onSelect = viewModel::setThemeMode,
            )
        }
        CollapsibleSelectorItem(
            position = GroupPosition.LAST,
            title = stringResource(R.string.settings_accent),
            summary = if (accentsPaused) {
                stringResource(R.string.settings_accent_paused, stringResource(prefs.themeMode.labelRes()))
            } else {
                stringResource(prefs.accent.labelRes())
            },
            expanded = accentsExpanded,
            onToggle = { accentsExpanded = !accentsExpanded },
        ) {
            AccentPicker(
                selected = prefs.accent,
                customAccent = prefs.customAccent,
                enabled = !accentsPaused,
                expanded = accentsExpanded,
                onSelect = viewModel::setAccent,
                onEditCustom = { editingCustom = true },
            )
        }
    }
    if (editingCustom) {
        CustomAccentDialog(
            initial = prefs.customAccent,
            onPick = {
                viewModel.setCustomAccent(it)
                editingCustom = false
            },
            onDismiss = { editingCustom = false },
        )
    }

    SectionHeader(stringResource(R.string.settings_instruments))
    Group {
        SelectorItem(GroupPosition.FIRST, stringResource(R.string.settings_speed_unit)) {
            PillSelector(
                options = SpeedUnit.entries,
                selected = prefs.speedUnit,
                onSelect = viewModel::setSpeedUnit,
                label = { it.symbol },
                accessibilityLabel = { unitName(it) },
            )
        }
        SelectorItem(
            GroupPosition.MIDDLE,
            stringResource(R.string.settings_north),
            summary = stringResource(R.string.settings_north_summary),
        ) {
            PillSelector(
                options = NorthReference.entries,
                selected = prefs.northReference,
                onSelect = viewModel::setNorthReference,
                label = { stringResource(if (it == NorthReference.MAGNETIC) R.string.north_magnetic else R.string.north_true) },
            )
        }
        SwitchItem(
            position = GroupPosition.MIDDLE,
            title = stringResource(R.string.settings_show_sun),
            summary = stringResource(R.string.settings_show_sun_summary),
            checked = prefs.showSun,
            onCheckedChange = viewModel::setShowSun,
        )
        SelectorItem(
            GroupPosition.MIDDLE,
            stringResource(R.string.settings_hud_orientation),
            summary = stringResource(R.string.settings_hud_orientation_summary),
        ) {
            PillSelector(
                options = HudOrientation.entries,
                selected = prefs.hudOrientation,
                onSelect = viewModel::setHudOrientation,
                label = { stringResource(if (it == HudOrientation.PORTRAIT) R.string.hud_portrait else R.string.hud_landscape) },
            )
        }
        SwitchItem(
            position = GroupPosition.LAST,
            title = stringResource(R.string.settings_keep_screen_on),
            summary = stringResource(R.string.settings_keep_screen_on_summary),
            checked = prefs.keepScreenOn,
            onCheckedChange = viewModel::setKeepScreenOn,
        )
    }

    SectionHeader(stringResource(R.string.settings_feedback))
    Group {
        SwitchItem(
            position = GroupPosition.FIRST,
            title = stringResource(R.string.settings_haptics),
            summary = stringResource(R.string.settings_haptics_summary),
            checked = prefs.hapticsEnabled,
            onCheckedChange = viewModel::setHapticsEnabled,
        )
        SwitchItem(
            position = GroupPosition.LAST,
            title = stringResource(R.string.settings_level_tone),
            summary = stringResource(R.string.settings_level_tone_summary),
            checked = prefs.levelTone,
            onCheckedChange = viewModel::setLevelTone,
        )
    }

    SoundSection(prefs.sound, onChange = viewModel::setSound)

    SectionHeader(stringResource(R.string.settings_tilt))
    Group {
        SelectorItem(GroupPosition.FIRST, stringResource(R.string.settings_slope_unit)) {
            PillSelector(
                options = SlopeUnit.entries,
                selected = prefs.slopeUnit,
                onSelect = viewModel::setSlopeUnit,
                label = { slopeUnitName(it) },
            )
        }
        GroupedSurface(GroupPosition.LAST) {
            val haptics = LocalGyroHaptics.current
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.settings_tilt_zero), style = MaterialTheme.typography.titleSmall) },
                supportingContent = {
                    Text(stringResource(if (prefs.tiltReference != null) R.string.settings_tilt_zero_set else R.string.settings_tilt_zero_unset))
                },
                trailingContent = {
                    TextButton(
                        onClick = {
                            haptics.tick()
                            viewModel.resetTiltZero()
                        },
                        enabled = prefs.tiltReference != null,
                    ) { Text(stringResource(R.string.action_reset)) }
                },
            )
        }
    }

    SectionHeader(stringResource(R.string.settings_about))
    Group {
        val context = LocalContext.current
        LinkItem(
            position = GroupPosition.FIRST,
            title = stringResource(R.string.app_name),
            summary = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            linkDescription = stringResource(R.string.settings_version_link),
            onClick = { context.openUrl("https://github.com/nyphrux/Gyro/releases/tag/${BuildConfig.VERSION_NAME}") },
        )
        GroupedSurface(GroupPosition.MIDDLE) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.titleSmall) },
                supportingContent = { Text(stringResource(R.string.settings_privacy_summary)) },
            )
        }
        LinkItem(
            position = GroupPosition.LAST,
            title = stringResource(R.string.settings_developer),
            summary = stringResource(R.string.settings_developer_info),
            linkDescription = stringResource(R.string.settings_developer_link),
            onClick = { context.openUrl("https://github.com/nyphrux") },
        )
    }
}

/**
 * The beep used by "Beep when level" and the speed alert. Sliders change a local draft while
 * dragging and save when let go, so DataStore is written once per adjustment, not per frame.
 */
@Composable
private fun SoundSection(saved: SoundSettings, onChange: (SoundSettings) -> Unit) {
    var draft by remember(saved) { mutableStateOf(saved) }
    val cues = rememberGyroCues()
    val haptics = LocalGyroHaptics.current
    SectionHeader(stringResource(R.string.settings_sound))
    Group {
        SliderItem(
            position = GroupPosition.FIRST,
            title = stringResource(R.string.settings_sound_volume),
            value = stringResource(R.string.settings_sound_volume_value, (draft.volume * 100).roundToInt()),
            sliderValue = draft.volume,
            range = SoundSettings.MIN_VOLUME..SoundSettings.MAX_VOLUME,
            steps = 18, // 5 % steps
            onValueChange = { draft = draft.copy(volume = (it * 20).roundToInt() / 20f) },
            onValueChangeFinished = { onChange(draft) },
        )
        SliderItem(
            position = GroupPosition.MIDDLE,
            title = stringResource(R.string.settings_sound_length),
            value = stringResource(R.string.settings_sound_length_value, draft.lengthMillis),
            sliderValue = draft.lengthMillis.toFloat(),
            range = SoundSettings.MIN_LENGTH_MS.toFloat()..SoundSettings.MAX_LENGTH_MS.toFloat(),
            steps = 18, // 50 ms steps
            onValueChange = { draft = draft.copy(lengthMillis = (it / 50f).roundToInt() * 50) },
            onValueChangeFinished = { onChange(draft) },
        )
        SliderItem(
            position = GroupPosition.MIDDLE,
            title = stringResource(R.string.settings_sound_pitch),
            value = stringResource(R.string.settings_sound_pitch_value, draft.pitchHz),
            sliderValue = draft.pitchHz.toFloat(),
            range = SoundSettings.MIN_PITCH_HZ.toFloat()..SoundSettings.MAX_PITCH_HZ.toFloat(),
            onValueChange = { draft = draft.copy(pitchHz = (it / 10f).roundToInt() * 10) },
            onValueChangeFinished = { onChange(draft) },
        )
        GroupedSurface(GroupPosition.LAST) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.settings_sound_preview), style = MaterialTheme.typography.titleSmall) },
                supportingContent = { Text(stringResource(R.string.settings_sound_preview_summary)) },
                trailingContent = {
                    FilledTonalButton(onClick = {
                        haptics.tick()
                        cues.preview(draft)
                    }) {
                        Icon(painterResource(R.drawable.ic_sound), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_sound_play))
                    }
                },
            )
        }
    }
}

@Composable
private fun SliderItem(
    position: GroupPosition,
    title: String,
    value: String,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    steps: Int = 0,
) {
    GroupedSurface(position) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = sliderValue.coerceIn(range.start, range.endInclusive),
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = range,
                steps = steps,
                modifier = Modifier.semantics { contentDescription = title },
            )
        }
    }
}

/** A row that opens a web page: the whole row is the button, with an "opens outside" icon. */
@Composable
private fun LinkItem(position: GroupPosition, title: String, summary: String, linkDescription: String, onClick: () -> Unit) {
    GroupedSurface(position) {
        ListItem(
            modifier = Modifier.clickable(onClickLabel = linkDescription, role = Role.Button, onClick = onClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
            supportingContent = { Text(summary) },
            trailingContent = {
                Icon(
                    painterResource(R.drawable.ic_open_external),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            },
        )
    }
}

/** Opens a link in the browser; does nothing (rather than crash) if no browser is installed. */
private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
    }
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(GroupGap), content = content)
}

@Composable
private fun SelectorItem(
    position: GroupPosition,
    title: String,
    summary: String? = null,
    content: @Composable () -> Unit,
) {
    GroupedSurface(position) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * A selector whose content can fold down to a single row. The header row (title, summary and
 * chevron) toggles it, and is announced as expanded or collapsed.
 */
@Composable
private fun CollapsibleSelectorItem(
    position: GroupPosition,
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val haptics = LocalGyroHaptics.current
    val expandLabel = stringResource(R.string.action_show_all)
    val collapseLabel = stringResource(R.string.action_show_less)
    GroupedSurface(position) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = if (expanded) collapseLabel else expandLabel, role = Role.Button) {
                        haptics.tick()
                        onToggle()
                    }
                    .semantics { stateDescription = if (expanded) collapseLabel else expandLabel }
                    .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                ExpandChevron(expanded)
            }
            Box(Modifier.padding(horizontal = 16.dp)) { content() }
        }
    }
}

/** Whole row is the toggle target (role = Switch), so the label is part of the touch area. */
@Composable
private fun SwitchItem(
    position: GroupPosition,
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = LocalGyroHaptics.current
    GroupedSurface(position) {
        ListItem(
            modifier = Modifier.toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = {
                    haptics.tick()
                    onCheckedChange(it)
                },
            ),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall) },
            supportingContent = { Text(summary) },
            trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        )
    }
}
