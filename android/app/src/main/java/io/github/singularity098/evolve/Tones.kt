package io.github.singularity098.evolve

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Evolve's sounds, synthesized: soft bells and short beeps, and buzzes. */
class Tones(private val context: Context) {
    private val rate = 44_100
    private val player = Executors.newSingleThreadExecutor { r -> Thread(r, "tones") }

    /** (frequency, relative amplitude) partials, decaying over [seconds]. */
    private fun render(seconds: Double, decay: Double, attack: Double, volume: Double, partials: List<Pair<Double, Double>>): ShortArray {
        val n = (seconds * rate).toInt()
        val out = ShortArray(n)
        val norm = partials.sumOf { it.second }
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            var v = 0.0
            for ((f, a) in partials) v += a * sin(2 * PI * f * t)
            val env = min(1.0, t / attack) * exp(-t / decay) * min(1.0, (seconds - t) / 0.02)
            out[i] = (v / norm * env * volume * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun play(samples: ShortArray) {
        player.execute {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(samples.size * 2)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                Thread.sleep(samples.size * 1000L / rate + 50)
                track.release()
            } catch (_: Exception) {
            }
        }
    }

    private val sounds get() = (context.applicationContext as EvolveApplication).store.settings.sounds
    private val buzz get() = (context.applicationContext as EvolveApplication).store.settings.vibrate

    /** The end of a meditation: a soft singing-bowl bell. */
    fun bell() {
        if (sounds) play(render(5.0, 1.6, 0.01, 0.55, listOf(432.0 to 1.0, 864.5 to 0.35, 1296.0 to 0.15, 1729.0 to 0.06)))
        vibrate(longArrayOf(0, 120))
    }

    /** Rest over: up a fifth. */
    fun restOver() {
        if (sounds) play(render(0.18, 0.2, 0.005, 0.7, listOf(660.0 to 1.0)) + render(0.35, 0.25, 0.005, 0.7, listOf(990.0 to 1.0)))
        vibrate(longArrayOf(0, 250, 120, 250))
    }

    /** The last seconds of an interval. */
    fun tick() {
        if (sounds) play(render(0.12, 0.1, 0.003, 0.6, listOf(880.0 to 1.0)))
    }

    /** A new interval: high for hard work, low for rest. */
    fun go(hard: Boolean) {
        if (sounds) play(render(0.45, 0.3, 0.004, 0.75, listOf((if (hard) 1320.0 else 660.0) to 1.0, (if (hard) 2640.0 else 1320.0) to 0.2)))
        vibrate(if (hard) longArrayOf(0, 400) else longArrayOf(0, 150))
    }

    /** All done. */
    fun done() {
        if (sounds) {
            play(
                render(0.2, 0.3, 0.004, 0.7, listOf(660.0 to 1.0)) +
                    render(0.2, 0.3, 0.004, 0.7, listOf(880.0 to 1.0)) +
                    render(0.6, 0.4, 0.004, 0.7, listOf(1320.0 to 1.0))
            )
        }
        vibrate(longArrayOf(0, 200, 100, 200, 100, 400))
    }

    /** Breathe in (the recovery breath is over: breathe on). */
    fun breathe() {
        if (sounds) play(render(1.6, 0.8, 0.05, 0.5, listOf(528.0 to 1.0, 1056.0 to 0.25)))
        vibrate(longArrayOf(0, 150))
    }

    private fun vibrate(pattern: LongArray) {
        if (!buzz) return
        val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        v?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }
}
