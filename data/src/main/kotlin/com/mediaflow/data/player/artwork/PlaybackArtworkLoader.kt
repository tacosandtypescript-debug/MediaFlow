package com.mediaflow.data.player.artwork

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.net.URL

/** Loads album art for MediaSession, notification, and widget from http, file, or content. */
object PlaybackArtworkLoader {
    fun load(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        val trimmed = url.trim()
        return runCatching {
            when {
                trimmed.startsWith("http://", ignoreCase = true) ||
                    trimmed.startsWith("https://", ignoreCase = true) -> {
                    URL(trimmed).openStream().use { BitmapFactory.decodeStream(it) }
                }
                trimmed.startsWith("content://", ignoreCase = true) -> {
                    context.contentResolver.openInputStream(Uri.parse(trimmed))?.use {
                        BitmapFactory.decodeStream(it)
                    }
                }
                trimmed.startsWith("file:", ignoreCase = true) -> {
                    val path = Uri.parse(trimmed).path ?: return null
                    decodeFile(path)
                }
                trimmed.startsWith("/") -> decodeFile(trimmed)
                else -> decodeFile(trimmed)
            }
        }.getOrNull()?.takeIf { it.width > 1 && it.height > 1 }
    }

    private fun decodeFile(path: String): Bitmap? {
        val file = File(path)
        if (!file.isFile || file.length() == 0L) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }
}
