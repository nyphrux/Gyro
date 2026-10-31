package com.gyro.app.core.prefs

import com.gyro.app.core.math.Vec3
import com.gyro.app.core.speed.SpeedUnit
import com.gyro.app.core.tilt.SlopeUnit
import kotlinx.coroutines.flow.Flow

/**
 * SYSTEM follows the device; BLACK is pure-black dark for OLED; NIGHT is red-on-black for night
 * vision. DIM is a softer, lifted dark; MIDNIGHT a deep blue-black dark; PAPER a warm cream light;
 * CONTRAST a high-contrast light; TERMINAL green phosphor on black.
 */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK, BLACK, NIGHT, DIM, MIDNIGHT, PAPER, CONTRAST, TERMINAL;

    /** Night and Terminal are single-hue themes, so the accent colour does not apply. */
    val ignoresAccent: Boolean get() = this == NIGHT || this == TERMINAL
}

/**
 * Accent colour. WALLPAPER uses Material You colours from the wallpaper; CUSTOM uses
 * [UserPreferences.customAccent]. Ignored by themes where [ThemeMode.ignoresAccent].
 * New entries go before WALLPAPER so the two special swatches stay last.
 */
enum class AccentColor {
    TEAL, BLUE, INDIGO, VIOLET, FUCHSIA, ROSE, AMBER, LIME, GREEN, MOCHA, GRAPHITE,
    RED, ORANGE, GOLD, OLIVE, MINT, CYAN, SKY, NAVY, LAVENDER, PINK, SLATE,
    WALLPAPER, CUSTOM,
}

/** How the head-up display is laid out. LANDSCAPE turns it a quarter turn for a sideways phone. */
enum class HudOrientation { PORTRAIT, LANDSCAPE }

/**
 * The tone Gyro plays for the level beep and the speed alert.
 *
 * @property volume 0..1 of the notification stream volume.
 * @property lengthMillis length of one beep.
 * @property pitchHz frequency of the beep.
 */
data class SoundSettings(
    val volume: Float = DEFAULT_VOLUME,
    val lengthMillis: Int = DEFAULT_LENGTH_MS,
    val pitchHz: Int = DEFAULT_PITCH_HZ,
) {
    /** The same settings with every value pulled into its allowed range. */
    fun coerced(): SoundSettings = SoundSettings(
        volume = volume.takeIf { it.isFinite() }?.coerceIn(MIN_VOLUME, MAX_VOLUME) ?: DEFAULT_VOLUME,
        lengthMillis = lengthMillis.coerceIn(MIN_LENGTH_MS, MAX_LENGTH_MS),
        pitchHz = pitchHz.coerceIn(MIN_PITCH_HZ, MAX_PITCH_HZ),
    )

    companion object {
        const val MIN_VOLUME = 0.05f
        const val MAX_VOLUME = 1f
        const val DEFAULT_VOLUME = 0.8f
        const val MIN_LENGTH_MS = 50
        const val MAX_LENGTH_MS = 1000
        const val DEFAULT_LENGTH_MS = 180
        const val MIN_PITCH_HZ = 300
        const val MAX_PITCH_HZ = 3000
        const val DEFAULT_PITCH_HZ = 1200
    }
}

/** Starting colour for the custom accent (Gyro's teal), as ARGB. */
const val DEFAULT_CUSTOM_ACCENT: Int = 0xFF00879E.toInt()

/** Which north the compass points to. TRUE needs a location fix for magnetic declination. */
enum class NorthReference { MAGNETIC, TRUE }

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.TEAL,
    /** Colour behind the CUSTOM accent, as opaque ARGB. Kept when another accent is picked. */
    val customAccent: Int = DEFAULT_CUSTOM_ACCENT,
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
    val hudOrientation: HudOrientation = HudOrientation.PORTRAIT,
    val sound: SoundSettings = SoundSettings(),
    /** Speed alert threshold in m/s; null = off. */
    val speedAlertMetersPerSecond: Float? = null,
)

/** Persisted user settings. The Android implementation is backed by DataStore. */
interface PreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setAccent(accent: AccentColor)
    /** Saves [argb] as the custom colour and selects the CUSTOM accent. */
    suspend fun setCustomAccent(argb: Int)
    suspend fun setSpeedUnit(unit: SpeedUnit)
    suspend fun setNorthReference(reference: NorthReference)
    suspend fun setHapticsEnabled(enabled: Boolean)
    suspend fun setKeepScreenOn(enabled: Boolean)
    suspend fun setTiltReference(reference: Vec3?)
    suspend fun setShowSun(enabled: Boolean)
    suspend fun setLevelTone(enabled: Boolean)
    suspend fun setSlopeUnit(unit: SlopeUnit)
    suspend fun setHudMirrored(mirrored: Boolean)
    suspend fun setHudOrientation(orientation: HudOrientation)
    suspend fun setSound(sound: SoundSettings)
    suspend fun setSpeedAlert(metersPerSecond: Float?)
}
