package com.mediaflow.data.download.detection

import com.mediaflow.data.resolver.PlatformUrlSupport

/** Thin wrapper so download code can detect platforms without importing resolver internals. */
object PlatformDetector {
    fun platformFor(url: String): PlatformUrlSupport.Platform? = PlatformUrlSupport.platformFor(url)

    fun isSupported(url: String): Boolean = PlatformUrlSupport.isSupported(url)

    fun isDirectMedia(url: String): Boolean = PlatformUrlSupport.isDirectMedia(url)

    fun canonicalExtractionUrl(url: String): String = PlatformUrlSupport.canonicalExtractionUrl(url)
}
