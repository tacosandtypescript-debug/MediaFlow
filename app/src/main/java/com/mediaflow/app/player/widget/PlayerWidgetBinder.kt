package com.mediaflow.app.player.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.mediaflow.app.R
import com.mediaflow.data.player.external.PlayerExternalSnapshot
import com.mediaflow.data.player.notification.PlaybackTransportActions

/** Maps [PlayerExternalSnapshot] onto the home-screen RemoteViews. */
object PlayerWidgetBinder {
    fun bind(
        context: Context,
        snapshot: PlayerExternalSnapshot?,
        artwork: Bitmap?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_player)
        if (snapshot == null || snapshot.mediaId.isNullOrBlank()) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_idle_title))
            views.setTextViewText(R.id.widget_artist, context.getString(R.string.widget_idle_artist))
            views.setImageViewResource(R.id.widget_play_pause, android.R.drawable.ic_media_play)
            views.setContentDescription(R.id.widget_play_pause, context.getString(R.string.widget_play))
        } else {
            views.setTextViewText(R.id.widget_title, snapshot.title)
            views.setTextViewText(R.id.widget_artist, snapshot.artist)
            if (snapshot.isPlaying) {
                views.setImageViewResource(R.id.widget_play_pause, android.R.drawable.ic_media_pause)
                views.setContentDescription(R.id.widget_play_pause, context.getString(R.string.widget_pause))
            } else {
                views.setImageViewResource(R.id.widget_play_pause, android.R.drawable.ic_media_play)
                views.setContentDescription(R.id.widget_play_pause, context.getString(R.string.widget_play))
            }
        }
        if (artwork != null) {
            views.setImageViewBitmap(R.id.widget_cover, artwork)
        } else {
            views.setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher)
        }
        val playing = snapshot?.isPlaying == true
        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            PlaybackTransportActions.serviceIntent(
                context,
                if (playing) PlaybackTransportActions.ACTION_PAUSE else PlaybackTransportActions.ACTION_PLAY,
            ),
        )
        views.setOnClickPendingIntent(
            R.id.widget_prev,
            PlaybackTransportActions.serviceIntent(context, PlaybackTransportActions.ACTION_PREV),
        )
        views.setOnClickPendingIntent(
            R.id.widget_next,
            PlaybackTransportActions.serviceIntent(context, PlaybackTransportActions.ACTION_NEXT),
        )
        val open = PlaybackTransportActions.nowPlayingIntent(context, snapshot?.mediaId)
        views.setOnClickPendingIntent(R.id.widget_player_root, open)
        views.setOnClickPendingIntent(R.id.widget_cover, open)
        views.setOnClickPendingIntent(R.id.widget_text, open)
        return views
    }

    fun push(context: Context, appWidgetIds: IntArray, views: RemoteViews) {
        val manager = AppWidgetManager.getInstance(context)
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, views) }
    }
}
