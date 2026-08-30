package com.mediaflow.app.ui.player.visualizer.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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
    ): AudioReactiveState {
        if (!isPlaying) {
            return AudioReactiveState(isPlaying = false)
        }
        val b = clamp01(bass)
        val m = clamp01(mids)
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

    /** Parse Android Visualizer FFT magnitudes into 3 bands (skip DC bin). */
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

    private fun clamp01(v: Float): Float = min(1f, max(0f, v))
}
