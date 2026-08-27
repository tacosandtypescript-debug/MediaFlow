package com.mediaflow.data.download

import android.content.Context
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.DownloadStatus
import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import com.mediaflow.domain.repository.DownloadRequest
import com.mediaflow.data.resolver.PlatformUrlSupport
import com.mediaflow.data.resolver.InstagramAnonymousResolver
import com.mediaflow.data.media.MediaStorePublisher
import com.mediaflow.data.media.MediaFileValidator
import com.mediaflow.data.media.MediaFlowLibraryStore
import com.mediaflow.data.media.MediaTrackMuxer
import dev.ffmpegkit_maintained.ytdlp.YtDlp
import dev.ffmpegkit_maintained.ytdlp.YtDlpException
import dev.ffmpegkit_maintained.ytdlp.YtDlpRequest
import dev.ffmpegkit_maintained.ytdlp.YtDlpResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Future

/** Runs yt-dlp for supported platform pages and exposes truthful progress. */
class YtDlpPlatformDownloader(private val context: Context) {
    private val outputDirectory = File(context.filesDir, "downloads").apply { mkdirs() }
    private val store = PlatformDownloadStore(context)
    private val ownership = MediaFlowLibraryStore(context)
    private val sessions = LinkedHashMap<String, Session>()
    private val _items = MutableStateFlow(store.load())
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    init {
        runCatching { YtDlp.init(context) }
    }

    fun start(id: String, request: DownloadRequest) {
        val now = System.currentTimeMillis()
        update(
            DownloadItem(
                id = id,
                sourceUrl = request.sourceUrl,
                title = request.fileName,
                fileName = request.fileName,
                mediaType = request.mediaType,
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
                ),
                progress = 0f,
                isProgressKnown = false,
                status = DownloadStatus.PREPARING,
                durationSeconds = request.durationSeconds,
                createdAt = now,
            ),
        )

        executeDownload(id, request, request.sourceUrl)
    }

    /** TikTok CDN URLs require the browser referer and must not be re-extracted by yt-dlp. */
    private fun downloadDirectPlatformFile(
        id: String,
        request: DownloadRequest,
        directUrl: String,
        referer: String,
        cookieHeader: String?,
    ) {
        val baseName = sanitize(request.fileName?.substringBeforeLast('.') ?: "mediaflow_$id")
        val output = File(outputDirectory, "$baseName.mp4")
        val partial = File(outputDirectory, "$baseName.mp4.part")
        runCatching {
            val connection = URL(directUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", PLATFORM_USER_AGENT)
            connection.setRequestProperty("Referer", referer)
            connection.setRequestProperty("Accept", "video/mp4,video/*;q=0.9,*/*;q=0.8")
            cookieHeader?.let { connection.setRequestProperty("Cookie", it) }
            check(connection.responseCode in 200..299) {
                "TikTok CDN respondió HTTP ${connection.responseCode}"
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
            check(partial.length() > 0L) { "TikTok no entregó contenido" }
            if (output.exists()) output.delete()
            check(partial.renameTo(output)) { "No se pudo guardar el vídeo de TikTok" }
            completeFile(id, request, output)
        }.onFailure { error ->
            partial.delete()
            updateFailed(id, error)
        }
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

    private fun completeFile(id: String, request: DownloadRequest, output: File) {
        val current = _items.value.firstOrNull { it.id == id } ?: return
        val mimeType = current.selectedFormat?.mimeType ?: "video/mp4"
        val validation = MediaFileValidator.validate(
            output,
            request.mediaType,
            request.extension,
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
        val publishedUri = MediaStorePublisher.publishIfMissing(context, output, mimeType, output.name)
        publishedUri?.let(ownership::add)
        update(current.copy(
            fileName = output.name,
            title = output.nameWithoutExtension,
            localUri = publishedUri?.toString() ?: android.net.Uri.fromFile(output).toString(),
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
    }

    private fun executeDownload(id: String, request: DownloadRequest, sourceUrl: String) {
        val baseName = sanitize(request.fileName?.substringBeforeLast('.') ?: "mediaflow_$id")
        val template = File(outputDirectory, "$baseName.%(ext)s").absolutePath
        val platform = PlatformUrlSupport.platformFor(sourceUrl)

        // Instagram sometimes exposes a public CDN asset to a normal web view
        // even when its yt-dlp extractor is rate-limited on Android. This
        // fallback never imports browser/account cookies and only proceeds
        // when the page exposes a public MP4 URL immediately.
        if (platform == PlatformUrlSupport.Platform.INSTAGRAM) {
            val anonymousAsset = runCatching {
                InstagramAnonymousResolver(context).resolve(instagramEmbedUrl(sourceUrl)).getOrNull()
            }.getOrNull()
            if (!anonymousAsset.isNullOrBlank()) {
                downloadDirectPlatformFile(
                    id = id,
                    request = request,
                    directUrl = anonymousAsset,
                    referer = "https://www.instagram.com/",
                    cookieHeader = null,
                )
                return
            }
        }

        val referer = when (platform) {
            PlatformUrlSupport.Platform.INSTAGRAM -> "https://www.instagram.com/"
            PlatformUrlSupport.Platform.TIKTOK -> "https://www.tiktok.com/"
            else -> null
        }
        val extractionUrl = if (platform == PlatformUrlSupport.Platform.INSTAGRAM) {
            instagramEmbedUrl(sourceUrl)
        } else {
            sourceUrl
        }
        val space = if (platform == PlatformUrlSupport.Platform.X) {
            runCatching {
                val store = com.mediaflow.data.provider.x.spaces.XSpaceStore(context)
                val all = store.loadAllSync().values
                all.firstOrNull { it.url == sourceUrl || it.id in sourceUrl || sourceUrl.contains(it.id) }
            }.getOrNull()
        } else null

        val targetUrl = space?.audioStreamUrl?.takeIf { it.isNotBlank() } ?: extractionUrl
        val ytdlpRequest = YtDlpRequest(targetUrl)
            .setOutputTemplate(template)
            // Do not read browser/config cookies: downloads are intentionally anonymous.
            .addOption("--ignore-config")
            .addOption("--no-cookies")
            .addOption("--no-cache-dir")
            .addOption("--no-playlist")
            .addOption("--no-part")
            .addOption("--retries", "3")
            .addOption("--hls-prefer-native")
            .addOption("--no-check-formats")
            .addOption("--downloader", "m3u8:native")
            .addOption("--user-agent", PLATFORM_USER_AGENT)
            .addOption("-f", PlatformFormatSelector.select(request))

        if (request.requiresMuxing) {
            // Never silently fall back to another quality. A separated format
            // is accepted only if yt-dlp can actually merge its exact video
            // stream with audio into the requested container.
            ytdlpRequest.addOption("--merge-output-format", request.extension ?: "mp4")
        }

        referer?.let { ytdlpRequest.addOption("--referer", it) }

        if (request.requiresMuxing) {
            executeSeparatedTracks(id, request, extractionUrl, referer)
            return
        }

        val future = runCatching {
            YtDlp.executeAsync(ytdlpRequest) { progress, _, _ ->
                val current = _items.value.firstOrNull { it.id == id } ?: return@executeAsync
                update(current.copy(
                    progress = (progress / 100f).coerceIn(0f, 1f),
                    isProgressKnown = progress >= 0f,
                    status = DownloadStatus.DOWNLOADING,
                ))
            }
        }.getOrElse { error ->
            updateFailed(id, error)
            return
        }
        sessions[id] = Session(request, future)
        Thread {
            runCatching { future.get() }
                .onSuccess { response -> finish(id, request, response) }
                .onFailure { error -> updateFailed(id, error) }
        }.start()
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
        Thread {
            val workDirectory = File(outputDirectory, "tracks_$id").apply { mkdirs() }
            runCatching {
                val videoTemplate = File(workDirectory, "video_%(id)s.%(ext)s").absolutePath
                val audioTemplate = File(workDirectory, "audio_%(id)s.%(ext)s").absolutePath
                val videoResponse = YtDlp.execute(
                    separatedRequest(extractionUrl, videoTemplate, request.formatId ?: error("Falta el formato de vídeo"), referer),
                    null,
                )
                check(videoResponse.isSuccess()) {
                    videoResponse.errorOutput?.ifBlank { null } ?: "yt-dlp no pudo descargar la pista de vídeo."
                }
                val videoFile = findTrackFile(workDirectory, "video_")
                check(videoFile != null) { "yt-dlp terminó sin generar la pista de vídeo." }

                updateProgress(id, 0L, -1L)
                val audioResponse = YtDlp.execute(
                    separatedRequest(extractionUrl, audioTemplate, "bestaudio", referer),
                    null,
                )
                check(audioResponse.isSuccess()) {
                    audioResponse.errorOutput?.ifBlank { null } ?: "yt-dlp no pudo descargar la pista de audio."
                }
                val audioFile = findTrackFile(workDirectory, "audio_")
                check(audioFile != null) { "yt-dlp terminó sin generar la pista de audio." }

                val baseName = sanitize(request.fileName?.substringBeforeLast('.') ?: "mediaflow_$id")
                val output = File(outputDirectory, "$baseName.${request.extension ?: "mp4"}")
                MediaTrackMuxer.mergeMp4(videoFile, audioFile, output).getOrThrow()
                completeFile(id, request, output)
            }.onFailure { error ->
                updateFailed(id, error)
            }.also {
                workDirectory.deleteRecursively()
            }
        }.start()
    }

    private fun separatedRequest(
        sourceUrl: String,
        template: String,
        format: String,
        referer: String?,
    ): YtDlpRequest {
        val request = YtDlpRequest(sourceUrl)
            .setOutputTemplate(template)
            .addOption("--ignore-config")
            .addOption("--no-cookies")
            .addOption("--no-cache-dir")
            .addOption("--no-playlist")
            .addOption("--no-part")
            .addOption("--retries", "3")
            .addOption("--user-agent", PLATFORM_USER_AGENT)
            .addOption("-f", format)
        referer?.let { request.addOption("--referer", it) }
        return request
    }

    private fun findTrackFile(directory: File, prefix: String): File? = directory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.name.startsWith(prefix) && !it.name.endsWith(".part", true) }
        .maxByOrNull { it.lastModified() }

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
        )
        start(id, request)
    }

    private fun finish(id: String, request: DownloadRequest, response: YtDlpResponse) {
        if (!response.isSuccess()) {
            updateFailed(id, YtDlpException(response.errorOutput ?: "yt-dlp no pudo descargar el vídeo"))
            return
        }
        val current = _items.value.firstOrNull { it.id == id } ?: return
        val output = outputDirectory.listFiles()
            ?.filter { it.isFile && it.nameWithoutExtension == sanitize(request.fileName?.substringBeforeLast('.') ?: "mediaflow_$id") }
            ?.filter { it.lastModified() >= current.createdAt - FILE_TIME_TOLERANCE_MS }
            ?.maxByOrNull { it.lastModified() }
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
        update(current.copy(
            fileName = output.name,
            title = output.nameWithoutExtension,
            localUri = MediaStorePublisher.publishIfMissing(
                context,
                output,
                mimeFor(extension),
                output.name,
            )?.also(ownership::add)?.toString() ?: android.net.Uri.fromFile(output).toString(),
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
        outputDirectory.listFiles()
            .orEmpty()
            .filter {
                it.isFile &&
                    it.name.startsWith(baseName) &&
                    (it.name.endsWith(".part", ignoreCase = true) ||
                        it.lastModified() >= startedAt - FILE_TIME_TOLERANCE_MS)
            }
            .forEach { it.delete() }
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
                    text.contains("status code 0", true) ||
                    text.contains("video not available", true)) ->
                "TikTok bloqueó la extracción anónima. yt-dlp siguió el enlace público, pero TikTok no entregó una respuesta de vídeo válida (status code 0). Esta edición no usa cookies ni tiene impersonación TLS; no se creó ningún archivo."
            platformType == PlatformUrlSupport.Platform.INSTAGRAM &&
                (text.contains("challenge", true) ||
                    text.contains("checkpoint", true) ||
                    text.contains("not available", true) ||
                    text.contains("status code 403", true)) ->
                "Instagram no entregó $mediaLabel de forma pública y anónima. Puede ser privado, requerir sesión o estar limitado temporalmente; no se creó ningún archivo."
            else -> "$platform no pudo entregar $mediaLabel: ${text.ifBlank { "error desconocido" }}"
        }
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "")
        .ifBlank { "mediaflow_download" }

    private fun instagramEmbedUrl(sourceUrl: String): String {
        val trimmed = sourceUrl.trim()
        if (trimmed.contains("/embed", ignoreCase = true)) return trimmed
        val queryIndex = trimmed.indexOf('?')
        val path = if (queryIndex >= 0) trimmed.substring(0, queryIndex) else trimmed
        val query = if (queryIndex >= 0) trimmed.substring(queryIndex) else ""
        return "${path.trimEnd('/')}/embed/$query"
    }

    private fun mimeFor(extension: String): String = when (extension) {
        "webm" -> "video/webm"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        else -> "video/mp4"
    }

    private data class Session(val request: DownloadRequest, val future: Future<YtDlpResponse>)

    private companion object {
        const val FILE_TIME_TOLERANCE_MS = 1_000L
        const val PLATFORM_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"
    }
}
