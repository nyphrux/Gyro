package com.gyro.app.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.gyro.app.core.prefs.SoundSettings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Audible and vibration cues that must work when the user is not touching or even looking at
 * the phone (a level face-down on a shelf, a phone in a car mount). Unlike [GyroHaptics], these
 * are not touch feedback, so they do not depend on the system touch-feedback setting.
 *
 * The beep is synthesised (a sine with short fades, so it never clicks) from the user's
 * [SoundSettings]: volume, length and pitch. It plays on the notification stream, so it follows
 * the notification volume and stays quiet when the phone is on silent.
 *
 * Audio is built and started on a single background thread; a new cue stops the previous one.
 */
class GyroCues(context: Context, var settings: SoundSettings = SoundSettings()) {
    private val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    private val audio: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "GyroCues").apply { isDaemon = true }
    }
    private var track: AudioTrack? = null // only touched on the audio thread
    @Volatile private var released = false

    /** One beep when the tilt meter finds level. */
    fun levelFound() = beep(count = 1, sound = settings)

    /** Two beeps and two buzzes when the speed alert threshold is crossed. */
    fun speedAlert() {
        beep(count = 2, sound = settings)
        runCatching {
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 120, 200), -1))
            }
        }
    }

    /** Plays one beep with [sound], e.g. to try out settings before they are saved. */
    fun preview(sound: SoundSettings) = beep(count = 1, sound = sound)

    fun release() {
        released = true
        runCatching {
            audio.execute { stopCurrent() }
            audio.shutdown()
        }
    }

    private fun beep(count: Int, sound: SoundSettings) {
        if (released) return
        val s = sound.coerced()
        runCatching {
            audio.execute {
                if (released) return@execute
                stopCurrent()
                track = runCatching { play(tone(s, count)) }.getOrNull()
            }
        }
    }

    private fun stopCurrent() {
        track?.let { t ->
            runCatching { t.stop() }
            runCatching { t.release() }
        }
        track = null
    }

    private fun play(pcm: ShortArray): AudioTrack {
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        t.write(pcm, 0, pcm.size)
        t.play()
        return t
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val GAP_MS = 110
        const val FADE_MS = 6
        /** Headroom below full scale, so a sine at 100 % never clips. */
        const val PEAK = 0.9

        /** [count] beeps separated by short gaps, as 16-bit mono PCM. */
        fun tone(sound: SoundSettings, count: Int): ShortArray {
            val beep = SAMPLE_RATE * sound.lengthMillis / 1000
            val gap = SAMPLE_RATE * GAP_MS / 1000
            val fade = min(SAMPLE_RATE * FADE_MS / 1000, beep / 2).coerceAtLeast(1)
            val amplitude = Short.MAX_VALUE * PEAK * sound.volume
            val step = 2.0 * PI * sound.pitchHz / SAMPLE_RATE
            val out = ShortArray(beep * count + gap * (count - 1))
            for (n in 0 until count) {
                val start = n * (beep + gap)
                for (i in 0 until beep) {
                    val envelope = when {
                        i < fade -> i.toDouble() / fade
                        i >= beep - fade -> (beep - 1 - i).toDouble() / fade
                        else -> 1.0
                    }
                    out[start + i] = (sin(step * i) * amplitude * envelope).roundToInt().toShort()
                }
            }
            return out
        }
    }
}

/** The user's sound settings, provided once at the top of the app. */
val LocalSoundSettings = staticCompositionLocalOf { SoundSettings() }

@Composable
fun rememberGyroCues(): GyroCues {
    val context = LocalContext.current
    val sound = LocalSoundSettings.current
    val cues = remember(context) { GyroCues(context.applicationContext, sound) }
    SideEffect { cues.settings = sound }
    DisposableEffect(cues) { onDispose { cues.release() } }
    return cues
}
