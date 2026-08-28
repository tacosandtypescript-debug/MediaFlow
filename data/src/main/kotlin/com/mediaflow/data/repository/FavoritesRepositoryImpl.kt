package com.mediaflow.data.repository

import android.content.Context
import com.mediaflow.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File

/**
 * Thread-safe persistent JSON repository for user favorite media URIs.
 */
class FavoritesRepositoryImpl(
    private val context: Context,
) : FavoritesRepository {

    private val mutex = Mutex()
    private val storageFile: File by lazy {
        File(context.filesDir, "favorites.json")
    }

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())

    init {
        loadFromDisk()
    }

    override fun observeFavoriteMediaUris(): Flow<Set<String>> = _favorites.asStateFlow()

    override suspend fun isFavorite(mediaUri: String): Boolean = mutex.withLock {
        _favorites.value.contains(mediaUri)
    }

    override suspend fun toggleFavorite(mediaUri: String): Boolean = mutex.withLock {
        val current = _favorites.value
        val isFav = current.contains(mediaUri)
        val updated = if (isFav) current - mediaUri else current + mediaUri
        _favorites.value = updated
        persistToDisk(updated)
        !isFav
    }

    override suspend fun setFavorite(mediaUri: String, isFavorite: Boolean) = mutex.withLock {
        val current = _favorites.value
        val updated = if (isFavorite) current + mediaUri else current - mediaUri
        _favorites.value = updated
        persistToDisk(updated)
    }

    private fun loadFromDisk() {
        runCatching {
            if (!storageFile.exists()) return
            val jsonText = storageFile.readText()
            if (jsonText.isBlank()) return
            val array = JSONArray(jsonText)
            val set = mutableSetOf<String>()
            for (i in 0 until array.length()) {
                set.add(array.getString(i))
            }
            _favorites.value = set
        }
    }

    private fun persistToDisk(set: Set<String>) {
        runCatching {
            val array = JSONArray()
            for (item in set) {
                array.put(item)
            }
            val tmp = File(storageFile.parentFile, "${storageFile.name}.tmp")
            tmp.writeText(array.toString(2))
            if (tmp.renameTo(storageFile)) {
                // Success
            } else {
                storageFile.delete()
                tmp.renameTo(storageFile)
            }
        }
    }
}
