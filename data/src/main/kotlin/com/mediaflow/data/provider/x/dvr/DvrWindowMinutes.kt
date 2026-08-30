package com.mediaflow.data.provider.x.dvr

enum class DvrWindowMinutes(val minutes: Int) {
    FIVE(5),
    FIFTEEN(15),
    THIRTY(30),
    SIXTY(60),
    ;

    val durationMs: Long get() = minutes * 60_000L
}
