package com.mediaflow.data.download.extractors

import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import org.json.JSONArray
import org.json.JSONObject

/** Maps yt-dlp format JSON to [MediaFormat] without inventing height/fps/codec. */
object YtDlpFormatParser {
    fun parseRoot(json: String): List<MediaFormat> {
        val root = JSONObject(json.trim())
        val duration = root.optDouble("duration", Double.NaN)
            .takeIf { !it.isNaN() && it >= 0 }
            ?.toLong()
        val array = root.optJSONArray("formats")
        val parsed = if (array != null) parseArray(array, duration) else emptyList()
        if (parsed.isNotEmpty()) return parsed.distinctBy { it.formatId }
        return listOfNotNull(parseOne(root, duration))
    }

    fun parseArray(array: JSONArray, duration: Long?): List<MediaFormat> = buildList {
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            parseOne(obj, duration)?.let(::add)
        }
    }

    fun parseOne(json: JSONObject, duration: Long?): MediaFormat? {
        val formatId = json.optString("format_id").takeIf { it.isNotBlank() }
            ?: json.optString("id").takeIf { it.isNotBlank() }
            ?: if (json.has("url") || json.has("ext")) "direct" else return null
        val videoCodec = json.optString("vcodec").takeIf { it.isNotBlank() && it != "none" && it != "null" }
        val audioCodec = json.optString("acodec").takeIf { it.isNotBlank() && it != "none" && it != "null" }
        val extension = json.optString("ext").takeIf { it.isNotBlank() } ?: "mp4"
        val formatNote = json.optString("format_note")
        if (extension.equals("mhtml", ignoreCase = true) || formatNote.contains("storyboard", ignoreCase = true)) {
            return null
        }
        val height = json.optInt("height", 0).takeIf { it > 0 }
        val width = json.optInt("width", 0).takeIf { it > 0 }
        val mediaType = when {
            videoCodec != null -> MediaType.VIDEO
            audioCodec != null -> MediaType.AUDIO
            extension.lowercase() in listOf("mp3", "m4a", "aac", "wav", "ogg", "opus") -> MediaType.AUDIO
            height != null || width != null -> MediaType.VIDEO
            else -> MediaType.VIDEO
        }
        val progressive = (videoCodec != null && audioCodec != null) ||
            (videoCodec == null && audioCodec == null && mediaType == MediaType.VIDEO)
        val fps = json.optDouble("fps", Double.NaN).takeIf { !it.isNaN() && it > 0 }
        val size = firstPositive(json, "filesize", "filesize_approx")
        val bitrate = firstPositive(json, "tbr", "vbr", "abr")
        return MediaFormat(
            formatId = formatId,
            extension = extension,
            mimeType = mimeFor(extension, mediaType),
            mediaType = mediaType,
            qualityLabel = height?.let { "${it}p" } ?: formatNote.takeIf { it.isNotBlank() },
            width = width,
            height = height,
            fps = fps,
            container = json.optString("container").takeIf { it.isNotBlank() } ?: extension,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            durationSeconds = duration,
            bitrate = bitrate,
            fileSize = size,
            isProgressive = progressive,
            requiresMuxing = videoCodec != null && audioCodec == null,
            streamUrl = json.optString("url").takeIf { it.startsWith("https://") },
        )
    }

    private fun firstPositive(json: JSONObject, vararg keys: String): Long? = keys
        .asSequence()
        .mapNotNull { key -> json.optDouble(key, Double.NaN).takeIf { !it.isNaN() && it > 0 }?.toLong() }
        .firstOrNull()

    private fun mimeFor(extension: String?, mediaType: MediaType): String? = when (extension?.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        else -> if (mediaType == MediaType.VIDEO) "video/*" else "audio/*"
    }
}
