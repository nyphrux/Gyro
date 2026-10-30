package com.gyro.app.platform.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gyro.app.core.math.Vec3
import com.gyro.app.core.prefs.AccentColor
import com.gyro.app.core.prefs.NorthReference
import com.gyro.app.core.prefs.PreferencesRepository
import com.gyro.app.core.prefs.ThemeMode
import com.gyro.app.core.prefs.UserPreferences
import com.gyro.app.core.speed.SpeedUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.gyroDataStore: DataStore<Preferences> by preferencesDataStore(name = "gyro_preferences")

class DataStorePreferencesRepository(private val dataStore: DataStore<Preferences>, private val defaultSpeedUnit: SpeedUnit) : PreferencesRepository {
    constructor(context: Context, defaultSpeedUnit: SpeedUnit) :
        this(context.applicationContext.gyroDataStore, defaultSpeedUnit)

    override val preferences: Flow<UserPreferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toUserPreferences() }
        .distinctUntilChanged()

    private fun Preferences.toUserPreferences(): UserPreferences {
        val rx = this[Keys.TILT_REF_X]
        val ry = this[Keys.TILT_REF_Y]
        val rz = this[Keys.TILT_REF_Z]
        return UserPreferences(
            themeMode = this[Keys.THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM,
            // Older versions stored only a "wallpaper colours" switch; carry it over as the Wallpaper accent.
            accent = this[Keys.ACCENT]?.let { name -> AccentColor.entries.firstOrNull { it.name == name } }
                ?: if (this[Keys.DYNAMIC_COLOR] == true) AccentColor.WALLPAPER else AccentColor.TEAL,
            speedUnit = SpeedUnit.fromName(this[Keys.SPEED_UNIT]) ?: defaultSpeedUnit,
            northReference = this[Keys.NORTH]?.let { name -> NorthReference.entries.firstOrNull { it.name == name } }
                ?: NorthReference.MAGNETIC,
            hapticsEnabled = this[Keys.HAPTICS] ?: true,
            keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: false,
            tiltReference = if (rx != null && ry != null && rz != null) Vec3(rx, ry, rz) else null,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) = set(Keys.THEME, mode.name)
    override suspend fun setAccent(accent: AccentColor) {
        dataStore.edit { prefs ->
            prefs[Keys.ACCENT] = accent.name
            prefs.remove(Keys.DYNAMIC_COLOR)
        }
    }
    override suspend fun setSpeedUnit(unit: SpeedUnit) = set(Keys.SPEED_UNIT, unit.name)
    override suspend fun setNorthReference(reference: NorthReference) = set(Keys.NORTH, reference.name)
    override suspend fun setHapticsEnabled(enabled: Boolean) = set(Keys.HAPTICS, enabled)
    override suspend fun setKeepScreenOn(enabled: Boolean) = set(Keys.KEEP_SCREEN_ON, enabled)

    override suspend fun setTiltReference(reference: Vec3?) {
        dataStore.edit { prefs ->
            if (reference == null) {
                prefs.remove(Keys.TILT_REF_X)
                prefs.remove(Keys.TILT_REF_Y)
                prefs.remove(Keys.TILT_REF_Z)
            } else {
                prefs[Keys.TILT_REF_X] = reference.x
                prefs[Keys.TILT_REF_Y] = reference.y
                prefs[Keys.TILT_REF_Z] = reference.z
            }
        }
    }

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        /** Legacy (pre-accent) switch, read once for migration. */
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCENT = stringPreferencesKey("accent")
        val SPEED_UNIT = stringPreferencesKey("speed_unit")
        val NORTH = stringPreferencesKey("north_reference")
        val HAPTICS = booleanPreferencesKey("haptics")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val TILT_REF_X = floatPreferencesKey("tilt_reference_x")
        val TILT_REF_Y = floatPreferencesKey("tilt_reference_y")
        val TILT_REF_Z = floatPreferencesKey("tilt_reference_z")
    }
}
