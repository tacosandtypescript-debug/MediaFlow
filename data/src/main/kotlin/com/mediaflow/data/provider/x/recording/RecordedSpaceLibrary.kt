package com.mediaflow.data.provider.x.recording

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RecordedSpaceLibrary(private val file: File) {
    @Synchronized
    fun items(): List<RecordedSpace> {
        if (!file.isFile) return emptyList()
        val text = file.readText().trim()
        if (text.isEmpty()) return emptyList()
        val array = JSONArray(text)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val markersJson = obj.optJSONArray("markers") ?: JSONArray()
                val markers = buildList {
                    for (m in 0 until markersJson.length()) {
                        val marker = markersJson.getJSONObject(m)
                        add(
                            RecordingMarker(
                                relativeTimestampMs = marker.getLong("relativeTimestampMs"),
                                label = if (marker.isNull("label")) null else marker.optString("label"),
                            ),
                        )
                    }
                }
                add(
                    RecordedSpace(
                        spaceId = obj.getString("spaceId"),
                        originalUrl = obj.getString("originalUrl"),
                        filePath = obj.getString("filePath"),
                        elapsedMs = obj.getLong("elapsedMs"),
                        markers = markers,
                    ),
                )
            }
        }
    }

    @Synchronized
    fun add(item: RecordedSpace) {
        val next = items().filterNot { it.spaceId == item.spaceId } + item
        persist(next)
    }

    private fun persist(items: List<RecordedSpace>) {
        file.parentFile?.mkdirs()
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("spaceId", item.spaceId)
                    .put("originalUrl", item.originalUrl)
                    .put("filePath", item.filePath)
                    .put("elapsedMs", item.elapsedMs)
                    .put(
                        "markers",
                        JSONArray().apply {
                            item.markers.forEach { marker ->
                                put(
                                    JSONObject().apply {
                                        put("relativeTimestampMs", marker.relativeTimestampMs)
                                        marker.label?.let { put("label", it) }
                                    },
                                )
                            }
                        },
                    ),
            )
        }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(array.toString())
        tmp.copyTo(file, overwrite = true)
        tmp.delete()
    }
}
