package com.gyro.app.ui.common

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Audible and vibration cues that must work when the user is not touching or even looking at
 * the phone (a level face-down on a shelf, a phone in a car mount). Unlike [GyroHaptics], these
 * are not touch feedback, so they do not depend on the system touch-feedback setting.
 *
 * Tones play on the notification stream, so they follow the notification volume and stay quiet
 * when the phone is on silent.
 */
class GyroCues(context: Context) {
    private val tones: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME) }.getOrNull()
    private val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator

    /** A short, bright confirmation when the tilt meter finds level. */
    fun levelFound() {
        runCatching { tones?.startTone(ToneGenerator.TONE_PROP_ACK, 150) }
    }

    /** Two beeps and two buzzes when the speed alert threshold is crossed. */
    fun speedAlert() {
        runCatching { tones?.startTone(ToneGenerator.TONE_PROP_BEEP2, 450) }
        runCatching {
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 120, 200), -1))
            }
        }
    }

    fun release() {
        runCatching { tones?.release() }
    }

    private companion object {
        const val TONE_VOLUME = 80 // percent of the stream volume
    }
}

@Composable
fun rememberGyroCues(): GyroCues {
    val context = LocalContext.current
    val cues = remember(context) { GyroCues(context.applicationContext) }
    DisposableEffect(cues) { onDispose { cues.release() } }
    return cues
}
