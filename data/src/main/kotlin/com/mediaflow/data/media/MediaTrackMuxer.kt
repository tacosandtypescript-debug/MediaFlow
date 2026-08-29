package com.mediaflow.data.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import java.io.File
import java.nio.ByteBuffer

/**
 * Combines one video-only and one audio-only file into an MP4 without
 * transcoding. The operation is deliberately narrow: accepting only codecs
 * that Android's MP4 muxer can copy preserves the selected streams and avoids
 * pretending that an unsupported merge succeeded.
 */
object MediaTrackMuxer {
    private const val MAX_SAMPLE_BUFFER_BYTES = 32 * 1024 * 1024

    fun canMuxWithoutTranscoding(videoMime: String?, audioMime: String?): Boolean =
        videoMime in SUPPORTED_VIDEO_MIMES && audioMime in SUPPORTED_AUDIO_MIMES

    fun mergeMp4(videoFile: File, audioFile: File, outputFile: File): Result<Unit> = runCatching {
        require(videoFile.isFile && videoFile.length() > 0L) { "La pista de vídeo está vacía." }
        require(audioFile.isFile && audioFile.length() > 0L) { "La pista de audio está vacía." }
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) outputFile.delete()

        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)
            val videoTrack = findTrack(videoExtractor, "video/")
            val audioTrack = findTrack(audioExtractor, "audio/")
            require(videoTrack >= 0) { "La pista de vídeo no contiene un códec reconocible." }
            require(audioTrack >= 0) { "La pista de audio no contiene un códec reconocible." }

            val videoFormat = videoExtractor.getTrackFormat(videoTrack)
            val audioFormat = audioExtractor.getTrackFormat(audioTrack)
            val videoMime = videoFormat.getString(MediaFormat.KEY_MIME)
            val audioMime = audioFormat.getString(MediaFormat.KEY_MIME)
            require(canMuxWithoutTranscoding(videoMime, audioMime)) {
                "La combinación MP4 no soporta $videoMime + $audioMime sin transcodificar."
            }

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxedVideoTrack = muxer.addTrack(videoFormat)
            val muxedAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()
            started = true
            copyTrack(videoExtractor, videoTrack, muxer, muxedVideoTrack)
            copyTrack(audioExtractor, audioTrack, muxer, muxedAudioTrack)
        } finally {
            videoExtractor.release()
            audioExtractor.release()
            if (started) runCatching { muxer?.stop() }
            muxer?.release()
        }
        require(outputFile.isFile && outputFile.length() > 0L) { "El archivo combinado está vacío." }
    }.onFailure {
        outputFile.delete()
    }

    private fun findTrack(extractor: MediaExtractor, prefix: String): Int {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith(prefix) == true) return index
        }
        return -1
    }

    private fun copyTrack(
        extractor: MediaExtractor,
        sourceTrack: Int,
        muxer: MediaMuxer,
        targetTrack: Int,
    ) {
        extractor.selectTrack(sourceTrack)
        // sampleSize is API 28+. A fixed bounded buffer keeps the muxer usable
        // on the app's minSdk 24; newer devices additionally verify that a
        // sample was not truncated.
        val buffer = ByteBuffer.allocate(MAX_SAMPLE_BUFFER_BYTES)
        val info = MediaCodec.BufferInfo()
        while (true) {
            val expectedSampleSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                extractor.sampleSize.takeIf { it >= 0L }
            } else null
            buffer.clear()
            val read = extractor.readSampleData(buffer, 0)
            if (read < 0) break
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                require(expectedSampleSize == read.toLong()) {
                    "Una muestra multimedia excede el límite seguro."
                }
            }
            info.offset = 0
            info.size = read
            info.presentationTimeUs = extractor.sampleTime.coerceAtLeast(0L)
            val sampleFlags = extractor.sampleFlags
            require(sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED == 0) {
                "No se pueden combinar pistas multimedia cifradas."
            }
            info.flags = 0
            if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                info.flags = info.flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
            }
            // SAMPLE_FLAG_PARTIAL_FRAME was added in API 26. The muxer runs
            // on minSdk 24, so do not read/use it on older devices.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0
            ) {
                info.flags = info.flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
            }
            muxer.writeSampleData(targetTrack, buffer, info)
            extractor.advance()
        }
        extractor.unselectTrack(sourceTrack)
    }

    private val SUPPORTED_VIDEO_MIMES = setOf("video/avc", "video/hevc", "video/mp4v-es", "video/3gpp")
    private val SUPPORTED_AUDIO_MIMES = setOf("audio/mp4a-latm", "audio/mp4a", "audio/aac", "audio/mpeg", "audio/amr-nb", "audio/amr-wb")
}
