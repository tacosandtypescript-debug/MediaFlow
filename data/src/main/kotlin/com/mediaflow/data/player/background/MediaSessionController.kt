package com.mediaflow.data.player.background

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import com.mediaflow.core.model.XSpace
import com.mediaflow.domain.player.EnginePlaybackState
import com.mediaflow.domain.player.PlayerServiceState

/**
 * Manages the Android system [MediaSession] for lock screen controls, bluetooth devices,
 * headset hardware buttons, and system media control centers.
 */
class MediaSessionController(
    context: Context,
    private val onPlayRequested: () -> Unit,
    private val onPauseRequested: () -> Unit,
    private val onStopRequested: () -> Unit,
    private val onSeekRequested: (Long) -> Unit = {},
    private val onSkipNextRequested: () -> Unit = {},
    private val onSkipPreviousRequested: () -> Unit = {},
) {
    private val mediaSession: MediaSession = MediaSession(context, "MediaFlowPlaybackSession").apply {
        setFlags(
            MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
        )
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                onPlayRequested()
            }

            override fun onPause() {
                onPauseRequested()
            }

            override fun onStop() {
                onStopRequested()
            }

            override fun onSeekTo(pos: Long) {
                onSeekRequested(pos)
            }

            override fun onSkipToNext() {
                onSkipNextRequested()
            }

            override fun onSkipToPrevious() {
                onSkipPreviousRequested()
            }
        })
        isActive = true
    }

    val sessionToken: MediaSession.Token
        get() = mediaSession.sessionToken

    /**
     * Updates session playback state and available transport actions.
     */
    fun updatePlaybackState(serviceState: PlayerServiceState) {
        val stateBuilder = PlaybackState.Builder()

        val playbackStateCode = when (serviceState.playbackState) {
            EnginePlaybackState.PLAYING -> PlaybackState.STATE_PLAYING
            EnginePlaybackState.PAUSED -> PlaybackState.STATE_PAUSED
            EnginePlaybackState.PREPARING -> PlaybackState.STATE_BUFFERING
            EnginePlaybackState.ENDED -> PlaybackState.STATE_STOPPED
            EnginePlaybackState.ERROR -> PlaybackState.STATE_ERROR
            EnginePlaybackState.IDLE -> PlaybackState.STATE_NONE
        }

        var actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_STOP

        if (!serviceState.isLive) {
            actions = actions or PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_REWIND or
                PlaybackState.ACTION_FAST_FORWARD
        }

        stateBuilder.setActions(actions)
        stateBuilder.setState(
            playbackStateCode,
            serviceState.currentPositionMs,
            if (serviceState.isPlaying) serviceState.speed else 0f,
        )

        mediaSession.setPlaybackState(stateBuilder.build())
    }

    /**
     * Updates session metadata (title, host, duration, artwork).
     */
    fun updateMetadata(
        title: String,
        artist: String? = null,
        album: String? = null,
        durationMs: Long = 0L,
        artworkBitmap: Bitmap? = null,
    ) {
        val metaBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist ?: "MediaFlow")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, album ?: "Audio")

        if (durationMs > 0L) {
            metaBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
        }

        if (artworkBitmap != null) {
            metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artworkBitmap)
            metaBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, artworkBitmap)
        }

        mediaSession.setMetadata(metaBuilder.build())
    }

    fun release() {
        mediaSession.isActive = false
        mediaSession.release()
    }
}
