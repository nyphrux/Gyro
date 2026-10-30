# Gyro

A compass, GPS speedometer and inclinometer for Android 13+, built with Kotlin, Jetpack Compose
and Material 3. No analytics, and sensor and location data never leave the device. The only network
access is the update check in Settings, which asks GitHub for the latest release when you tap it.

## Features

**Compass**
- Magnetic or true north (true north looks up the local magnetic declination once).
- Bearing lock: point at something, lock it, and Gyro tells you which way and how far to turn to
  stay on that bearing, with a haptic tick when you're back on course.
- Optional sun marker showing where the sun is (or will rise or set), worked out on the device from
  your approximate location and the time.
- Calibration and magnetic interference warnings.

**Speedometer**
- GPS (Doppler) speed in km/h, mph, m/s or knots, on an auto-ranging gauge.
- Trip distance, average moving speed and moving time, kept across app restarts.
- Top speed (low-accuracy fixes never set a record).
- Direction of travel from GPS, which is useful in a car, where the magnetometer is unreliable.
- Speed alert: beeps and vibrates when you go over a speed you choose.
- Head-up display: huge mirrored digits at full brightness, for reflecting in a windscreen.

**Tilt meter**
- Two-axis bubble level when the phone is lying flat; spirit level when it's standing on an edge.
- Readings in degrees, percent gradient or rise:run ratio (1:12).
- Hold a reading to read it after taking the phone out of an awkward spot.
- Custom zero, plus an optional beep when level for when you can't see the screen.

**Everywhere**
- Light, dark, pure black and red night-vision themes with a choice of accent colours.
- Long-press the launcher icon to jump straight to Compass, Speed or Tilt.

## Install

Download the latest `Gyro-x.y.z.apk` from
[Releases](https://github.com/nyphrux/Gyro/releases/latest) and open it on your phone. Once it's
installed, Settings → Check for updates finds and installs new versions.

## Build and run

Requirements: Android Studio (recent, with AGP 9 support) or the Android SDK command-line tools
with platform 36 installed. The Gradle wrapper (Gradle 9.1) and JDK 17 toolchain are provisioned
automatically.

```bash
./gradlew assembleDebug              # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug               # install on a connected device
./gradlew testDebugUnitTest          # JVM unit tests (sensor maths, filters, state machines, sun position)
./gradlew connectedDebugAndroidTest  # Compose UI tests on a device/emulator
```

Use a real device: emulators have no real magnetometer, gravity sensor or GNSS motion.

**Judge smoothness on the `profile` variant, not `debug`.** Debug builds of Compose apps are
much slower (debuggable ART, no R8) and will stutter on a phone. In Android Studio pick
*Build Variants → app → profile* and press Run, or `./gradlew installProfile`. It is the release
build signed with the debug key, so it installs like a debug build.

## Permissions

- `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`: requested only when you open the
  speedometer (precise needed for GPS speed), or choose *True north* or the sun marker
  (approximate is enough). Foreground only; no background location.
- `VIBRATE`: the speed alert vibrates even when touch feedback is off. Granted at install.
- `INTERNET` + `REQUEST_INSTALL_PACKAGES`: only for the update check and installing an update
  you chose to download.
- Haptic ticks use `View.performHapticFeedback`, which needs no permission.

## Privacy and backups

Settings are included in Android backups. The tilt meter's custom zero and the running trip are
not: they're kept in a separate device-only store, because a zero measured on one phone is wrong
on another.

## Known limitations

- Phones are locked to portrait so a level does not flip mid-measurement; large screens
  (Android 16+) may rotate, which the sensor maths handles.
- "Permanently denied" can only be detected after a request returns without a dialog; the
  rationale card therefore appears once before the settings shortcut.
- GPS speed is unavailable or unreliable indoors, in tunnels and in urban canyons; the app says
  so rather than estimating. Trip distance skips signal gaps rather than guessing across them.
- The speedometer, trip and speed alert only run while the Speed screen is open and Gyro is in
  the foreground.
- Accelerometer-only tilt (no gravity sensor) is sensitive to hand movement.

## License

Gyro is released under the [MIT License](LICENSE).
