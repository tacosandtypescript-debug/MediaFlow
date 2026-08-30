package com.mediaflow.data.provider.x.dvr

/**
 * Pulls new HLS media segments from a live playlist. Independent of mpv pause.
 */
class HlsLiveIngest(
    private val fetch: (String) -> ByteArray,
) {
    private val seen = LinkedHashSet<String>()

    fun pull(playlistUrl: String): List<ByteArray> {
        if (playlistUrl.isBlank()) return emptyList()
        val playlist = runCatching { fetch(playlistUrl).decodeToString() }.getOrNull() ?: return emptyList()
        val refs = HlsMediaPlaylistParser.parse(playlist, playlistUrl)
        val fresh = refs.filter { it.uri !in seen }
        if (fresh.isEmpty()) return emptyList()
        return buildList {
            for (ref in fresh) {
                val bytes = runCatching { fetch(ref.uri) }.getOrNull() ?: continue
                if (bytes.isEmpty()) continue
                seen.add(ref.uri)
                add(bytes)
            }
        }
    }
}
