package com.mediaflow.domain.repository

import com.mediaflow.core.model.Playlist
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing user playlists.
 */
interface PlaylistRepository {
    fun observePlaylists(): Flow<List<Playlist>>
    suspend fun getPlaylist(id: String): Playlist?
    suspend fun createPlaylist(name: String): Playlist
    suspend fun renamePlaylist(id: String, newName: String)
    suspend fun deletePlaylist(id: String)
    suspend fun addMediaToPlaylist(playlistId: String, mediaUri: String)
    suspend fun removeMediaFromPlaylist(playlistId: String, mediaUri: String)
    suspend fun isMediaInPlaylist(playlistId: String, mediaUri: String): Boolean
}
