package com.mediaflow.data.provider.x.recording

data class RecordedSpace(
    val spaceId: String,
    val originalUrl: String,
    val filePath: String,
    val elapsedMs: Long,
    val markers: List<RecordingMarker> = emptyList(),
)
