package com.mediaflow.data.resolver.tiktok

import java.net.URI

object TikTokUrlSanitizer {
    private val EMBEDDED_URL = Regex(
        """https?://(?:www\.|m\.|vm\.|vt\.)?tiktok\.com/[^\s<>"']+""",
        RegexOption.IGNORE_CASE,
    )

    private val TRACKING_KEYS = setOf(
        "ttclid",
        "si",
        "_d",
        "_t",
        "_r",
        "is_from_webapp",
        "sender_device",
        "sender_web_id",
        "share_app_id",
        "share_item_id",
        "share_link_id",
        "share_source",
        "ug_source",
        "refer",
        "referer_url",
        "referer_video_id",
    )

    fun extractUrl(paste: String): String? {
        val trimmed = paste.trim()
        EMBEDDED_URL.find(trimmed)?.value?.trimEnd('.', ',', ';', ')', ']')?.let { return it }
        val firstToken = trimmed.split(Regex("\\s+")).firstOrNull().orEmpty()
        return firstToken.takeIf { looksLikeTikTokHost(it) }
    }

    fun sanitize(url: String): String {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return url.trim()
        val host = uri.host ?: return url.trim()
        val keptQuery = stripTracking(uri.rawQuery)
        val scheme = if (uri.scheme.equals("http", ignoreCase = true)) "https" else (uri.scheme ?: "https")
        val path = uri.path.orEmpty().ifBlank { "/" }
        val query = keptQuery?.let { "?$it" }.orEmpty()
        return "$scheme://${host.lowercase()}$path$query"
    }

    fun looksLikeTikTokHost(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
        val clean = host.removePrefix("www.").removePrefix("m.")
        return clean == "tiktok.com" || clean.endsWith(".tiktok.com")
    }

    fun isShortHost(url: String): Boolean {
        val host = runCatching { URI(url.trim()).host?.lowercase()?.trimEnd('.') }.getOrNull() ?: return false
        return host == "vm.tiktok.com" || host == "vt.tiktok.com" ||
            host.endsWith(".vm.tiktok.com") || host.endsWith(".vt.tiktok.com")
    }

    private fun stripTracking(rawQuery: String?): String? {
        if (rawQuery.isNullOrBlank()) return null
        val kept = rawQuery.split('&').filter { part ->
            val key = part.substringBefore('=').lowercase()
            key.isNotBlank() && key !in TRACKING_KEYS && !key.startsWith("utm_")
        }
        return kept.takeIf { it.isNotEmpty() }?.joinToString("&")
    }
}
