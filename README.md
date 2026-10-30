# Gyro

A compass, GPS speedometer and inclinometer for Android 13+, built with Kotlin, Jetpack Compose
and Material 3. No analytics, and sensor and location data never leave the device. The only network access is the
update check in Settings, which asks GitHub for the latest release when you tap it.

## Build and run

Requirements: Android Studio (recent, with AGP 9 support) or the Android SDK command-line tools
with platform 36 installed. The Gradle wrapper (Gradle 9.1) and JDK 17 toolchain are provisioned
automatically.

```bash
./gradlew assembleDebug            # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug             # install on a connected device
./gradlew testDebugUnitTest        # JVM unit tests (sensor maths, filters, state machines)
./gradlew connectedDebugAndroidTest  # Compose UI tests on a device/emulator
```

Use a real device: emulators have no real magnetometer, gravity sensor or GNSS motion.

**Judge smoothness on the `profile` variant, not `debug`.** Debug builds of Compose apps are
much slower (debuggable ART, no R8) and will stutter on a phone. In Android Studio pick
*Build Variants → app → profile* and press Run, or `./gradlew installProfile`. It is the release
build signed with the debug key, so it installs like a debug build.

### Permissions

- `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`: requested only when you open the
  speedometer (precise needed for GPS speed) or choose *True north* (approximate is enough).
  Foreground only; no background location.
- No other runtime permissions. Haptics use `View.performHapticFeedback`, which needs none.

## Known limitations

- Android build and on-device behaviour unverified (see above).
- Phones are locked to portrait so a level does not flip mid-measurement; large screens
  (Android 16+) may rotate, which the sensor maths handles.
- "Permanently denied" can only be detected after a request returns without a dialog; the
  rationale card therefore appears once before the settings shortcut.
- GPS speed is unavailable or unreliable indoors, in tunnels and in urban canyons; the app says
  so rather than estimating.
- Accelerometer-only tilt (no gravity sensor) is sensitive to hand movement.

## License

Gyro is released under the MIT License.