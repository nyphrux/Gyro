package com.gyro.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** True when the user turned animations off (Developer options / Accessibility "Remove animations"). */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun rememberSystemReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Gyro's motion tokens. Layout motion borrows the reference app's soft, slightly underdamped
 * spring; anything that represents a measurement is critically damped so it never overshoots
 * the true value.
 */
object GyroMotion {
    /** Screen and container movement. */
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)

    /** Colour, alpha and other non-spatial changes. */
    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)

    /** Instrument needles, arcs and bubbles: quick and without overshoot. */
    fun <T> instrument(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 500f)

    fun <T> fade(durationMillis: Int = 180): FiniteAnimationSpec<T> = tween(durationMillis)

    @Composable
    @ReadOnlyComposable
    fun <T> spatialOrSnap(): FiniteAnimationSpec<T> = if (LocalReducedMotion.current) snap() else spatial()

    @Composable
    @ReadOnlyComposable
    fun <T> effectsOrSnap(): FiniteAnimationSpec<T> = if (LocalReducedMotion.current) snap() else effects()
}
