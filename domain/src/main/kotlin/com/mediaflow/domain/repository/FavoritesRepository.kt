package com.mediaflow.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for managing user favorite audio items.
 */
interface FavoritesRepository {
    fun observeFavoriteMediaUris(): Flow<Set<String>>
    suspend fun isFavorite(mediaUri: String): Boolean
    suspend fun toggleFavorite(mediaUri: String): Boolean
    suspend fun setFavorite(mediaUri: String, isFavorite: Boolean)
}
