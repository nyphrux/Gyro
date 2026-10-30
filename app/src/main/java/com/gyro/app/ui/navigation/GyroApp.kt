package com.gyro.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gyro.app.R
import com.gyro.app.core.prefs.UserPreferences
import com.gyro.app.ui.common.LocalGyroHaptics
import com.gyro.app.ui.common.rememberGyroHaptics
import com.gyro.app.ui.compass.CompassScreen
import com.gyro.app.ui.components.GyroNavBar
import com.gyro.app.ui.components.GyroTopBar
import com.gyro.app.ui.components.TopBarIconButton
import com.gyro.app.ui.settings.SettingsScreen
import com.gyro.app.ui.speed.SpeedometerScreen
import com.gyro.app.ui.theme.GyroMotion
import com.gyro.app.ui.tilt.TiltScreen

/** Height of the floating navigation pill plus its margin; tool content is padded by this. */
private val NavBarSpace = 96.dp

/**
 * App shell. Navigation is a small state machine: three peer tools plus a Settings sub-page.
 * Keys are a saveable enum (type-safe, survives process death), each destination's UI state is
 * kept in a SaveableStateHolder, and ViewModels are Activity-scoped, so switching tools never
 * loses state. Tools that are not on screen leave composition, which stops their sensors.
 *
 * Back: Settings → tools; any tool → Compass; Compass → exit.
 */
@Composable
fun GyroApp(preferences: UserPreferences) {
    var tool by rememberSaveable { mutableStateOf(Tool.COMPASS) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val haptics = rememberGyroHaptics(preferences.hapticsEnabled)
    val stateHolder = rememberSaveableStateHolder()

    KeepScreenOn(preferences.keepScreenOn)
    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = !showSettings && tool != Tool.COMPASS) { tool = Tool.COMPASS }

    CompositionLocalProvider(LocalGyroHaptics provides haptics) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = showSettings,
                transitionSpec = {
                    // Same push/pop motion as the reference app's sub-pages.
                    val forward = targetState
                    (
                        slideInHorizontally(GyroMotion.spatial()) { full -> if (forward) full else -full / 3 } +
                            fadeIn(GyroMotion.fade(220))
                        ) togetherWith (
                        slideOutHorizontally(GyroMotion.spatial()) { full -> if (forward) -full / 3 else full } +
                            fadeOut(GyroMotion.fade(180))
                        )
                },
                label = "settingsTransition",
            ) { settings ->
                if (settings) {
                    stateHolder.SaveableStateProvider("settings") {
                        SettingsScreen(onBack = { showSettings = false })
                    }
                } else {
                    stateHolder.SaveableStateProvider("tools") {
                        ToolsShell(
                            tool = tool,
                            onSelectTool = {
                                if (it != tool) {
                                    haptics.tick()
                                    tool = it
                                }
                            },
                            onOpenSettings = { showSettings = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsShell(tool: Tool, onSelectTool: (Tool) -> Unit, onOpenSettings: () -> Unit) {
    val toolStates = rememberSaveableStateHolder()
    val horizontalInsets = WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal)
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentPadding = PaddingValues(bottom = bottomInset + NavBarSpace)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(horizontalInsets),
    ) {
        Column(Modifier.fillMaxSize()) {
            GyroTopBar(
                title = stringResource(tool.label),
                overline = stringResource(tool.subtitle),
                modifier = Modifier.statusBarsPadding(),
                actions = {
                    TopBarIconButton(R.drawable.ic_tune, stringResource(R.string.nav_settings), onOpenSettings)
                },
            )
            AnimatedContent(
                targetState = tool,
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(GyroMotion.spatial()) { full -> direction * full / 6 } + fadeIn(GyroMotion.fade(220))) togetherWith
                        (slideOutHorizontally(GyroMotion.spatial()) { full -> -direction * full / 6 } + fadeOut(GyroMotion.fade(140)))
                },
                modifier = Modifier.weight(1f),
                label = "toolTransition",
            ) { current ->
                toolStates.SaveableStateProvider(current.name) {
                    when (current) {
                        Tool.COMPASS -> CompassScreen(contentPadding)
                        Tool.SPEED -> SpeedometerScreen(contentPadding)
                        Tool.TILT -> TiltScreen(contentPadding)
                    }
                }
            }
        }
        GyroNavBar(
            selected = tool,
            onSelect = onSelectTool,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
