package com.mediaflow.data.resolver.tiktok

import java.net.URI

object TikTokVideoId {
    private val VIDEO_PATH = Regex("""/(?:@[^/]+/)?video/(\d+)(?:/|$)""", RegexOption.IGNORE_CASE)
    private val PHOTO_PATH = Regex("""/(?:@[^/]+/)?photo/(\d+)(?:/|$)""", RegexOption.IGNORE_CASE)

    fun fromUrl(url: String): String? {
        val path = runCatching { URI(url.trim()).path }.getOrNull() ?: return null
        VIDEO_PATH.find(path)?.groupValues?.getOrNull(1)?.let { return it }
        return PHOTO_PATH.find(path)?.groupValues?.getOrNull(1)
    }

    fun canonicalWatchUrl(url: String, videoId: String? = fromUrl(url)): String? {
        val id = videoId ?: return null
        val uri = runCatching { URI(url.trim()) }.getOrNull()
        val path = uri?.path.orEmpty()
        val user = Regex("""/@([^/]+)""", RegexOption.IGNORE_CASE).find(path)?.groupValues?.getOrNull(1)
            ?: "video"
        return "https://www.tiktok.com/@$user/video/$id"
    }
}
