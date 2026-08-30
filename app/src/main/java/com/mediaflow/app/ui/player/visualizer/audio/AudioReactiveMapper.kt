package com.mediaflow.app.ui.player.visualizer.audio

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Maps measured RMS / band energy to [AudioReactiveState].
 * No Random — silence and pause collapse reaction.
 */
object AudioReactiveMapper {
    const val BeatJump = 0.18f
    const val PauseCeiling = 0.08f

    fun fromBands(
        bass: Float,
        mids: Float,
        highs: Float,
        rms: Float,
        isPlaying: Boolean,
        previousEnergy: Float,
        lowMids: Float = 0f,
    ): AudioReactiveState {
        if (!isPlaying) {
            return AudioReactiveState(isPlaying = false)
        }
        val b = clamp01(bass + lowMids * 0.45f)
        val m = clamp01(mids + lowMids * 0.35f)
        val h = clamp01(highs)
        val a = clamp01(rms)
        if (a < 0.004f && b < 0.004f && m < 0.004f && h < 0.004f) {
            return AudioReactiveState(isPlaying = true)
        }
        val energy = clamp01((b * 0.5f + m * 0.3f + h * 0.1f + a * 0.1f))
        val peak = max(a, max(b, max(m, h)))
        val beat = energy - previousEnergy >= BeatJump && energy > 0.22f
        return AudioReactiveState(
            amplitude = a,
            bass = b,
            mids = m,
            highs = h,
            beat = beat,
            energy = energy,
            peak = peak,
            isPlaying = true,
        )
    }

    fun fromPcm(
        pcm: ByteArray,
        isPlaying: Boolean,
        previousEnergy: Float,
    ): AudioReactiveState {
        if (!isPlaying) return AudioReactiveState(isPlaying = false)
        val fft = fftFromPcm(pcm)
        val bands = bandsFromFft(fft)
        val rms = rmsFromWave(pcm)
        return fromBands(bands.first, bands.second, bands.third, rms, true, previousEnergy)
    }

    fun fromPlaybackFrame(
        pcm: ByteArray,
        fft: ByteArray,
        bass: Float,
        lowMids: Float,
        mids: Float,
        highs: Float,
        rms: Float,
        isPlaying: Boolean,
        previousEnergy: Float,
    ): AudioReactiveState {
        if (!isPlaying) return AudioReactiveState(isPlaying = false)
        if (pcm.isNotEmpty()) return fromPcm(pcm, true, previousEnergy)
        if (fft.isNotEmpty()) {
            val bands = bandsFromFft(fft)
            return fromBands(bands.first, bands.second, bands.third, rms, true, previousEnergy)
        }
        return fromBands(bass, mids, highs, rms, true, previousEnergy, lowMids = lowMids)
    }

    /** DFT packed like android.media.audiofx.Visualizer FFT (re,im pairs). */
    fun fftFromPcm(pcm: ByteArray): ByteArray {
        val n = highestPowerOfTwo(pcm.size).coerceAtMost(256)
        if (n < 8) return ByteArray(0)
        val out = ByteArray(n)
        val half = n / 2
        for (k in 0 until half) {
            var re = 0.0
            var im = 0.0
            for (t in 0 until n) {
                val sample = ((pcm.getOrElse(t) { 128.toByte() }.toInt() and 0xFF) - 128) / 128.0
                val angle = 2.0 * Math.PI * k * t / n
                re += sample * cos(angle)
                im -= sample * sin(angle)
            }
            out[k * 2] = (re / n * 128.0).toInt().coerceIn(-128, 127).toByte()
            if (k * 2 + 1 < out.size) {
                out[k * 2 + 1] = (im / n * 128.0).toInt().coerceIn(-128, 127).toByte()
            }
        }
        return out
    }

    /** Parse FFT magnitudes into bass / mids / highs (skip DC bin). */
    fun bandsFromFft(fft: ByteArray): Triple<Float, Float, Float> {
        if (fft.size < 8) return Triple(0f, 0f, 0f)
        val n = fft.size / 2
        var bass = 0.0
        var mids = 0.0
        var highs = 0.0
        var bc = 0
        var mc = 0
        var hc = 0
        for (i in 1 until n) {
            val re = fft[i * 2].toInt()
            val im = fft.getOrElse(i * 2 + 1) { 0 }.toInt()
            val mag = sqrt((re * re + im * im).toDouble()) / 128.0
            when {
                i < n / 8 -> { bass += mag; bc++ }
                i < n / 3 -> { mids += mag; mc++ }
                else -> { highs += mag; hc++ }
            }
        }
        fun avg(sum: Double, count: Int) = if (count == 0) 0f else clamp01((sum / count).toFloat() * 2.2f)
        return Triple(avg(bass, bc), avg(mids, mc), avg(highs, hc))
    }

    fun rmsFromWave(wave: ByteArray): Float {
        if (wave.isEmpty()) return 0f
        var acc = 0.0
        for (b in wave) {
            val v = (b.toInt() and 0xFF) - 128
            acc += v * v
        }
        return clamp01((sqrt(acc / wave.size) / 64.0).toFloat())
    }

    fun shouldAnalyze(enabled: Boolean): Boolean = enabled

    fun shouldAnalyze(enabled: Boolean, isPlaying: Boolean): Boolean = enabled && isPlaying

    private fun highestPowerOfTwo(n: Int): Int {
        var p = 1
        while (p * 2 <= n) p *= 2
        return p
    }

    private fun clamp01(v: Float): Float = min(1f, max(0f, v))
}
