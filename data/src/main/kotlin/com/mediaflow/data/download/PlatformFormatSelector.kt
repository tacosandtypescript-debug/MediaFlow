package com.mediaflow.data.download

import com.mediaflow.core.model.MediaType
import com.mediaflow.domain.repository.DownloadRequest

/**
 * Builds yt-dlp selectors without silently changing the user's chosen format.
 * In particular, separated video never falls back to an unrelated progressive
 * quality when audio/muxing is unavailable.
 */
object PlatformFormatSelector {
    private const val VIDEO_MP4_PREFERRED = "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/b"
    private const val AUDIO_M4A_PREFERRED = "bestaudio[ext=m4a]/bestaudio"

    fun select(request: DownloadRequest): String = when {
        request.formatId == "space_audio_m4a" -> "b/best/0/bestaudio"
        request.formatId == "bestaudio" -> AUDIO_M4A_PREFERRED
        request.formatId == "anonymous" -> VIDEO_MP4_PREFERRED
        request.requiresMuxing && !request.formatId.isNullOrBlank() && request.formatId != "yt-dlp" ->
            muxedVideoAndAacAudio(request.formatId!!)
        !request.formatId.isNullOrBlank() && request.formatId != "yt-dlp" && request.formatId != "anonymous" ->
            request.formatId!!
        request.mediaType == MediaType.AUDIO -> AUDIO_M4A_PREFERRED
        request.qualityLabel?.contains("1080") == true -> heightBoundedMp4(1080)
        request.qualityLabel?.contains("720") == true -> heightBoundedMp4(720)
        request.qualityLabel?.contains("480") == true -> heightBoundedMp4(480)
        request.qualityLabel?.contains("360") == true -> heightBoundedMp4(360)
        else -> VIDEO_MP4_PREFERRED
    }

    private fun muxedVideoAndAacAudio(formatId: String): String =
        "$formatId+bestaudio[ext=m4a]/$formatId+bestaudio[acodec^=mp4a]/$formatId+bestaudio[acodec^=aac]/$formatId+bestaudio"

    private fun heightBoundedMp4(height: Int): String =
        "bv*[ext=mp4][height<=$height]+ba[ext=m4a]/b[ext=mp4][height<=$height]/b[height<=$height]/b"
}
