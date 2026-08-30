package com.mediaflow.data.media

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class VideoFrameInfo(
    val artworkUri: String?,
    val width: Int?,
    val height: Int?,
    val durationMs: Long,
    val thumbnailUri: String? = artworkUri,
)

/**
 * Pulls a cached still frame from a local video for library mosaics.
 */
object VideoFrameThumbnail {
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")

    fun cacheFile(cacheDir: File, uriString: String): File {
        val dir = File(cacheDir, "video_thumbs").apply { mkdirs() }
        return File(dir, "${cacheKey(uriString)}.jpg")
    }

    fun cacheFileName(uriString: String): String = "${cacheKey(uriString)}.jpg"

    fun needsFrame(thumbnailUri: String?): Boolean {
        if (thumbnailUri.isNullOrBlank()) return true
        val lower = thumbnailUri.trim().lowercase()
        val extension = lower
            .substringAfterLast('/', lower)
            .substringAfterLast('.', "")
            .substringBefore('?')
            .substringBefore('#')
        return extension !in IMAGE_EXTENSIONS
    }

    fun ensure(context: Context, videoFile: File): VideoFrameInfo =
        extract(context, Uri.fromFile(videoFile).toString())

    fun cachedUri(context: Context, uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        val cached = cacheFile(context.cacheDir, uriString)
        return cached.takeIf { it.isFile && it.length() > 0L }?.let { Uri.fromFile(it).toString() }
    }

    fun cacheKey(uriString: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(uriString.trim().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(24)

    fun extract(context: Context, uriString: String): VideoFrameInfo {
        if (uriString.isBlank()) return VideoFrameInfo(null, null, null, 0L)
        val cached = cacheFile(context.cacheDir, uriString)
        if (!cached.isFile || cached.length() == 0L) {
            writeStoreThumbnail(context, uriString, cached)
        }
        val retriever = MediaMetadataRetriever()
        return try {
            setSource(context, retriever, uriString)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (!cached.isFile || cached.length() == 0L) {
                val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(2_000_000L, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.frameAtTime
                writeJpeg(cached, frame)
            }
            cachedArtwork(cached, width, height, durationMs)
        } catch (_: Exception) {
            cachedArtwork(cached, null, null, 0L)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun writeStoreThumbnail(context: Context, uriString: String, dest: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val uri = Uri.parse(uriString)
        if (uri.scheme?.equals("content", ignoreCase = true) != true) return
        runCatching {
            val bitmap = context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
            writeJpeg(dest, bitmap)
        }
    }

    private fun writeJpeg(dest: File, frame: Bitmap?) {
        if (frame == null || frame.width <= 2 || frame.height <= 2) return
        FileOutputStream(dest).use { out ->
            frame.compress(Bitmap.CompressFormat.JPEG, 82, out)
        }
        if (!frame.isRecycled) frame.recycle()
    }

    private fun cachedArtwork(
        cached: File,
        width: Int?,
        height: Int?,
        durationMs: Long,
    ): VideoFrameInfo {
        val art = cached.takeIf { it.isFile && it.length() > 0L }?.let { Uri.fromFile(it).toString() }
        return VideoFrameInfo(art, width, height, durationMs)
    }

    private fun setSource(context: Context, retriever: MediaMetadataRetriever, uriString: String) {
        val uri = Uri.parse(uriString)
        when (uri.scheme?.lowercase()) {
            "content" -> retriever.setDataSource(context, uri)
            "file" -> retriever.setDataSource(uri.path ?: uriString)
            else -> {
                val file = File(uriString)
                if (file.isFile) retriever.setDataSource(file.absolutePath)
                else retriever.setDataSource(context, uri)
            }
        }
    }
}
