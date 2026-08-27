package com.mediaflow.core.model

/**
 * Metadata of a multimedia format.
 *
 * Fields that may be unavailable on the source are optional. A format is only
 * ever reported from a real analyzed source; nothing here asserts availability.
 */
data class MediaFormat(
    val formatId: String,
    val extension: String? = null,
    val mimeType: String? = null,
    val mediaType: MediaType,
    val qualityLabel: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val container: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val durationSeconds: Long? = null,
    val bitrate: Long? = null,
    val fileSize: Long? = null,
    val isProgressive: Boolean = false,
    val requiresMuxing: Boolean = false,
)
