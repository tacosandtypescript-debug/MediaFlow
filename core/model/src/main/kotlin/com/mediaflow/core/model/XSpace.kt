package com.mediaflow.core.model

/**
 * Complete normalized model representing an X Space session with rich participant metadata.
 */
data class XSpace(
    val id: String,
    val url: String,
    val title: String,
    val state: XSpaceState = XSpaceState.UNKNOWN,
    val host: XParticipant,
    val cohosts: List<XParticipant> = emptyList(),
    val speakers: List<XParticipant> = emptyList(),
    val participants: List<XParticipant> = emptyList(),
    val createdAtMs: Long? = null,
    val startedAtMs: Long? = null,
    val endedAtMs: Long? = null,
    val durationSeconds: Long = 0L,
    val recordingAvailable: Boolean = false,
    val liveListenersCount: Int = 0,
    val replayCount: Int = 0,
    val audioStreamUrl: String? = null,
    val speakerSegments: List<SpeakerSegment> = emptyList(),
    val rawMetadata: String? = null,
) {
    /**
     * All individuals who had speaking roles (Host, Co-hosts, and Speakers).
     */
    val allSpeakers: List<XParticipant>
        get() {
            val list = mutableListOf<XParticipant>()
            list.add(host)
            list.addAll(cohosts)
            list.addAll(speakers)
            return list.distinctBy { it.userId ?: it.cleanUsername.lowercase() }
        }

    /**
     * Human-friendly formatted duration (e.g. "1 h 30 min" or "45 min").
     */
    val formattedDuration: String
        get() {
            val totalSeconds = durationSeconds.coerceAtLeast(0L)
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            return when {
                hours > 0 && minutes > 0 -> "$hours h $minutes min"
                hours > 0 -> "$hours h"
                minutes > 0 -> "$minutes min"
                totalSeconds > 0 -> "$totalSeconds seg"
                else -> "--"
            }
        }

    /**
     * Comma-separated summary of speakers for discrete UI cards.
     */
    val speakersSummary: String
        get() = allSpeakers.joinToString(", ") { it.formattedHandle }
}
