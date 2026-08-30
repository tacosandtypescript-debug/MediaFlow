package com.mediaflow.data.download

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Copies a remote or sidecar thumbnail into filesDir/thumbs without cookies. */
internal object ThumbnailPersister {
    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif")

    fun thumbsDir(context: Context): File =
        File(context.filesDir, "thumbs").apply { mkdirs() }

    fun localFile(context: Context, downloadId: String, extension: String = "jpg"): File =
        File(thumbsDir(context), "$downloadId.${normalizeExtension(extension)}")

    fun existingUri(context: Context, downloadId: String): String? {
        val dir = thumbsDir(context)
        val match = dir.listFiles().orEmpty().firstOrNull { file ->
            file.isFile &&
                file.length() > 0L &&
                file.nameWithoutExtension == downloadId &&
                file.extension.lowercase() in IMAGE_EXTENSIONS
        }
        return match?.let(::fileUri)
    }

    fun persist(context: Context, downloadId: String, remoteUrl: String?): String? {
        existingUri(context, downloadId)?.let { return it }
        val url = remoteUrl?.takeIf(::isPersistableImageUrl) ?: return null
        val extension = extensionFromUrl(url)
        val dest = localFile(context, downloadId, extension)
        return runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.139 Safari/537.36",
            )
            check(connection.responseCode in 200..299) {
                "Miniatura HTTP ${connection.responseCode}"
            }
            connection.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            check(dest.length() > 0L) { "Miniatura vacía" }
            fileUri(dest)
        }.getOrElse {
            dest.delete()
            null
        }
    }

    /**
     * Moves a yt-dlp sidecar image next to [mediaFile] into filesDir/thumbs
     * and deletes the extra file so finish() cannot treat it as media.
     */
    fun harvestNearby(context: Context, downloadId: String, mediaFile: File): String? {
        existingUri(context, downloadId)?.let { existing ->
            deleteSidecars(mediaFile.parentFile, mediaFile.nameWithoutExtension)
            return existing
        }
        val sidecar = findSidecar(mediaFile.parentFile, mediaFile.nameWithoutExtension) ?: return null
        return copySidecar(context, downloadId, sidecar)
    }

    fun harvestFromDirectory(
        context: Context,
        downloadId: String,
        directory: File,
        expectedBaseName: String? = null,
    ): String? {
        existingUri(context, downloadId)?.let { existing ->
            deleteImageFiles(directory, expectedBaseName)
            return existing
        }
        val images = directory.listFiles().orEmpty().filter { isImageFile(it) }
        val sidecar = expectedBaseName?.let { base ->
            images.firstOrNull { it.nameWithoutExtension == base || it.nameWithoutExtension.startsWith(base) }
        } ?: images.maxByOrNull { it.lastModified() }
        return sidecar?.let { copySidecar(context, downloadId, it) }
    }

    private fun copySidecar(context: Context, downloadId: String, sidecar: File): String? {
        val dest = localFile(context, downloadId, sidecar.extension)
        return runCatching {
            sidecar.copyTo(dest, overwrite = true)
            check(dest.length() > 0L) { "Miniatura vacía" }
            sidecar.delete()
            fileUri(dest)
        }.getOrElse {
            dest.delete()
            null
        }
    }

    private fun findSidecar(directory: File?, baseName: String): File? {
        if (directory == null || baseName.isBlank()) return null
        return directory.listFiles().orEmpty().firstOrNull { file ->
            isImageFile(file) &&
                (file.nameWithoutExtension == baseName || file.nameWithoutExtension.startsWith(baseName))
        }
    }

    private fun deleteSidecars(directory: File?, baseName: String) {
        if (directory == null) return
        directory.listFiles().orEmpty()
            .filter { isImageFile(it) && (it.nameWithoutExtension == baseName || it.nameWithoutExtension.startsWith(baseName)) }
            .forEach { it.delete() }
    }

    private fun deleteImageFiles(directory: File, expectedBaseName: String?) {
        directory.listFiles().orEmpty()
            .filter { file ->
                isImageFile(file) &&
                    (expectedBaseName == null ||
                        file.nameWithoutExtension == expectedBaseName ||
                        file.nameWithoutExtension.startsWith(expectedBaseName))
            }
            .forEach { it.delete() }
    }

    private fun isImageFile(file: File): Boolean =
        file.isFile && file.length() > 0L && file.extension.lowercase() in IMAGE_EXTENSIONS

    fun isPersistableImageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.trim().lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        val extension = lower
            .substringAfterLast('/', lower)
            .substringAfterLast('.', "")
            .substringBefore('?')
            .substringBefore('#')
        if (extension in setOf("mp4", "m4a", "mp3", "webm", "mkv", "m3u8", "m3u", "aac", "opus")) return false
        return extension.isEmpty() || extension in IMAGE_EXTENSIONS ||
            !lower.substringAfterLast('/').contains('.')
    }

    private fun extensionFromUrl(url: String): String {
        val raw = url.substringAfterLast('/')
            .substringAfterLast('.', "jpg")
            .substringBefore('?')
            .substringBefore('#')
            .lowercase()
        return normalizeExtension(raw)
    }

    private fun normalizeExtension(extension: String): String {
        val lower = extension.lowercase()
        return if (lower in IMAGE_EXTENSIONS) lower else "jpg"
    }

    /** Coil and isLoadableArtworkUrl need file:/// (three slashes), not File.toURI()'s file:/. */
    private fun fileUri(file: File): String = Uri.fromFile(file).toString()
}
