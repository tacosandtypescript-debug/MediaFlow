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
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
        val cleanHost = host.removePrefix("www.")
        fun hostIs(base: String) = cleanHost == base || cleanHost.endsWith(".$base")
        return when {
            hostIs("facebook.com") || hostIs("fb.watch") || hostIs("fb.com") -> Platform.FACEBOOK

            hostIs("x.com") || hostIs("twitter.com") ||
                cleanHost == "vxtwitter.com" || cleanHost == "fxtwitter.com" || cleanHost == "fixupx.com" -> Platform.X

            hostIs("instagram.com") || hostIs("instagr.am") -> Platform.INSTAGRAM

            hostIs("tiktok.com") || hostIs("douyin.com") -> Platform.TIKTOK

            hostIs("youtube.com") || hostIs("youtu.be") -> Platform.YOUTUBE

            else -> null
        }
    }

    fun isSupported(url: String): Boolean {
        return platformFor(url) != null || isGenericYtDlpPage(url)
    }

    /** Dedicated YouTube playlist pages. A watch URL with list= stays a single video. */
    fun isYoutubePlaylist(url: String): Boolean {
        if (platformFor(url) != Platform.YOUTUBE) return false
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        return uri.path.orEmpty().lowercase().contains("/playlist")
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
