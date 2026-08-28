package com.mediaflow.core.model

/**
 * Model representing a user-defined media playlist.
 */
data class Playlist(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val mediaUris: List<String> = emptyList(),
) {
    val itemCount: Int
        get() = mediaUris.size
}
