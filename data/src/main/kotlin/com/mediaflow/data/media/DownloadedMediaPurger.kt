package com.mediaflow.data.media

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.mediaflow.core.model.DownloadItem
import java.io.File

/** Deletes a downloaded file, its MediaStore row, and the private ownership ledger entry. */
object DownloadedMediaPurger {

    fun purge(context: Context, item: DownloadItem) {
        val app = context.applicationContext
        val resolver = app.contentResolver
        val ownership = MediaFlowLibraryStore(app)
        val uris = buildSet {
            item.localUri?.takeIf { it.isNotBlank() }?.let(::add)
            if (item.id.contains("://")) add(item.id)
        }
        uris.forEach { raw ->
            val uri = runCatching { raw.toUri() }.getOrNull() ?: return@forEach
            when (uri.scheme) {
                "content" -> {
                    runCatching { resolver.delete(uri, null, null) }
                    resolver.notifyChange(uri, null)
                }
                "file" -> uri.path?.let { File(it).takeIf { file -> file.isFile }?.delete() }
            }
            ownership.removeMatching(uri)
        }
        item.fileName?.takeIf { it.isNotBlank() }?.let { name ->
            File(File(app.filesDir, "downloads"), name).takeIf { it.isFile }?.delete()
        }
    }
}
