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

    fun existingUri(context: Context, downloadId: String): String? =
        existingFile(context, downloadId)?.let(::fileUri)

    private fun existingFile(context: Context, downloadId: String): File? {
        val dir = thumbsDir(context)
        return dir.listFiles().orEmpty()
            .filter { file ->
                file.isFile &&
                    file.length() > 0L &&
                    file.nameWithoutExtension == downloadId &&
                    file.extension.lowercase() in IMAGE_EXTENSIONS
            }
            .maxByOrNull { it.length() }
    }

    fun persist(context: Context, downloadId: String, remoteUrl: String?): String? {
        val existing = existingFile(context, downloadId)
        if (existing != null && existing.length() >= MIN_KEEP_BYTES) {
            return fileUri(existing)
        }
        val urls = ArtworkUrlQuality.candidates(remoteUrl).filter(::isPersistableImageUrl)
        if (urls.isEmpty()) return existing?.let(::fileUri)
        for (url in urls) {
            val extension = extensionFromUrl(url)
            val dest = localFile(context, downloadId, extension)
            val saved = runCatching {
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
            if (saved != null) return saved
        }
        return existing?.let(::fileUri)
    }

    /**
     * Moves a yt-dlp sidecar image next to [mediaFile] into filesDir/thumbs
     * and deletes the extra file so finish() cannot treat it as media.
     */
    fun harvestNearby(context: Context, downloadId: String, mediaFile: File): String? {
        val sidecar = findSidecar(mediaFile.parentFile, mediaFile.nameWithoutExtension)
        val existing = existingFile(context, downloadId)
        if (sidecar != null && (existing == null || sidecar.length() > existing.length())) {
            return copySidecar(context, downloadId, sidecar)
        }
        deleteSidecars(mediaFile.parentFile, mediaFile.nameWithoutExtension)
        return existing?.let(::fileUri)
    }

    fun harvestFromDirectory(
        context: Context,
        downloadId: String,
        directory: File,
        expectedBaseName: String? = null,
    ): String? {
        val images = directory.listFiles().orEmpty().filter { isImageFile(it) }
        val sidecar = images
            .filter { file ->
                expectedBaseName == null ||
                    file.nameWithoutExtension == expectedBaseName ||
                    file.nameWithoutExtension.startsWith(expectedBaseName)
            }
            .maxByOrNull { it.length() }
            ?: images.maxByOrNull { it.length() }
        val existing = existingFile(context, downloadId)
        if (sidecar != null && (existing == null || sidecar.length() > existing.length())) {
            return copySidecar(context, downloadId, sidecar)
        }
        deleteImageFiles(directory, expectedBaseName)
        return existing?.let(::fileUri)
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

    private const val MIN_KEEP_BYTES = 40_960L
}
