package com.mediaflow.data.resolver

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Resolves the public TikTok HTML page without cookies or a login session. */
class TikTokAnonymousResolver {
    data class ResolvedVideo(val url: String, val cookieHeader: String? = null)

    fun resolve(sourceUrl: String): Result<ResolvedVideo> = runCatching {
        require(PlatformUrlSupport.platformFor(sourceUrl) == PlatformUrlSupport.Platform.TIKTOK)
        val cookies = linkedMapOf<String, String>()
        val html = fetchHtml(sourceUrl, cookies)
        val videoUrl = extractPlayAddress(html)
            ?: error("TikTok no publicó un enlace de vídeo anónimo")
        val cookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            .takeIf { it.isNotBlank() }
        ResolvedVideo(videoUrl, cookieHeader)
    }

    /** Fetches the page and the CDN MP4 in one pass so the playAddr token does not expire. */
    fun downloadTo(
        sourceUrl: String,
        destination: File,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = runCatching {
        require(PlatformUrlSupport.platformFor(sourceUrl) == PlatformUrlSupport.Platform.TIKTOK)
        val cookies = linkedMapOf<String, String>()
        val html = fetchHtml(sourceUrl, cookies)
        val videoUrl = extractPlayAddress(html)
            ?: error("TikTok no publicó un enlace de vídeo anónimo")
        val cookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            .takeIf { it.isNotBlank() }
        val partial = File(destination.parentFile, "${destination.name}.part")
        try {
            val connection = URL(videoUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", BROWSER_USER_AGENT)
            connection.setRequestProperty("Referer", "https://www.tiktok.com/")
            connection.setRequestProperty("Origin", "https://www.tiktok.com")
            connection.setRequestProperty("Accept", "video/mp4,video/*;q=0.9,*/*;q=0.8")
            cookieHeader?.let { connection.setRequestProperty("Cookie", it) }
            check(connection.responseCode in 200..299) {
                "TikTok CDN respondió HTTP ${connection.responseCode}"
            }
            val total = connection.contentLengthLong
            var downloaded = 0L
            onProgress(downloaded, total)
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
            check(partial.length() > 0L) { "TikTok no entregó contenido" }
            if (destination.exists()) destination.delete()
            check(partial.renameTo(destination)) { "No se pudo guardar el vídeo de TikTok" }
            destination
        } finally {
            if (partial.exists() && partial != destination) partial.delete()
        }
    }

    private fun fetchHtml(sourceUrl: String, cookies: MutableMap<String, String>): String {
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
            if (cookies.isNotEmpty()) {
                connection.setRequestProperty(
                    "Cookie",
                    cookies.entries.joinToString("; ") { "${it.key}=${it.value}" },
                )
            }
            try {
                val code = connection.responseCode
                mergeCookies(cookies, connection)
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
                DOWNLOAD_ADDR,
                DOWNLOAD_ADDR_LIST,
                PLAY_ADDR,
                PLAY_ADDR_LIST,
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

        internal fun mergeCookies(cookies: MutableMap<String, String>, connection: HttpURLConnection) {
            var index = 0
            while (true) {
                val key = connection.getHeaderFieldKey(index)
                val value = connection.getHeaderField(index) ?: break
                index++
                if (key == null || !key.equals("Set-Cookie", ignoreCase = true)) continue
                val pair = value.substringBefore(';')
                val name = pair.substringBefore('=', missingDelimiterValue = "").trim()
                val cookieValue = pair.substringAfter('=', missingDelimiterValue = "").trim()
                if (name.isBlank() || cookieValue.isBlank()) continue
                cookies[name] = cookieValue
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
