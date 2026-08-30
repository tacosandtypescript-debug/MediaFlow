package com.mediaflow.data.player

import kotlin.math.pow

/**
 * Playback-side band/RMS helpers for libmpv lavfi astats metadata.
 * No microphone. Silence and pause stay at zero.
 */
object MpvAudioAnalysis {
    const val FilterGraph =
        "lavfi=[asplit=2[main][side];[side]asetnsamples=n=1024:p=0,aformat=sample_fmts=flt:channel_layouts=mono,astats=metadata=1:reset=1,anullsink]"

    fun shouldRun(visualizerEnabled: Boolean, isPlaying: Boolean): Boolean =
        visualizerEnabled && isPlaying

    fun rmsDbToUnit(db: Double): Float {
        if (db.isNaN() || db.isInfinite() || db < -80.0) return 0f
        val linear = 10.0.pow(db / 20.0)
        return linear.toFloat().coerceIn(0f, 1f)
    }

    fun parseKey(map: Map<String, String>, vararg keys: String): Float {
        for (key in keys) {
            val raw = map[key] ?: continue
            val db = raw.toDoubleOrNull() ?: continue
            return rmsDbToUnit(db)
        }
        return 0f
    }

    fun frameFromMetadata(
        map: Map<String, String>,
        isPlaying: Boolean,
    ): PlaybackPcmFrame {
        if (!isPlaying) return PlaybackPcmFrame.Silent
        val overall = parseKey(
            map,
            "lavfi.astats.Overall.RMS_level",
            "lavfi.astats.1.RMS_level",
        )
        val peakDb = parseKey(
            map,
            "lavfi.astats.Overall.Peak_level",
            "lavfi.astats.1.Peak_level",
        )
        val rms = if (overall > 0f) overall else peakDb
        val bass = parseKey(map, "lavfi.astats.Overall.RMS_level.bass")
        val lowMids = parseKey(map, "lavfi.astats.Overall.RMS_level.lowmids")
        val mids = parseKey(map, "lavfi.astats.Overall.RMS_level.mids")
        val highs = parseKey(map, "lavfi.astats.Overall.RMS_level.highs")
        return PlaybackPcmFrame(
            pcm = ByteArray(0),
            fft = ByteArray(0),
            bass = bass,
            lowMids = lowMids,
            mids = mids,
            highs = highs,
            rms = rms,
            isPlaying = true,
        )
    }

    fun parseMetadataBlob(blob: String): Map<String, String> {
        if (blob.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        val parts = blob.split('\n', ',', ';')
        for (part in parts) {
            val idx = part.indexOf('=')
            if (idx <= 0) continue
            val k = part.substring(0, idx).trim()
            val v = part.substring(idx + 1).trim()
            if (k.isNotEmpty()) out[k] = v
        }
        return out
    }
}
