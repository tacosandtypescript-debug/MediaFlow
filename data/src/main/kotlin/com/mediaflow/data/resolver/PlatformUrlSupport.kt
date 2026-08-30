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
        DIRECT("Directo", "direct"),
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

            hostIs("tiktok.com") || hostIs("vm.tiktok.com") || hostIs("vt.tiktok.com") ||
                hostIs("douyin.com") -> Platform.TIKTOK

            hostIs("youtube.com") || hostIs("youtu.be") -> Platform.YOUTUBE

            isDirectMediaPath(uri.path) -> Platform.DIRECT

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

    fun isYoutubeMusic(url: String): Boolean {
        val host = runCatching { URI(url.trim()).host?.lowercase()?.trimEnd('.') }.getOrNull() ?: return false
        return host == "music.youtube.com" || host.endsWith(".music.youtube.com")
    }

    /**
     * music.youtube.com / m.youtube.com / youtu.be / share `si=` params all map
     * to the same watch page. yt-dlp's music client needs cookies; the watch
     * extractor does not.
     */
    fun canonicalExtractionUrl(url: String): String {
        val trimmed = url.trim()
        if (platformFor(trimmed) != Platform.YOUTUBE) return trimmed
        if (isYoutubePlaylist(trimmed)) return trimmed
        val videoId = youtubeVideoId(trimmed) ?: return trimmed
        return "https://www.youtube.com/watch?v=$videoId"
    }

    fun youtubeVideoId(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.trimEnd('.')?.removePrefix("www.") ?: return null
        val path = uri.path.orEmpty()
        if (host == "youtu.be") {
            return path.trim('/').substringBefore('/').takeIf { it.matches(YOUTUBE_VIDEO_ID) }
        }
        queryParam(uri.rawQuery, "v")?.takeIf { it.matches(YOUTUBE_VIDEO_ID) }?.let { return it }
        val match = YOUTUBE_PATH_ID.find(path) ?: return null
        return match.groupValues[2].takeIf { it.matches(YOUTUBE_VIDEO_ID) }
    }

    fun isDirectMedia(url: String): Boolean = platformFor(url) == Platform.DIRECT

    /** Any HTTPS page is delegated to yt-dlp unless it is a known direct file. */
    fun isGenericYtDlpPage(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) return false
        return !isDirectMediaPath(uri.path)
    }

    private fun isDirectMediaPath(path: String?): Boolean {
        val extension = path.orEmpty().substringAfterLast('.', "").lowercase()
        return extension in DIRECT_MEDIA_EXTENSIONS
    }

    private val DIRECT_MEDIA_EXTENSIONS = setOf(
        "mp4", "m4v", "webm", "mp3", "m4a", "aac", "wav", "ogg",
    )
    private val YOUTUBE_VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val YOUTUBE_PATH_ID = Regex("/(embed|shorts|live|watch)/([A-Za-z0-9_-]{11})")

    private fun queryParam(rawQuery: String?, name: String): String? {
        if (rawQuery.isNullOrBlank()) return null
        return rawQuery.split('&').firstNotNullOfOrNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) return@firstNotNullOfOrNull null
            val key = part.substring(0, eq)
            if (!key.equals(name, ignoreCase = true)) return@firstNotNullOfOrNull null
            part.substring(eq + 1).takeIf { it.isNotBlank() }
        }
    }
}
