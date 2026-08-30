package com.mediaflow.data.provider.x.dvr

data class DvrSegment(
    val index: Int,
    val relativeStartMs: Long,
    val payload: ByteArray,
) {
    val durationMs: Long get() = payload.size.toLong()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DvrSegment) return false
        return index == other.index &&
            relativeStartMs == other.relativeStartMs &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + relativeStartMs.hashCode()
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
