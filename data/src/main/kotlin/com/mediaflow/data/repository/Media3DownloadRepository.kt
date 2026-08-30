package com.mediaflow.data.repository

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import android.net.Uri
import android.util.Base64
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest as Media3DownloadRequest
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.DownloadStatus
import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import com.mediaflow.data.download.DirectDownloadService
import com.mediaflow.data.download.Media3DownloadInfrastructure
import com.mediaflow.data.download.Media3DownloadStateMapper
import com.mediaflow.data.download.ThumbnailPersister
import com.mediaflow.data.download.YtDlpPlatformDownloader
import com.mediaflow.data.media.MediaStorePublisher
import com.mediaflow.data.media.MediaFileValidator
import com.mediaflow.data.media.MediaFlowLibraryStore
import com.mediaflow.data.media.metadata.DefaultMediaMetadataWriter
import com.mediaflow.data.media.metadata.MediaMetadata
import com.mediaflow.data.media.metadata.MediaMetadataWriter
import com.mediaflow.data.resolver.PlatformUrlSupport
import com.mediaflow.domain.repository.DownloadRepository
import com.mediaflow.domain.repository.DownloadRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Real DownloadRepository adapter backed by Media3 DownloadManager.
 *
 * It observes the persistent DownloadIndex, maps Media3 states without
 * fabricating progress, and exports completed cache content through a
 * CacheDataSource into a controlled private file once it is fully complete.
 */
@OptIn(markerClass = [UnstableApi::class])
class Media3DownloadRepository private constructor(
    private val context: Context,
    private val mediaMetadataWriter: MediaMetadataWriter = DefaultMediaMetadataWriter(),
) : DownloadRepository {
    private val infrastructure = Media3DownloadInfrastructure.get(context)
    private val manager = infrastructure.downloadManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val platformDownloader = YtDlpPlatformDownloader(context, mediaMetadataWriter)
    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    private val publishedUris = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val ownership = MediaFlowLibraryStore(context)
    private val listener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            if (download.state == Download.STATE_COMPLETED) {
                scope.launch { exportCompletedFile(download) }
            }
            refresh()
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            refresh()
        }
    }

    init {
        manager.addListener(listener)
        refresh()
        scope.launch { registerPersistedCompletedDownloads() }
    }

    override fun observeDownloads(): Flow<List<DownloadItem>> = kotlinx.coroutines.flow.combine(
        _downloads,
        platformDownloader.items,
    ) { direct, platform -> (direct + platform).sortedByDescending { it.createdAt } }

    override suspend fun getDownloadById(id: String): DownloadItem? =
        (_downloads.value + platformDownloader.items.value).firstOrNull { it.id == id }

    override suspend fun startDownload(request: DownloadRequest): String {
        require(request.sourceUrl.startsWith("https://", ignoreCase = true)) {
            "Solo se permiten URLs HTTPS"
        }
        val id = stableId(request)
        if (PlatformUrlSupport.isSupported(request.sourceUrl)) {
            platformDownloader.start(id, request)
            return id
        }
        val media3Request = Media3DownloadRequest.Builder(id, request.sourceUrl.toUri())
            .setMimeType(request.mimeType)
            .setData(encodeMetadata(request))
            .build()
        DirectDownloadService.addDownload(context, media3Request)
        refresh()
        return id
    }

    override suspend fun pauseDownload(id: String) {
        if (platformDownloader.contains(id)) return
        DirectDownloadService.setStopReason(context, id, USER_PAUSE_REASON)
        refresh()
    }

    override suspend fun resumeDownload(id: String) {
        if (platformDownloader.contains(id)) return
        DirectDownloadService.setStopReason(context, id, Download.STOP_REASON_NONE)
        refresh()
    }

    override suspend fun cancelDownload(id: String) {
        if (platformDownloader.contains(id)) {
            platformDownloader.cancel(id)
            return
        }
        DirectDownloadService.removeDownload(context, id)
        refresh()
    }

    override suspend fun retryDownload(id: String) {
        if (platformDownloader.contains(id)) {
            platformDownloader.retry(id)
            return
        }
        val download = manager.downloadIndex.getDownload(id) ?: return
        DirectDownloadService.addDownload(context, download.request)
        refresh()
    }

    override suspend fun removeDownload(id: String) {
        if (platformDownloader.contains(id)) {
            platformDownloader.remove(id)
            return
        }
        DirectDownloadService.removeDownload(context, id)
        refresh()
    }

    private fun refresh() {
        val cursor = manager.downloadIndex.getDownloads()
        val items = mutableListOf<DownloadItem>()
        cursor.use {
            while (it.moveToNext()) {
                items += it.download.toDomainItem()
            }
        }
        _downloads.value = items.sortedByDescending { it.createdAt }
    }

    /** Rebuilds the private ownership ledger after process/application restart. */
    private suspend fun registerPersistedCompletedDownloads() {
        val cursor = manager.downloadIndex.getDownloads()
        cursor.use {
            while (it.moveToNext()) {
                val download = it.download
                if (download.state == Download.STATE_COMPLETED) {
                    exportCompletedFile(download)
                }
            }
        }
    }

    private suspend fun exportCompletedFile(download: Download) {
        val metadata = decodeMetadata(download.request.data)
        val fileName = metadata.fileName ?: "mediaflow_${download.request.id}"
        val extension = metadata.extension?.let { ".${it}" } ?: ""
        val safeName = sanitizeFileName(fileName).let {
            if (extension.isNotEmpty() && !it.endsWith(extension, ignoreCase = true)) "$it$extension" else it
        }
        val outputDir = File(context.filesDir, "downloads").apply { mkdirs() }
        val output = File(outputDir, safeName)
        if (output.exists() && output.length() > 0L) {
            val mimeType = metadata.mimeType ?: if (metadata.mediaType == MediaType.VIDEO) "video/mp4" else "audio/mpeg"
            MediaFileValidator.validate(
                output,
                metadata.mediaType,
                metadata.extension,
                metadata.durationSeconds,
                metadata.width,
                metadata.height,
                metadata.videoCodec,
                metadata.audioCodec,
            )
                .getOrNull()
                ?.let { MediaStorePublisher.publishIfMissing(context, output, mimeType, output.name) }
                ?.also { uri ->
                    publishedUris[download.request.id] = uri.toString()
                    ownership.add(uri)
                }
            ThumbnailPersister.persist(context, download.request.id, metadata.thumbnailUrl)
            return
        }

        runCatching {
            val dataSource = infrastructure.cacheDataSourceFactory.createDataSource()
            val dataSpec = DataSpec(download.request.uri)
            dataSource.open(dataSpec)
            FileOutputStream(output).use { sink ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = dataSource.read(buffer, 0, buffer.size)
                    if (read == C.RESULT_END_OF_INPUT) break
                    sink.write(buffer, 0, read)
                }
            }
            dataSource.close()
            val mimeType = metadata.mimeType ?: if (metadata.mediaType == MediaType.VIDEO) "video/mp4" else "audio/mpeg"
            MediaFileValidator.validate(
                output,
                metadata.mediaType,
                metadata.extension,
                metadata.durationSeconds,
                metadata.width,
                metadata.height,
                metadata.videoCodec,
                metadata.audioCodec,
            )
                .getOrElse { validationError ->
                    throw IllegalStateException("La validación multimedia falló: ${validationError.message}")
                }

            // Attempt non-blocking metadata embedding
            runCatching {
                val mediaMetadata = MediaMetadata(
                    title = com.mediaflow.data.ytdlp.YtDlpRuntime.fileStem(safeName),
                )
                mediaMetadataWriter.writeMetadata(output, mediaMetadata)
            }.onFailure { error ->
                android.util.Log.w("Media3DownloadRepository", "No se pudieron incrustar metadatos en ${output.name}: ${error.message}")
            }

            MediaStorePublisher.publishIfMissing(context, output, mimeType, output.name)?.let {
                publishedUris[download.request.id] = it.toString()
                ownership.add(it)
            }
            ThumbnailPersister.persist(context, download.request.id, metadata.thumbnailUrl)
            refresh()
        }.onFailure {
            output.delete()
            refresh()
        }
    }

    private fun Download.toDomainItem(): DownloadItem {
        val metadata = decodeMetadata(request.data)
        val percentage = percentDownloaded
        val progressKnown = percentage >= 0f && !percentage.isNaN()
        val totalBytes = contentLength.takeIf { it != C.LENGTH_UNSET.toLong() && it >= 0L }
        val downloaded = bytesDownloaded.coerceAtLeast(0L)
        val localUri = if (state == Download.STATE_COMPLETED) {
            publishedUris[request.id] ?: completedFileUri(metadata.fileName, metadata.extension, request.id)
        } else {
            null
        }
        val thumbnailUri = ThumbnailPersister.existingUri(context, request.id) ?: metadata.thumbnailUrl
        return DownloadItem(
            id = request.id,
            sourceUrl = request.uri.toString(),
            title = metadata.fileName,
            fileName = metadata.fileName,
            mediaType = metadata.mediaType,
            thumbnailUri = thumbnailUri,
            selectedFormat = metadata.extension?.let { extension ->
                MediaFormat(
                    formatId = metadata.formatId ?: "direct",
                    extension = extension,
                    mimeType = metadata.mimeType,
                    mediaType = metadata.mediaType,
                    qualityLabel = metadata.qualityLabel,
                    width = metadata.width,
                    height = metadata.height,
                    fps = metadata.fps,
                    container = metadata.container,
                    videoCodec = metadata.videoCodec,
                    audioCodec = metadata.audioCodec,
                    isProgressive = true,
                    requiresMuxing = false,
                )
            },
            localUri = localUri,
            durationSeconds = metadata.durationSeconds,
            progress = if (progressKnown) (percentage / 100f).coerceIn(0f, 1f) else 0f,
            isProgressKnown = progressKnown,
            downloadedBytes = downloaded,
            totalBytes = totalBytes,
            speedBytesPerSecond = 0L,
            status = toDomainStatus(),
            errorMessage = if (state == Download.STATE_FAILED) {
                "Error de descarga (código $failureReason)"
            } else null,
            createdAt = startTimeMs,
            completedAt = if (state == Download.STATE_COMPLETED) updateTimeMs else null,
        )
    }

    private fun Download.toDomainStatus(): DownloadStatus =
        Media3DownloadStateMapper.map(state, stopReason)

    private fun completedFileUri(fileName: String?, extension: String?, id: String): String? {
        val base = sanitizeFileName(fileName ?: "mediaflow_$id")
        val name = if (!extension.isNullOrBlank() && !base.endsWith(".$extension", true)) {
            "$base.$extension"
        } else base
        val file = File(File(context.filesDir, "downloads"), name)
        return file.takeIf { it.exists() && it.length() > 0L }?.let { Uri.fromFile(it).toString() }
    }

    private fun stableId(request: DownloadRequest): String =
        MessageDigest.getInstance("SHA-256")
            .digest("${request.sourceUrl}|${request.fileName}".toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)

    private fun encodeMetadata(request: DownloadRequest): ByteArray =
        Base64.encode(
            JSONObject()
                .put("fileName", request.fileName)
                .put("mediaType", request.mediaType.name)
                .put("mimeType", request.mimeType)
                .put("extension", request.extension)
                .put("qualityLabel", request.qualityLabel)
                .put("formatId", request.formatId)
                .put("durationSeconds", request.durationSeconds)
                .put("requiresMuxing", request.requiresMuxing)
                .put("width", request.width)
                .put("height", request.height)
                .put("fps", request.fps)
                .put("container", request.container)
                .put("videoCodec", request.videoCodec)
                .put("audioCodec", request.audioCodec)
                .put("thumbnailUrl", request.thumbnailUrl)
                .toString()
                .toByteArray(),
            Base64.NO_WRAP,
        )

    private fun decodeMetadata(data: ByteArray): Metadata {
        return runCatching {
            val json = JSONObject(String(Base64.decode(data, Base64.NO_WRAP)))
            Metadata(
                fileName = json.optString("fileName").takeIf { it.isNotBlank() },
                mediaType = runCatching { MediaType.valueOf(json.optString("mediaType")) }
                    .getOrDefault(MediaType.VIDEO),
                extension = json.optString("extension").takeIf { it.isNotBlank() },
                mimeType = json.optString("mimeType").takeIf { it.isNotBlank() },
                qualityLabel = json.optString("qualityLabel").takeIf { it.isNotBlank() },
                formatId = json.optString("formatId").takeIf { it.isNotBlank() },
                durationSeconds = json.optLong("durationSeconds", 0L).takeIf { it > 0L },
                requiresMuxing = json.optBoolean("requiresMuxing", false),
                width = json.optInt("width", 0).takeIf { it > 0 },
                height = json.optInt("height", 0).takeIf { it > 0 },
                fps = json.optDouble("fps", Double.NaN).takeIf { !it.isNaN() && it > 0 },
                container = json.optString("container").takeIf { it.isNotBlank() },
                videoCodec = json.optString("videoCodec").takeIf { it.isNotBlank() },
                audioCodec = json.optString("audioCodec").takeIf { it.isNotBlank() },
                thumbnailUrl = json.optString("thumbnailUrl").takeIf { it.isNotBlank() },
            )
        }.getOrDefault(Metadata())
    }

    private fun sanitizeFileName(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|\u0000-\u001F]"), "")
            .ifBlank { "mediaflow_download" }

    private data class Metadata(
        val fileName: String? = null,
        val mediaType: MediaType = MediaType.VIDEO,
        val extension: String? = null,
        val mimeType: String? = null,
        val qualityLabel: String? = null,
        val formatId: String? = null,
        val durationSeconds: Long? = null,
        val requiresMuxing: Boolean = false,
        val width: Int? = null,
        val height: Int? = null,
        val fps: Double? = null,
        val container: String? = null,
        val videoCodec: String? = null,
        val audioCodec: String? = null,
        val thumbnailUrl: String? = null,
    )

    companion object {
        const val USER_PAUSE_REASON = 1

        @Suppress("StaticFieldLeak")
        @Volatile
        private var instance: Media3DownloadRepository? = null

        fun get(context: Context): Media3DownloadRepository =
            instance ?: synchronized(this) {
                instance ?: Media3DownloadRepository(context.applicationContext).also { instance = it }
            }
    }
}
