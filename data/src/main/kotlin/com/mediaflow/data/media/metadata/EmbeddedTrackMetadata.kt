package com.mediaflow.data.media.metadata

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

data class EmbeddedTrackTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
    val artworkUri: String? = null,
)

/**
 * Reads ID3 / MP4 tags and embedded album art from a local audio/video file.
 */
object EmbeddedTrackMetadata {
    fun read(context: Context, uriString: String): EmbeddedTrackTags {
        val fromFile = readRetriever(context, uriString)
        val fromStore = readMediaStore(context, uriString)
        return EmbeddedTrackTags(
            title = fromFile.title ?: fromStore.title,
            artist = fromFile.artist ?: fromStore.artist,
            album = fromFile.album ?: fromStore.album,
            durationMs = fromFile.durationMs.takeIf { it > 0L } ?: fromStore.durationMs,
            artworkUri = fromFile.artworkUri,
        )
    }

    private fun readRetriever(context: Context, uriString: String): EmbeddedTrackTags {
        val retriever = MediaMetadataRetriever()
        return try {
            setDataSource(context, retriever, uriString)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(0L)
                ?: 0L
            val artworkUri = persistEmbeddedArt(context, uriString, retriever.embeddedPicture)
            EmbeddedTrackTags(
                title = title?.trim()?.takeIf { it.isNotEmpty() },
                artist = artist?.trim()?.takeIf { it.isNotEmpty() },
                album = album?.trim()?.takeIf { it.isNotEmpty() },
                durationMs = durationMs,
                artworkUri = artworkUri,
            )
        } catch (_: Exception) {
            EmbeddedTrackTags()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readMediaStore(context: Context, uriString: String): EmbeddedTrackTags {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return EmbeddedTrackTags()
        if (uri.scheme?.equals("content", ignoreCase = true) != true) return EmbeddedTrackTags()
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.MediaColumns.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use EmbeddedTrackTags()
                fun column(name: String): String? {
                    val index = cursor.getColumnIndex(name)
                    if (index < 0 || cursor.isNull(index)) return null
                    return cursor.getString(index)?.trim()?.takeIf { it.isNotEmpty() }
                }
                val duration = listOf(MediaStore.MediaColumns.DURATION, MediaStore.Audio.Media.DURATION)
                    .firstNotNullOfOrNull { name ->
                        val index = cursor.getColumnIndex(name)
                        if (index < 0 || cursor.isNull(index)) null
                        else cursor.getLong(index).takeIf { it > 0L }
                    } ?: 0L
                EmbeddedTrackTags(
                    title = column(MediaStore.MediaColumns.TITLE),
                    artist = column(MediaStore.Audio.Media.ARTIST),
                    album = column(MediaStore.Audio.Media.ALBUM),
                    durationMs = duration,
                )
            }
        }.getOrNull() ?: EmbeddedTrackTags()
    }

    private fun setDataSource(context: Context, retriever: MediaMetadataRetriever, uriString: String) {
        val uri = Uri.parse(uriString)
        when (uri.scheme?.lowercase()) {
            "content", "android.resource" -> retriever.setDataSource(context, uri)
            "file" -> retriever.setDataSource(uri.path ?: uriString)
            else -> {
                val asFile = File(uriString)
                if (asFile.isFile) retriever.setDataSource(asFile.absolutePath)
                else retriever.setDataSource(context, uri)
            }
        }
    }

    private fun persistEmbeddedArt(context: Context, uriString: String, picture: ByteArray?): String? {
        if (picture == null || picture.isEmpty()) return null
        return runCatching {
            val dir = File(context.filesDir, "embedded_art").apply { mkdirs() }
            val name = "${uriString.hashCode().toUInt().toString(16)}.jpg"
            val outFile = File(dir, name)
            if (!outFile.isFile || outFile.length() == 0L) {
                FileOutputStream(outFile).use { it.write(picture) }
            }
            if (outFile.isFile && outFile.length() > 0L) Uri.fromFile(outFile).toString() else null
        }.getOrNull()
    }
}
