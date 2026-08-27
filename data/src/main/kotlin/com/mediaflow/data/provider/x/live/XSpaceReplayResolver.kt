package com.mediaflow.data.provider.x.live

import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.provider.x.spaces.XSpaceMetadataResolver
import com.mediaflow.domain.live.ReplayResolutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves replay status and download URL for ended X Spaces.
 */
class XSpaceReplayResolver(
    private val metadataResolver: XSpaceMetadataResolver = XSpaceMetadataResolver(),
) {
    suspend fun resolveReplay(spaceId: String, originalUrl: String): ReplayResolutionResult = withContext(Dispatchers.IO) {
        runCatching {
            val space: XSpace = metadataResolver.resolve(spaceId, originalUrl)

            when (space.state) {
                XSpaceState.UPCOMING -> {
                    ReplayResolutionResult.Processing("Este Space todavía no ha comenzado.")
                }
                XSpaceState.LIVE -> {
                    ReplayResolutionResult.Processing("El Space sigue transmitiendo en directo.")
                }
                XSpaceState.ENDED, XSpaceState.TIMED_OUT, XSpaceState.UNKNOWN -> {
                    val streamUrl = space.audioStreamUrl
                    if (streamUrl != null) {
                        ReplayResolutionResult.Available(streamUrl, space)
                    } else if (space.recordingAvailable) {
                        ReplayResolutionResult.Processing("La grabación del Space se está procesando.")
                    } else {
                        ReplayResolutionResult.NotAvailable("El host finalizó el Space sin guardar la grabación.")
                    }
                }
            }
        }.getOrElse { error ->
            ReplayResolutionResult.Error(error.message ?: "No se pudo consultar el estado de la grabación.")
        }
    }
}
