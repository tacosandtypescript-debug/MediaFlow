package com.mediaflow.data.resolver.tiktok

/** Bounded TikTok resolve/extract failure stages. Not retried infinitely. */
enum class TikTokResolveStage {
    URL_RESOLUTION_FAILED,
    REDIRECT_FAILED,
    VIDEO_ID_NOT_FOUND,
    TIKTOK_BLOCKED,
    EXTRACTOR_FAILED,
    MEDIA_URL_FAILED,
    DOWNLOAD_FAILED,
}
