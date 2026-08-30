package com.mediaflow.app.ui.player

import com.mediaflow.app.ui.common.media.preferredArtworkUrl
import com.mediaflow.data.media.metadata.EmbeddedTrackTags

data class PlayerMediaDisplay(
    val title: String = "",
    val artist: String? = null,
    val album: String? = null,
    val artworkUri: String? = null,
    val durationMs: Long = 0L,
)

/**
 * Resolves player title/artist/album from embedded tags, never preferring a
 * numeric MediaStore id when a real title tag exists.
 */
object PlayerDisplayMetadata {
    fun title(
        taggedTitle: String? = null,
        serviceTitle: String? = null,
        fileName: String? = null,
        uri: String = "",
    ): String = displayTitle(taggedTitle, serviceTitle, fileName, uri).ifBlank { "Media" }

    fun artist(taggedArtist: String?, serviceArtist: String?): String? =
        taggedArtist.humanOrNull() ?: serviceArtist.humanOrNull()

    fun album(taggedAlbum: String?): String? = taggedAlbum.humanOrNull()

    /**
     * Embedded tags belong to one URI. After skip/swipe they must not paint
     * the previous track's title/cover onto the new one.
     */
    fun tagsBelongToCurrent(tagsUri: String?, currentFilePath: String?, currentMediaId: String?): Boolean {
        val tagged = tagsUri?.trim().orEmpty()
        if (tagged.isBlank()) return false
        return tagged == currentFilePath?.trim() || tagged == currentMediaId?.trim()
    }

    private fun String?.humanOrNull(): String? {
        val value = this?.trim().orEmpty()
        if (value.isEmpty()) return null
        val lower = value.lowercase()
        if (lower == "<unknown>" || lower == "unknown" || lower == "null") return null
        return value
    }

    fun displayTitle(taggedTitle: String?, vararg fallbacks: String?): String {
        taggedTitle?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val names = fallbacks.mapNotNull(::normalizeFallback)
        return names.firstOrNull { !isNumericOnlyId(it) } ?: names.firstOrNull().orEmpty()
    }

    fun isNumericOnlyId(value: String): Boolean {
        val token = stem(value)
        return token.isNotEmpty() && token.all { it.isDigit() }
    }

    fun resolve(
        tags: EmbeddedTrackTags,
        mediaUri: String,
        providedTitle: String? = null,
        downloadTitle: String? = null,
        downloadFileName: String? = null,
        downloadArtwork: String? = null,
        spaceTitle: String? = null,
        spaceArtist: String? = null,
        spaceArtwork: String? = null,
        preservedArtwork: String? = null,
    ): PlayerMediaDisplay {
        val title = displayTitle(
            tags.title,
            spaceTitle,
            providedTitle,
            downloadTitle,
            downloadFileName,
            mediaUri,
        )
        val artist = tags.artist?.takeIf { it.isNotBlank() } ?: spaceArtist
        val artwork = preferredArtworkUrl(
            tags.artworkUri ?: preservedArtwork ?: downloadArtwork,
            spaceArtwork,
        )
        return PlayerMediaDisplay(
            title = title,
            artist = artist,
            album = tags.album,
            artworkUri = artwork,
            durationMs = tags.durationMs.coerceAtLeast(0L),
        )
    }

    private fun normalizeFallback(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val last = trimmed.substringAfterLast('/').substringBefore('?')
        val token = stem(last)
        return token.takeIf { it.isNotEmpty() } ?: last.takeIf { it.isNotEmpty() }
    }

    private fun stem(value: String): String {
        val last = value.substringAfterLast('/').substringBefore('?')
        return if (last.contains('.')) last.substringBeforeLast('.') else last
    }
}
