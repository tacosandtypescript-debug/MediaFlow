package com.mediaflow.data.player

import android.content.Context
import `is`.xyz.mpv.MPV
import java.io.File

/**
 * Encapsulates recommended options and initialization properties for libmpv.
 */
object MpvConfig {

    /**
     * Applies standard optimal configurations for Android multimedia playback.
     */
    fun applyDefaults(mpv: MPV, context: Context) {
        // Video rendering configuration
        mpv.setOptionString("vo", "gpu")
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("hwdec", "auto")
        mpv.setOptionString("hwdec-codecs", "all")

        // Playback behavior
        mpv.setOptionString("keep-open", "yes")
        mpv.setOptionString("idle", "yes")
        mpv.setOptionString("force-window", "no")
        mpv.setOptionString("ytdl", "no") // local downloads and streams
        mpv.setOptionString("audio-display", "no") // avoid blank video canvas for pure audio

        // Audio output setup
        mpv.setOptionString("ao", "audiotrack,opensles")

        // SSL / Network certificate path
        runCatching {
            val cacert = File(context.cacheDir, "cacert.pem")
            if (cacert.isFile) {
                mpv.setOptionString("tls-verify", "yes")
                mpv.setOptionString("tls-ca-file", cacert.absolutePath)
            }
        }
    }
}
