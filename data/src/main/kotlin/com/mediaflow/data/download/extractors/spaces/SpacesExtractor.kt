package com.mediaflow.data.download.extractors.spaces

import com.mediaflow.data.download.extractors.PlatformDownloadProfile
import com.mediaflow.data.provider.x.XUrlParser
import com.mediaflow.data.resolver.PlatformUrlSupport

object SpacesExtractor {
    val profile = PlatformDownloadProfile(
        platform = PlatformUrlSupport.Platform.X,
        pageReferer = "https://x.com/",
        muxVideoOnlyWithAac = false,
    )

    fun isSpaceUrl(url: String): Boolean = XUrlParser.extractDirectSpaceId(url) != null
}
