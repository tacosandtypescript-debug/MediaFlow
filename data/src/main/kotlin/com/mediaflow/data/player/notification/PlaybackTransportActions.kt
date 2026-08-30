package com.mediaflow.data.player.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.mediaflow.data.player.background.MediaPlaybackService

/** Transport intents shared by notification, widget, and headset via the playback service. */
object PlaybackTransportActions {
    const val ACTION_PLAY = "com.mediaflow.action.PLAY"
    const val ACTION_PAUSE = "com.mediaflow.action.PAUSE"
    const val ACTION_STOP = "com.mediaflow.action.STOP"
    const val ACTION_PREV = "com.mediaflow.action.PREV"
    const val ACTION_NEXT = "com.mediaflow.action.NEXT"
    const val EXTRA_OPEN_PLAYER = "extra_open_player"

    fun serviceIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, MediaPlaybackService::class.java).apply { this.action = action }
        val request = action.hashCode()
        return PendingIntent.getService(
            context,
            request,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun nowPlayingIntent(context: Context, mediaId: String?): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_PLAYER, true)
            putExtra(MediaPlaybackService.EXTRA_MEDIA_URI, mediaId)
        } ?: Intent()
        return PendingIntent.getActivity(
            context,
            10,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
