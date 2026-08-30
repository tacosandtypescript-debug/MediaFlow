package com.mediaflow.data.download

import org.json.JSONArray

/**
 * Picks the highest-resolution artwork URL yt-dlp/YouTube/Music actually publish.
 * Does not invent IDs; only rewrites known size tokens on a real URL.
 */
object ArtworkUrlQuality {
    private val YT_VI = Regex(
        """(?i)^https?://(?:i\d*\.ytimg\.com|img\.youtube\.com)/vi(?:_webp)?/([A-Za-z0-9_-]{11})/[\w.-]+""",
    )
    private val GOOGLE_SIZE = Regex("""(?i)=w\d+-h\d+[^=]*$""")
    private val GOOGLE_S = Regex("""(?i)=s\d+[^=]*$""")

    fun upgrade(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        YT_VI.find(trimmed)?.groupValues?.get(1)?.let { id ->
            return "https://i.ytimg.com/vi/$id/maxresdefault.jpg"
        }
        val withoutQuerySize = trimmed
            .replace(GOOGLE_SIZE, "=s0")
            .replace(GOOGLE_S, "=s0")
        return withoutQuerySize
    }

    fun candidates(url: String?): List<String> {
        val upgraded = upgrade(url) ?: return emptyList()
        val original = url!!.trim()
        val id = YT_VI.find(original)?.groupValues?.get(1)
            ?: YT_VI.find(upgraded)?.groupValues?.get(1)
        if (id != null) {
            return listOf(
                "https://i.ytimg.com/vi/$id/maxresdefault.jpg",
                "https://i.ytimg.com/vi/$id/sddefault.jpg",
                "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                original,
            ).distinct()
        }
        return listOf(upgraded, original).distinct()
    }

    fun pickBest(thumbnails: JSONArray?, fallback: String?): String? {
        var bestUrl: String? = null
        var bestArea = -1L
        if (thumbnails != null) {
            for (i in 0 until thumbnails.length()) {
                val obj = thumbnails.optJSONObject(i) ?: continue
                val url = obj.optString("url").takeIf { it.startsWith("https://") } ?: continue
                val width = obj.optLong("width", 0L)
                val height = obj.optLong("height", 0L)
                val area = (width * height).takeIf { it > 0L } ?: preferenceScore(url)
                if (area >= bestArea) {
                    bestArea = area
                    bestUrl = url
                }
            }
        }
        return upgrade(bestUrl ?: fallback)
    }

    private fun preferenceScore(url: String): Long {
        val lower = url.lowercase()
        return when {
            lower.contains("maxres") -> 1_280L * 720L
            lower.contains("sddefault") -> 640L * 480L
            lower.contains("hqdefault") -> 480L * 360L
            lower.contains("mqdefault") -> 320L * 180L
            else -> 1L
        }
    }
}
