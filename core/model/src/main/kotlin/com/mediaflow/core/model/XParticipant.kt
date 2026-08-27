package com.mediaflow.core.model

/**
 * Structured participant in an X Space session.
 */
data class XParticipant(
    val displayName: String,
    val username: String,
    val userId: String? = null,
    val avatarUrl: String? = null,
    val role: ParticipantRole = ParticipantRole.UNKNOWN,
) {
    val cleanUsername: String
        get() = username.removePrefix("@").trim()

    val formattedHandle: String
        get() = "@$cleanUsername"
}
