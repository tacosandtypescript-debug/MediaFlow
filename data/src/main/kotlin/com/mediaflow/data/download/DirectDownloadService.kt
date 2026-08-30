package com.mediaflow.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.scheduler.Scheduler
import com.mediaflow.data.R

/**
 * Background service hosting the single Media3 DownloadManager.
 *
 * Media3 keeps the DownloadIndex persistent and drives this service while
 * transfers continue in the background. It does not know anything about UI.
 */
@OptIn(markerClass = [UnstableApi::class])
class DirectDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    NOTIFICATION_CHANNEL_ID,
    R.string.download_notification_channel,
    R.string.download_notification_description,
) {
    override fun getDownloadManager(): DownloadManager =
        Media3DownloadInfrastructure.get(this).downloadManager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification {
        ensureNotificationChannel(this)
        val active = downloads.count { it.state == Download.STATE_DOWNLOADING }
        val title = getString(R.string.download_notification_title)
        val text = if (active > 0) {
            "$active descarga(s) activa(s)"
        } else {
            getString(R.string.download_notification_idle)
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(contentIntent(this))
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "mediaflow_downloads"
        private const val FOREGROUND_NOTIFICATION_ID = 2001

        fun addDownload(context: Context, request: DownloadRequest) {
            sendAddDownload(context, DirectDownloadService::class.java, request, true)
        }

        fun removeDownload(context: Context, id: String) {
            sendRemoveDownload(context, DirectDownloadService::class.java, id, true)
        }

        fun setStopReason(context: Context, id: String, reason: Int) {
            sendSetStopReason(context, DirectDownloadService::class.java, id, reason, true)
        }

        private fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(
                    NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        context.getString(R.string.download_notification_channel),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = context.getString(R.string.download_notification_title)
                    },
                )
            }
        }

        private fun contentIntent(context: Context): PendingIntent {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent()
            return PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
