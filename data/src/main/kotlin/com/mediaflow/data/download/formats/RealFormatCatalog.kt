package com.mediaflow.data.download.formats

import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType

/**
 * UI/download catalog built only from extractor [MediaFormat]s.
 *
 * Heights, fps, and codecs appear iff they exist on a format. Standard rungs
 * such as 480/1440/2160 are never invented.
 */
object RealFormatCatalog {
    data class ListedFormat(
        val format: MediaFormat,
        val label: String,
        val height: Int?,
        val fps: Double?,
        val videoCodec: String?,
        val audioCodec: String?,
    )

    fun listed(formats: List<MediaFormat>): List<ListedFormat> =
        formats
            .map { ListedFormat(it, labelFor(it), it.height, it.fps, it.videoCodec, it.audioCodec) }
            .distinctBy { it.label }

    fun listedHeights(formats: List<MediaFormat>): List<Int> =
        formats.mapNotNull { it.height }.distinct()

    fun listedFps(formats: List<MediaFormat>): List<Double> =
        formats.mapNotNull { it.fps }.distinct()

    fun listedVideoCodecs(formats: List<MediaFormat>): List<String> =
        formats.mapNotNull { it.videoCodec?.takeIf { codec -> codec.isNotBlank() } }.distinct()

    fun listedAudioCodecs(formats: List<MediaFormat>): List<String> =
        formats.mapNotNull { it.audioCodec?.takeIf { codec -> codec.isNotBlank() } }.distinct()

    fun labelFor(format: MediaFormat): String {
        val audioName = prettyAudioCodec(format.audioCodec)
        val videoName = prettyVideoCodec(format.videoCodec)
        val container = prettyContainer(format.extension ?: format.container)
        val bitrate = format.bitrate?.takeIf { it > 0 }?.let { "$it kbps" }
        val fpsPart = format.fps?.let { fps ->
            val fpsLabel = if (fps % 1.0 == 0.0) fps.toInt().toString() else fps.toString()
            "${fpsLabel}fps"
        }
        val isAudioOnly = format.height == null &&
            (format.mediaType == MediaType.AUDIO || format.videoCodec.isNullOrBlank())

        val parts = buildList {
            format.height?.let { add("${it}p") }
            if (!isAudioOnly) {
                videoName?.let(::add)
            }
            when {
                isAudioOnly && audioName != null && bitrate != null -> {
                    add(audioName)
                    add(bitrate)
                }
                isAudioOnly && audioName != null && container != null -> {
                    add(audioName)
                }
                isAudioOnly && audioName != null -> {
                    add("Audio")
                    add(audioName)
                }
                audioName != null && audioName != videoName -> add(audioName)
            }
            if (!isAudioOnly) {
                bitrate?.let(::add)
                fpsPart?.let(::add)
            }
            container?.let { ext ->
                if (ext !in this) add(ext)
            }
            if (isEmpty()) {
                format.formatId.takeIf { it.isNotBlank() }?.let(::add)
            }
            if (isEmpty()) add(if (isAudioOnly) "Audio" else "Video")
        }
        return parts.joinToString(" · ")
    }

    private fun prettyAudioCodec(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val lower = value.lowercase()
        return when {
            lower.contains("opus") -> "Opus"
            lower.contains("mp4a") || lower.contains("aac") -> "AAC"
            lower.contains("mp3") || lower.contains("mpeg") -> "MP3"
            lower.contains("vorbis") -> "Vorbis"
            lower.contains("flac") -> "FLAC"
            else -> value
        }
    }

    private fun prettyVideoCodec(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return value
    }

    private fun prettyContainer(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return when (value.lowercase()) {
            "m4a" -> "M4A"
            "mp4", "m4v" -> "MP4"
            "webm" -> "WebM"
            "mkv" -> "MKV"
            "mp3" -> "MP3"
            "ogg" -> "OGG"
            "opus" -> "Opus"
            else -> value.uppercase()
        }
    }

    /**
     * Prefers progressive MP4 / AVC+AAC over VP9/AV1, matching PreferredDownloadFormat.
     */
    fun bestCompatible(formats: List<MediaFormat>): MediaFormat? {
        if (formats.isEmpty()) return null
        pickCompatible(formats.filter { it.isProgressive && !it.requiresMuxing })
            ?.let { return it }
        val heights = formats.mapNotNull { it.height }.distinct().sortedDescending()
        for (height in heights) {
            pickCompatible(formats.filter { it.height == height })?.let { return it }
        }
        pickCompatible(formats)?.let { return it }
        return best(formats)
    }

    private fun pickCompatible(pool: List<MediaFormat>): MediaFormat? {
        if (pool.isEmpty()) return null
        val progressive = pool.filter { it.isProgressive && !it.requiresMuxing }
        if (progressive.isNotEmpty()) return best(progressive)
        val muxable = pool.filter { isMuxableAvcHevcMp4(it) }
        if (muxable.isNotEmpty()) return best(muxable)
        return null
    }

    private fun best(pool: List<MediaFormat>): MediaFormat? {
        if (pool.isEmpty()) return null
        val bestClass = pool.maxOf { compatibilityScore(it) }
        return pool.filter { compatibilityScore(it) == bestClass }
            .maxWithOrNull(
                compareBy<MediaFormat> { it.height ?: 0 }
                    .thenBy { it.fps ?: 0.0 }
                    .thenBy { it.bitrate ?: 0L },
            )
    }

    private fun isMuxableAvcHevcMp4(format: MediaFormat): Boolean {
        val codec = format.videoCodec?.lowercase().orEmpty()
        val ext = format.extension?.lowercase().orEmpty()
        val container = format.container?.lowercase().orEmpty()
        val muxableCodec = codec.contains("avc") || codec.contains("h264") ||
            codec.contains("hev") || codec.contains("h265") || codec.contains("hvc")
        val mp4 = ext in setOf("mp4", "m4v") || container.contains("mp4")
        return muxableCodec && (mp4 || ext.isEmpty())
    }

    private fun compatibilityScore(format: MediaFormat): Int {
        val codec = format.videoCodec?.lowercase().orEmpty()
        val audio = format.audioCodec?.lowercase().orEmpty()
        val ext = format.extension?.lowercase().orEmpty()
        var score = 0
        if (format.isProgressive && !format.requiresMuxing) score += 8
        if (isMuxableAvcHevcMp4(format)) score += 6
        if (ext in setOf("mp4", "m4v", "m4a")) score += 2
        if (audio.contains("mp4a") || audio.contains("aac")) score += 2
        if (codec.contains("vp9") || codec.contains("vp09") || codec.contains("av01") ||
            (codec.contains("av1") && !codec.contains("avc"))
        ) {
            score -= 6
        }
        if (ext == "webm" || ext == "mkv") score -= 2
        if (audio.contains("opus")) score -= 1
        return score
    }
}
