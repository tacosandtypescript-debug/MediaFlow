package com.mediaflow.data.resolver.tiktok

import com.mediaflow.data.resolver.TikTokAnonymousResolver
import java.net.HttpURLConnection
import java.net.URL

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

/**
 * Uses [TikTokLinkResolver] then one primary + one fallback extractor.
 * Extractors are not called until resolution succeeds.
 */
object TikTokExtractPipeline {
    private val BLOCKED_BODY = Regex(
        "access denied|captcha|please wait|blocked|too many requests|verify you are human",
        RegexOption.IGNORE_CASE,
    )

    fun resolveCanonical(
        sourceUrl: String,
        hop: TikTokRedirectHop = defaultHop(),
    ): TikTokResolvedLink {
        val hopper = TikTokHttpHopper { url ->
            val result = hop.hop(url)
            val blocked = mapBlocked(result.statusCode, result.bodySnippet) != null
            TikTokHttpHopper.Hop(
                status = result.statusCode,
                location = result.location,
                blocked = blocked,
            )
        }
        return when (val outcome = TikTokLinkResolver(hopper).resolve(sourceUrl)) {
            is TikTokResolveResult.Success -> outcome.link
            is TikTokResolveResult.Failure -> throw TikTokResolveException(
                outcome.stage,
                outcome.message,
            )
        }
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
}
