package com.mediaflow.data.resolver.tiktok

/**
 * One HTTP hop so unit tests can fixture redirects without a live network.
 */
fun interface TikTokHttpHopper {
    data class Hop(
        val status: Int,
        val location: String? = null,
        val blocked: Boolean = false,
    )

    fun hop(url: String): Hop
}

data class TikTokResolvedLink(
    val input: String,
    val sanitizedUrl: String,
    val redirectChain: List<String>,
    val canonicalUrl: String,
    val videoId: String,
)

sealed class TikTokResolveResult {
    data class Success(val link: TikTokResolvedLink) : TikTokResolveResult()
    data class Failure(
        val stage: TikTokResolveStage,
        val message: String,
    ) : TikTokResolveResult()
}

/**
 * input → sanitized URL → redirect chain → canonical URL → video ID.
 * Stops before extractor / download.
 */
class TikTokLinkResolver(
    private val hopper: TikTokHttpHopper,
    private val maxHops: Int = 8,
) {
    fun resolve(paste: String): TikTokResolveResult {
        val extracted = TikTokUrlSanitizer.extractUrl(paste)
            ?: return fail(TikTokResolveStage.URL_RESOLUTION_FAILED, "No TikTok URL in paste")
        if (!TikTokUrlSanitizer.looksLikeTikTokHost(extracted)) {
            return fail(TikTokResolveStage.URL_RESOLUTION_FAILED, "Not a TikTok host")
        }
        val sanitized = TikTokUrlSanitizer.sanitize(extracted)
        val chain = mutableListOf(sanitized)
        var current = sanitized
        var hops = 0
        while (TikTokVideoId.fromUrl(current) == null && hops < maxHops) {
            val hop = runCatching { hopper.hop(current) }.getOrElse {
                return fail(TikTokResolveStage.REDIRECT_FAILED, it.message ?: "Hop failed")
            }
            if (hop.blocked || hop.status == 403 || hop.status == 429) {
                return fail(TikTokResolveStage.TIKTOK_BLOCKED, "HTTP ${hop.status}")
            }
            if (hop.status in 200..299) {
                return fail(
                    TikTokResolveStage.VIDEO_ID_NOT_FOUND,
                    "TikTok no devolvió /video/{id} tras las redirecciones",
                )
            }
            val location = hop.location?.takeIf { it.isNotBlank() }
                ?: return fail(TikTokResolveStage.REDIRECT_FAILED, "No Location for $current")
            val next = resolveLocation(current, location)
            val nextSanitized = TikTokUrlSanitizer.sanitize(next)
            if (nextSanitized == current) {
                return fail(TikTokResolveStage.REDIRECT_FAILED, "Redirect loop")
            }
            chain += nextSanitized
            current = nextSanitized
            hops++
        }
        val videoId = TikTokVideoId.fromUrl(current)
            ?: return fail(TikTokResolveStage.VIDEO_ID_NOT_FOUND, "No /video/{id} after redirects")
        val canonical = TikTokVideoId.canonicalWatchUrl(current, videoId)
            ?: return fail(TikTokResolveStage.VIDEO_ID_NOT_FOUND, "Cannot canonicalize")
        if (chain.last() != canonical) chain += canonical
        return TikTokResolveResult.Success(
            TikTokResolvedLink(
                input = paste,
                sanitizedUrl = sanitized,
                redirectChain = chain,
                canonicalUrl = canonical,
                videoId = videoId,
            ),
        )
    }

    private fun fail(stage: TikTokResolveStage, message: String) =
        TikTokResolveResult.Failure(stage, message)

    private fun resolveLocation(current: String, location: String): String {
        if (location.startsWith("http://", ignoreCase = true) ||
            location.startsWith("https://", ignoreCase = true)
        ) {
            return location
        }
        val base = java.net.URI(current)
        return base.resolve(location).toString()
    }
}
