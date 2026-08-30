package com.mediaflow.data.provider.x.dvr

data class HlsSegmentRef(
    val uri: String,
    val durationMs: Long,
)

/**
 * Media playlist parser for Space HLS. Does not invent segments.
 */
object HlsMediaPlaylistParser {
    fun parse(body: String, playlistUrl: String): List<HlsSegmentRef> {
        val base = playlistUrl.substringBeforeLast('/') + "/"
        val lines = body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val out = ArrayList<HlsSegmentRef>()
        var pendingDurationMs = 0L
        for (line in lines) {
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                val raw = line.removePrefix("#EXTINF:").substringBefore(',').trim()
                val seconds = raw.toDoubleOrNull() ?: 0.0
                pendingDurationMs = (seconds * 1_000.0).toLong()
                continue
            }
            if (line.startsWith("#")) continue
            val uri = if (line.startsWith("http://") || line.startsWith("https://")) {
                line
            } else {
                base + line
            }
            out.add(HlsSegmentRef(uri = uri, durationMs = pendingDurationMs))
            pendingDurationMs = 0L
        }
        return out
    }
}
