package com.mediaflow.data.player.background

import android.content.Context
import android.graphics.Bitmap
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.mediaflow.domain.player.EnginePlaybackState
import com.mediaflow.domain.player.PlayerServiceState

/**
 * Android MediaSession for lock screen, Bluetooth, headsets, and MediaStyle.
 * Driven only by [PlayerServiceState].
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
    private val mediaSession: MediaSessionCompat = MediaSessionCompat(context, "MediaFlowPlaybackSession").apply {
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = onPlayRequested()
            override fun onPause() = onPauseRequested()
            override fun onStop() = onStopRequested()
            override fun onSeekTo(pos: Long) = onSeekRequested(pos)
            override fun onSkipToNext() = onSkipNextRequested()
            override fun onSkipToPrevious() = onSkipPreviousRequested()
        })
        isActive = true
    }

    val sessionToken: MediaSessionCompat.Token
        get() = mediaSession.sessionToken

    fun updatePlaybackState(serviceState: PlayerServiceState) {
        val playbackStateCode = when (serviceState.playbackState) {
            EnginePlaybackState.PLAYING -> PlaybackStateCompat.STATE_PLAYING
            EnginePlaybackState.PAUSED -> PlaybackStateCompat.STATE_PAUSED
            EnginePlaybackState.PREPARING -> PlaybackStateCompat.STATE_BUFFERING
            EnginePlaybackState.ENDED -> PlaybackStateCompat.STATE_STOPPED
            EnginePlaybackState.ERROR -> PlaybackStateCompat.STATE_ERROR
            EnginePlaybackState.IDLE -> PlaybackStateCompat.STATE_NONE
        }

        var actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS

        if (!serviceState.isLive) {
            actions = actions or PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_REWIND or
                PlaybackStateCompat.ACTION_FAST_FORWARD
        }

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    playbackStateCode,
                    serviceState.currentPositionMs,
                    if (serviceState.isPlaying) serviceState.speed else 0f,
                )
                .build(),
        )
    }

    fun updateMetadata(
        title: String,
        artist: String? = null,
        album: String? = null,
        durationMs: Long = 0L,
        artworkBitmap: Bitmap? = null,
    ) {
        val metaBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist ?: "MediaFlow")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album ?: "Audio")
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, artist ?: "MediaFlow")

        if (durationMs > 0L) {
            metaBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
        }
        if (artworkBitmap != null) {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artworkBitmap)
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artworkBitmap)
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, artworkBitmap)
        }
        mediaSession.setMetadata(metaBuilder.build())
    }

    fun release() {
        mediaSession.isActive = false
        mediaSession.release()
    }
}
