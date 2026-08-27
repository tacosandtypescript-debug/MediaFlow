package com.mediaflow.data.resolver

import java.net.URI

/** URL families handled by the embedded yt-dlp adapter. */
object PlatformUrlSupport {
    enum class Platform(val label: String, val filePrefix: String) {
        FACEBOOK("Facebook", "facebook"),
        X("X", "x"),
        INSTAGRAM("Instagram", "instagram"),
        TIKTOK("TikTok", "tiktok"),
    }

    fun platformFor(url: String): Platform? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host?.lowercase() ?: return null
        val path = uri.path.orEmpty()
        return when {
            (host == "facebook.com" || host.endsWith(".facebook.com")) &&
                (path.startsWith("/share/r/") || path.startsWith("/reel/") || path.startsWith("/watch")) -> Platform.FACEBOOK
            (host == "x.com" || host.endsWith(".x.com") || host == "twitter.com" || host.endsWith(".twitter.com")) &&
                path.contains("/status/") -> Platform.X
            (host == "instagram.com" || host.endsWith(".instagram.com")) &&
                (path.startsWith("/reel/") || path.startsWith("/p/") || path.startsWith("/tv/")) -> Platform.INSTAGRAM
            (host == "tiktok.com" || host.endsWith(".tiktok.com")) && path.isNotBlank() -> Platform.TIKTOK
            else -> null
        }
    }

    fun isSupported(url: String): Boolean {
        return platformFor(url) != null || isGenericYtDlpPage(url)
    }

    /** Any HTTPS page is delegated to yt-dlp unless it is a known direct file. */
    fun isGenericYtDlpPage(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return false
        val extension = uri.path.orEmpty().substringAfterLast('.', "").lowercase()
        return extension !in DIRECT_MEDIA_EXTENSIONS
    }

    private val DIRECT_MEDIA_EXTENSIONS = setOf(
        "mp4", "m4v", "webm", "mp3", "m4a", "aac", "wav", "ogg",
    )
}
