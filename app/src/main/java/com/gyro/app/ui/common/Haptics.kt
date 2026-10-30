package com.gyro.app.ui.common

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/**
 * Thin wrapper over [View.performHapticFeedback]. View-based feedback respects the system
 * "touch feedback" setting and needs no VIBRATE permission. Gated by Gyro's own toggle.
 */
@Stable
class GyroHaptics(private val view: View?, private val enabled: Boolean) {
    /** A light detent, e.g. passing a cardinal direction or switching tool. */
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    /** Something reached a meaningful state (level found, zero set). */
    fun confirm() = perform(HapticFeedbackConstants.CONFIRM)

    fun reject() = perform(HapticFeedbackConstants.REJECT)

    private fun perform(constant: Int) {
        if (enabled) view?.performHapticFeedback(constant)
    }
}

val LocalGyroHaptics = staticCompositionLocalOf { GyroHaptics(view = null, enabled = false) }

@Composable
fun rememberGyroHaptics(enabled: Boolean): GyroHaptics {
    val view = LocalView.current
    return remember(view, enabled) { GyroHaptics(view, enabled) }
}
