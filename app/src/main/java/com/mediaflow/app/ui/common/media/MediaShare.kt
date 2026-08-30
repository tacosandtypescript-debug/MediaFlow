package com.mediaflow.app.ui.common.media

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.mediaflow.app.R
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaType
import java.io.File

/**
 * Opens the system share sheet so a downloaded video or audio file can be sent
 * to WhatsApp, Telegram, Drive, or any other app that accepts the file.
 */
object MediaShare {
    fun share(context: Context, item: DownloadItem): Boolean {
        val uri = item.localUri ?: return fail(context)
        return share(
            context = context,
            uriString = uri,
            mimeType = item.selectedFormat?.mimeType,
            title = item.title ?: item.fileName,
            isAudio = item.mediaType == MediaType.AUDIO,
        )
    }

    fun share(
        context: Context,
        uriString: String,
        mimeType: String? = null,
        title: String? = null,
        isAudio: Boolean = false,
    ): Boolean {
        if (uriString.isBlank() || isRemoteStream(uriString)) return fail(context)
        val shareUri = resolveShareableUri(context, uriString) ?: return fail(context)
        val mime = mimeType?.takeIf { it.isNotBlank() && it != "*/*" }
            ?: context.contentResolver.getType(shareUri)
            ?: mimeFromName(uriString, isAudio)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, shareUri)
            title?.takeIf { it.isNotBlank() }?.let { label ->
                putExtra(Intent.EXTRA_SUBJECT, label)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, title ?: "MediaFlow", shareUri)
        }
        val chooser = Intent.createChooser(
            send,
            context.getString(
                if (isAudio || mime.startsWith("audio/")) {
                    R.string.share_audio
                } else {
                    R.string.share_video
                },
            ),
        ).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrElse { fail(context) }
    }

    fun shareMultiple(context: Context, uris: List<String>, isAudio: Boolean): Boolean {
        val unique = uris.map { it.trim() }.filter { it.isNotBlank() && !isRemoteStream(it) }.distinct()
        if (unique.isEmpty()) return fail(context)
        if (unique.size == 1) {
            return share(context, unique.first(), isAudio = isAudio)
        }
        val shareUris = unique.mapNotNull { resolveShareableUri(context, it) }
        if (shareUris.isEmpty()) return fail(context)
        val mime = if (isAudio) "audio/*" else "*/*"
        val streamList = ArrayList(shareUris)
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mime
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, streamList)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(
                context.contentResolver,
                "MediaFlow",
                shareUris.first(),
            ).also { clip ->
                shareUris.drop(1).forEach { uri ->
                    clip.addItem(ClipData.Item(uri))
                }
            }
        }
        val chooser = Intent.createChooser(
            send,
            context.getString(if (isAudio) R.string.share_audio else R.string.share_video),
        ).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(chooser)
            true
        }.getOrElse { fail(context) }
    }

    internal fun isRemoteStream(uriString: String): Boolean {
        val value = uriString.trim()
        return value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("rtmp://", ignoreCase = true)
    }

    internal fun mimeFromName(uriString: String, isAudio: Boolean): String {
        val name = uriString.substringAfterLast('/').substringBefore('?').lowercase()
        return when {
            name.endsWith(".mp3") -> "audio/mpeg"
            name.endsWith(".m4a") -> "audio/mp4"
            name.endsWith(".aac") -> "audio/aac"
            name.endsWith(".opus") || name.endsWith(".ogg") -> "audio/ogg"
            name.endsWith(".wav") -> "audio/wav"
            name.endsWith(".webm") && isAudio -> "audio/webm"
            name.endsWith(".webm") -> "video/webm"
            name.endsWith(".mkv") -> "video/x-matroska"
            name.endsWith(".m4v") -> "video/mp4"
            name.endsWith(".mp4") || name.endsWith(".mov") -> "video/mp4"
            isAudio -> "audio/*"
            else -> "video/*"
        }
    }

    internal fun resolveShareableUri(context: Context, uriString: String): Uri? {
        val trimmed = uriString.trim()
        if (trimmed.startsWith("content://", ignoreCase = true)) {
            return trimmed.toUri()
        }
        val file = fileFrom(context, trimmed) ?: return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    private fun fileFrom(context: Context, uriString: String): File? {
        val path = if (uriString.startsWith("file://", ignoreCase = true)) {
            uriString.toUri().path
        } else {
            uriString
        } ?: return null
        val direct = File(path)
        if (direct.isFile) return direct
        val downloads = File(context.filesDir, "downloads/${direct.name}")
        return downloads.takeIf { it.isFile }
    }

    private fun fail(context: Context): Boolean {
        Toast.makeText(context, R.string.share_failed, Toast.LENGTH_SHORT).show()
        return false
    }
}
