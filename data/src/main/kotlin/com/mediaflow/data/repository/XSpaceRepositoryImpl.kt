package com.mediaflow.data.repository

import android.content.Context
import com.mediaflow.core.model.XSpace
import com.mediaflow.data.provider.x.spaces.XSpaceStore
import com.mediaflow.domain.repository.XSpaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Production implementation of [XSpaceRepository] backed by [XSpaceStore] and in-memory cache.
 */
class XSpaceRepositoryImpl(
    context: Context,
    private val store: XSpaceStore = XSpaceStore(context.applicationContext),
    coroutineScope: CoroutineScope? = null,
) : XSpaceRepository {

    private val scope = coroutineScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _spaces = MutableStateFlow<Map<String, XSpace>>(emptyMap())
    val spaces: StateFlow<Map<String, XSpace>> = _spaces.asStateFlow()

    private val mediaToSpaceMap = mutableMapOf<String, String>()

    private val initJob = scope.async {
        val loaded = store.loadAll()
        mutex.withLock {
            _spaces.value = loaded
            loaded.values.forEach { space ->
                mediaToSpaceMap[space.id] = space.id
                mediaToSpaceMap[space.url] = space.id
                space.audioStreamUrl?.let { mediaToSpaceMap[it] = space.id }
            }
        }
        loaded
    }

    override suspend fun saveSpace(space: XSpace, mediaId: String?) {
        initJob.await()
        mutex.withLock {
            val updated = _spaces.value.toMutableMap()
            updated[space.id] = space
            _spaces.value = updated

            mediaToSpaceMap[space.id] = space.id
            mediaToSpaceMap[space.url] = space.id
            space.audioStreamUrl?.let { mediaToSpaceMap[it] = space.id }
            if (mediaId != null) {
                mediaToSpaceMap[mediaId] = space.id
            }

            store.saveAll(updated)
        }
    }

    override suspend fun getSpace(spaceId: String): XSpace? {
        initJob.await()
        return _spaces.value[spaceId]
    }

    override suspend fun getSpaceForMedia(mediaId: String): XSpace? {
        initJob.await()
        val spaceId = mediaToSpaceMap[mediaId] ?: mediaId
        return lookupSpace(mediaId, _spaces.value, mediaToSpaceMap)
    }

    override fun observeAllSpaces(): Flow<Map<String, XSpace>> {
        return _spaces.asStateFlow()
    }

    override fun observeSpaceForMedia(mediaId: String): Flow<XSpace?> {
        return _spaces.map { map -> lookupSpace(mediaId, map, mediaToSpaceMap) }
    }

    private fun lookupSpace(
        mediaId: String,
        map: Map<String, XSpace>,
        mediaMap: Map<String, String>,
    ): XSpace? {
        val spaceId = mediaMap[mediaId] ?: mediaId
        map[spaceId]?.let { return it }
        return map.values.firstOrNull { space ->
            space.id == mediaId ||
                space.url == mediaId ||
                space.audioStreamUrl == mediaId
        }
    }
}
