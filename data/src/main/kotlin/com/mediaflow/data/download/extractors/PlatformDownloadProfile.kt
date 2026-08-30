package com.mediaflow.data.download.extractors

import com.mediaflow.data.download.extractors.facebook.FacebookExtractor
import com.mediaflow.data.download.extractors.spaces.SpacesExtractor
import com.mediaflow.data.download.extractors.tiktok.TikTokExtractor
import com.mediaflow.data.download.extractors.twitter.TwitterExtractor
import com.mediaflow.data.download.extractors.youtube.YoutubeExtractor
import com.mediaflow.data.resolver.PlatformUrlSupport

/** Per-platform download hints. No DRM; public pages only. */
data class PlatformDownloadProfile(
    val platform: PlatformUrlSupport.Platform,
    val pageReferer: String?,
    val muxVideoOnlyWithAac: Boolean,
)

object PlatformDownloadProfiles {
    fun forPlatform(platform: PlatformUrlSupport.Platform?): PlatformDownloadProfile? = when (platform) {
        PlatformUrlSupport.Platform.YOUTUBE -> YoutubeExtractor.profile
        PlatformUrlSupport.Platform.TIKTOK -> TikTokExtractor.profile
        PlatformUrlSupport.Platform.FACEBOOK -> FacebookExtractor.profile
        PlatformUrlSupport.Platform.X -> TwitterExtractor.profile
        PlatformUrlSupport.Platform.DIRECT -> null
        PlatformUrlSupport.Platform.INSTAGRAM -> PlatformDownloadProfile(
            platform = platform,
            pageReferer = "https://www.instagram.com/",
            muxVideoOnlyWithAac = true,
        )
        null -> null
    }

    fun forUrl(url: String): PlatformDownloadProfile? {
        if (SpacesExtractor.isSpaceUrl(url)) return SpacesExtractor.profile
        return forPlatform(PlatformUrlSupport.platformFor(url))
    }
}
