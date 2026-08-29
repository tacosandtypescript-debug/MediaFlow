package com.mediaflow.data.provider.x.live

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
    private val replayWaitDelaysMs: List<Long> = REPLAY_WAIT_DELAYS_MS,
    private val maxReplayWaitAttempts: Int = MAX_REPLAY_WAIT_ATTEMPTS,
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {
    companion object {
        val REPLAY_WAIT_DELAYS_MS = listOf(5_000L, 15_000L, 30_000L, 60_000L, 120_000L)
        const val MAX_REPLAY_WAIT_ATTEMPTS = 8
        const val REPLAY_TIMEOUT_MESSAGE = "La repetición no está lista. Inténtalo más tarde."

        fun isStillBroadcasting(result: ReplayResolutionResult): Boolean {
            val message = (result as? ReplayResolutionResult.Processing)?.message ?: return false
            return message.contains("directo", ignoreCase = true) ||
                message.contains("comenzado", ignoreCase = true) ||
                message.contains("activo", ignoreCase = true)
        }
    }
    /**
     * Checks if a space has ended. If initial check is inconclusive or network is transitioning,
     * performs bounded retries with progressive backoff before declaring ENDED.
     */
    suspend fun verifySpaceEnded(
        spaceId: String,
        originalUrl: String,
        onProgress: suspend (attempt: Int, message: String) -> Unit = { _, _ -> },
    ): ReplayResolutionResult = withContext(Dispatchers.IO) {
        var currentDelay = baseDelayMs

        for (attempt in 1..maxRetries) {
            onProgress(attempt, "Verificando estado del Space (intento $attempt/$maxRetries)...")

            val space = runCatching { metadataResolver.resolve(spaceId, originalUrl) }.getOrNull()

            if (space != null) {
                when (space.state) {
                    XSpaceState.ENDED, XSpaceState.TIMED_OUT -> {
                        return@withContext replayResolver.resolveFromSpace(space)
                    }
                    XSpaceState.LIVE -> {
                        return@withContext ReplayResolutionResult.Processing("El Space sigue activo en directo.")
                    }
                    XSpaceState.UPCOMING -> {
                        return@withContext ReplayResolutionResult.Processing("El Space no ha comenzado.")
                    }
                    XSpaceState.UNKNOWN -> Unit
                }
            }

            if (attempt < maxRetries) {
                sleeper(currentDelay)
                currentDelay *= 2
            }
        }

        replayResolver.resolveReplay(spaceId, originalUrl)
    }

    /**
     * After confirming ENDED, waits a bounded number of times for X to publish a replay URL.
     */
    suspend fun waitForReplay(
        spaceId: String,
        originalUrl: String,
        onProgress: suspend (attempt: Int, message: String) -> Unit = { _, _ -> },
    ): ReplayResolutionResult = withContext(Dispatchers.IO) {
        var result = verifySpaceEnded(spaceId, originalUrl)
        if (isStillBroadcasting(result) || result is ReplayResolutionResult.Available || result is ReplayResolutionResult.NotAvailable) {
            return@withContext result
        }

        if (result is ReplayResolutionResult.Processing || result is ReplayResolutionResult.Error) {
            onProgress(1, "Esperando repetición")
        }

        var attempt = 1
        while (attempt < maxReplayWaitAttempts) {
            val delayMs = replayWaitDelaysMs.getOrElse(attempt - 1) { replayWaitDelaysMs.last() }
            attempt += 1
            onProgress(attempt, "Esperando repetición")
            sleeper(delayMs)
            result = replayResolver.resolveReplay(spaceId, originalUrl)
            when {
                result is ReplayResolutionResult.Available -> return@withContext result
                result is ReplayResolutionResult.NotAvailable -> return@withContext result
                isStillBroadcasting(result) -> return@withContext result
            }
        }

        when (result) {
            is ReplayResolutionResult.Available -> result
            is ReplayResolutionResult.NotAvailable -> result
            else -> ReplayResolutionResult.NotAvailable(REPLAY_TIMEOUT_MESSAGE)
        }
    }
}
