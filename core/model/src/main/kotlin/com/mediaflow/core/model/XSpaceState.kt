package com.mediaflow.core.model

/**
 * Lifecycle status of an X Space broadcast.
 */
enum class XSpaceState {
    UPCOMING,
    LIVE,
    ENDED,
    TIMED_OUT,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): XSpaceState = when (value?.trim()?.lowercase()) {
            "notstarted", "not_started", "upcoming" -> UPCOMING
            "running", "live" -> LIVE
            "ended" -> ENDED
            "timedout", "timed_out" -> TIMED_OUT
            else -> UNKNOWN
        }
    }
}
