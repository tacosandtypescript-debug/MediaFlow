package com.mediaflow.data.provider.x.recording

data class RecordingMarker(
    val relativeTimestampMs: Long,
    val label: String? = null,
)
