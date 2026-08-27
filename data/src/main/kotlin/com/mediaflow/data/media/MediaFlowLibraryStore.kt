package com.mediaflow.data.media

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import java.io.File

/**
 * Private ownership ledger for media published by this application.
 *
 * A relative path is only a namespace, not proof of ownership: users or
 * another installation may already have files in that folder. The gallery
 * therefore accepts a MediaStore URI only after the publisher records it in
 * this app-private ledger.
 */
class MediaFlowLibraryStore private constructor(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "mediaflow_library.json"))

    internal constructor(file: File, marker: Unit = Unit) : this(file)

    @Synchronized
    fun uris(): Set<String> = runCatching {
        val array = JSONArray(file.takeIf { it.isFile }?.readText().orEmpty())
        buildSet {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }.getOrDefault(emptySet())

    @Synchronized
    fun add(uri: Uri) {
        val values = (uris() + uri.toString()).sorted()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.parentFile?.mkdirs()
        temp.writeText(JSONArray(values).toString())
        temp.copyTo(file, overwrite = true)
        temp.delete()
    }

    @Synchronized
    fun remove(uri: Uri) {
        val values = uris() - uri.toString()
        if (values.isEmpty()) {
            file.delete()
        } else {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(JSONArray(values.sorted()).toString())
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
    }

    companion object {
        fun mediaStoreId(uriString: String): Long? = runCatching {
            val uri = Uri.parse(uriString)
            if (uri.scheme != "content") return@runCatching null
            uri.lastPathSegment?.toLongOrNull()
        }.getOrNull()
    }
}
