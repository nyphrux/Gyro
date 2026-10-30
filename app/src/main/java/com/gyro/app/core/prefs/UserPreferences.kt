package com.gyro.app.core.prefs

import com.gyro.app.core.math.Vec3
import com.gyro.app.core.speed.SpeedUnit
import com.gyro.app.core.tilt.SlopeUnit
import kotlinx.coroutines.flow.Flow

/** SYSTEM follows the device; BLACK is pure-black dark for OLED; NIGHT is red-on-black for night vision. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK, NIGHT }

/** Accent colour. WALLPAPER uses Material You colours from the wallpaper. Ignored by NIGHT. */
enum class AccentColor { TEAL, BLUE, INDIGO, VIOLET, FUCHSIA, ROSE, AMBER, LIME, GREEN, MOCHA, GRAPHITE, WALLPAPER }

/** Which north the compass points to. TRUE needs a location fix for magnetic declination. */
enum class NorthReference { MAGNETIC, TRUE }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.TEAL,
    val speedUnit: SpeedUnit = SpeedUnit.KILOMETRES_PER_HOUR,
    val northReference: NorthReference = NorthReference.MAGNETIC,
    val hapticsEnabled: Boolean = true,
    val keepScreenOn: Boolean = false,
    /**
     * Device-frame gravity captured when the user set a custom tilt zero; null = true level.
     * Stored separately and excluded from backups, because it only means anything on this phone.
     */
    val tiltReference: Vec3? = null,
    /** Draw the sun's direction on the compass (needs approximate location). */
    val showSun: Boolean = false,
    /** Play a short tone when the tilt meter finds level. */
    val levelTone: Boolean = false,
    val slopeUnit: SlopeUnit = SlopeUnit.DEGREES,
    /** Mirror the head-up display so it reads correctly reflected in a windscreen. */
    val hudMirrored: Boolean = true,
    /** Speed alert threshold in m/s; null = off. */
    val speedAlertMetersPerSecond: Float? = null,
)

/** Persisted user settings. The Android implementation is backed by DataStore. */
interface PreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setAccent(accent: AccentColor)
    suspend fun setSpeedUnit(unit: SpeedUnit)
    suspend fun setNorthReference(reference: NorthReference)
    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setKeepScreenOn(enabled: Boolean)
    suspend fun setTiltReference(reference: Vec3?)
    suspend fun setShowSun(enabled: Boolean)
    suspend fun setLevelTone(enabled: Boolean)
    suspend fun setSlopeUnit(unit: SlopeUnit)
    suspend fun setHudMirrored(mirrored: Boolean)
    suspend fun setSpeedAlert(metersPerSecond: Float?)
}
