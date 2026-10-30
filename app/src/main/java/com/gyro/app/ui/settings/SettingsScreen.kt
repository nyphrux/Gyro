package com.gyro.app.ui.settings

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import com.gyro.app.BuildConfig
import com.gyro.app.R
import com.gyro.app.core.prefs.NorthReference
import com.gyro.app.core.prefs.ThemeMode
import com.gyro.app.core.prefs.UserPreferences
import com.gyro.app.core.speed.SpeedUnit
import com.gyro.app.ui.common.LocalGyroHaptics
import com.gyro.app.ui.components.GroupGap
import com.gyro.app.ui.components.GroupPosition
import com.gyro.app.ui.components.GroupedSurface
import com.gyro.app.ui.components.GyroTopBar
import com.gyro.app.ui.components.PillSelector
import com.gyro.app.ui.components.SectionHeader
import com.gyro.app.ui.components.TopBarIconButton
import com.gyro.app.ui.speed.unitName
import androidx.core.net.toUri

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
    Group {
        SelectorItem(
            GroupPosition.FIRST,
            stringResource(R.string.settings_theme),
            summary = when (prefs.themeMode) {
                ThemeMode.SYSTEM -> stringResource(R.string.theme_system_summary)
                ThemeMode.DARK -> stringResource(R.string.theme_dark_summary)
                ThemeMode.LIGHT -> stringResource(R.string.theme_light_summary)
                ThemeMode.BLACK -> stringResource(R.string.theme_black_summary)
                ThemeMode.NIGHT -> stringResource(R.string.theme_night_summary)
            },
        ) {
            ThemePicker(selected = prefs.themeMode, accent = prefs.accent, onSelect = viewModel::setThemeMode)
        }
        SelectorItem(
            GroupPosition.LAST,
            stringResource(R.string.settings_accent),
            summary = if (prefs.themeMode == ThemeMode.NIGHT) {
                stringResource(R.string.settings_accent_night)
            } else {
                stringResource(prefs.accent.labelRes())
            },
        ) {
            AccentPicker(selected = prefs.accent, enabled = prefs.themeMode != ThemeMode.NIGHT, onSelect = viewModel::setAccent)
        }
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
            position = GroupPosition.LAST,
            title = stringResource(R.string.settings_keep_screen_on),
            summary = stringResource(R.string.settings_keep_screen_on_summary),
            checked = prefs.keepScreenOn,
            onCheckedChange = viewModel::setKeepScreenOn,
        )
    }

    SectionHeader(stringResource(R.string.settings_feedback))
    SwitchItem(
        position = GroupPosition.SINGLE,
        title = stringResource(R.string.settings_haptics),
        summary = stringResource(R.string.settings_haptics_summary),
        checked = prefs.hapticsEnabled,
        onCheckedChange = viewModel::setHapticsEnabled,
    )

    SectionHeader(stringResource(R.string.settings_tilt))
    GroupedSurface(GroupPosition.SINGLE) {
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

    SectionHeader(stringResource(R.string.settings_about))
    Group {
        val context = LocalContext.current

        GroupedSurface(GroupPosition.FIRST) {
            ListItem(
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, "https://github.com/nyphrux/Gyro/releases/tag/${BuildConfig.VERSION_NAME}".toUri())
                    context.startActivity(intent)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall) },
                supportingContent = { Text("${stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)} " + stringResource(R.string.settings_version_info)) },
            )
        }
        GroupedSurface(GroupPosition.MIDDLE) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.titleSmall) },
                supportingContent = { Text(stringResource(R.string.settings_privacy_summary)) },
            )
        }
        GroupedSurface(GroupPosition.LAST) {
            ListItem(
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, "https://github.com/nyphrux".toUri())
                    context.startActivity(intent)
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(stringResource(R.string.settings_developer), style = MaterialTheme.typography.titleSmall) },
                supportingContent = { Text(stringResource(R.string.settings_developer_info)) },
            )
        }
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
