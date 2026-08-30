package com.mediaflow.data.resolver.tiktok

import com.mediaflow.data.resolver.PlatformUrlSupport
import com.mediaflow.data.resolver.TikTokAnonymousResolver
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class TikTokResolvedLink(
    val originalUrl: String,
    val canonicalUrl: String,
    val videoId: String,
)

class TikTokResolveException(
    val stage: TikTokResolveStage,
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause)

data class TikTokHopResult(
    val statusCode: Int,
    val location: String? = null,
    val bodySnippet: String? = null,
)

fun interface TikTokRedirectHop {
    fun hop(url: String): TikTokHopResult
}

fun interface TikTokPageExtractor<T> {
    fun extract(canonicalUrl: String): T
}

object TikTokExtractPipeline {
    const val MAX_REDIRECTS = 8
    private val VIDEO_ID = Regex("""(?:^|/)video/(\d{8,})(?:/|$|\?)""")
    private val BLOCKED_BODY = Regex(
        "access denied|captcha|please wait|blocked|too many requests|verify you are human",
        RegexOption.IGNORE_CASE,
    )

    fun resolveCanonical(
        sourceUrl: String,
        hop: TikTokRedirectHop = defaultHop(),
    ): TikTokResolvedLink {
        val trimmed = sourceUrl.trim()
        if (PlatformUrlSupport.platformFor(trimmed) != PlatformUrlSupport.Platform.TIKTOK) {
            throw TikTokResolveException(
                TikTokResolveStage.URL_RESOLUTION_FAILED,
                "La URL no es de TikTok.",
            )
        }
        videoIdFromUrl(trimmed)?.let { id ->
            return TikTokResolvedLink(trimmed, canonicalUrl(trimmed, id), id)
        }
        var current = trimmed
        repeat(MAX_REDIRECTS) {
            val result = try {
                hop.hop(current)
            } catch (error: TikTokResolveException) {
                throw error
            } catch (error: Exception) {
                throw TikTokResolveException(
                    TikTokResolveStage.REDIRECT_FAILED,
                    "Fallo al seguir la redirección de TikTok.",
                    error,
                )
            }
            val mapped = mapHttp(result)
            if (mapped != null) throw mapped
            if (result.statusCode in 300..399) {
                val location = result.location?.takeIf { it.isNotBlank() }
                    ?: throw TikTokResolveException(
                        TikTokResolveStage.REDIRECT_FAILED,
                        "TikTok redirigió sin Location.",
                    )
                current = resolveRedirect(current, location)
                videoIdFromUrl(current)?.let { id ->
                    return TikTokResolvedLink(trimmed, canonicalUrl(current, id), id)
                }
                return@repeat
            }
            if (result.statusCode in 200..299) {
                videoIdFromUrl(current)?.let { id ->
                    return TikTokResolvedLink(trimmed, canonicalUrl(current, id), id)
                }
                throw TikTokResolveException(
                    TikTokResolveStage.VIDEO_ID_NOT_FOUND,
                    "TikTok no devolvió /video/{id} tras las redirecciones.",
                )
            }
            throw TikTokResolveException(
                TikTokResolveStage.REDIRECT_FAILED,
                "TikTok respondió HTTP ${result.statusCode} al resolver el enlace.",
            )
        }
        throw TikTokResolveException(
            TikTokResolveStage.REDIRECT_FAILED,
            "TikTok redirigió demasiadas veces.",
        )
    }

    fun <T> resolveThenExtract(
        sourceUrl: String,
        hop: TikTokRedirectHop = defaultHop(),
        primary: TikTokPageExtractor<T>,
        fallback: TikTokPageExtractor<T>? = null,
    ): T {
        val resolved = resolveCanonical(sourceUrl, hop)
        return try {
            primary.extract(resolved.canonicalUrl)
        } catch (error: TikTokResolveException) {
            throw error
        } catch (primaryError: Exception) {
            if (fallback == null) {
                throw TikTokResolveException(
                    TikTokResolveStage.EXTRACTOR_FAILED,
                    "El extractor no pudo leer ${resolved.canonicalUrl}.",
                    primaryError,
                )
            }
            try {
                fallback.extract(resolved.canonicalUrl)
            } catch (error: TikTokResolveException) {
                throw error
            } catch (fallbackError: Exception) {
                throw TikTokResolveException(
                    TikTokResolveStage.EXTRACTOR_FAILED,
                    "El extractor no pudo leer ${resolved.canonicalUrl}.",
                    fallbackError,
                )
            }
        }
    }

    fun videoIdFromUrl(url: String): String? {
        val path = runCatching { URI(url.trim()).path }.getOrNull() ?: url
        return VIDEO_ID.find(path)?.groupValues?.get(1)
            ?: VIDEO_ID.find(url)?.groupValues?.get(1)
    }

    fun canonicalUrl(url: String, videoId: String): String {
        val uri = runCatching { URI(url.trim()) }.getOrNull()
        val path = uri?.path.orEmpty()
        val user = Regex("""/@([^/]+)""").find(path)?.groupValues?.get(1)
        return if (user != null) {
            "https://www.tiktok.com/@$user/video/$videoId"
        } else {
            "https://www.tiktok.com/video/$videoId"
        }
    }

    fun mapBlocked(statusCode: Int, bodySnippet: String?): TikTokResolveException? {
        if (statusCode == 403 || statusCode == 429) {
            return TikTokResolveException(
                TikTokResolveStage.TIKTOK_BLOCKED,
                "TikTok bloqueó la petición (HTTP $statusCode).",
            )
        }
        if (bodySnippet != null && BLOCKED_BODY.containsMatchIn(bodySnippet)) {
            return TikTokResolveException(
                TikTokResolveStage.TIKTOK_BLOCKED,
                "TikTok bloqueó la petición.",
            )
        }
        return null
    }

    private fun mapHttp(result: TikTokHopResult): TikTokResolveException? =
        mapBlocked(result.statusCode, result.bodySnippet)

    fun defaultHop(): TikTokRedirectHop = TikTokRedirectHop { url ->
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = TikTokAnonymousResolver.TIMEOUT_MS
        connection.readTimeout = TikTokAnonymousResolver.TIMEOUT_MS
        connection.setRequestProperty("User-Agent", TikTokAnonymousResolver.BROWSER_USER_AGENT)
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        connection.setRequestProperty("Referer", "https://www.tiktok.com/")
        connection.setRequestProperty("Origin", "https://www.tiktok.com")
        try {
            val code = connection.responseCode
            val location = connection.getHeaderField("Location")
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val snippet = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(2048) }
            TikTokHopResult(code, location, snippet)
        } finally {
            connection.disconnect()
        }
    }

    private fun resolveRedirect(current: String, location: String): String {
        if (location.startsWith("https://", ignoreCase = true) ||
            location.startsWith("http://", ignoreCase = true)
        ) {
            return location
        }
        return URL(URL(current), location).toString()
    }
}
