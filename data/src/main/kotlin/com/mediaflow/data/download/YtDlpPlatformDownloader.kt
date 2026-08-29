package com.mediaflow.data.download

import android.content.Context
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.DownloadStatus
import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import com.mediaflow.domain.repository.DownloadRequest
import com.mediaflow.data.resolver.PlatformUrlSupport
import com.mediaflow.data.resolver.InstagramAnonymousResolver
import com.mediaflow.data.resolver.TikTokAnonymousResolver
import com.mediaflow.data.media.MediaStorePublisher
import com.mediaflow.data.media.MediaFileValidator
import com.mediaflow.data.media.MediaFlowLibraryStore
import com.mediaflow.data.media.MediaTrackMuxer
import com.mediaflow.data.media.metadata.DefaultMediaMetadataWriter
import com.mediaflow.data.media.metadata.MediaMetadata
import com.mediaflow.data.media.metadata.MediaMetadataWriter
import com.mediaflow.data.provider.x.spaces.XSpaceStore
import com.mediaflow.data.ytdlp.YtDlpRuntime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Runs yt-dlp for supported platform pages and exposes truthful progress. */
class YtDlpPlatformDownloader(
    private val context: Context,
    private val mediaMetadataWriter: MediaMetadataWriter = DefaultMediaMetadataWriter(),
) {
    private val outputDirectory = File(context.filesDir, "downloads").apply { mkdirs() }
    private val store = PlatformDownloadStore(context)
    private val xSpaceStore = XSpaceStore(context)
    private val ownership = MediaFlowLibraryStore(context)
    private val sessions = LinkedHashMap<String, Session>()
    private val executor = Executors.newCachedThreadPool()
    private val _items = MutableStateFlow(store.load())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    init {
        runCatching { YtDlpRuntime.ensureReady(context) }
    }

    fun start(id: String, request: DownloadRequest) {
        val now = System.currentTimeMillis()
        val initialThumb = ThumbnailPersister.existingUri(context, id) ?: request.thumbnailUrl
        update(
            DownloadItem(
                id = id,
                sourceUrl = request.sourceUrl,
                title = request.fileName,
                fileName = request.fileName,
                mediaType = request.mediaType,
                thumbnailUri = initialThumb,
                selectedFormat = MediaFormat(
                    formatId = request.formatId ?: "yt-dlp",
                    extension = request.extension ?: "mp4",
                    mimeType = request.mimeType ?: "video/mp4",
                    mediaType = request.mediaType,
                    qualityLabel = request.qualityLabel,
                    width = request.width,
                    height = request.height,
                    fps = request.fps,
                    container = request.container,
                    videoCodec = request.videoCodec,
                    audioCodec = request.audioCodec,
                    requiresMuxing = request.requiresMuxing,
                    streamUrl = request.streamUrl,
                ),
                progress = 0f,
                isProgressKnown = false,
                status = DownloadStatus.PREPARING,
                durationSeconds = request.durationSeconds,
                createdAt = now,
            ),
        )

        persistThumbnailAsync(id, request.thumbnailUrl)
        executor.execute { executeDownload(id, request, request.sourceUrl) }
    }

    /**
     * Downloads a public CDN asset with a browser referer.
     * Returns true only when the file is saved; failures must not mark the item failed
     * so the caller can fall through to yt-dlp.
     */
    private fun downloadDirectPlatformFile(
        id: String,
        request: DownloadRequest,
        directUrl: String,
        referer: String,
        cookieHeader: String?,
        userAgent: String = ANONYMOUS_BROWSER_USER_AGENT,
    ): Boolean {
        val baseName = sanitize(request.fileName?.substringBeforeLast('.') ?: "mediaflow_$id")
        val output = File(outputDirectory, "$baseName.mp4")
        val partial = File(outputDirectory, "$baseName.mp4.part")
        val outcome = runCatching {
            val connection = URL(directUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Referer", referer)
            connection.setRequestProperty("Origin", referer.trimEnd('/'))
            connection.setRequestProperty("Accept", "video/mp4,video/*;q=0.9,*/*;q=0.8")
            cookieHeader?.let { connection.setRequestProperty("Cookie", it) }
            check(connection.responseCode in 200..299) {
                "CDN respondió HTTP ${connection.responseCode}"
            }
            val total = connection.contentLengthLong
            var downloaded = 0L
            updateProgress(id, downloaded, total)
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { outputStream ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        outputStream.write(buffer, 0, read)
                        downloaded += read
                        updateProgress(id, downloaded, total)
                    }
                }
            }
            check(partial.length() > 0L) { "El CDN no entregó contenido" }
            if (output.exists()) output.delete()
            check(partial.renameTo(output)) { "No se pudo guardar el vídeo" }
            if (!completeFile(id, request, output, commitFailure = false)) {
                output.delete()
                error("El archivo del CDN no se pudo validar como vídeo")
            }
        }
        if (outcome.isFailure) {
            partial.delete()
            return false
        }
        return _items.value.firstOrNull { it.id == id }?.status == DownloadStatus.COMPLETED
    }

    private fun updateProgress(id: String, downloaded: Long, total: Long) {
        val current = _items.value.firstOrNull { it.id == id } ?: return
        val known = total > 0L
        update(current.copy(
            progress = if (known) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f,
            isProgressKnown = known,
            downloadedBytes = downloaded,
            totalBytes = total.takeIf { known },
            status = DownloadStatus.DOWNLOADING,
        ))
    }

    private fun completeFile(
        id: String,
        request: DownloadRequest,
        output: File,
        commitFailure: Boolean = true,
    ): Boolean {
        val current = _items.value.firstOrNull { it.id == id } ?: return false
        val extension = output.extension.lowercase()
        val mimeType = mimeFor(extension)
        val validation = MediaFileValidator.validate(
            output,
            request.mediaType,
            extension,
            request.durationSeconds,
            request.width,
            request.height,
            request.videoCodec,
            request.audioCodec,
        )
            .getOrElse { error ->
                if (commitFailure) updateFailed(id, error, request.mediaType)
                return false
            }

        // Attempt non-blocking metadata embedding
        runCatching {
            val space = xSpaceStore.loadAllSync()[current.sourceUrl]
                ?: xSpaceStore.loadAllSync().values.firstOrNull { it.url == current.sourceUrl || it.id in current.sourceUrl }
            val metadata = space?.let { s -> MediaMetadata.fromXSpace(s) }
                ?: MediaMetadata(
                    title = request.fileName?.substringBeforeLast('.')?.ifBlank { null } ?: current.title?.ifBlank { null },
                )
            mediaMetadataWriter.writeMetadata(output, metadata)
        }.onFailure { error ->
            android.util.Log.w("YtDlpPlatformDownloader", "No se pudieron incrustar metadatos en ${output.name}: ${error.message}")
        }

        val publishedUri = MediaStorePublisher.publishIfMissing(context, output, mimeType, output.name)
        publishedUri?.let(ownership::add)
        val thumbnailUri = harvestThumbnail(id, output, request.thumbnailUrl) ?: current.thumbnailUri
        update(current.copy(
            fileName = output.name,
            title = output.nameWithoutExtension,
            localUri = publishedUri?.toString() ?: android.net.Uri.fromFile(output).toString(),
            thumbnailUri = thumbnailUri,
            progress = 1f,
            isProgressKnown = true,
            totalBytes = output.length(),
            downloadedBytes = output.length(),
            status = DownloadStatus.COMPLETED,
            completedAt = System.currentTimeMillis(),
            selectedFormat = current.selectedFormat?.copy(
                extension = output.extension.lowercase(),
                mimeType = mimeType,
            ),
            durationSeconds = validation.durationSeconds ?: current.durationSeconds,
        ))
        persistThumbnailAsync(id, request.thumbnailUrl)
        return true
    }

    private fun executeDownload(id: String, request: DownloadRequest, sourceUrl: String) {
        val baseName = sanitize(request.fileName?.substringBeforeLast('.') ?: "mediaflow_$id")
        deleteStaleOutputs(baseName)
        val platform = PlatformUrlSupport.platformFor(sourceUrl)

        if (request.mediaType != MediaType.AUDIO &&
            tryDirectStreamUrl(id, request, sourceUrl, platform)
        ) {
            return
        }

        // Public Instagram/TikTok pages sometimes expose a CDN MP4 without
        // cookies. Try that first, but never mark the download failed if the
        // anonymous URL is missing or the CDN transfer fails — yt-dlp runs next.
        // Anonymous CDN paths are progressive MP4. Audio-only downloads go through yt-dlp.
        if (request.mediaType != MediaType.AUDIO &&
            tryAnonymousDirectDownload(id, request, sourceUrl, platform)
        ) {
            return
        }

        val referer = when (platform) {
            PlatformUrlSupport.Platform.INSTAGRAM -> "https://www.instagram.com/"
            PlatformUrlSupport.Platform.TIKTOK -> "https://www.tiktok.com/"
            else -> null
        }
        val extractionUrl = sourceUrl
        val space = if (platform == PlatformUrlSupport.Platform.X) {
            runCatching {
                val store = com.mediaflow.data.provider.x.spaces.XSpaceStore(context)
                val all = store.loadAllSync().values
                all.firstOrNull { it.url == sourceUrl || it.id in sourceUrl || sourceUrl.contains(it.id) }
            }.getOrNull()
        } else null

        val targetUrl = space?.audioStreamUrl?.takeIf { it.isNotBlank() } ?: extractionUrl

        if (request.requiresMuxing) {
            executeSeparatedTracks(id, request, extractionUrl, referer)
            return
        }

        downloadCombined(id, request, targetUrl, PlatformFormatSelector.select(request), referer)
    }

    private fun downloadCombined(
        id: String,
        request: DownloadRequest,
        targetUrl: String,
        format: String,
        referer: String?,
        allowProgressiveFallback: Boolean = true,
    ) {
        val baseName = sanitize(request.fileName?.substringBeforeLast('.') ?: "mediaflow_$id")
        deleteStaleOutputs(baseName)
        val template = File(outputDirectory, "$baseName.%(ext)s").absolutePath
        val startedAt = System.currentTimeMillis()
        val options = YtDlpRuntime.downloadOptions(
            outputDirectory = outputDirectory,
            outputTemplate = template,
            format = format,
            referer = referer,
        )
        val future = runCatching {
            executor.submit<Unit> {
                YtDlpRuntime.download(
                    context = context,
                    url = targetUrl,
                    options = options,
                    outputDirectory = outputDirectory,
                ) { progress ->
                    val current = _items.value.firstOrNull { it.id == id } ?: return@download
                    update(current.copy(
                        progress = (progress / 100f).coerceIn(0f, 1f),
                        isProgressKnown = progress >= 0f,
                        status = DownloadStatus.DOWNLOADING,
                    ))
                }
            }
        }.getOrElse { error ->
            updateFailed(id, error)
            return
        }
        sessions[id] = Session(request, future)
        executor.execute {
            runCatching { future.get() }
                .onSuccess { finish(id, request, startedAt) }
                .onFailure { error ->
                    val canFallback = allowProgressiveFallback &&
                        request.mediaType == MediaType.VIDEO &&
                        format != PROGRESSIVE_VIDEO_FALLBACK
                    if (canFallback) {
                        android.util.Log.w(
                            "YtDlpPlatformDownloader",
                            "El formato de vídeo $format falló (${error.message}). Reintentando MP4 progresivo.",
                        )
                        downloadCombined(
                            id,
                            request.copy(requiresMuxing = false, formatId = "yt-dlp"),
                            targetUrl,
                            PROGRESSIVE_VIDEO_FALLBACK,
                            referer,
                            allowProgressiveFallback = false,
                        )
                    } else {
                        updateFailed(id, error)
                    }
                }
        }
    }

    /**
     * Downloads the exact video stream and best audio stream independently,
     * then combines them with Android's MediaMuxer. This keeps yt-dlp from
     * depending on a hidden/native FFmpeg binary and makes unsupported codec
     * combinations fail before they can enter the library.
     */
    private fun executeSeparatedTracks(
        id: String,
        request: DownloadRequest,
        extractionUrl: String,
        referer: String?,
    ) {
        val future = executor.submit<Unit> {
            val workDirectory = File(outputDirectory, "tracks_$id").apply { mkdirs() }
            try {
                val videoStartedAt = System.currentTimeMillis()
                val videoTemplate = File(workDirectory, "video_%(id)s.%(ext)s").absolutePath
                val audioTemplate = File(workDirectory, "audio_%(id)s.%(ext)s").absolutePath
                YtDlpRuntime.download(
                    context = context,
                    url = extractionUrl,
                    options = YtDlpRuntime.downloadOptions(
                        outputDirectory = workDirectory,
                        outputTemplate = videoTemplate,
                        format = request.formatId ?: error("Falta el formato de vídeo"),
                        referer = referer,
                    ),
                    outputDirectory = workDirectory,
                )
                val videoFile = YtDlpRuntime.findOutputFile(workDirectory, videoStartedAt, prefix = "video_")
                check(videoFile != null) { "yt-dlp terminó sin generar la pista de vídeo." }

                updateProgress(id, 0L, -1L)
                val audioFormat = if (request.extension == "webm") {
                    "bestaudio[ext=webm]/bestaudio[acodec=opus]/bestaudio"
                } else {
                    "bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]/bestaudio[acodec^=aac]/bestaudio/ba"
                }
                val audioStartedAt = System.currentTimeMillis()
                YtDlpRuntime.download(
                    context = context,
                    url = extractionUrl,
                    options = YtDlpRuntime.downloadOptions(
                        outputDirectory = workDirectory,
                        outputTemplate = audioTemplate,
                        format = audioFormat,
                        referer = referer,
                    ),
                    outputDirectory = workDirectory,
                )
                val audioFile = YtDlpRuntime.findOutputFile(workDirectory, audioStartedAt, prefix = "audio_")
                check(audioFile != null) { "yt-dlp terminó sin generar la pista de audio." }

                ThumbnailPersister.harvestFromDirectory(context, id, workDirectory)

                val baseName = sanitize(request.fileName?.substringBeforeLast('.') ?: "mediaflow_$id")
                val output = File(outputDirectory, "$baseName.${request.extension ?: "mp4"}")
                MediaTrackMuxer.mergeMp4(videoFile, audioFile, output).getOrThrow()
                completeFile(id, request, output)
            } finally {
                workDirectory.deleteRecursively()
            }
        }
        sessions[id] = Session(request, future)
        executor.execute {
            runCatching { future.get() }
                .onFailure { error ->
                    android.util.Log.w(
                        "YtDlpPlatformDownloader",
                        "No se pudieron unir las pistas (${error.message}). Descargando MP4 progresivo.",
                    )
                    downloadCombined(
                        id,
                        request.copy(requiresMuxing = false, formatId = "yt-dlp"),
                        extractionUrl,
                        PROGRESSIVE_VIDEO_FALLBACK,
                        referer,
                        allowProgressiveFallback = false,
                    )
                }
        }
    }

    private fun deleteStaleOutputs(baseName: String) {
        val restricted = YtDlpRuntime.restrictFileName(baseName)
        outputDirectory.listFiles().orEmpty()
            .filter { file ->
                if (!file.isFile) return@filter false
                val stem = file.nameWithoutExtension
                stem == baseName || stem == restricted ||
                    file.name.startsWith("$baseName.") || file.name.startsWith("$restricted.")
            }
            .forEach { stale ->
                runCatching { stale.delete() }
            }
    }

    /** Accepts mp4/mkv/webm produced for this session; never renames webm/mkv to .mp4. */
    private fun findSessionOutput(id: String, request: DownloadRequest, startedAt: Long): File? {
        val baseName = sanitize(request.fileName?.substringBeforeLast('.') ?: "mediaflow_$id")
        val found = YtDlpRuntime.findOutputFile(
            directory = outputDirectory,
            startedAt = startedAt,
            expectedBaseName = baseName,
        )
        if (found != null && found.extension.lowercase() in SESSION_MEDIA_EXTENSIONS) return found
        val recent = outputDirectory.listFiles().orEmpty().filter { file ->
            file.isFile &&
                file.length() > 0L &&
                !file.name.endsWith(".part", ignoreCase = true) &&
                file.lastModified() >= startedAt - YtDlpRuntime.FILE_TIME_TOLERANCE_MS
        }
        val media = recent.filter { it.extension.lowercase() in SESSION_MEDIA_EXTENSIONS }
        return media.maxByOrNull { it.lastModified() }
    }

    fun contains(id: String): Boolean = sessions.containsKey(id) || _items.value.any { it.id == id }

    fun cancel(id: String) {
        sessions.remove(id)?.future?.cancel(true)
        _items.value.firstOrNull { it.id == id }?.let { item ->
            cleanupPartial(item.fileName, id, item.createdAt)
            update(item.copy(status = DownloadStatus.CANCELED))
        }
    }

    fun remove(id: String) {
        sessions.remove(id)?.future?.cancel(true)
        val updated = _items.value.filterNot { it.id == id }
        _items.value = updated
        store.save(updated)
    }

    fun retry(id: String) {
        val old = _items.value.firstOrNull { it.id == id } ?: return
        val request = sessions[id]?.request ?: DownloadRequest(
            sourceUrl = old.sourceUrl,
            mediaType = old.mediaType,
            qualityLabel = old.selectedFormat?.qualityLabel,
            fileName = old.fileName,
            mimeType = old.selectedFormat?.mimeType,
            extension = old.selectedFormat?.extension,
            formatId = old.selectedFormat?.formatId,
            durationSeconds = old.durationSeconds,
            requiresMuxing = old.selectedFormat?.requiresMuxing == true,
            width = old.selectedFormat?.width,
            height = old.selectedFormat?.height,
            fps = old.selectedFormat?.fps,
            container = old.selectedFormat?.container,
            videoCodec = old.selectedFormat?.videoCodec,
            audioCodec = old.selectedFormat?.audioCodec,
            thumbnailUrl = old.thumbnailUri?.takeIf { it.startsWith("http", ignoreCase = true) },
            streamUrl = old.selectedFormat?.streamUrl,
        )
        start(id, request)
    }

    private fun finish(id: String, request: DownloadRequest, startedAt: Long) {
        val current = _items.value.firstOrNull { it.id == id } ?: return
        val output = findSessionOutput(id, request, minOf(startedAt, current.createdAt))
        if (output == null || output.length() == 0L) {
            updateFailed(id, IllegalStateException("yt-dlp terminó sin generar un archivo"))
            return
        }
        val extension = output.extension.lowercase()
        val validation = MediaFileValidator.validate(
            output,
            request.mediaType,
            extension,
            request.durationSeconds,
            request.width,
            request.height,
            request.videoCodec,
            request.audioCodec,
        )
            .getOrElse { error ->
                updateFailed(id, error, request.mediaType)
                return
            }

        // Attempt non-blocking metadata embedding
        runCatching {
            val space = xSpaceStore.loadAllSync()[current.sourceUrl]
                ?: xSpaceStore.loadAllSync().values.firstOrNull { it.url == current.sourceUrl || it.id in current.sourceUrl }
            val metadata = space?.let { s -> MediaMetadata.fromXSpace(s) }
                ?: MediaMetadata(
                    title = request.fileName?.substringBeforeLast('.')?.ifBlank { null } ?: current.title?.ifBlank { null },
                )
            mediaMetadataWriter.writeMetadata(output, metadata)
        }.onFailure { error ->
            android.util.Log.w("YtDlpPlatformDownloader", "No se pudieron incrustar metadatos en ${output.name}: ${error.message}")
        }

        val thumbnailUri = harvestThumbnail(id, output, request.thumbnailUrl) ?: current.thumbnailUri
        update(current.copy(
            fileName = output.name,
            title = output.nameWithoutExtension,
            localUri = MediaStorePublisher.publishIfMissing(
                context,
                output,
                mimeFor(extension),
                output.name,
            )?.also(ownership::add)?.toString() ?: android.net.Uri.fromFile(output).toString(),
            thumbnailUri = thumbnailUri,
            progress = 1f,
            isProgressKnown = true,
            totalBytes = output.length(),
            downloadedBytes = output.length(),
            status = DownloadStatus.COMPLETED,
            completedAt = System.currentTimeMillis(),
            selectedFormat = current.selectedFormat?.copy(
                extension = extension,
                mimeType = mimeFor(extension),
            ),
            durationSeconds = validation.durationSeconds ?: current.durationSeconds,
        ))
        persistThumbnailAsync(id, request.thumbnailUrl)
        sessions.remove(id)
    }

    private fun updateFailed(id: String, error: Throwable, mediaType: MediaType? = null) {
        _items.value.firstOrNull { it.id == id }?.let { item ->
            cleanupPartial(item.fileName, id, item.createdAt)
            val effectiveType = mediaType
                ?: item.selectedFormat?.let { if (it.extension in listOf("mp3", "m4a", "aac", "opus")) MediaType.AUDIO else MediaType.VIDEO }
                ?: MediaType.VIDEO
            update(item.copy(status = DownloadStatus.FAILED, errorMessage = friendlyError(item.sourceUrl, error, effectiveType)))
        }
        sessions.remove(id)
    }

    /** Removes yt-dlp output candidates after cancellation or a failed run. */
    private fun cleanupPartial(fileName: String?, id: String, startedAt: Long) {
        val baseName = sanitize(fileName?.substringBeforeLast('.') ?: "mediaflow_$id")
        val restricted = YtDlpRuntime.restrictFileName(baseName)
        outputDirectory.listFiles()
            .orEmpty()
            .filter { file ->
                if (!file.isFile) return@filter false
                val matchesName = file.name.startsWith(baseName) || file.name.startsWith(restricted)
                val recent = file.lastModified() >= startedAt - YtDlpRuntime.FILE_TIME_TOLERANCE_MS
                matchesName && (file.name.endsWith(".part", ignoreCase = true) || recent)
            }
            .forEach { it.delete() }
    }

    private fun harvestThumbnail(id: String, mediaFile: File, remoteUrl: String?): String? {
        return ThumbnailPersister.harvestNearby(context, id, mediaFile)
            ?: ThumbnailPersister.existingUri(context, id)
            ?: ThumbnailPersister.persist(context, id, remoteUrl)
    }

    private fun persistThumbnailAsync(id: String, remoteUrl: String?) {
        executor.execute {
            val local = ThumbnailPersister.persist(context, id, remoteUrl) ?: return@execute
            val current = _items.value.firstOrNull { it.id == id } ?: return@execute
            if (current.thumbnailUri != local) {
                update(current.copy(thumbnailUri = local))
            }
        }
    }

    private fun update(item: DownloadItem) {
        val updated = (_items.value.filterNot { it.id == item.id } + item)
            .sortedByDescending { it.createdAt }
        _items.value = updated
        store.save(updated)
    }

    private fun friendlyError(sourceUrl: String, error: Throwable, mediaType: MediaType = MediaType.VIDEO): String {
        val text = (error.message ?: error.cause?.message).orEmpty()
        val platformType = PlatformUrlSupport.platformFor(sourceUrl)
        val platform = platformType?.label ?: "La plataforma"
        val isSpace = platformType == PlatformUrlSupport.Platform.X &&
            (sourceUrl.contains("/spaces/", true) || sourceUrl.contains("audio_space", true) || mediaType == MediaType.AUDIO)
        val mediaLabel = when {
            isSpace -> "este Space"
            mediaType == MediaType.AUDIO -> "este audio"
            else -> "este vídeo"
        }
        return when {
            platformType == PlatformUrlSupport.Platform.INSTAGRAM &&
                (text.contains("login", true) ||
                    text.contains("cookies", true) ||
                    text.contains("rate-limit", true) ||
                    text.contains("rate limit", true) ||
                    text.contains("not available", true) ||
                    text.contains("status code 403", true)) ->
                "Instagram bloqueó la extracción anónima de $mediaLabel desde Android (límite, contenido privado o sesión requerida). MediaFlow no usa cookies ni creó ningún archivo."
            text.contains("login", true) || text.contains("cookies", true) || text.contains("private", true) ->
                "$platform solicita acceso. Esta descarga requiere un contenido público o cookies de sesión."
            platformType == PlatformUrlSupport.Platform.TIKTOK &&
                (text.contains("impersonat", true) ||
                    text.contains("unexpected response", true) ||
                    text.contains("Requested format is not available", true) ||
                    text.contains("status code 0", true) ||
                    text.contains("video not available", true)) ->
                "TikTok bloqueó la extracción anónima. El vídeo puede ser privado o el CDN no entregó el archivo. MediaFlow no usa cuenta ni cookies de sesión."
            platformType == PlatformUrlSupport.Platform.INSTAGRAM &&
                (text.contains("challenge", true) ||
                    text.contains("checkpoint", true) ||
                    text.contains("not available", true) ||
                    text.contains("status code 403", true)) ->
                "Instagram no entregó $mediaLabel de forma pública y anónima. Puede ser privado, requerir sesión o estar limitado temporalmente; no se creó ningún archivo."
            else -> "$platform no pudo entregar $mediaLabel: ${text.ifBlank { "error desconocido" }}"
        }
    }

    private fun sanitize(value: String): String = YtDlpRuntime.restrictFileName(
        value.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), ""),
    )

    private fun tryDirectStreamUrl(
        id: String,
        request: DownloadRequest,
        sourceUrl: String,
        platform: PlatformUrlSupport.Platform?,
    ): Boolean {
        val streamUrl = request.streamUrl?.takeIf { it.startsWith("https://") } ?: return false
        val referer = when (platform) {
            PlatformUrlSupport.Platform.TIKTOK -> "https://www.tiktok.com/"
            PlatformUrlSupport.Platform.INSTAGRAM -> "https://www.instagram.com/"
            PlatformUrlSupport.Platform.FACEBOOK -> "https://www.facebook.com/"
            else -> null
        } ?: return false
        val cookies = if (platform == PlatformUrlSupport.Platform.TIKTOK) {
            runCatching { TikTokAnonymousResolver().sessionCookieHeader(sourceUrl) }.getOrNull()
        } else {
            null
        }
        val userAgent = if (platform == PlatformUrlSupport.Platform.TIKTOK) {
            TikTokAnonymousResolver.BROWSER_USER_AGENT
        } else {
            ANONYMOUS_BROWSER_USER_AGENT
        }
        return downloadDirectPlatformFile(
            id = id,
            request = request,
            directUrl = streamUrl,
            referer = referer,
            cookieHeader = cookies,
            userAgent = userAgent,
        )
    }

    private fun tryAnonymousDirectDownload(
        id: String,
        request: DownloadRequest,
        sourceUrl: String,
        platform: PlatformUrlSupport.Platform?,
    ): Boolean {
        val (directUrl, referer) = when (platform) {
            PlatformUrlSupport.Platform.INSTAGRAM -> {
                val asset = runCatching {
                    InstagramAnonymousResolver(context).resolve(sourceUrl).getOrNull()
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: return false
                asset to "https://www.instagram.com/"
            }
            PlatformUrlSupport.Platform.TIKTOK -> {
                val baseName = sanitize(request.fileName?.substringBeforeLast('.') ?: "mediaflow_$id")
                val output = File(outputDirectory, "$baseName.mp4")
                val saved = runCatching {
                    TikTokAnonymousResolver().downloadTo(sourceUrl, output) { downloaded, total ->
                        updateProgress(id, downloaded, total)
                    }.getOrThrow()
                }.onFailure { error ->
                    android.util.Log.w(
                        "YtDlpPlatformDownloader",
                        "TikTok anónimo no entregó el MP4: ${error.message ?: error.toString()}",
                        error,
                    )
                }.getOrNull() ?: return false
                if (!completeFile(id, request, saved, commitFailure = false)) {
                    saved.delete()
                    return false
                }
                return _items.value.firstOrNull { it.id == id }?.status == DownloadStatus.COMPLETED
            }
            else -> return false
        }
        return downloadDirectPlatformFile(
            id = id,
            request = request,
            directUrl = directUrl,
            referer = referer,
            cookieHeader = null,
        )
    }

    private fun mimeFor(extension: String): String = when (extension) {
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "aac" -> "audio/aac"
        "opus", "ogg" -> "audio/ogg"
        else -> "video/mp4"
    }

    private data class Session(val request: DownloadRequest, val future: Future<*>)

    private companion object {
        val SESSION_MEDIA_EXTENSIONS = setOf(
            "mp4", "m4v", "m4a", "webm", "mkv", "mov", "mp3", "aac", "opus", "ogg", "wav",
        )
        const val ANONYMOUS_BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.139 Safari/537.36"
        const val PROGRESSIVE_VIDEO_FALLBACK = "b[ext=mp4]/bv*[ext=mp4]+ba[ext=m4a]/b"
    }
}
