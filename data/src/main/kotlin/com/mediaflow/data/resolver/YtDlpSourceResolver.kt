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
import com.mediaflow.domain.repository.SourceInfo
import com.mediaflow.domain.repository.SourceResolver
import com.mediaflow.domain.repository.XSpaceRepository
import dev.ffmpegkit_maintained.ytdlp.YtDlp
import dev.ffmpegkit_maintained.ytdlp.YtDlpRequest
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
                    durationSeconds = space.durationSeconds.takeIf { it > 0 },
                    availableFormats = formats,
                    spaceMetadata = space,
                    errorMessage = statusMessage,
                )
            }
        }

        runCatching {
            YtDlp.init(appContext)
            val outputTemplate = File(analysisDirectory, "analysis_%(id)s.%(ext)s").absolutePath
            try {
                val response = YtDlp.execute(
                    YtDlpRequest(trimmed)
                        .setOutputTemplate(outputTemplate)
                        .addOption("--ignore-config")
                        .addOption("--no-cookies")
                        .addOption("--no-cache-dir")
                        .addOption("--no-playlist")
                        .addOption("--dump-single-json")
                        .addOption("--skip-download")
                        .addOption("--no-write-thumbnail")
                        .addOption("--no-write-info-json")
                        .addOption("--no-write-playlist-metafiles")
                        .addOption("--retries", "3")
                        .addOption("--fragment-retries", "3")
                        .addOption("--socket-timeout", "30")
                        .addOption("--force-ipv4")
                        .addOption("--no-warnings")
                        .addOption("--hls-prefer-native")
                        .addOption("--extractor-args", "youtube:player_client=android,web")
                        .addOption("--user-agent", USER_AGENT),
                    null,
                )
                check(response.isSuccess) {
                    response.errorOutput?.ifBlank { null }
                        ?: "El extractor no pudo analizar la fuente."
                }
                parseForTest(trimmed, response.output)
            } finally {
                analysisDirectory.listFiles().orEmpty().forEach { it.delete() }
            }
        }.getOrElse { error ->
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

    private fun friendlyAnalysisError(sourceUrl: String, error: Throwable): String {
        val text = (error.message ?: error.cause?.message).orEmpty()
        val platform = PlatformUrlSupport.platformFor(sourceUrl)?.label ?: "La plataforma"
        return when {
            text.contains("Twitter Space ended and replay is disabled", ignoreCase = true) ->
                "La grabación de este X Space no está disponible o fue desactivada por el autor."
            text.contains("Twitter Space not found", ignoreCase = true) ->
                "El X Space no existe o fue eliminado."
            text.contains("DECRYPTION_FAILED_OR_BAD_RECORD_MAC", ignoreCase = true) ||
                text.contains("SSL", ignoreCase = true) ->
                "$platform no respondió de forma estable por HTTPS. Comprueba la conexión y vuelve a intentarlo; no se creó ningún archivo."
            text.contains("private", ignoreCase = true) ||
                text.contains("login", ignoreCase = true) ||
                text.contains("cookies", ignoreCase = true) ->
                "$platform requiere acceso o el contenido no es público. MediaFlow no usa cookies de sesión."
            else -> text.ifBlank { "No se pudo analizar la fuente." }
        }
    }

    internal suspend fun parseForTest(sourceUrl: String, output: String): SourceInfo {
        val root = JSONObject(output.trim())
        val title = root.optString("title").takeIf { it.isNotBlank() }
        val thumbnail = root.optString("thumbnail").takeIf { it.isNotBlank() }
        val duration = root.optDouble("duration", Double.NaN)
            .takeIf { !it.isNaN() && it >= 0 }
            ?.toLong()

        val parsedFormats = root.optJSONArray("formats")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val format = array.optJSONObject(index) ?: continue
                    toMediaFormat(format, duration)?.let(::add)
                }
            }
        }.orEmpty().distinctBy { it.formatId }

        val formats = if (parsedFormats.isNotEmpty()) {
            parsedFormats
        } else {
            listOfNotNull(toMediaFormat(root, duration))
        }

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

        val effectiveFormats = if (spaceMetadata != null && formats.isEmpty()) {
            spaceResolver.createMediaFormats(spaceMetadata)
        } else {
            formats
        }

        check(effectiveFormats.isNotEmpty() || spaceMetadata != null) { "La fuente no devolvió formatos descargables." }

        return SourceInfo(
            sourceUrl = sourceUrl,
            title = spaceMetadata?.title ?: title,
            thumbnailUrl = spaceMetadata?.host?.avatarUrl ?: thumbnail,
            durationSeconds = spaceMetadata?.durationSeconds?.takeIf { it > 0 } ?: duration,
            availableFormats = effectiveFormats.sortedWith(
                compareByDescending<MediaFormat> { it.height ?: 0 }
                    .thenByDescending { it.fps ?: 0.0 }
                    .thenByDescending { it.bitrate ?: 0L },
            ),
            spaceMetadata = spaceMetadata,
        )
    }

    private fun toMediaFormat(json: JSONObject, duration: Long?): MediaFormat? {
        val formatId = json.optString("format_id").takeIf { it.isNotBlank() }
            ?: json.optString("id").takeIf { it.isNotBlank() }
            ?: if (json.has("url") || json.has("ext")) "direct" else return null
        val videoCodec = json.optString("vcodec").takeIf { it.isNotBlank() && it != "none" && it != "null" }
        val audioCodec = json.optString("acodec").takeIf { it.isNotBlank() && it != "none" && it != "null" }
        val extension = json.optString("ext").takeIf { it.isNotBlank() } ?: "mp4"
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

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
