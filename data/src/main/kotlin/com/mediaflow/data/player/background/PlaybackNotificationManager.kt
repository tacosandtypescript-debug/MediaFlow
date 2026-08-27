package com.mediaflow.data.player.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.session.MediaSession
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mediaflow.core.model.XSpace
import com.mediaflow.domain.player.PlayerServiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Creates and updates foreground media playback notifications with transport controls,
 * Space metadata, live badge, and album art.
 */
class PlaybackNotificationManager(
    private val context: Context,
) {
    companion object {
        const val NOTIFICATION_ID = 1002
        const val CHANNEL_ID = "mediaflow_playback_channel"
        private const val CHANNEL_NAME = "Reproducción de medios"

        const val ACTION_PLAY = "com.mediaflow.action.PLAY"
        const val ACTION_PAUSE = "com.mediaflow.action.PAUSE"
        const val ACTION_STOP = "com.mediaflow.action.STOP"
        const val ACTION_PREV = "com.mediaflow.action.PREV"
        const val ACTION_NEXT = "com.mediaflow.action.NEXT"
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
        if (url.isNullOrBlank() || !url.startsWith("http")) return@withContext null
        runCatching {
            val stream = URL(url).openStream()
            val bmp = BitmapFactory.decodeStream(stream)
            stream.close()
            bmp
        }.getOrNull()
    }

    fun buildNotification(
        serviceState: PlayerServiceState,
        space: XSpace? = null,
        sessionToken: MediaSession.Token? = null,
        artwork: Bitmap? = null,
    ): Notification {
        val title = space?.title ?: serviceState.title ?: "MediaFlow Player"
        val isLive = serviceState.isLive || space?.isLive == true

        val hostText = if (space != null) {
            "Host: ${space.host.formattedHandle}" + if (isLive) " · 🔴 EN VIVO" else ""
        } else {
            if (isLive) "🔴 Transmisión en directo" else "Reproduciendo archivo local"
        }

        val openAppIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Play / Pause Action
        val playPauseIntent = Intent(context, MediaPlaybackService::class.java).apply {
            action = if (serviceState.isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            context,
            1,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Stop Action
        val stopIntent = Intent(context, MediaPlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(hostText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(serviceState.isPlaying)
            .setOnlyAlertOnce(true)

        if (artwork != null) {
            builder.setLargeIcon(artwork)
        }

        // Action: Play/Pause
        val playPauseIcon = if (serviceState.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseTitle = if (serviceState.isPlaying) "Pausar" else "Reproducir"
        builder.addAction(playPauseIcon, playPauseTitle, playPausePendingIntent)

        // Action: Stop
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPendingIntent)

        // Set Framework MediaStyle for modern Android notification widget
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val style = androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1)
            builder.setStyle(style)
        }

        return builder.build()
    }
}
