package com.mediaflow.core.model

/**
 * Normalized role of an individual associated with an X Space.
 */
enum class ParticipantRole {
    HOST,
    COHOST,
    SPEAKER,
    LISTENER,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): ParticipantRole = when (value?.trim()?.uppercase()) {
            "HOST", "ADMIN" -> HOST
            "COHOST" -> COHOST
            "SPEAKER" -> SPEAKER
            "LISTENER" -> LISTENER
            else -> UNKNOWN
        }
    }
}
