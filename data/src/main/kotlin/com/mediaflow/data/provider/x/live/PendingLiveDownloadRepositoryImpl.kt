package com.mediaflow.data.provider.x.live

import android.content.Context
import com.mediaflow.domain.live.PendingLiveDownload
import com.mediaflow.domain.live.PendingLiveDownloadRepository
import com.mediaflow.domain.live.PendingLiveDownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Thread-safe persistent repository for post-live space downloads.
 */
class PendingLiveDownloadRepositoryImpl(
    private val context: Context,
) : PendingLiveDownloadRepository {

    private val mutex = Mutex()
    private val storageFile: File by lazy {
        File(context.filesDir, "pending_live_downloads.json")
    }

    private val _pendingDownloads = MutableStateFlow<List<PendingLiveDownload>>(emptyList())

    init {
        loadFromDisk()
    }

    override fun observePendingDownloads(): Flow<List<PendingLiveDownload>> {
        return _pendingDownloads.asStateFlow()
    }

    override suspend fun getPendingDownload(spaceId: String): PendingLiveDownload? = mutex.withLock {
        _pendingDownloads.value.firstOrNull { it.spaceId == spaceId }
    }

    override suspend fun savePendingDownload(download: PendingLiveDownload) = mutex.withLock {
        val current = _pendingDownloads.value.toMutableList()
        val index = current.indexOfFirst { it.spaceId == download.spaceId }
        if (index >= 0) {
            current[index] = download
        } else {
            current.add(download)
        }
        _pendingDownloads.value = current
        persistToDisk(current)
    }

    override suspend fun removePendingDownload(spaceId: String) = mutex.withLock {
        val current = _pendingDownloads.value.filter { it.spaceId != spaceId }
        _pendingDownloads.value = current
        persistToDisk(current)
    }

    override suspend fun isAutoDownloadEnabled(spaceId: String): Boolean = mutex.withLock {
        val item = _pendingDownloads.value.firstOrNull { it.spaceId == spaceId }
        item?.autoDownloadAfterEnd == true
    }

    override suspend fun setAutoDownloadEnabled(
        spaceId: String,
        title: String,
        hostHandle: String,
        sourceUrl: String,
        enabled: Boolean,
    ) = mutex.withLock {
        val current = _pendingDownloads.value.toMutableList()
        val index = current.indexOfFirst { it.spaceId == spaceId }
        if (enabled) {
            val item = if (index >= 0) {
                current[index].copy(autoDownloadAfterEnd = true)
            } else {
                PendingLiveDownload(
                    spaceId = spaceId,
                    title = title,
                    hostHandle = hostHandle,
                    sourceUrl = sourceUrl,
                    autoDownloadAfterEnd = true,
                    status = PendingLiveDownloadStatus.WAITING_FOR_END,
                )
            }
            if (index >= 0) current[index] = item else current.add(item)
        } else {
            if (index >= 0) {
                current[index] = current[index].copy(autoDownloadAfterEnd = false)
            }
        }
        _pendingDownloads.value = current
        persistToDisk(current)
    }

    private fun loadFromDisk() {
        runCatching {
            if (!storageFile.exists()) return
            val jsonText = storageFile.readText()
            if (jsonText.isBlank()) return
            val array = JSONArray(jsonText)
            val list = mutableListOf<PendingLiveDownload>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val statusStr = obj.optString("status", PendingLiveDownloadStatus.WAITING_FOR_END.name)
                val status = runCatching { PendingLiveDownloadStatus.valueOf(statusStr) }
                    .getOrDefault(PendingLiveDownloadStatus.WAITING_FOR_END)

                list.add(
                    PendingLiveDownload(
                        spaceId = obj.getString("spaceId"),
                        title = obj.optString("title", "X Space"),
                        hostHandle = obj.optString("hostHandle", "@host"),
                        sourceUrl = obj.optString("sourceUrl", ""),
                        requestedAt = obj.optLong("requestedAt", System.currentTimeMillis()),
                        autoDownloadAfterEnd = obj.optBoolean("autoDownloadAfterEnd", false),
                        status = status,
                        replayStreamUrl = obj.optString("replayStreamUrl").takeIf { it.isNotBlank() },
                        downloadId = obj.optString("downloadId").takeIf { it.isNotBlank() },
                        errorMessage = obj.optString("errorMessage").takeIf { it.isNotBlank() },
                        attemptCount = obj.optInt("attemptCount", 0),
                        nextRetryAtMs = obj.optLong("nextRetryAtMs", 0L).takeIf { it > 0L },
                    )
                )
            }
            _pendingDownloads.value = list
        }
    }

    private fun persistToDisk(list: List<PendingLiveDownload>) {
        runCatching {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("spaceId", item.spaceId)
                    put("title", item.title)
                    put("hostHandle", item.hostHandle)
                    put("sourceUrl", item.sourceUrl)
                    put("requestedAt", item.requestedAt)
                    put("autoDownloadAfterEnd", item.autoDownloadAfterEnd)
                    put("status", item.status.name)
                    item.replayStreamUrl?.let { put("replayStreamUrl", it) }
                    item.downloadId?.let { put("downloadId", it) }
                    item.errorMessage?.let { put("errorMessage", it) }
                    put("attemptCount", item.attemptCount)
                    item.nextRetryAtMs?.let { put("nextRetryAtMs", it) }
                }
                array.put(obj)
            }
            val tmp = File(storageFile.parentFile, "${storageFile.name}.tmp")
            tmp.writeText(array.toString(2))
            if (tmp.renameTo(storageFile)) {
                // Success
            } else {
                storageFile.delete()
                tmp.renameTo(storageFile)
            }
        }
    }
}
