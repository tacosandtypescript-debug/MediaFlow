package com.mediaflow.data.resolver

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Resolves public Instagram Reel CDN assets without cookies or a login session. */
class InstagramAnonymousResolver(private val context: Context) {
    fun resolve(sourceUrl: String): Result<String> {
        val pageUrl = sourceUrl.trim()
        val embed = embedUrl(pageUrl)
        for (candidate in listOf(pageUrl, embed).distinct()) {
            val html = runCatching { fetchHtml(candidate) }.getOrNull() ?: continue
            extractVideoUrlFromHtml(html)?.let { return Result.success(it) }
        }
        return resolveViaWebView(if (pageUrl.contains("/embed", ignoreCase = true)) pageUrl else embed)
    }

    private fun fetchHtml(url: String): String {
        var current = url
        repeat(MAX_REDIRECTS) {
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = HTML_TIMEOUT_MS
            connection.readTimeout = HTML_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", BROWSER_USER_AGENT)
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            connection.setRequestProperty("Referer", "https://www.instagram.com/")
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: error("Instagram redirigió sin Location")
                    current = resolveRedirect(current, location)
                    return@repeat
                }
                check(code in 200..299) { "Instagram respondió HTTP $code" }
                return connection.inputStream.use { it.bufferedReader(Charsets.UTF_8).readText() }
            } finally {
                connection.disconnect()
            }
        }
        error("Instagram redirigió demasiadas veces")
    }

    private fun resolveViaWebView(sourceUrl: String): Result<String> {
        val latch = CountDownLatch(1)
        val finished = AtomicBoolean(false)
        val result = AtomicReference<Result<String>?>(null)
        val handler = Handler(Looper.getMainLooper())

        handler.post {
            val candidates = java.util.Collections.synchronizedMap(LinkedHashMap<String, Long>())
            val webView = WebView(context)
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            CookieManager.getInstance().setAcceptCookie(false)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
            fun finish(value: Result<String>) {
                if (!finished.compareAndSet(false, true)) return
                webView.stopLoading()
                webView.destroy()
                result.set(value)
                latch.countDown()
            }

            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = true
            webView.settings.mediaPlaybackRequiresUserGesture = false
            webView.settings.userAgentString = BROWSER_USER_AGENT
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): android.webkit.WebResourceResponse? {
                    val url = request.url.toString()
                    if (isInstagramVideo(url)) {
                        val fullUrl = removeRangeParameters(url)
                        val end = RANGE_END.find(url)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                        candidates[fullUrl] = maxOf(candidates[fullUrl] ?: 0L, end)
                    }
                    return null
                }
            }
            webView.loadUrl(sourceUrl)
            handler.postDelayed({
                val best = synchronized(candidates) {
                    candidates.maxByOrNull { it.value }?.key
                }
                finish(
                    best?.let { Result.success(it) }
                        ?: Result.failure(IllegalStateException("Instagram no publicó un vídeo anónimo")),
                )
            }, RESOLVE_TIMEOUT_MS)
        }

        if (!latch.await(RESOLVE_TIMEOUT_MS + 5_000L, TimeUnit.MILLISECONDS)) {
            return Result.failure(IllegalStateException("Tiempo agotado al analizar Instagram"))
        }
        return result.get() ?: Result.failure(IllegalStateException("Instagram no respondió"))
    }

    private fun isInstagramVideo(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return uri.path.orEmpty().endsWith(".mp4") &&
            (host.contains("instagram") || host.contains("fbcdn") || host.contains("cdninstagram"))
    }

    private fun removeRangeParameters(url: String): String = url
        .replace(Regex("&?bytestart=\\d+"), "")
        .replace(Regex("&?byteend=\\d+"), "")
        .replace("?&", "?")
        .trimEnd('?', '&')

    companion object {
        const val RESOLVE_TIMEOUT_MS = 15_000L
        const val HTML_TIMEOUT_MS = 15_000
        const val MAX_REDIRECTS = 8
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.139 Safari/537.36"
        val RANGE_END = Regex("[?&]byteend=(\\d+)")
        private val UNICODE_ESCAPE = Regex("""\\u([0-9a-fA-F]{4})""")
        private val VIDEO_URL_JSON = Regex(""""video_url"\s*:\s*"([^"]+)"""")
        private val VIDEO_VERSIONS_URL = Regex(""""video_versions"\s*:\s*\[[^\]]*?"url"\s*:\s*"([^"]+)"""")
        private val CONTENT_URL_JSON = Regex(""""contentUrl"\s*:\s*"([^"]+)"""")
        private val OG_VIDEO = Regex(
            """<meta\s+[^>]*(?:property|name)=["']og:video(?::(?:url|secure_url))?["'][^>]*content=["']([^"']+)["']""" +
                """|<meta\s+[^>]*content=["']([^"']+)["'][^>]*(?:property|name)=["']og:video(?::(?:url|secure_url))?["']""",
            RegexOption.IGNORE_CASE,
        )
        private val ESCAPED_MP4 = Regex("""https:\\u002F\\u002F[^"'\s<>]+?\.mp4[^"'\s<]*|https:\\?/\\?/[^"'\s<>]+?\.mp4[^"'\s<]*""")

        internal fun embedUrl(sourceUrl: String): String {
            val trimmed = sourceUrl.trim()
            if (trimmed.contains("/embed", ignoreCase = true)) return trimmed
            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return trimmed
            val path = uri.path.orEmpty().trimEnd('/')
            if (path.isEmpty() || path == "/") return trimmed
            return "https://www.instagram.com$path/embed/"
        }

        internal fun extractVideoUrlFromHtml(html: String): String? {
            val discovered = ArrayList<String>()
            OG_VIDEO.findAll(html).forEach { match ->
                discovered += match.groupValues[1].ifBlank { match.groupValues[2] }
            }
            VIDEO_URL_JSON.findAll(html).forEach { discovered += it.groupValues[1] }
            VIDEO_VERSIONS_URL.findAll(html).forEach { discovered += it.groupValues[1] }
            CONTENT_URL_JSON.findAll(html).forEach { discovered += it.groupValues[1] }
            ESCAPED_MP4.findAll(html).forEach { discovered += it.value }
            return discovered
                .mapNotNull { decodeHttpsUrl(it) }
                .firstOrNull { isAnonymousVideoUrl(it) }
        }

        internal fun decodeHttpsUrl(raw: String): String? {
            val decoded = unescapeJsonString(raw).replace("&amp;", "&").trim()
            if (!decoded.startsWith("https://", ignoreCase = true)) return null
            return decoded
        }

        internal fun isAnonymousVideoUrl(url: String): Boolean {
            val uri = runCatching { URI(url) }.getOrNull() ?: return url.contains(".mp4", ignoreCase = true)
            val host = uri.host?.lowercase() ?: return false
            val path = uri.path.orEmpty().lowercase()
            if (path.endsWith(".m3u8") || path.endsWith(".mpd")) return false
            if (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") || path.endsWith(".webp")) {
                return false
            }
            val instagramHost = host.contains("instagram") || host.contains("fbcdn") || host.contains("cdninstagram")
            return instagramHost && (path.endsWith(".mp4") || url.contains(".mp4", ignoreCase = true))
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
            return URL(URL(current), location).toString()
        }
    }
}
