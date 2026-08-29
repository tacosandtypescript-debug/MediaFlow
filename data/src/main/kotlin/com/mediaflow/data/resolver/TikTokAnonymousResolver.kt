package com.mediaflow.data.resolver

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Resolves the public TikTok HTML page without cookies or a login session. */
class TikTokAnonymousResolver {
    data class ResolvedVideo(val url: String)

    fun resolve(sourceUrl: String): Result<ResolvedVideo> = runCatching {
        require(PlatformUrlSupport.platformFor(sourceUrl) == PlatformUrlSupport.Platform.TIKTOK)
        val connection = URL(sourceUrl).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.setRequestProperty("User-Agent", BROWSER_USER_AGENT)
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        connection.setRequestProperty("Referer", "https://www.tiktok.com/")
        val html = connection.inputStream.use { it.bufferedReader(Charsets.UTF_8).readText() }
        val videoUrl = extractPlayAddress(html)
            ?: error("TikTok no publicó un enlace de vídeo anónimo")
        // TikTok may return transient Set-Cookie headers, but this anonymous
        // resolver deliberately never stores or forwards them.
        ResolvedVideo(videoUrl)
    }

    private fun extractPlayAddress(html: String): String? {
        val candidates = listOf(
            Regex("""\"playAddr\":\"([^\"]+)\""""),
            Regex("""\"downloadAddr\":\"([^\"]+)\""""),
            Regex("""\"playUrl\":\[\{\"src\":\"([^\"]+)\""""),
            Regex("""\"playUrl\":\"([^\"]+)\""""),
        )
        for (pattern in candidates) {
            val match = pattern.find(html) ?: continue
            val raw = match.groupValues[1]
                .replace("\\u002F", "/")
                .replace("\\u0026", "&")
                .replace("\\/", "/")
                .replace("\\u003D", "=")
            if (runCatching { URI(raw) }.getOrNull()?.scheme == "https") {
                return raw
            }
        }
        return null
    }

    private companion object {
        const val TIMEOUT_MS = 20_000
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"
    }
}
