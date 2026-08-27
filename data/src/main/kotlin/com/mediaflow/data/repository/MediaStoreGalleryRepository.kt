package com.mediaflow.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.mediaflow.core.model.DownloadItem
import com.mediaflow.core.model.MediaFormat
import com.mediaflow.core.model.MediaType
import com.mediaflow.domain.repository.GalleryRepository
import com.mediaflow.data.media.MediaFlowLibraryStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Reads the device media collections without inventing gallery items. */
class MediaStoreGalleryRepository(context: Context) : GalleryRepository {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val ownership = MediaFlowLibraryStore(appContext)
    private val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else MediaStore.Files.getContentUri("external")

    override fun observeGallery(): Flow<List<DownloadItem>> = callbackFlow {
        fun emitCurrent() = trySend(query()).isSuccess

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                emitCurrent()
            }
        }
        resolver.registerContentObserver(collection, true, observer)
        emitCurrent()
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    override suspend fun getItemById(id: String): DownloadItem? =
        query(uri = runCatching { Uri.parse(id) }.getOrNull()).firstOrNull()

    override suspend fun deleteItem(id: String): Boolean = runCatching {
        val uri = Uri.parse(id)
        val deleted = resolver.delete(uri, null, null) > 0
        if (deleted) ownership.remove(uri)
        deleted
    }.getOrDefault(false)

    override suspend fun renameItem(id: String, newName: String): DownloadItem? {
        val cleanName = newName.trim().replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "")
        if (cleanName.isBlank()) return null
        return runCatching {
            val changed = resolver.update(
                Uri.parse(id),
                ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, cleanName) },
                null,
                null,
            )
            if (changed > 0) getItemById(id) else null
        }.getOrNull()
    }

    override suspend fun getLocalUri(id: String): String? =
        getItemById(id)?.localUri

    private fun query(uri: Uri? = null): List<DownloadItem> = runCatching {
        if (uri != null) return@runCatching querySingle(uri)
        queryCollection(collection)
    }.getOrThrow()

    private fun querySingle(uri: Uri): List<DownloadItem> = runCatching {
        val requestedId = MediaFlowLibraryStore.mediaStoreId(uri.toString())
            ?: return@runCatching emptyList()
        if (requestedId !in ownedMediaStoreIds()) return@runCatching emptyList()
        queryCollection(collection).filter {
            MediaFlowLibraryStore.mediaStoreId(it.id.toString()) == requestedId
        }
    }.getOrDefault(emptyList())

    private fun queryCollection(collectionUri: Uri): List<DownloadItem> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        val selection: String
        val selectionArgs: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // The path narrows the MediaStore query; the ownership ledger is
            // the stronger check that excludes old/external files in it.
            val ownedIds = ownedMediaStoreIds()
            if (ownedIds.isEmpty()) return emptyList()
            selection = "(${MediaStore.MediaColumns.RELATIVE_PATH} = ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} = ?) AND ${MediaStore.MediaColumns.IS_PENDING} = 0 AND ${MediaStore.MediaColumns._ID} IN (${ownedIds.joinToString(",")})"
            selectionArgs = arrayOf("Movies/MediaFlow/", "Music/MediaFlow/")
        } else {
            // Pre-Q publishing is intentionally unsupported; there is no
            // trustworthy ownership marker to distinguish external files.
            return emptyList()
        }
        return resolver.query(
            collectionUri,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            buildList {
                val seenKeys = mutableSetOf<String>()
                val idIndex = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val dateIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val itemId = cursor.getLong(idIndex)
                    val mime = cursor.getString(mimeIndex).orEmpty()
                    if (!mime.startsWith("audio/") && !mime.startsWith("video/")) continue
                    val mediaType = if (mime.startsWith("audio/")) MediaType.AUDIO else MediaType.VIDEO
                    // Files.getContentUri(...) and the type-specific
                    // publisher URI have different path prefixes, although
                    // they refer to the same MediaStore row. Match ownership
                    // by the controlled row ID and retain the original URI
                    // from the ledger for opening/deleting the item.
                    if (itemId !in ownedMediaStoreIds()) continue
                    val itemUri = ownership.uris().firstOrNull {
                        MediaFlowLibraryStore.mediaStoreId(it) == itemId
                    }?.let(Uri::parse) ?: ContentUris.withAppendedId(collectionUri, itemId)
                    val name = cursor.getString(nameIndex).orEmpty()
                    val duplicateKey = "${normalizeCollisionName(name)}|$mime|${cursor.getLongOrNull(sizeIndex) ?: -1L}"
                    if (!seenKeys.add(duplicateKey)) continue
                    add(
                        DownloadItem(
                            id = itemUri.toString(),
                            sourceUrl = itemUri.toString(),
                            title = name,
                            fileName = name,
                            mediaType = mediaType,
                            selectedFormat = MediaFormat(
                                formatId = "mediastore:$itemId",
                                extension = name.substringAfterLast('.', "").ifBlank { null },
                                mimeType = mime.ifBlank { null },
                                mediaType = mediaType,
                                fileSize = cursor.getLongOrNull(sizeIndex),
                                isProgressive = true,
                            ),
                            localUri = itemUri.toString(),
                            createdAt = cursor.getLongOrNull(dateIndex)?.times(1000L) ?: 0L,
                            completedAt = cursor.getLongOrNull(dateIndex)?.times(1000L),
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    private fun android.database.Cursor.getLongOrNull(index: Int): Long? =
        if (index >= 0 && !isNull(index)) getLong(index) else null

    private fun ownedMediaStoreIds(): Set<Long> = ownership.uris()
        .mapNotNull(MediaFlowLibraryStore::mediaStoreId)
        .toSet()

    private fun normalizeCollisionName(name: String): String =
        name.replace(Regex(" \\([0-9]+\\)(?=\\.[^.]+$)"), "")

}
