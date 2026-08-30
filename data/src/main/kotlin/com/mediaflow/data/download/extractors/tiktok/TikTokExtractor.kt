package com.mediaflow.data.download.extractors.tiktok

import com.mediaflow.data.download.extractors.PlatformDownloadProfile
import com.mediaflow.data.resolver.PlatformUrlSupport

object TikTokExtractor {
    val profile = PlatformDownloadProfile(
        platform = PlatformUrlSupport.Platform.TIKTOK,
        pageReferer = "https://www.tiktok.com/",
        muxVideoOnlyWithAac = true,
    )
}
