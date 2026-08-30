package com.mediaflow.data.resolver

import android.content.Context
import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.provider.x.XContentDetector
import com.mediaflow.data.provider.x.XUrlParser
import com.mediaflow.data.provider.x.spaces.XSpaceMetadataResolver
import com.mediaflow.data.provider.x.spaces.XSpaceStore
import com.mediaflow.data.repository.XSpaceRepositoryImpl
import com.mediaflow.data.ytdlp.YtDlpRuntime
import com.mediaflow.data.resolver.tiktok.TikTokExtractPipeline
import com.mediaflow.data.resolver.tiktok.TikTokResolveException
import com.mediaflow.data.resolver.tiktok.TikTokResolveStage
import com.mediaflow.domain.repository.PlaylistEntry
import com.mediaflow.domain.repository.SourceInfo
import com.mediaflow.domain.repository.SourceResolver
import com.mediaflow.domain.repository.XSpaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Truthful source analyser backed by yt-dlp's JSON extractor enriched with
 * specialized providers such as [XSpaceMetadataResolver].
 */
class YtDlpSourceResolver(
    context: Context,
    private val spaceResolver: XSpaceMetadataResolver = XSpaceMetadataResolver(),
    private val spaceRepository: XSpaceRepository = XSpaceRepositoryImpl(context),
) : SourceResolver {
    private val appContext = context.applicationContext
    private val directResolver = DirectUrlSourceResolver()
    private val spaceStore = XSpaceStore(appContext)
    private val analysisDirectory = File(appContext.filesDir, "yt_dlp_analysis").apply { mkdirs() }

    override suspend fun analyze(sourceUrl: String): SourceInfo = withContext(Dispatchers.IO) {
        val trimmed = sourceUrl.trim()
        val direct = directResolver.analyze(trimmed)
        if (direct.availableFormats.singleOrNull()?.formatId == "direct") return@withContext direct

        if (!PlatformUrlSupport.isSupported(trimmed)) {
            return@withContext SourceInfo(
                sourceUrl = trimmed,
                errorMessage = "Enlace HTTPS no compatible o no público.",
            )
        }

        // Fast-path for X URLs: check if it's an X Space via specialized resolver
        if (XUrlParser.isXUrl(trimmed)) {
            // A transient X/guest-token failure must not discard a Space that
            // was already resolved and persisted. Reuse only an exact URL or
            // matching status/Space id; never fall back to an unrelated item.
            val space = runCatching { spaceResolver.resolveFromUrl(trimmed) }.getOrNull()
                ?: findCachedSpace(trimmed)
            if (space != null) {
                spaceRepository.saveSpace(space)
                val formats = spaceResolver.createMediaFormats(space)
                val statusMessage = when (space.state) {
                    XSpaceState.UPCOMING -> "Este Space todavía no ha comenzado (Programado)."
                    XSpaceState.ENDED, XSpaceState.TIMED_OUT -> if (!space.recordingAvailable && formats.isEmpty()) {
                        "La grabación de este Space no está disponible o fue desactivada."
                    } else null
                    XSpaceState.LIVE -> if (space.audioStreamUrl == null) {
                        "El stream en vivo no está disponible en este momento."
                    } else null
                    else -> null
                }
                return@withContext SourceInfo(
                    sourceUrl = trimmed,
                    title = space.title,
                    thumbnailUrl = space.host.avatarUrl,
                    durationSeconds = com.mediaflow.data.provider.x.spaces.SpaceAvailabilityResolver.displayDurationSeconds(space),
                    availableFormats = formats,
                    spaceMetadata = space,
                    errorMessage = statusMessage,
                )
            }
        }

        val extractionUrl = if (PlatformUrlSupport.platformFor(trimmed) == PlatformUrlSupport.Platform.TIKTOK) {
            val resolved = runCatching { TikTokExtractPipeline.resolveCanonical(trimmed) }
                .getOrElse { error ->
                    return@withContext SourceInfo(
                        sourceUrl = trimmed,
                        errorMessage = friendlyAnalysisError(trimmed, error),
                    )
                }
            resolved.canonicalUrl
        } else {
            PlatformUrlSupport.canonicalExtractionUrl(trimmed)
        }
        runCatching {
            try {
                val json = YtDlpRuntime.extractJson(
                    appContext,
                    extractionUrl,
                    analysisDirectory,
                    allowPlaylist = PlatformUrlSupport.isYoutubePlaylist(trimmed),
                )
                check(json.isNotBlank() && json != "None") {
                    "El extractor no pudo analizar la fuente."
                }
                parseForTest(trimmed, json)
            } finally {
                analysisDirectory.listFiles().orEmpty().forEach { it.delete() }
            }
        }.getOrElse { error ->
            analyzeAnonymousFallback(extractionUrl)?.let { return@withContext it }
            if (PlatformUrlSupport.platformFor(trimmed) == PlatformUrlSupport.Platform.TIKTOK) {
                return@withContext SourceInfo(
                    sourceUrl = trimmed,
                    errorMessage = friendlyAnalysisError(
                        trimmed,
                        TikTokResolveException(
                            TikTokResolveStage.EXTRACTOR_FAILED,
                            "El extractor no pudo leer $extractionUrl.",
                            error,
                        ),
                    ),
                )
            }

            // Fallback for X Space if yt-dlp fails due to replay disabled or missing audio stream
            if (XUrlParser.isXUrl(trimmed)) {
                val space = runCatching { spaceResolver.resolveFromUrl(trimmed) }.getOrNull()
                if (space != null) {
                    spaceRepository.saveSpace(space)
                    val formats = spaceResolver.createMediaFormats(space)
                    val statusMessage = when (space.state) {
                        XSpaceState.UPCOMING -> "Este Space todavía no ha comenzado (Programado)."
                        XSpaceState.ENDED, XSpaceState.TIMED_OUT -> if (!space.recordingAvailable && formats.isEmpty()) {
                            "La grabación de este Space no está disponible o fue desactivada."
                        } else null
                        XSpaceState.LIVE -> if (space.audioStreamUrl == null) {
                            "El stream en vivo no está disponible en este momento."
                        } else null
                        else -> null
                    }
                    return@withContext SourceInfo(
                        sourceUrl = trimmed,
                        title = space.title,
                        thumbnailUrl = space.host.avatarUrl,
                        durationSeconds = space.durationSeconds.takeIf { it > 0 },
                        availableFormats = formats,
                        spaceMetadata = space,
                        errorMessage = statusMessage,
                    )
                }
            }

            SourceInfo(
                sourceUrl = trimmed,
                errorMessage = friendlyAnalysisError(trimmed, error),
            )
        }
    }

    private fun analyzeAnonymousFallback(sourceUrl: String): SourceInfo? {
        val platform = PlatformUrlSupport.platformFor(sourceUrl) ?: return null
        return when (platform) {
            PlatformUrlSupport.Platform.TIKTOK -> {
                val video = runCatching { TikTokAnonymousResolver().resolve(sourceUrl).getOrThrow() }.getOrNull()
                    ?: return null
                anonymousVideoInfo(
                    sourceUrl,
                    video.url,
                    title = video.title?.takeIf { it.isNotBlank() } ?: "Vídeo de TikTok",
                    thumbnailUrl = video.thumbnailUrl,
                )
            }
            PlatformUrlSupport.Platform.INSTAGRAM -> {
                val cdn = runCatching {
                    InstagramAnonymousResolver(appContext).resolve(sourceUrl).getOrNull()
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
                anonymousVideoInfo(sourceUrl, cdn, title = "Vídeo de Instagram")
            }
            else -> null
        }
    }

    private fun anonymousVideoInfo(
        sourceUrl: String,
        cdnUrl: String,
        title: String,
        thumbnailUrl: String? = null,
    ): SourceInfo = SourceInfo(
        sourceUrl = sourceUrl,
        title = title,
        thumbnailUrl = thumbnailUrl,
        availableFormats = listOf(
            MediaFormat(
                formatId = "anonymous",
                extension = "mp4",
                mimeType = "video/mp4",
                mediaType = MediaType.VIDEO,
                qualityLabel = "Automática",
                isProgressive = true,
                requiresMuxing = false,
                streamUrl = cdnUrl.takeIf { it.startsWith("https://") },
            ),
        ),
    )

    private fun findCachedSpace(url: String): XSpace? {
        val statusId = XUrlParser.extractStatusId(url)
        val directId = XUrlParser.extractDirectSpaceId(url)
        return runCatching {
            spaceStore.loadAllSync().values.firstOrNull { space ->
                space.url == url ||
                    (statusId != null && XUrlParser.extractStatusId(space.url) == statusId) ||
                    (directId != null && space.id == directId)
            }
        }.getOrNull()
    }

    internal fun friendlyAnalysisError(sourceUrl: String, error: Throwable): String {
        val text = (error.message ?: error.cause?.message).orEmpty()
        val platform = PlatformUrlSupport.platformFor(sourceUrl)?.label ?: "La plataforma"
        val resolveStage = (error as? TikTokResolveException)?.stage
            ?: (error.cause as? TikTokResolveException)?.stage
        if (resolveStage != null) {
            return when (resolveStage) {
                TikTokResolveStage.URL_RESOLUTION_FAILED ->
                    "No se pudo resolver el enlace de TikTok."
                TikTokResolveStage.REDIRECT_FAILED ->
                    "TikTok no completó la redirección del enlace corto."
                TikTokResolveStage.VIDEO_ID_NOT_FOUND ->
                    "TikTok no devolvió un /video/{id} canónico."
                TikTokResolveStage.TIKTOK_BLOCKED ->
                    "TikTok bloqueó el análisis (HTTP 403/429). MediaFlow no usa cookies de sesión."
                TikTokResolveStage.EXTRACTOR_FAILED ->
                    "El extractor no pudo leer la URL canónica de TikTok."
                TikTokResolveStage.MEDIA_URL_FAILED ->
                    "TikTok no publicó una URL de vídeo descargable."
                TikTokResolveStage.DOWNLOAD_FAILED ->
                    "La descarga de TikTok falló después de resolver la URL canónica."
            }
        }
        return when {
            text.contains("Twitter Space ended and replay is disabled", ignoreCase = true) ->
                "La grabación de este X Space no está disponible o fue desactivada por el autor."
            text.contains("Twitter Space not found", ignoreCase = true) ->
                "El X Space no existe o fue eliminado."
            text.contains("Unexpected response from webpage", ignoreCase = true) ||
                text.contains("please report this issue on", ignoreCase = true) ||
                (platform == "TikTok" && text.contains("[TikTok]", ignoreCase = true)) ->
                "TikTok bloqueó el análisis automático. Si el vídeo es público, pulsa Descargar: se intentará por la página anónima, sin cookies."
            text.contains("Read-only file system", ignoreCase = true) ||
                text.contains("Errno 30", ignoreCase = true) ->
                "$platform no se pudo analizar: yt-dlp intentó escribir fuera del almacenamiento de la app. Vuelve a intentarlo."
            text.contains("DECRYPTION_FAILED_OR_BAD_RECORD_MAC", ignoreCase = true) ||
                text.contains("SSL", ignoreCase = true) ->
                "$platform no respondió de forma estable por HTTPS. Comprueba la conexión y vuelve a intentarlo; no se creó ningún archivo."
            text.contains("page needs to be reloaded", ignoreCase = true) ||
                text.contains("Requested format is not available", ignoreCase = true) ->
                "$platform no entregó formatos descargables. Vuelve a intentarlo; MediaFlow no usa cookies de sesión."
            text.contains("private", ignoreCase = true) ||
                text.contains("login", ignoreCase = true) ||
                text.contains("sign in", ignoreCase = true) ||
                text.contains("cookies", ignoreCase = true) ->
                "$platform requiere acceso o el contenido no es público. MediaFlow no usa cookies de sesión."
            else -> text.ifBlank { "No se pudo analizar la fuente." }
                .lineSequence()
                .firstOrNull()
                ?.take(180)
                ?: "No se pudo analizar la fuente."
        }
    }

    internal suspend fun parseForTest(sourceUrl: String, output: String): SourceInfo {
        val root = JSONObject(output.trim())
        val title = root.optString("title").takeIf { it.isNotBlank() }
        val thumbnail = root.optString("thumbnail").takeIf { it.isNotBlank() }
        val duration = root.optDouble("duration", Double.NaN)
            .takeIf { !it.isNaN() && it >= 0 }
            ?.toLong()

        val formats = com.mediaflow.data.download.extractors.YtDlpFormatParser.parseRoot(output)

        // Check if content is an X Space
        val isSpace = XContentDetector.detectFromYtDlpJson(root) == com.mediaflow.core.model.XContentType.SPACE ||
            XUrlParser.extractDirectSpaceId(sourceUrl) != null

        val spaceMetadata: XSpace? = if (isSpace) {
            val spaceId = XUrlParser.extractDirectSpaceId(sourceUrl)
                ?: root.optString("id").takeIf { it.isNotBlank() }
                ?: root.optString("display_id")
            val resolved = spaceResolver.resolve(spaceId, sourceUrl, root)
            spaceRepository.saveSpace(resolved)
            resolved
        } else null

        val playlistEntries = parsePlaylistEntries(root)
        val effectiveFormats = when {
            spaceMetadata != null && formats.isEmpty() -> spaceResolver.createMediaFormats(spaceMetadata)
            playlistEntries.isNotEmpty() && formats.isEmpty() -> playlistFormats()
            else -> formats
        }

        check(effectiveFormats.isNotEmpty() || spaceMetadata != null || playlistEntries.isNotEmpty()) {
            "La fuente no devolvió formatos descargables."
        }

        return SourceInfo(
            sourceUrl = sourceUrl,
            title = spaceMetadata?.title ?: title,
            thumbnailUrl = spaceMetadata?.host?.avatarUrl ?: thumbnail ?: playlistEntries.firstOrNull()?.thumbnailUrl,
            durationSeconds = spaceMetadata?.let {
                com.mediaflow.data.provider.x.spaces.SpaceAvailabilityResolver.displayDurationSeconds(it)
            } ?: duration,
            availableFormats = effectiveFormats.sortedWith(
                compareByDescending<MediaFormat> { it.height ?: 0 }
                    .thenByDescending { it.fps ?: 0.0 }
                    .thenByDescending { it.bitrate ?: 0L },
            ),
            spaceMetadata = spaceMetadata,
            playlistEntries = playlistEntries,
        )
    }

    private fun parsePlaylistEntries(root: JSONObject): List<PlaylistEntry> {
        val type = root.optString("_type")
        val array = root.optJSONArray("entries") ?: return emptyList()
        if (type.isNotBlank() && type != "playlist" && array.length() == 0) return emptyList()
        val entries = buildList {
            for (index in 0 until minOf(array.length(), YtDlpRuntime.MAX_PLAYLIST_ITEMS)) {
                val json = array.optJSONObject(index) ?: continue
                val url = playlistEntryUrl(json) ?: continue
                val duration = json.optDouble("duration", Double.NaN)
                    .takeIf { !it.isNaN() && it >= 0 }
                    ?.toLong()
                add(
                    PlaylistEntry(
                        sourceUrl = url,
                        title = json.optString("title").takeIf { it.isNotBlank() },
                        thumbnailUrl = playlistThumbnail(json),
                        durationSeconds = duration,
                    ),
                )
            }
        }
        return entries.distinctBy { it.sourceUrl }
    }

    private fun playlistEntryUrl(json: JSONObject): String? {
        val direct = listOf("webpage_url", "original_url", "url")
            .map { json.optString(it) }
            .firstOrNull { it.startsWith("https://", ignoreCase = true) }
        if (direct != null) return direct
        val id = json.optString("id").takeIf { it.isNotBlank() && !it.contains(' ') } ?: return null
        return "https://www.youtube.com/watch?v=$id"
    }

    private fun playlistThumbnail(json: JSONObject): String? {
        json.optString("thumbnail").takeIf { it.startsWith("https://") }?.let { return it }
        val thumbs = json.optJSONArray("thumbnails") ?: return null
        for (index in thumbs.length() - 1 downTo 0) {
            val url = thumbs.optJSONObject(index)?.optString("url").orEmpty()
            if (url.startsWith("https://")) return url
        }
        return null
    }

    private fun playlistFormats(): List<MediaFormat> = listOf(
        MediaFormat(
            formatId = "yt-dlp",
            extension = "mp4",
            mimeType = "video/mp4",
            mediaType = MediaType.VIDEO,
            qualityLabel = "Automática",
            isProgressive = true,
            requiresMuxing = false,
        ),
        MediaFormat(
            formatId = "bestaudio",
            extension = "m4a",
            mimeType = "audio/mp4",
            mediaType = MediaType.AUDIO,
            qualityLabel = "Audio",
            isProgressive = true,
            requiresMuxing = false,
        ),
    )

    private fun toMediaFormat(json: JSONObject, duration: Long?): MediaFormat? {
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
            height != null || width != null || extension.lowercase() in listOf("mp4", "m4v", "webm", "mkv", "mov", "flv", "3gp") -> MediaType.VIDEO
            else -> MediaType.VIDEO
        }

        val progressive = (videoCodec != null && audioCodec != null) ||
            (videoCodec == null && audioCodec == null && mediaType == MediaType.VIDEO)
        val size = firstPositive(json, "filesize", "filesize_approx")
        val bitrate = firstPositive(json, "tbr", "vbr", "abr")
        val fps = json.optDouble("fps", Double.NaN).takeIf { !it.isNaN() && it > 0 }
        return MediaFormat(
            formatId = formatId,
            extension = extension,
            mimeType = mimeFor(extension, mediaType),
            mediaType = mediaType,
            qualityLabel = qualityLabel(height, json.optString("format_note")),
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

    private fun qualityLabel(height: Int?, note: String): String? = when {
        height != null -> "${height}p"
        note.isNotBlank() -> note
        else -> null
    }

    private fun mimeFor(extension: String?, mediaType: MediaType): String? = when (extension?.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "wav" -> "audio/wav"
        "ogg", "opus" -> "audio/ogg"
        else -> if (mediaType == MediaType.VIDEO) "video/*" else "audio/*"
    }
}
