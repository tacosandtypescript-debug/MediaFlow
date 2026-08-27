package com.mediaflow.data.provider.x

import com.mediaflow.core.model.XContentType
import org.json.JSONObject

/**
 * Detects whether an X resource is a Space, Video, or generic post.
 */
object XContentDetector {

    /**
     * Inspects a URL quickly to see if it is explicitly an X Space URL.
     */
    fun detectFromUrl(url: String): XContentType {
        if (!XUrlParser.isXUrl(url)) return XContentType.UNSUPPORTED
        if (XUrlParser.extractDirectSpaceId(url) != null) return XContentType.SPACE
        if (XUrlParser.extractStatusId(url) != null) return XContentType.MEDIA_POST // Needs full inspection
        return XContentType.UNSUPPORTED
    }

    /**
     * Inspects yt-dlp extracted JSON output to categorize the content type with full certainty.
     */
    fun detectFromYtDlpJson(json: JSONObject): XContentType {
        val extractor = json.optString("extractor", "").lowercase()
        val extractorKey = json.optString("extractor_key", "").lowercase()
        val webpageUrl = json.optString("webpage_url", "")

        if (extractor == "twitter:spaces" || extractorKey == "twitterspaces" || webpageUrl.contains("/i/spaces/")) {
            return XContentType.SPACE
        }

        val formats = json.optJSONArray("formats")
        val hasVideo = (0 until (formats?.length() ?: 0)).any { i ->
            val fmt = formats?.optJSONObject(i)
            val vcodec = fmt?.optString("vcodec", "none") ?: "none"
            vcodec != "none"
        }

        return if (hasVideo) XContentType.VIDEO else XContentType.MEDIA_POST
    }
}
