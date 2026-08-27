package com.mediaflow.data.player

import android.content.Context
import com.mediaflow.core.model.PlaybackProgress
import com.mediaflow.core.model.PlaybackStatus
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Thread-safe, disk-backed persistence for media playback progress records.
 * Uses atomic file replacement to safeguard against sudden process termination.
 */
class PlatformProgressStore private constructor(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "mediaflow_progress.json"))

    internal constructor(file: File, marker: Unit = Unit) : this(file)

    @Synchronized
    fun load(): Map<String, PlaybackProgress> = runCatching {
        if (!file.isFile) return@runCatching emptyMap()
        val text = file.readText()
        if (text.isBlank()) return@runCatching emptyMap()
        val array = JSONArray(text)
        buildMap {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                decode(obj)?.let { put(it.mediaId, it) }
            }
        }
    }.getOrDefault(emptyMap())

    @Synchronized
    fun save(items: Map<String, PlaybackProgress>) {
        val array = JSONArray()
        items.values.forEach { array.put(encode(it)) }

        val tempFile = File(file.parentFile, "${file.name}.tmp")
        tempFile.parentFile?.mkdirs()
        tempFile.writeText(array.toString())
        tempFile.copyTo(file, overwrite = true)
        tempFile.delete()
    }

    private fun encode(progress: PlaybackProgress): JSONObject = JSONObject().apply {
        put("mediaId", progress.mediaId)
        put("filePath", progress.filePath)
        put("totalDurationMs", progress.totalDurationMs)
        put("currentPositionMs", progress.currentPositionMs)
        put("playbackPercentage", progress.playbackPercentage.toDouble())
        put("lastPlayedAt", progress.lastPlayedAt)
        put("status", progress.status.name)
        put("playCount", progress.playCount)
    }

    private fun decode(json: JSONObject): PlaybackProgress? = runCatching {
        val mediaId = json.getString("mediaId")
        val filePath = json.optString("filePath", mediaId)
        val totalDurationMs = json.optLong("totalDurationMs", 0L)
        val currentPositionMs = json.optLong("currentPositionMs", 0L)
        val statusStr = json.optString("status", PlaybackStatus.NEW.name)
        val status = runCatching { PlaybackStatus.valueOf(statusStr) }.getOrDefault(PlaybackStatus.NEW)
        val lastPlayedAt = json.optLong("lastPlayedAt", System.currentTimeMillis())
        val playCount = json.optInt("playCount", 0)

        PlaybackProgress(
            mediaId = mediaId,
            filePath = filePath,
            totalDurationMs = totalDurationMs,
            currentPositionMs = currentPositionMs,
            lastPlayedAt = lastPlayedAt,
            status = status,
            playCount = playCount,
        )
    }.getOrNull()
}
