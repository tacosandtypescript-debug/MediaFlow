package com.mediaflow.data.provider.x.live

import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState
import com.mediaflow.data.provider.x.spaces.XSpaceMetadataResolver
import com.mediaflow.domain.live.ReplayResolutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Robust monitor to verify whether an active live Space has legitimately ended.
 * Prevents false positives caused by temporary buffering glitches or mobile network switches.
 */
class LiveSpaceEndMonitor(
    private val metadataResolver: XSpaceMetadataResolver = XSpaceMetadataResolver(),
    private val replayResolver: XSpaceReplayResolver = XSpaceReplayResolver(metadataResolver),
    private val maxRetries: Int = 3,
    private val baseDelayMs: Long = 3_000L,
) {
    /**
     * Checks if a space has ended. If initial check is inconclusive or network is transitioning,
     * performs bounded retries with progressive backoff before declaring ENDED.
     */
    suspend fun verifySpaceEnded(
        spaceId: String,
        originalUrl: String,
        onProgress: (attempt: Int, message: String) -> Unit = { _, _ -> },
    ): ReplayResolutionResult = withContext(Dispatchers.IO) {
        var currentDelay = baseDelayMs

        for (attempt in 1..maxRetries) {
            onProgress(attempt, "Verificando estado del Space (intento $attempt/$maxRetries)...")

            val space = runCatching { metadataResolver.resolve(spaceId, originalUrl) }.getOrNull()

            if (space != null) {
                when (space.state) {
                    XSpaceState.ENDED, XSpaceState.TIMED_OUT -> {
                        // Confirmed ended! Resolve replay URL
                        return@withContext replayResolver.resolveReplay(spaceId, originalUrl)
                    }
                    XSpaceState.LIVE -> {
                        // Still live according to X GraphQL! It was a temporary network glitch or reconnection needed
                        return@withContext ReplayResolutionResult.Processing("El Space sigue activo en directo.")
                    }
                    XSpaceState.UPCOMING -> {
                        return@withContext ReplayResolutionResult.Processing("El Space no ha comenzado.")
                    }
                    XSpaceState.UNKNOWN -> Unit
                }
            }

            if (attempt < maxRetries) {
                delay(currentDelay)
                currentDelay *= 2
            }
        }

        // Final attempt via replay resolver
        replayResolver.resolveReplay(spaceId, originalUrl)
    }
}
