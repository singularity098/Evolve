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
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Evolve's sounds, synthesized: a singing bowl, short beeps, and buzzes. */
class Tones(private val context: Context) {
    private val rate = 44_100
    private val player = Executors.newSingleThreadExecutor { r -> Thread(r, "tones") }

    // Render the singing bowl off the main thread, ahead of its first use.
    init {
        player.execute { bowl }
    }

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

    /** The end of a meditation: a short singing bowl in a little reverb. */
    fun bell() {
        if (sounds) play(bowl)
        vibrate(longArrayOf(0, 120))
    }

    /**
     * A struck singing bowl (G3): inharmonic partials, each a slowly beating pair, ringing for
     * about a second, then a light reverb tail; about six seconds in all. Rendered once.
     */
    private val bowl: ShortArray by lazy {
        val seconds = 6.0
        val n = (seconds * rate).toInt()
        val f = 196.0
        // (ratio, amplitude, decay seconds, beat Hz)
        val partials = listOf(
            doubleArrayOf(1.0, 1.0, 1.1, 0.9),
            doubleArrayOf(2.76, 0.45, 0.7, 1.7),
            doubleArrayOf(5.40, 0.18, 0.45, 2.3),
            doubleArrayOf(8.93, 0.07, 0.3, 3.1),
        )
        val dry = DoubleArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / rate
            var v = 0.0
            for (p in partials) {
                val hz = f * p[0]
                v += p[1] * 0.5 * (sin(2 * PI * hz * t) + sin(2 * PI * (hz + p[3]) * t)) * exp(-t / p[2])
            }
            dry[i] = v * min(1.0, t / 0.012)
        }
        val wet = reverb(dry, 0.3)
        val fade = (0.5 * rate).toInt()
        for (i in 0 until fade) wet[n - 1 - i] *= i.toDouble() / fade
        val peak = wet.maxOf { abs(it) }
        ShortArray(n) { (wet[it] / peak * 0.7 * Short.MAX_VALUE).toInt().toShort() }
    }

    /** A small Schroeder reverb: four feedback combs in parallel, then two allpasses. */
    private fun reverb(x: DoubleArray, mix: Double): DoubleArray {
        fun comb(x: DoubleArray, d: Int, g: Double): DoubleArray {
            val y = x.copyOf()
            for (i in d until y.size) y[i] += g * y[i - d]
            return y
        }
        fun allpass(x: DoubleArray, d: Int, g: Double): DoubleArray {
            val w = comb(x, d, g)
            return DoubleArray(x.size) { -g * w[it] + (if (it >= d) w[it - d] else 0.0) }
        }
        val combs = listOf(1557 to 0.84, 1617 to 0.83, 1491 to 0.85, 1422 to 0.86).map { (d, g) -> comb(x, d, g) }
        var w = DoubleArray(x.size) { i -> combs.sumOf { it[i] } / combs.size }
        w = allpass(w, 225, 0.5)
        w = allpass(w, 556, 0.5)
        return DoubleArray(x.size) { (1 - mix) * x[it] + mix * w[it] }
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

    /** Breathe in (the recovery breath is over: breathe on): the same singing bowl. */
    fun breathe() {
        if (sounds) play(bowl)
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
