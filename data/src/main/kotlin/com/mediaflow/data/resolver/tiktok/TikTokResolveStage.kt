package com.mediaflow.data.resolver.tiktok

/**
 * Shared failure codes for the TikTok pipeline.
 * Resolution stops before EXTRACTOR_FAILED; later codes are owned by extract/download.
 */
enum class TikTokResolveStage {
    URL_RESOLUTION_FAILED,
    REDIRECT_FAILED,
    VIDEO_ID_NOT_FOUND,
    TIKTOK_BLOCKED,
    EXTRACTOR_FAILED,
    MEDIA_URL_FAILED,
    DOWNLOAD_FAILED,
}
