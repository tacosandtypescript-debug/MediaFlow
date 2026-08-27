package com.mediaflow.domain.model

import com.mediaflow.core.model.XParticipant
import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState

/**
 * Encapsulates the resolved live stream playback source for an active X Space.
 */
data class LiveSpaceSource(
    val spaceId: String,
    val title: String,
    val host: XParticipant,
    val streamUrl: String,
    val state: XSpaceState = XSpaceState.LIVE,
    val liveListenersCount: Int = 0,
    val startedAtMs: Long? = null,
    val allSpeakers: List<XParticipant> = emptyList(),
    val rawSpace: XSpace? = null,
) {
    val isLive: Boolean
        get() = state == XSpaceState.LIVE
}
