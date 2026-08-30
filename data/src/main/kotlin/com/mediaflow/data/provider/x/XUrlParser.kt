package com.mediaflow.data.provider.x

import java.net.URI

/**
 * Parses and extracts identifiers from X (Twitter) URLs.
 */
object XUrlParser {

    private val X_DOMAINS = setOf(
        "x.com",
        "twitter.com",
        "mobile.twitter.com",
        "mobile.x.com",
        "vxtwitter.com",
        "fxtwitter.com",
        "fixupx.com",
    )

    private val SPACE_PATH_REGEX = Regex("""^/i/spaces/([0-9a-zA-Z]{10,20})""", RegexOption.IGNORE_CASE)
    private val STATUS_PATH_REGEX = Regex("""^/[^/]+/status/([0-9]+)""", RegexOption.IGNORE_CASE)

    fun isXUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return false
        }
        val host = runCatching { URI(trimmed).host?.lowercase() }.getOrNull() ?: return false
        val cleanHost = host.removePrefix("www.")
        return X_DOMAINS.contains(cleanHost)
    }

    /**
     * Extracts direct Space ID from an `/i/spaces/<id>` URL.
     */
    fun extractDirectSpaceId(url: String): String? {
        val trimmed = url.trim()
        if (!isXUrl(trimmed)) return null
        val path = runCatching { URI(trimmed).path }.getOrNull() ?: return null
        val match = SPACE_PATH_REGEX.find(path) ?: return null
        return match.groupValues[1]
    }

    /**
     * Extracts Tweet/Status ID from an `/<user>/status/<id>` URL.
     */
    fun extractStatusId(url: String): String? {
        val trimmed = url.trim()
        if (!isXUrl(trimmed)) return null
        val path = runCatching { URI(trimmed).path }.getOrNull() ?: return null
        val match = STATUS_PATH_REGEX.find(path) ?: return null
        return match.groupValues[1]
    }
}
