package com.mediaflow.data.repository

import android.content.Context
import com.mediaflow.core.model.Playlist
import com.mediaflow.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Thread-safe persistent JSON repository for user playlists.
 */
class PlaylistRepositoryImpl(
    private val context: Context,
) : PlaylistRepository {

    private val mutex = Mutex()
    private val storageFile: File by lazy {
        File(context.filesDir, "playlists.json")
    }

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())

    init {
        loadFromDisk()
    }

    override fun observePlaylists(): Flow<List<Playlist>> = _playlists.asStateFlow()

    override suspend fun getPlaylist(id: String): Playlist? = mutex.withLock {
        _playlists.value.firstOrNull { it.id == id }
    }

    override suspend fun createPlaylist(name: String): Playlist = mutex.withLock {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "El nombre de la playlist no puede estar vacío" }

        val newPlaylist = Playlist(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            createdAt = System.currentTimeMillis(),
            mediaUris = emptyList(),
        )

        val updated = _playlists.value + newPlaylist
        _playlists.value = updated
        persistToDisk(updated)
        newPlaylist
    }

    override suspend fun renamePlaylist(id: String, newName: String) = mutex.withLock {
        val trimmed = newName.trim()
        require(trimmed.isNotBlank()) { "El nombre de la playlist no puede estar vacío" }

        val updated = _playlists.value.map { playlist ->
            if (playlist.id == id) playlist.copy(name = trimmed) else playlist
        }
        _playlists.value = updated
        persistToDisk(updated)
    }

    override suspend fun deletePlaylist(id: String) = mutex.withLock {
        val updated = _playlists.value.filter { it.id != id }
        _playlists.value = updated
        persistToDisk(updated)
    }

    override suspend fun addMediaToPlaylist(playlistId: String, mediaUri: String) = mutex.withLock {
        val updated = _playlists.value.map { playlist ->
            if (playlist.id == playlistId && !playlist.mediaUris.contains(mediaUri)) {
                playlist.copy(mediaUris = playlist.mediaUris + mediaUri)
            } else {
                playlist
            }
        }
        _playlists.value = updated
        persistToDisk(updated)
    }

    override suspend fun removeMediaFromPlaylist(playlistId: String, mediaUri: String) = mutex.withLock {
        val updated = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(mediaUris = playlist.mediaUris.filter { it != mediaUri })
            } else {
                playlist
            }
        }
        _playlists.value = updated
        persistToDisk(updated)
    }

    override suspend fun isMediaInPlaylist(playlistId: String, mediaUri: String): Boolean = mutex.withLock {
        _playlists.value.firstOrNull { it.id == playlistId }?.mediaUris?.contains(mediaUri) == true
    }

    private fun loadFromDisk() {
        runCatching {
            if (!storageFile.exists()) return
            val jsonText = storageFile.readText()
            if (jsonText.isBlank()) return
            val array = JSONArray(jsonText)
            val list = mutableListOf<Playlist>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val mediaArray = obj.optJSONArray("mediaUris") ?: JSONArray()
                val mediaUris = mutableListOf<String>()
                for (j in 0 until mediaArray.length()) {
                    mediaUris.add(mediaArray.getString(j))
                }
                list.add(Playlist(id = id, name = name, createdAt = createdAt, mediaUris = mediaUris))
            }
            _playlists.value = list
        }
    }

    private fun persistToDisk(list: List<Playlist>) {
        runCatching {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("createdAt", item.createdAt)
                    val mediaArray = JSONArray()
                    for (uri in item.mediaUris) {
                        mediaArray.put(uri)
                    }
                    put("mediaUris", mediaArray)
                }
                array.put(obj)
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
