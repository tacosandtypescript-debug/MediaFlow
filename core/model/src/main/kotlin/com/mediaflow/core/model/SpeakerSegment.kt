package com.mediaflow.core.model

/**
 * Timestamped speaker turn segment, prepared for future audio diarization.
 */
data class SpeakerSegment(
    val speakerId: String,
    val startSeconds: Double,
    val endSeconds: Double,
    val textSnippet: String? = null,
)
