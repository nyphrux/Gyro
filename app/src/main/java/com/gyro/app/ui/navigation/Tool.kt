package com.gyro.app.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.gyro.app.R

/** The three top-level destinations. An enum gives type-safe, saveable navigation keys. */
enum class Tool(@StringRes val label: Int, @StringRes val subtitle: Int, @DrawableRes val icon: Int) {
    COMPASS(R.string.tool_compass, R.string.tool_compass_subtitle, R.drawable.ic_compass),
    SPEED(R.string.tool_speed, R.string.tool_speed_subtitle, R.drawable.ic_speed),
    TILT(R.string.tool_tilt, R.string.tool_tilt_subtitle, R.drawable.ic_level),
}
