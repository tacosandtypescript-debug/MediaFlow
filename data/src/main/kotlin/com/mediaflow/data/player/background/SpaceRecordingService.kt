package com.mediaflow.data.player.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mediaflow.data.R
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Foreground data-sync sibling to [MediaPlaybackService].
 * Notification: "Recording X Space", elapsed time, Stop. Pause playback is unrelated.
 */
class SpaceRecordingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val elapsedMs = intent?.getLongExtra(EXTRA_ELAPSED_MS, 0L) ?: 0L
        val notification = buildNotification(elapsedMs)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun buildNotification(elapsedMs: Long): Notification {
        ensureChannel()
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SpaceRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notification)
            .setContentTitle(NOTIFICATION_TITLE)
            .setContentText(formatElapsed(elapsedMs))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        const val ACTION_STOP = "com.mediaflow.action.STOP_SPACE_RECORDING"
        const val EXTRA_ELAPSED_MS = "extra_elapsed_ms"
        const val NOTIFICATION_TITLE = "Recording X Space"
        const val CHANNEL_ID = "mediaflow_space_recording"
        const val CHANNEL_NAME = "Grabación de Spaces"
        const val NOTIFICATION_ID = 1004

        fun start(context: Context, elapsedMs: Long) {
            val intent = Intent(context, SpaceRecordingService::class.java)
                .putExtra(EXTRA_ELAPSED_MS, elapsedMs)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, SpaceRecordingService::class.java).setAction(ACTION_STOP),
            )
        }

        fun formatElapsed(elapsedMs: Long): String {
            val total = TimeUnit.MILLISECONDS.toSeconds(elapsedMs.coerceAtLeast(0L))
            val hours = total / 3600
            val minutes = (total % 3600) / 60
            val seconds = total % 60
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%d:%02d", minutes, seconds)
            }
        }
    }
}
