package com.mediaflow.data.player.external

import com.mediaflow.core.model.XSpace
import com.mediaflow.domain.player.PlayerServiceState

/**
 * Shared view of the current track for notification, lock screen, and widget.
 * Built only from [PlayerServiceState] so those surfaces cannot drift.
 */
data class PlayerExternalSnapshot(
    val mediaId: String?,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val isPlaying: Boolean,
    val isLive: Boolean,
    val durationMs: Long,
    val positionMs: Long,
) {
    val visualKey: String
        get() = listOf(mediaId.orEmpty(), title, artist, artworkUrl.orEmpty(), isPlaying, isLive).joinToString("|")
}

object PlayerExternalSnapshotFactory {
    fun from(state: PlayerServiceState, space: XSpace? = null): PlayerExternalSnapshot {
        val title = space?.title?.takeIf { it.isNotBlank() }
            ?: state.title?.takeIf { it.isNotBlank() }
            ?: "MediaFlow"
        val artist = when {
            space != null -> {
                val live = if (state.isLive || space.isLive) " · En vivo" else ""
                "Host: ${space.host.formattedHandle}$live"
            }
            !state.artistOrHost.isNullOrBlank() -> state.artistOrHost!!
            state.isLive -> "En vivo"
            state.isPlaying -> "Reproduciendo"
            else -> "En pausa"
        }
        return PlayerExternalSnapshot(
            mediaId = state.mediaId ?: state.filePath,
            title = title,
            artist = artist,
            artworkUrl = state.artworkUrl,
            isPlaying = state.isPlaying,
            isLive = state.isLive || space?.isLive == true,
            durationMs = state.durationMs,
            positionMs = state.currentPositionMs,
        )
    }
}
