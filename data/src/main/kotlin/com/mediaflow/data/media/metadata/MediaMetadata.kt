package com.mediaflow.data.media.metadata

import com.mediaflow.core.model.XSpace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Normalized metadata container for tagging downloaded audio and video files.
 */
data class MediaMetadata(
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val description: String? = null,
    val date: String? = null,
) {
    val hasContent: Boolean
        get() = !title.isNullOrBlank() ||
            !artist.isNullOrBlank() ||
            !albumArtist.isNullOrBlank() ||
            !album.isNullOrBlank() ||
            !description.isNullOrBlank() ||
            !date.isNullOrBlank()

    companion object {
        /**
         * Creates a [MediaMetadata] instance mapped from an [XSpace] record.
         */
        fun fromXSpace(space: XSpace): MediaMetadata {
            val dateStr = space.startedAtMs?.let { ms ->
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
            } ?: space.createdAtMs?.let { ms ->
                SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))
            }

            val hostName = space.host.displayName.trim().ifBlank { space.host.cleanUsername }
            val hostHandle = space.host.formattedHandle.takeIf { it.isNotBlank() }

            val descriptionText = buildString {
                append("X Space ID: ").append(space.id)
                if (space.allSpeakers.isNotEmpty()) {
                    append("\nSpeakers: ").append(space.speakersSummary)
                }
                if (space.url.isNotBlank()) {
                    append("\nURL: ").append(space.url)
                }
            }

            return MediaMetadata(
                title = space.title.trim().ifBlank { null },
                artist = hostName.ifBlank { null },
                albumArtist = hostHandle,
                album = "X Spaces",
                description = descriptionText.trim().ifBlank { null },
                date = dateStr,
            )
        }
    }
}
