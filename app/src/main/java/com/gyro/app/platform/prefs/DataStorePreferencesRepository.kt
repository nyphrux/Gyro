package com.gyro.app.platform.prefs

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
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
import com.gyro.app.core.speed.TripRecord
import com.gyro.app.core.speed.TripRepository
import com.gyro.app.core.speed.TripStats
import com.gyro.app.core.tilt.SlopeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.io.IOException

/*
 * Two stores:
 * - "gyro_preferences": settings that make sense on any phone (theme, units...). Backed up.
 * - "gyro_device": state that only means something on this phone: the tilt meter's custom zero
 *   (a gravity vector in this device's sensor frame) and the running trip. Excluded from backup
 *   and device transfer in res/xml/backup_rules.xml and data_extraction_rules.xml, so restoring
 *   onto a new phone does not silently skew its level.
 */

/** File name of the device-only store; must match the backup exclusion rules. */
private const val DEVICE_STORE_NAME = "gyro_device"

private val Context.gyroDataStore: DataStore<Preferences> by preferencesDataStore(name = "gyro_preferences")

private val Context.gyroDeviceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DEVICE_STORE_NAME,
    produceMigrations = { context -> listOf(LegacyTiltReferenceMigration(context.gyroDataStore)) },
)

private object TiltKeys {
    val X = floatPreferencesKey("tilt_reference_x")
    val Y = floatPreferencesKey("tilt_reference_y")
    val Z = floatPreferencesKey("tilt_reference_z")
    val ALL = listOf(X, Y, Z)
}

/** Versions up to 1.0 kept the tilt zero in the backed-up store; move it to the device store once. */
private class LegacyTiltReferenceMigration(private val legacy: DataStore<Preferences>) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[TiltKeys.X] == null && legacyPreferences().let { old -> TiltKeys.ALL.any { old[it] != null } }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val old = legacyPreferences()
        val x = old[TiltKeys.X]
        val y = old[TiltKeys.Y]
        val z = old[TiltKeys.Z]
        if (x == null || y == null || z == null) return currentData
        return currentData.toMutablePreferences().apply {
            this[TiltKeys.X] = x
            this[TiltKeys.Y] = y
            this[TiltKeys.Z] = z
        }.toPreferences()
    }

    override suspend fun cleanUp() {
        legacy.edit { prefs -> TiltKeys.ALL.forEach { prefs.remove(it) } }
    }

    private suspend fun legacyPreferences(): Preferences =
        legacy.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.first()
}

private fun DataStore<Preferences>.safeData(): Flow<Preferences> =
    data.catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

class DataStorePreferencesRepository(
    private val dataStore: DataStore<Preferences>,
    private val deviceStore: DataStore<Preferences>,
    private val defaultSpeedUnit: SpeedUnit,
) : PreferencesRepository {
    constructor(context: Context, defaultSpeedUnit: SpeedUnit) :
        this(context.applicationContext.gyroDataStore, context.applicationContext.gyroDeviceDataStore, defaultSpeedUnit)

    override val preferences: Flow<UserPreferences> =
        combine(dataStore.safeData(), deviceStore.safeData()) { settings, device -> toUserPreferences(settings, device) }
            .distinctUntilChanged()

    private fun toUserPreferences(settings: Preferences, device: Preferences): UserPreferences {
        val rx = device[TiltKeys.X]
        val ry = device[TiltKeys.Y]
        val rz = device[TiltKeys.Z]
        return UserPreferences(
            themeMode = settings[Keys.THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM,
            // Older versions stored only a "wallpaper colours" switch; carry it over as the Wallpaper accent.
            accent = settings[Keys.ACCENT]?.let { name -> AccentColor.entries.firstOrNull { it.name == name } }
                ?: if (settings[Keys.DYNAMIC_COLOR] == true) AccentColor.WALLPAPER else AccentColor.TEAL,
            speedUnit = SpeedUnit.fromName(settings[Keys.SPEED_UNIT]) ?: defaultSpeedUnit,
            northReference = settings[Keys.NORTH]?.let { name -> NorthReference.entries.firstOrNull { it.name == name } }
                ?: NorthReference.MAGNETIC,
            hapticsEnabled = settings[Keys.HAPTICS] ?: true,
            keepScreenOn = settings[Keys.KEEP_SCREEN_ON] ?: false,
            tiltReference = if (rx != null && ry != null && rz != null) Vec3(rx, ry, rz) else null,
            showSun = settings[Keys.SHOW_SUN] ?: false,
            levelTone = settings[Keys.LEVEL_TONE] ?: false,
            slopeUnit = SlopeUnit.fromName(settings[Keys.SLOPE_UNIT]) ?: SlopeUnit.DEGREES,
            hudMirrored = settings[Keys.HUD_MIRRORED] ?: true,
            speedAlertMetersPerSecond = settings[Keys.SPEED_ALERT]?.takeIf { it > 0f },
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
    override suspend fun setShowSun(enabled: Boolean) = set(Keys.SHOW_SUN, enabled)
    override suspend fun setLevelTone(enabled: Boolean) = set(Keys.LEVEL_TONE, enabled)
    override suspend fun setSlopeUnit(unit: SlopeUnit) = set(Keys.SLOPE_UNIT, unit.name)
    override suspend fun setHudMirrored(mirrored: Boolean) = set(Keys.HUD_MIRRORED, mirrored)

    override suspend fun setSpeedAlert(metersPerSecond: Float?) {
        dataStore.edit { prefs ->
            if (metersPerSecond == null || metersPerSecond <= 0f) prefs.remove(Keys.SPEED_ALERT) else prefs[Keys.SPEED_ALERT] = metersPerSecond
        }
    }

    override suspend fun setTiltReference(reference: Vec3?) {
        deviceStore.edit { prefs ->
            if (reference == null) {
                TiltKeys.ALL.forEach { prefs.remove(it) }
            } else {
                prefs[TiltKeys.X] = reference.x
                prefs[TiltKeys.Y] = reference.y
                prefs[TiltKeys.Z] = reference.z
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
        val SHOW_SUN = booleanPreferencesKey("show_sun")
        val LEVEL_TONE = booleanPreferencesKey("level_tone")
        val SLOPE_UNIT = stringPreferencesKey("slope_unit")
        val HUD_MIRRORED = booleanPreferencesKey("hud_mirrored")
        val SPEED_ALERT = floatPreferencesKey("speed_alert_mps")
    }
}

/** The speedometer's trip and top speed, kept in the device-only store. */
class DataStoreTripRepository(private val deviceStore: DataStore<Preferences>) : TripRepository {
    constructor(context: Context) : this(context.applicationContext.gyroDeviceDataStore)

    override suspend fun load(): TripRecord {
        val prefs = deviceStore.safeData().first()
        return TripRecord(
            trip = TripStats(
                distanceMeters = prefs[Keys.DISTANCE]?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
                movingSeconds = prefs[Keys.MOVING]?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
            ),
            topSpeedMetersPerSecond = prefs[Keys.TOP_SPEED]?.takeIf { it.isFinite() && it >= 0f } ?: 0f,
        )
    }

    override suspend fun save(record: TripRecord) {
        deviceStore.edit { prefs ->
            prefs[Keys.DISTANCE] = record.trip.distanceMeters
            prefs[Keys.MOVING] = record.trip.movingSeconds
            prefs[Keys.TOP_SPEED] = record.topSpeedMetersPerSecond
        }
    }

    private object Keys {
        val DISTANCE = doublePreferencesKey("trip_distance_m")
        val MOVING = doublePreferencesKey("trip_moving_s")
        val TOP_SPEED = floatPreferencesKey("top_speed_mps")
    }
}
