package com.mediaflow.data.provider.x.recording

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class RecordingCheckpoint(
    val spaceId: String,
    val originalUrl: String,
    val phase: RecordingPhase,
    val elapsedMs: Long,
    val segmentCount: Int,
    val markers: List<RecordingMarker>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("spaceId", spaceId)
        put("originalUrl", originalUrl)
        put("phase", phase.name)
        put("elapsedMs", elapsedMs)
        put("segmentCount", segmentCount)
        put("markers", JSONArray().apply {
            markers.forEach { marker ->
                put(
                    JSONObject().apply {
                        put("relativeTimestampMs", marker.relativeTimestampMs)
                        marker.label?.let { put("label", it) }
                    },
                )
            }
        })
    }

    companion object {
        fun fromJson(json: JSONObject): RecordingCheckpoint {
            val markerArray = json.optJSONArray("markers") ?: JSONArray()
            val markers = buildList {
                for (i in 0 until markerArray.length()) {
                    val obj = markerArray.getJSONObject(i)
                    add(
                        RecordingMarker(
                            relativeTimestampMs = obj.getLong("relativeTimestampMs"),
                            label = if (obj.isNull("label")) null else obj.optString("label"),
                        ),
                    )
                }
            }
            return RecordingCheckpoint(
                spaceId = json.getString("spaceId"),
                originalUrl = json.getString("originalUrl"),
                phase = RecordingPhase.valueOf(json.getString("phase")),
                elapsedMs = json.getLong("elapsedMs"),
                segmentCount = json.getInt("segmentCount"),
                markers = markers,
            )
        }
    }
}

class RecordingCheckpointStore(private val directory: File) {
    init {
        directory.mkdirs()
    }

    fun segmentFile(index: Int): File = File(directory, "seg_%06d.bin".format(index))

    fun writeSegment(index: Int, payload: ByteArray) {
        segmentFile(index).writeBytes(payload)
    }

    fun writeCheckpoint(checkpoint: RecordingCheckpoint) {
        val file = File(directory, "checkpoint.json")
        val tmp = File(directory, "checkpoint.json.tmp")
        tmp.writeText(checkpoint.toJson().toString())
        tmp.copyTo(file, overwrite = true)
        tmp.delete()
    }

    fun loadCheckpoint(): RecordingCheckpoint? {
        val file = File(directory, "checkpoint.json")
        if (!file.isFile) return null
        return runCatching { RecordingCheckpoint.fromJson(JSONObject(file.readText())) }.getOrNull()
    }

    fun recoverBytes(): ByteArray {
        val parts = directory.listFiles()
            ?.filter { it.name.startsWith("seg_") && it.name.endsWith(".bin") }
            ?.sortedBy { it.name }
            .orEmpty()
        return parts.fold(ByteArray(0)) { acc, file -> acc + file.readBytes() }
    }

    fun finalizeTo(output: File): File {
        output.parentFile?.mkdirs()
        output.writeBytes(recoverBytes())
        return output
    }
}
