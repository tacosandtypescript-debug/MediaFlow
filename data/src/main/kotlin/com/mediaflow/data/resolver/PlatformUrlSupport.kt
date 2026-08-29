package com.mediaflow.data.resolver

import java.net.URI

/** URL families handled by the embedded yt-dlp adapter. */
object PlatformUrlSupport {
    enum class Platform(val label: String, val filePrefix: String) {
        FACEBOOK("Facebook", "facebook"),
        X("X", "x"),
        INSTAGRAM("Instagram", "instagram"),
        TIKTOK("TikTok", "tiktok"),
        YOUTUBE("YouTube", "youtube"),
    }

    fun platformFor(url: String): Platform? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) && !uri.scheme.equals("http", ignoreCase = true)) return null
        val host = uri.host?.lowercase() ?: return null
        val cleanHost = host.removePrefix("www.")
        val path = uri.path.orEmpty()
        return when {
            cleanHost == "facebook.com" || cleanHost.endsWith(".facebook.com") ||
                cleanHost == "fb.watch" || cleanHost.endsWith(".fb.watch") ||
                cleanHost == "fb.com" || cleanHost.endsWith(".fb.com") -> Platform.FACEBOOK

            cleanHost == "x.com" || cleanHost.endsWith(".x.com") ||
                cleanHost == "twitter.com" || cleanHost.endsWith(".twitter.com") ||
                cleanHost == "vxtwitter.com" || cleanHost == "fxtwitter.com" || cleanHost == "fixupx.com" -> Platform.X

            cleanHost == "instagram.com" || cleanHost.endsWith(".instagram.com") ||
                cleanHost == "instagr.am" || cleanHost.endsWith(".instagr.am") -> Platform.INSTAGRAM

            cleanHost == "tiktok.com" || cleanHost.endsWith(".tiktok.com") ||
                cleanHost == "douyin.com" || cleanHost.endsWith(".douyin.com") -> Platform.TIKTOK

            cleanHost == "youtube.com" || cleanHost.endsWith(".youtube.com") ||
                cleanHost == "youtu.be" || cleanHost.endsWith(".youtu.be") -> Platform.YOUTUBE

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
