package com.mediaflow.data.download.extractors.facebook

import com.mediaflow.data.download.extractors.PlatformDownloadProfile
import com.mediaflow.data.resolver.PlatformUrlSupport

object FacebookExtractor {
    val profile = PlatformDownloadProfile(
        platform = PlatformUrlSupport.Platform.FACEBOOK,
        pageReferer = "https://www.facebook.com/",
        muxVideoOnlyWithAac = true,
    )
}
