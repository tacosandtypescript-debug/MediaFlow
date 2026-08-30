package com.mediaflow.data.download

import android.content.Context
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.DownloadStatus
import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Private, app-owned persistence for platform downloads not managed by Media3. */
class PlatformDownloadStore private constructor(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "platform_downloads.json"))

    internal constructor(file: File, marker: Unit = Unit) : this(file)

    @Synchronized
    fun load(): List<DownloadItem> = runCatching {
        val array = JSONArray(file.takeIf { it.isFile }?.readText().orEmpty())
        buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                decode(json)?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    @Synchronized
    fun save(items: List<DownloadItem>) {
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(JSONArray(items.map(::encode)).toString())
        temp.copyTo(file, overwrite = true)
        temp.delete()
    }

    private fun encode(item: DownloadItem): JSONObject = JSONObject().apply {
        put("id", item.id); put("sourceUrl", item.sourceUrl); put("title", item.title)
        put("fileName", item.fileName); put("mediaType", item.mediaType.name)
        put("localUri", item.localUri); put("thumbnailUri", item.thumbnailUri)
        item.width?.let { put("width", it) }
        item.height?.let { put("height", it) }
        put("durationSeconds", item.durationSeconds); put("progress", item.progress)
        put("isProgressKnown", item.isProgressKnown); put("downloadedBytes", item.downloadedBytes)
        put("totalBytes", item.totalBytes); put("status", item.status.name)
        put("errorMessage", item.errorMessage); put("createdAt", item.createdAt)
        put("completedAt", item.completedAt)
        item.selectedFormat?.let { format -> put("format", encodeFormat(format)) }
    }

    private fun encodeFormat(format: MediaFormat) = JSONObject().apply {
        put("formatId", format.formatId); put("extension", format.extension)
        put("mimeType", format.mimeType); put("mediaType", format.mediaType.name)
        put("qualityLabel", format.qualityLabel); put("width", format.width)
        put("height", format.height); put("fps", format.fps); put("container", format.container)
        put("videoCodec", format.videoCodec); put("audioCodec", format.audioCodec)
        put("durationSeconds", format.durationSeconds); put("bitrate", format.bitrate)
        put("fileSize", format.fileSize); put("isProgressive", format.isProgressive)
        put("requiresMuxing", format.requiresMuxing)
        put("streamUrl", format.streamUrl)
    }

    private fun decode(json: JSONObject): DownloadItem? = runCatching {
        DownloadItem(
            id = json.getString("id"), sourceUrl = json.getString("sourceUrl"),
            title = json.optString("title").takeIf { it.isNotBlank() },
            fileName = json.optString("fileName").takeIf { it.isNotBlank() },
            mediaType = MediaType.valueOf(json.optString("mediaType")),
            selectedFormat = json.optJSONObject("format")?.let(::decodeFormat),
            localUri = json.optString("localUri").takeIf { it.isNotBlank() },
            thumbnailUri = json.optString("thumbnailUri").takeIf { it.isNotBlank() },
            width = json.optInt("width", 0).takeIf { it > 0 },
            height = json.optInt("height", 0).takeIf { it > 0 },
            durationSeconds = json.optLong("durationSeconds", 0L).takeIf { it > 0L },
            progress = json.optDouble("progress", 0.0).toFloat().coerceIn(0f, 1f),
            isProgressKnown = json.optBoolean("isProgressKnown"),
            downloadedBytes = json.optLong("downloadedBytes"),
            totalBytes = json.optLong("totalBytes", 0L).takeIf { it > 0L },
            status = DownloadStatus.valueOf(json.optString("status")),
            errorMessage = json.optString("errorMessage").takeIf { it.isNotBlank() },
            createdAt = json.optLong("createdAt"),
            completedAt = json.optLong("completedAt", 0L).takeIf { it > 0L },
        )
    }.getOrNull()

    private fun decodeFormat(json: JSONObject) = MediaFormat(
        formatId = json.optString("formatId"),
        extension = json.optString("extension").takeIf { it.isNotBlank() },
        mimeType = json.optString("mimeType").takeIf { it.isNotBlank() },
        mediaType = MediaType.valueOf(json.optString("mediaType")),
        qualityLabel = json.optString("qualityLabel").takeIf { it.isNotBlank() },
        width = json.optInt("width", 0).takeIf { it > 0 }, height = json.optInt("height", 0).takeIf { it > 0 },
        fps = json.optDouble("fps", Double.NaN).takeIf { !it.isNaN() },
        container = json.optString("container").takeIf { it.isNotBlank() },
        videoCodec = json.optString("videoCodec").takeIf { it.isNotBlank() },
        audioCodec = json.optString("audioCodec").takeIf { it.isNotBlank() },
        durationSeconds = json.optLong("durationSeconds", 0L).takeIf { it > 0L },
        bitrate = json.optLong("bitrate", 0L).takeIf { it > 0L },
        fileSize = json.optLong("fileSize", 0L).takeIf { it > 0L },
        isProgressive = json.optBoolean("isProgressive"), requiresMuxing = json.optBoolean("requiresMuxing"),
        streamUrl = json.optString("streamUrl").takeIf { it.startsWith("https://") },
    )
}
