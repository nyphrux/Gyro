package com.gyro.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gyro.app.core.prefs.AccentColor
import com.gyro.app.core.prefs.DEFAULT_CUSTOM_ACCENT
import com.gyro.app.core.prefs.ThemeMode

private val GyroShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun ThemeMode.isDark(): Boolean = isDark(systemDark = isSystemInDarkTheme())

/** Whether this theme is dark, given whether the system is (only SYSTEM depends on it). */
fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT, ThemeMode.PAPER, ThemeMode.CONTRAST -> false
    ThemeMode.DARK, ThemeMode.BLACK, ThemeMode.NIGHT, ThemeMode.DIM, ThemeMode.MIDNIGHT, ThemeMode.TERMINAL -> true
}

@Composable
fun GyroTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentColor = AccentColor.TEAL,
    customAccent: Int = DEFAULT_CUSTOM_ACCENT,
    reducedMotion: Boolean = rememberSystemReducedMotion(),
    content: @Composable () -> Unit,
) {
    val dark = themeMode.isDark()
    val context = LocalContext.current
    val colorScheme = remember(context, themeMode, accent, customAccent, dark) { gyroColorScheme(context, themeMode, accent, dark, customAccent) }
    val semanticColors = remember(context, themeMode, accent, customAccent, dark) {
        val base = when {
            themeMode == ThemeMode.NIGHT -> NightSemanticColors
            themeMode == ThemeMode.TERMINAL -> TerminalSemanticColors
            themeMode == ThemeMode.BLACK -> BlackSemanticColors
            dark -> DarkSemanticColors
            else -> LightSemanticColors
        }
        base.copy(islandActive = islandAccent(context, themeMode, accent, customAccent))
    }
    CompositionLocalProvider(
        LocalGyroColors provides semanticColors,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GyroTypography,
            shapes = GyroShapes,
            content = content,
        )
    }
}

/** Convenience accessor: `GyroThemeColors.current.success`. */
object GyroThemeColors {
    val current: GyroSemanticColors
        @Composable get() = LocalGyroColors.current
}
