package com.mediaflow.data.download.extractors.youtube

import com.mediaflow.data.download.extractors.PlatformDownloadProfile
import com.mediaflow.data.resolver.PlatformUrlSupport

object YoutubeExtractor {
    val profile = PlatformDownloadProfile(
        platform = PlatformUrlSupport.Platform.YOUTUBE,
        pageReferer = "https://www.youtube.com/",
        muxVideoOnlyWithAac = true,
    )
}
