package com.mediaflow.data.download.extractors.twitter

import com.mediaflow.data.download.extractors.PlatformDownloadProfile
import com.mediaflow.data.resolver.PlatformUrlSupport

object TwitterExtractor {
    val profile = PlatformDownloadProfile(
        platform = PlatformUrlSupport.Platform.X,
        pageReferer = "https://x.com/",
        muxVideoOnlyWithAac = true,
    )
}
