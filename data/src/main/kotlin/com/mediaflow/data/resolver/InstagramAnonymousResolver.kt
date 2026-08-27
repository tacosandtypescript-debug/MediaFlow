package com.mediaflow.data.resolver

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Resolves public Instagram Reel CDN assets through a non-authenticated WebView. */
class InstagramAnonymousResolver(private val context: Context) {
    fun resolve(sourceUrl: String): Result<String> {
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
            (host.contains("instagram") || host.contains("fbcdn"))
    }

    private fun removeRangeParameters(url: String): String = url
        .replace(Regex("&?bytestart=\\d+"), "")
        .replace(Regex("&?byteend=\\d+"), "")
        .replace("?&", "?")
        .trimEnd('?', '&')

    private companion object {
        const val RESOLVE_TIMEOUT_MS = 15_000L
        const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"
        val RANGE_END = Regex("[?&]byteend=(\\d+)")
    }
}
