package com.mediaflow.data.download

import com.mediaflow.core.model.MediaType
import com.mediaflow.domain.repository.DownloadRequest

/**
 * Builds yt-dlp selectors without silently changing the user's chosen format.
 * In particular, separated video never falls back to an unrelated progressive
 * quality when audio/muxing is unavailable.
 */
object PlatformFormatSelector {
    fun select(request: DownloadRequest): String = when {
        request.formatId == "space_audio_m4a" -> "b/best/0/bestaudio"
        request.requiresMuxing && !request.formatId.isNullOrBlank() && request.formatId != "yt-dlp" ->
            "${request.formatId}+bestaudio"
        !request.formatId.isNullOrBlank() && request.formatId != "yt-dlp" -> request.formatId!!
        request.mediaType == MediaType.AUDIO -> "b/best/0/bestaudio"
        request.qualityLabel?.contains("1080") == true -> "best[height<=1080]/best"
        request.qualityLabel?.contains("720") == true -> "best[height<=720]/best"
        request.qualityLabel?.contains("480") == true -> "best[height<=480]/best"
        request.qualityLabel?.contains("360") == true -> "best[height<=360]/best"
        else -> "best"
    }
}
