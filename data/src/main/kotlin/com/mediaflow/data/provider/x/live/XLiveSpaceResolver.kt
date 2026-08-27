package com.mediaflow.data.provider.x.live

import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.provider.x.XUrlParser
import com.mediaflow.data.provider.x.spaces.XSpaceMetadataResolver
import com.mediaflow.domain.model.LiveSpaceSource
import com.mediaflow.domain.repository.LiveSpaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolver for verifying and obtaining playable HLS streams for live X Spaces.
 */
class XLiveSpaceResolver(
    private val metadataResolver: XSpaceMetadataResolver = XSpaceMetadataResolver(),
    private val liveStreamClient: XLiveStreamClient = XLiveStreamClient(),
) : LiveSpaceRepository {

    override suspend fun resolveLiveSpace(urlOrId: String): Result<LiveSpaceSource> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = urlOrId.trim()
            val spaceId = XUrlParser.extractDirectSpaceId(trimmed)
                ?: if (trimmed.matches(Regex("^[0-9a-zA-Z]{10,20}$"))) trimmed else null
                ?: throw IllegalArgumentException("No se pudo identificar un Space ID válido en la URL")

            val originalUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "https://x.com/i/spaces/$spaceId"
            }

            val space: XSpace = metadataResolver.resolve(spaceId, originalUrl)

            when (space.state) {
                XSpaceState.UPCOMING -> {
                    throw IllegalStateException("Este Space todavía no ha comenzado (Programado)")
                }
                XSpaceState.ENDED, XSpaceState.TIMED_OUT -> {
                    val replayUrl = space.audioStreamUrl
                    if (replayUrl != null) {
                        // Allow replay if available
                        return@runCatching LiveSpaceSource(
                            spaceId = space.id,
                            title = space.title,
                            host = space.host,
                            streamUrl = replayUrl,
                            state = space.state,
                            liveListenersCount = space.liveListenersCount,
                            startedAtMs = space.startedAtMs ?: space.createdAtMs,
                            allSpeakers = space.allSpeakers,
                            rawSpace = space,
                        )
                    } else {
                        throw IllegalStateException("Este Space ya terminó y la grabación no está disponible")
                    }
                }
                XSpaceState.LIVE, XSpaceState.UNKNOWN -> {
                    val streamUrl = space.audioStreamUrl
                        ?: throw IllegalStateException("El stream en vivo no está disponible en este momento")

                    LiveSpaceSource(
                        spaceId = space.id,
                        title = space.title,
                        host = space.host,
                        streamUrl = streamUrl,
                        state = XSpaceState.LIVE,
                        liveListenersCount = space.liveListenersCount,
                        startedAtMs = space.startedAtMs ?: space.createdAtMs,
                        allSpeakers = space.allSpeakers,
                        rawSpace = space,
                    )
                }
            }
        }
    }
}
