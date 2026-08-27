package com.mediaflow.domain.live

import com.mediaflow.core.model.XSpace

/**
 * Result representing the outcome of checking for an X Space replay.
 */
sealed class ReplayResolutionResult {
    data class Available(val replayUrl: String, val space: XSpace) : ReplayResolutionResult()
    data class Processing(val message: String = "La grabación se está procesando en los servidores de X.") : ReplayResolutionResult()
    data class NotAvailable(val reason: String = "El host no habilitó la grabación para este Space.") : ReplayResolutionResult()
    data class Error(val message: String) : ReplayResolutionResult()
}
