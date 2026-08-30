package com.mediaflow.data.media

import android.content.ContentValues
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

/** Publishes completed private downloads into the user's shared media library. */
object MediaStorePublisher {
    fun publishIfMissing(context: Context, file: File, mimeType: String, displayName: String): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.applicationContext.contentResolver
            val collection = if (mimeType.startsWith("video/")) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val relativePath = if (mimeType.startsWith("video/")) "Movies/MediaFlow/" else "Music/MediaFlow/"
            val owned = MediaFlowLibraryStore(context).uris()

            // A shared relative path is not proof of ownership. Reuse only
            // rows owned by this package and never claim external media.
            val exact = runCatching {
                resolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE),
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?",
                    arrayOf(displayName, relativePath, context.packageName),
                    null,
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) {
                        null
                    } else {
                        val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                        val existingSize = cursor.getLong(1)
                        if (existingSize == file.length()) {
                            uri
                        } else {
                            // A previous attempt can leave an owned row with
                            // the same name but different bytes. Reusing that
                            // row would expose stale or unplayable media.
                            resolver.delete(uri, null, null)
                            MediaFlowLibraryStore(context).remove(uri)
                            null
                        }
                    }
                }
            }.getOrNull()
            if (exact != null) return exact

            // If MediaStore added " (1)" because an external row already has
            // the requested name, reuse an already-owned row with the same
            // content size and normalized collision name instead of creating
            // another physical duplicate.
            val normalized = normalizeCollisionName(displayName)
            val ownedMatch = owned.firstNotNullOfOrNull { rawUri ->
                val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return@firstNotNullOfOrNull null
                runCatching {
                    resolver.query(
                        uri,
                        arrayOf(
                            MediaStore.MediaColumns.DISPLAY_NAME,
                            MediaStore.MediaColumns.MIME_TYPE,
                            MediaStore.MediaColumns.SIZE,
                            MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
                        ),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (!cursor.moveToFirst()) return@use null
                        val name = cursor.getString(0).orEmpty()
                        val type = cursor.getString(1).orEmpty()
                        val size = cursor.getLong(2)
                        val owner = cursor.getString(3)
                        if (owner == context.packageName && type == mimeType && size == file.length() && normalizeCollisionName(name) == normalized) uri else null
                    }
                }.getOrNull()
            }
            if (ownedMatch != null) return ownedMatch
        }
        return publish(context, file, mimeType, displayName)
    }

    fun publish(context: Context, file: File, mimeType: String, displayName: String): Uri? {
        if (!file.exists() || file.length() == 0L || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null
        }
        val resolver = context.applicationContext.contentResolver
        val isVideo = mimeType.startsWith("video/")
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (isVideo) "Movies/MediaFlow" else "Music/MediaFlow",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: return null
        return runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("No se pudo abrir el archivo publicado")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            uri
        }.getOrElse {
            resolver.delete(uri, null, null)
            null
        }
    }

    private fun normalizeCollisionName(name: String): String =
        name.replace(Regex(" \\([0-9]+\\)(?=\\.[^.]+$)"), "")
}
