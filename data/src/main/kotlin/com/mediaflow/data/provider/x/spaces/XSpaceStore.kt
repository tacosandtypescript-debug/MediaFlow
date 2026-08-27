package com.mediaflow.data.provider.x.spaces

import android.content.Context
import com.mediaflow.core.model.ParticipantRole
import com.mediaflow.core.model.SpeakerSegment
import com.mediaflow.core.model.XParticipant
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Thread-safe disk storage for [XSpace] records and mediaId-to-spaceId associations.
 */
class XSpaceStore(
    private val context: Context,
    private val fileName: String = "mediaflow_spaces.json",
) {
    private val targetFile: File
        get() = File(context.filesDir, fileName)

    private val tempFile: File
        get() = File(context.filesDir, "$fileName.tmp")

    fun loadAllSync(): Map<String, XSpace> {
        if (!targetFile.exists() || targetFile.length() == 0L) return emptyMap()
        return try {
            val content = targetFile.readText()
            if (content.isBlank()) return emptyMap()
            val root = JSONObject(content)
            val spacesObj = root.optJSONObject("spaces") ?: root
            val map = mutableMapOf<String, XSpace>()
            for (key in spacesObj.keys()) {
                val spaceObj = spacesObj.optJSONObject(key) ?: continue
                val space = deserializeSpace(spaceObj)
                map[key] = space
            }
            map
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    suspend fun loadAll(): Map<String, XSpace> = withContext(Dispatchers.IO) {
        loadAllSync()
    }

    suspend fun saveAll(spaces: Map<String, XSpace>): Unit = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            val spacesObj = JSONObject()
            spaces.forEach { (id, space) ->
                spacesObj.put(id, serializeSpace(space))
            }
            root.put("version", 1)
            root.put("spaces", spacesObj)

            tempFile.writeText(root.toString(2))
            if (targetFile.exists()) {
                targetFile.delete()
            }
            tempFile.renameTo(targetFile)
        } catch (_: Throwable) {}
    }

    private fun serializeSpace(space: XSpace): JSONObject {
        return JSONObject().apply {
            put("id", space.id)
            put("url", space.url)
            put("title", space.title)
            put("state", space.state.name)
            put("host", serializeParticipant(space.host))
            put("cohosts", JSONArray(space.cohosts.map { serializeParticipant(it) }))
            put("speakers", JSONArray(space.speakers.map { serializeParticipant(it) }))
            put("participants", JSONArray(space.participants.map { serializeParticipant(it) }))
            space.createdAtMs?.let { put("createdAtMs", it) }
            space.startedAtMs?.let { put("startedAtMs", it) }
            space.endedAtMs?.let { put("endedAtMs", it) }
            put("durationSeconds", space.durationSeconds)
            put("recordingAvailable", space.recordingAvailable)
            put("liveListenersCount", space.liveListenersCount)
            put("replayCount", space.replayCount)
            space.audioStreamUrl?.let { put("audioStreamUrl", it) }
            put("speakerSegments", JSONArray(space.speakerSegments.map { seg ->
                JSONObject().apply {
                    put("speakerId", seg.speakerId)
                    put("startSeconds", seg.startSeconds)
                    put("endSeconds", seg.endSeconds)
                    seg.textSnippet?.let { put("textSnippet", it) }
                }
            }))
            space.rawMetadata?.let { put("rawMetadata", it) }
        }
    }

    private fun serializeParticipant(p: XParticipant): JSONObject {
        return JSONObject().apply {
            put("displayName", p.displayName)
            put("username", p.username)
            p.userId?.let { put("userId", it) }
            p.avatarUrl?.let { put("avatarUrl", it) }
            put("role", p.role.name)
        }
    }

    private fun deserializeSpace(json: JSONObject): XSpace {
        val hostObj = json.optJSONObject("host") ?: JSONObject()
        val host = deserializeParticipant(hostObj, ParticipantRole.HOST)

        val cohosts = deserializeParticipantsArray(json.optJSONArray("cohosts"), ParticipantRole.COHOST)
        val speakers = deserializeParticipantsArray(json.optJSONArray("speakers"), ParticipantRole.SPEAKER)
        val participants = deserializeParticipantsArray(json.optJSONArray("participants"), ParticipantRole.UNKNOWN)

        val speakerSegments = mutableListOf<SpeakerSegment>()
        val segsArray = json.optJSONArray("speakerSegments")
        if (segsArray != null) {
            for (i in 0 until segsArray.length()) {
                val segObj = segsArray.optJSONObject(i) ?: continue
                speakerSegments.add(
                    SpeakerSegment(
                        speakerId = segObj.optString("speakerId"),
                        startSeconds = segObj.optDouble("startSeconds", 0.0),
                        endSeconds = segObj.optDouble("endSeconds", 0.0),
                        textSnippet = segObj.optString("textSnippet").takeIf { it.isNotBlank() },
                    )
                )
            }
        }

        return XSpace(
            id = json.optString("id"),
            url = json.optString("url"),
            title = json.optString("title"),
            state = XSpaceState.fromString(json.optString("state")),
            host = host,
            cohosts = cohosts,
            speakers = speakers,
            participants = participants.ifEmpty { listOf(host) + cohosts + speakers },
            createdAtMs = json.optLong("createdAtMs", 0L).takeIf { it > 0L },
            startedAtMs = json.optLong("startedAtMs", 0L).takeIf { it > 0L },
            endedAtMs = json.optLong("endedAtMs", 0L).takeIf { it > 0L },
            durationSeconds = json.optLong("durationSeconds", 0L),
            recordingAvailable = json.optBoolean("recordingAvailable", false),
            liveListenersCount = json.optInt("liveListenersCount", 0),
            replayCount = json.optInt("replayCount", 0),
            audioStreamUrl = json.optString("audioStreamUrl").takeIf { it.isNotBlank() },
            speakerSegments = speakerSegments,
            rawMetadata = json.optString("rawMetadata").takeIf { it.isNotBlank() },
        )
    }

    private fun deserializeParticipantsArray(array: JSONArray?, defaultRole: ParticipantRole): List<XParticipant> {
        if (array == null) return emptyList()
        val list = mutableListOf<XParticipant>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            list.add(deserializeParticipant(obj, defaultRole))
        }
        return list
    }

    private fun deserializeParticipant(obj: JSONObject, fallbackRole: ParticipantRole): XParticipant {
        val roleStr = obj.optString("role")
        val role = if (roleStr.isNotBlank()) ParticipantRole.fromString(roleStr) else fallbackRole
        return XParticipant(
            displayName = obj.optString("displayName", "User"),
            username = obj.optString("username", "user"),
            userId = obj.optString("userId").takeIf { it.isNotBlank() },
            avatarUrl = obj.optString("avatarUrl").takeIf { it.isNotBlank() },
            role = role,
        )
    }
}
