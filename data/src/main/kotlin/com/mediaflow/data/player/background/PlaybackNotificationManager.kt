package com.mediaflow.data.player.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.support.v4.media.session.MediaSessionCompat
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mediaflow.core.model.XSpace
import com.mediaflow.data.player.artwork.PlaybackArtworkLoader
import com.mediaflow.data.player.external.PlayerExternalSnapshotFactory
import com.mediaflow.data.player.notification.PlaybackTransportActions
import com.mediaflow.domain.player.PlayerServiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MediaStyle notification: cover, title, artist, previous / play-pause / next.
 */
class PlaybackNotificationManager(
    private val context: Context,
) {
    companion object {
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "mediaflow_playback_channel"
        private const val CHANNEL_NAME = "Reproducción de medios"

        const val ACTION_PLAY = PlaybackTransportActions.ACTION_PLAY
        const val ACTION_PAUSE = PlaybackTransportActions.ACTION_PAUSE
        const val ACTION_STOP = PlaybackTransportActions.ACTION_STOP
        const val ACTION_PREV = PlaybackTransportActions.ACTION_PREV
        const val ACTION_NEXT = PlaybackTransportActions.ACTION_NEXT
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Controles de reproducción multimedia en primer plano"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    suspend fun loadArtworkBitmap(url: String?): Bitmap? = withContext(Dispatchers.IO) {
        PlaybackArtworkLoader.load(context, url)
    }

    fun buildNotification(
        serviceState: PlayerServiceState,
        space: XSpace? = null,
        sessionToken: MediaSessionCompat.Token? = null,
        artwork: Bitmap? = null,
    ): Notification {
        val snapshot = PlayerExternalSnapshotFactory.from(serviceState, space)
        val playPauseIntent = PlaybackTransportActions.serviceIntent(
            context,
            if (snapshot.isPlaying) ACTION_PAUSE else ACTION_PLAY,
        )
        val playPauseIcon = if (snapshot.isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseTitle = if (snapshot.isPlaying) "Pausar" else "Reproducir"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(snapshot.title)
            .setContentText(snapshot.artist)
            .setSubText(if (snapshot.isLive) "En vivo" else null)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(PlaybackTransportActions.nowPlayingIntent(context, snapshot.mediaId))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(snapshot.isPlaying)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .addAction(
                android.R.drawable.ic_media_previous,
                "Anterior",
                PlaybackTransportActions.serviceIntent(context, ACTION_PREV),
            )
            .addAction(playPauseIcon, playPauseTitle, playPauseIntent)
            .addAction(
                android.R.drawable.ic_media_next,
                "Siguiente",
                PlaybackTransportActions.serviceIntent(context, ACTION_NEXT),
            )

        if (artwork != null) {
            builder.setLargeIcon(artwork)
        }

        val style = androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(0, 1, 2)
        if (sessionToken != null) {
            style.setMediaSession(sessionToken)
        }
        builder.setStyle(style)

        return builder.build()
    }
}
