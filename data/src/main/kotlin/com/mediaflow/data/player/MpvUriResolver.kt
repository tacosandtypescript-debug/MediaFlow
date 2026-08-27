package com.mediaflow.data.player

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Resolved source descriptor ready to be loaded by libmpv.
 */
data class ResolvedSource(
    val path: String,
    val parcelFileDescriptor: ParcelFileDescriptor? = null,
) : AutoCloseable {
    override fun close() {
        runCatching { parcelFileDescriptor?.close() }
    }
}

/**
 * Resolves application media URIs (content://, file://, raw paths, network URLs)
 * into a format suitable for libmpv commands (e.g. fd://<fd>, direct file paths, or URLs).
 */
class MpvUriResolver(private val context: Context) {

    /**
     * Resolves a media URI string into a [ResolvedSource].
     * Throws [FileNotFoundException] if the target media is missing or inaccessible.
     */
    fun resolve(sourceUri: String): ResolvedSource {
        val trimmed = sourceUri.trim()
        if (trimmed.isBlank()) {
            throw FileNotFoundException("Media URI is blank")
        }

        // 1. Content URI (MediaStore / FileProvider / ContentProvider)
        if (trimmed.startsWith("content://", ignoreCase = true)) {
            val uri = Uri.parse(trimmed)
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw FileNotFoundException("Unable to open content descriptor for $sourceUri")
            val fd = pfd.fd
            if (fd < 0) {
                pfd.close()
                throw FileNotFoundException("Invalid file descriptor for $sourceUri")
            }
            return ResolvedSource(path = "fd://$fd", parcelFileDescriptor = pfd)
        }

        // 2. File URI
        if (trimmed.startsWith("file://", ignoreCase = true)) {
            val uri = Uri.parse(trimmed)
            val path = uri.path ?: throw FileNotFoundException("Invalid file URI path: $sourceUri")
            val file = File(path)
            if (!file.exists() || !file.canRead()) {
                throw FileNotFoundException("File does not exist or is not readable: $path")
            }
            return ResolvedSource(path = file.absolutePath)
        }

        // 3. Network URL
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return ResolvedSource(path = trimmed)
        }

        // 4. Raw file path or relative path
        val file = File(trimmed)
        val isPathLike = file.isAbsolute || trimmed.startsWith("/") || trimmed.startsWith("\\") || (trimmed.length > 2 && trimmed[1] == ':')
        if (isPathLike) {
            if (!file.exists() || !file.canRead()) {
                throw FileNotFoundException("File does not exist or is not readable: ${file.absolutePath}")
            }
            return ResolvedSource(path = file.absolutePath)
        }

        if (file.exists() && file.canRead()) {
            return ResolvedSource(path = file.absolutePath)
        }

        // Fallback for custom protocols or pseudo-paths
        return ResolvedSource(path = trimmed)
    }
}
