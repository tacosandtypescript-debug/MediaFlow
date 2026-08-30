package com.mediaflow.data.provider.x.spaces

import com.mediaflow.core.model.XSpace
import com.mediaflow.core.model.XSpaceState

/** User-facing Space lifecycle. Live never invents a duration. */
enum class SpaceAvailability {
    LIVE,
    ENDED,
    SCHEDULED,
    UNAVAILABLE,
}

object SpaceAvailabilityResolver {
    fun from(space: XSpace): SpaceAvailability = when (space.state) {
        XSpaceState.LIVE ->
            if (!space.audioStreamUrl.isNullOrBlank()) SpaceAvailability.LIVE else SpaceAvailability.UNAVAILABLE
        XSpaceState.UPCOMING -> SpaceAvailability.SCHEDULED
        XSpaceState.ENDED, XSpaceState.TIMED_OUT ->
            if (space.recordingAvailable && !space.audioStreamUrl.isNullOrBlank()) {
                SpaceAvailability.ENDED
            } else {
                SpaceAvailability.UNAVAILABLE
            }
        XSpaceState.UNKNOWN -> SpaceAvailability.UNAVAILABLE
    }

    /** Live streams have no final duration; only ended replays may report seconds. */
    fun displayDurationSeconds(space: XSpace): Long? {
        if (from(space) == SpaceAvailability.LIVE) return null
        return space.durationSeconds.takeIf { it > 0L }
    }
}
