package com.gyro.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Gyro's palette: a deep "instrument teal" primary, a coral tertiary reserved for north and
 * other datum markers, on cool neutral surfaces. Tonal surface containers carry depth, as in
 * the DroidBubble reference, rather than heavy shadows or gradients.
 */

internal val LightColors = lightColorScheme(
    primary = Color(0xFF00687A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFABEDFF),
    onPrimaryContainer = Color(0xFF001F26),
    secondary = Color(0xFF4B6268),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCEE7EE),
    onSecondaryContainer = Color(0xFF061F24),
    tertiary = Color(0xFFA33A2A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDAD4),
    onTertiaryContainer = Color(0xFF3E0400),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF5FAFB),
    onBackground = Color(0xFF171D1E),
    surface = Color(0xFFF5FAFB),
    onSurface = Color(0xFF171D1E),
    surfaceVariant = Color(0xFFDBE4E6),
    onSurfaceVariant = Color(0xFF3F484A),
    outline = Color(0xFF6F797A),
    outlineVariant = Color(0xFFBFC8CA),
    inverseSurface = Color(0xFF2C3132),
    inverseOnSurface = Color(0xFFECF2F3),
    inversePrimary = Color(0xFF83D2E7),
    surfaceDim = Color(0xFFD5DBDC),
    surfaceBright = Color(0xFFF5FAFB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF4F5),
    surfaceContainer = Color(0xFFE9EFF0),
    surfaceContainerHigh = Color(0xFFE3E9EA),
    surfaceContainerHighest = Color(0xFFDEE3E5),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF83D2E7),
    onPrimary = Color(0xFF003641),
    primaryContainer = Color(0xFF004E5C),
    onPrimaryContainer = Color(0xFFABEDFF),
    secondary = Color(0xFFB2CBD2),
    onSecondary = Color(0xFF1C3439),
    secondaryContainer = Color(0xFF334A50),
    onSecondaryContainer = Color(0xFFCEE7EE),
    tertiary = Color(0xFFFFB4A6),
    onTertiary = Color(0xFF5F1509),
    tertiaryContainer = Color(0xFF7F2A1D),
    onTertiaryContainer = Color(0xFFFFDAD4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1415),
    onBackground = Color(0xFFDEE3E5),
    surface = Color(0xFF0E1415),
    onSurface = Color(0xFFDEE3E5),
    surfaceVariant = Color(0xFF3F484A),
    onSurfaceVariant = Color(0xFFBFC8CA),
    outline = Color(0xFF899294),
    outlineVariant = Color(0xFF3F484A),
    inverseSurface = Color(0xFFDEE3E5),
    inverseOnSurface = Color(0xFF2C3132),
    inversePrimary = Color(0xFF00687A),
    surfaceDim = Color(0xFF0E1415),
    surfaceBright = Color(0xFF343A3B),
    surfaceContainerLowest = Color(0xFF090F10),
    surfaceContainerLow = Color(0xFF171D1E),
    surfaceContainer = Color(0xFF1B2122),
    surfaceContainerHigh = Color(0xFF252B2C),
    surfaceContainerHighest = Color(0xFF303637),
)

/** Semantic colours Material 3 does not provide. Fixed under every accent (only Night and Terminal recolour them). */
@Immutable
data class GyroSemanticColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    /** The status island is always near-black, like a camera-cutout pill. */
    val island: Color,
    val onIsland: Color,
    val onIslandMuted: Color,
    /** Island tone colours: bright enough to read on the island. [islandActive] follows the accent. */
    val islandActive: Color = Color(0xFF6FD8EF),
    val islandSuccess: Color = Color(0xFF7EE29B),
    val islandWarning: Color = Color(0xFFFFC56E),
    val islandError: Color = Color(0xFFFF8A7A),
    val islandNeutral: Color = Color(0xFF9AA4A6),
)

internal val LightSemanticColors = GyroSemanticColors(
    success = Color(0xFF1B6D3A),
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFA4F4B4),
    onSuccessContainer = Color(0xFF00210C),
    warning = Color(0xFF855400),
    warningContainer = Color(0xFFFFDDB7),
    onWarningContainer = Color(0xFF2A1700),
    island = Color(0xFF0B0F10),
    onIsland = Color(0xFFF2F6F7),
    onIslandMuted = Color(0xFF9AA4A6),
)

internal val DarkSemanticColors = GyroSemanticColors(
    success = Color(0xFF89D89A),
    onSuccess = Color(0xFF00391A),
    successContainer = Color(0xFF005228),
    onSuccessContainer = Color(0xFFA4F4B4),
    warning = Color(0xFFFFB95C),
    warningContainer = Color(0xFF653E00),
    onWarningContainer = Color(0xFFFFDDB7),
    island = Color(0xFF000000),
    onIsland = Color(0xFFF2F6F7),
    onIslandMuted = Color(0xFF9AA4A6),
)

/** On a pure black page a black island would vanish, so it is lifted to a dark grey. */
internal val BlackSemanticColors = DarkSemanticColors.copy(island = Color(0xFF1E2224))

/** Night mode: reds and red-oranges only. States stay distinguishable by icon and wording. */
internal val NightSemanticColors = GyroSemanticColors(
    success = Color(0xFFFFA477),
    onSuccess = Color(0xFF2A0800),
    successContainer = Color(0xFF4A1A06),
    onSuccessContainer = Color(0xFFFFC9AE),
    warning = Color(0xFFFF7F50),
    warningContainer = Color(0xFF4F1A06),
    onWarningContainer = Color(0xFFFFC2A8),
    island = Color(0xFF1F0605),
    onIsland = Color(0xFFF0645A),
    onIslandMuted = Color(0xFF9A3B33),
    islandActive = Color(0xFFFF5A4E),
    islandSuccess = Color(0xFFFFA477),
    islandWarning = Color(0xFFFF7F50),
    islandError = Color(0xFFFF3B30),
    islandNeutral = Color(0xFF9A3B33),
)

/** Terminal: greens on black, with amber for warnings and errors like an old amber/green CRT. */
internal val TerminalSemanticColors = GyroSemanticColors(
    success = Color(0xFFA8FFBE),
    onSuccess = Color(0xFF002A0D),
    successContainer = Color(0xFF0A3D1A),
    onSuccessContainer = Color(0xFFC9FFD6),
    warning = Color(0xFFFFC14D),
    warningContainer = Color(0xFF3D2A00),
    onWarningContainer = Color(0xFFFFDFA0),
    island = Color(0xFF031A0A),
    onIsland = Color(0xFF7CFF9A),
    onIslandMuted = Color(0xFF2F8A47),
    islandActive = Color(0xFF33FF66),
    islandSuccess = Color(0xFFA8FFBE),
    islandWarning = Color(0xFFFFC14D),
    islandError = Color(0xFFFFB000),
    islandNeutral = Color(0xFF2F8A47),
)

val LocalGyroColors = staticCompositionLocalOf { LightSemanticColors }
