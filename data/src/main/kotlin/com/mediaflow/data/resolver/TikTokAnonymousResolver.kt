package com.mediaflow.data.resolver

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Resolves the public TikTok HTML page without cookies or a login session. */
class TikTokAnonymousResolver {
    data class ResolvedVideo(val url: String)

    fun resolve(sourceUrl: String): Result<ResolvedVideo> = runCatching {
        require(PlatformUrlSupport.platformFor(sourceUrl) == PlatformUrlSupport.Platform.TIKTOK)
        val html = fetchHtml(sourceUrl)
        val videoUrl = extractPlayAddress(html)
            ?: error("TikTok no publicó un enlace de vídeo anónimo")
        ResolvedVideo(videoUrl)
    }

    private fun fetchHtml(sourceUrl: String): String {
        var current = sourceUrl.trim()
        repeat(MAX_REDIRECTS) {
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", BROWSER_USER_AGENT)
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            connection.setRequestProperty("Referer", "https://www.tiktok.com/")
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: error("TikTok redirigió sin Location")
                    current = resolveRedirect(current, location)
                    return@repeat
                }
                check(code in 200..299) { "TikTok respondió HTTP $code" }
                return connection.inputStream.use { it.bufferedReader(Charsets.UTF_8).readText() }
            } finally {
                connection.disconnect()
            }
        }
        error("TikTok redirigió demasiadas veces")
    }

    companion object {
        const val TIMEOUT_MS = 20_000
        const val MAX_REDIRECTS = 8
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.135 Mobile Safari/537.36"

        private val UNICODE_ESCAPE = Regex("""\\u([0-9a-fA-F]{4})""")
        private val PLAY_ADDR = Regex(""""playAddr"\s*:\s*"([^"]+)"""")
        private val DOWNLOAD_ADDR = Regex(""""downloadAddr"\s*:\s*"([^"]+)"""")
        private val PLAY_URL_OBJECT = Regex(""""playUrl"\s*:\s*\[\s*\{\s*"src"\s*:\s*"([^"]+)"""")
        private val PLAY_URL = Regex(""""playUrl"\s*:\s*"([^"]+)"""")
        private val PLAY_ADDR_LIST = Regex(""""play_addr"\s*:\s*\{[^{}]*?"url_list"\s*:\s*\[\s*"([^"]+)"""")
        private val DOWNLOAD_ADDR_LIST = Regex(""""download_addr"\s*:\s*\{[^{}]*?"url_list"\s*:\s*\[\s*"([^"]+)"""")
        private val OG_VIDEO = Regex(
            """<meta\s+[^>]*(?:property|name)=["']og:video(?::(?:url|secure_url))?["'][^>]*content=["']([^"']+)["']""" +
                """|<meta\s+[^>]*content=["']([^"']+)["'][^>]*(?:property|name)=["']og:video(?::(?:url|secure_url))?["']""",
            RegexOption.IGNORE_CASE,
        )

        internal fun extractPlayAddress(html: String): String? {
            val patterns = listOf(
                PLAY_ADDR,
                DOWNLOAD_ADDR,
                PLAY_ADDR_LIST,
                DOWNLOAD_ADDR_LIST,
                PLAY_URL_OBJECT,
                PLAY_URL,
            )
            for (pattern in patterns) {
                val match = pattern.find(html) ?: continue
                decodeHttpsUrl(match.groupValues[1])?.let { return it }
            }
            OG_VIDEO.find(html)?.let { match ->
                decodeHttpsUrl(match.groupValues[1].ifBlank { match.groupValues[2] })?.let { return it }
            }
            return null
        }

        internal fun decodeHttpsUrl(raw: String): String? {
            val decoded = unescapeJsonString(raw).replace("&amp;", "&").trim()
            if (!decoded.startsWith("https://", ignoreCase = true)) return null
            if (decoded.startsWith("https://www.tiktok.com/login", ignoreCase = true)) return null
            val uri = runCatching { URI(decoded) }.getOrNull()
            if (uri != null && !uri.scheme.equals("https", ignoreCase = true)) return null
            return decoded
        }

        private fun unescapeJsonString(raw: String): String {
            val unslashed = raw.replace("\\/", "/")
            return UNICODE_ESCAPE.replace(unslashed) { match ->
                val code = match.groupValues[1].toIntOrNull(16) ?: return@replace match.value
                Character.toString(code)
            }
        }

        private fun resolveRedirect(current: String, location: String): String {
            if (location.startsWith("https://", ignoreCase = true) ||
                location.startsWith("http://", ignoreCase = true)
            ) {
                return location
            }
            val base = URL(current)
            return URL(base, location).toString()
        }
    }
}
